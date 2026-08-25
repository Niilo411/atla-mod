package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.AirScooterSeat;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.ModEntities;
import com.minecraft.atlamod.abilities.AbilitySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Every Air Scooter currently being ridden.
 *
 * Held in a static list rather than on the player's data, for the same reason
 * HeldBlocks and WaterSpheres are: the ride owns an ENTITY, and an entity reference
 * has no business being serialised into player NBT. Nothing here survives a restart,
 * which is exactly what is wanted — the seat is not saved either, so a crash mid-ride
 * leaves nothing behind to clean up.
 *
 * Every route out of a ride comes through {@link #stop}: pressing the key again,
 * running out of chi, shifting off the seat, dying, disconnecting, changing dimension
 * and the level unloading. There is no other way to end one, so none of them can
 * leave a player stuck seated or a seat orphaned.
 */
public final class AirScooters {

    /** Sprinting speed, near enough: vanilla sprint is about 0.28 blocks a tick. */
    private static final double TRAVEL_SPEED = 0.29;

    /** What Slipstream multiplies that by. */
    private static final double SLIPSTREAM_MULTIPLIER = 2.0;

    /**
     * How high above the ground the rider is carried — a full block.
     *
     * Note this is the RIDER'S FEET, and the seat is a player's 1.8 tall on top of
     * that, so a scooter needs about 2.8 blocks of headroom to pass. A normal
     * two-high doorway is too low to ride through; that is a consequence of hovering
     * a whole block up rather than a bug, and the fix if it ever grates is to hover
     * lower, not to shrink the seat (see AirScooterSeat.SIZE).
     */
    private static final double HOVER = 1.0;

    /** Fastest the scooter will sink when the ground drops away — the glide. */
    private static final double GLIDE_SPEED = 0.12;

    /** Fastest it climbs to get over a rise. Brisker than the drop, so steps feel crisp. */
    private static final double LIFT_SPEED = 0.25;

    /** How far ahead the ground is read, in ticks of travel, so a rise is met early. */
    private static final int LOOK_AHEAD_TICKS = 5;

    /**
     * How far up and down a column is searched for a surface to ride over. SCAN_DOWN
     * carries the extra block HOVER lifted the rider by, so the reach BELOW the ground
     * is the same as it was when the scooter rode lower.
     */
    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 6;

    /** Running costs, per second. */
    private static final int CHI_PER_SECOND = 5;
    private static final int XP_PER_SECOND = 1;

    private static final List<Ride> ACTIVE = new ArrayList<>();

    private AirScooters() {
    }

    private static final class Ride {
        final ServerLevel level;
        final UUID riderId;
        final AirScooterSeat seat;
        int ticks;

        Ride(ServerLevel level, UUID riderId, AirScooterSeat seat) {
            this.level = level;
            this.riderId = riderId;
            this.seat = seat;
        }
    }

    /** Whether this player is currently on a scooter. */
    public static boolean isRiding(ServerPlayer player) {
        return find(player.getUUID()) != null;
    }

    /**
     * Puts a player on a scooter.
     *
     * @return false if they could not be seated, in which case nothing was changed
     *         and the caller should not charge them for it
     */
    public static boolean start(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (isRiding(player)) return false;

        // Already in a boat, on a horse, or riding anything else at all. Stealing the
        // player out of another vehicle is a good way to break whatever put them there.
        if (player.isPassenger()) {
            player.displayClientMessage(
                    Component.literal("§bYou cannot scooter while riding something else!"), true);
            return false;
        }

        // Refused here as well as in the tick, or starting over water would toggle on
        // and cut out again a tick later, which reads as the key not working.
        if (player.isInWater() || overWater(level, player.position())) {
            player.displayClientMessage(
                    Component.literal("§bYour Air Scooter cannot cross water!"), true);
            return false;
        }

        AirScooterSeat seat = new AirScooterSeat(ModEntities.AIR_SCOOTER_SEAT.get(), level);
        seat.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);

        if (!level.addFreshEntity(seat)) return false;

        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }

        ACTIVE.add(new Ride(level, player.getUUID(), seat));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.9F, 1.4F);
        return true;
    }

    /**
     * Takes a player off their scooter, if they are on one. Safe to call on anyone,
     * at any time, however the ride is ending — that is the point of it.
     */
    public static void stop(ServerPlayer player) {
        Ride ride = find(player.getUUID());
        if (ride == null) return;

        ACTIVE.remove(ride);
        dismantle(ride, player);
    }

    /** Runs every scooter in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Ride> rides = ACTIVE.iterator();
        while (rides.hasNext()) {
            Ride ride = rides.next();
            ServerPlayer rider = server.getPlayerList().getPlayer(ride.riderId);

            if (!advance(ride, rider)) {
                rides.remove();
                dismantle(ride, rider);
            }
        }
    }

    /** @return false once this ride is over and should be taken down */
    private static boolean advance(Ride ride, ServerPlayer rider) {
        // Gone, dead, or moved worlds out from under us.
        if (rider == null || !rider.isAlive() || rider.level() != ride.level) return false;

        // Shifted off the seat, or something else pulled them out of it. Vanilla lets
        // a passenger dismount on its own, so this is a normal way for a ride to end
        // rather than an error — the key just stops being what turned it off.
        if (rider.getVehicle() != ride.seat) return false;
        if (ride.seat.isRemoved()) return false;

        // An air scooter is a thing for solid ground. Ride out over water and it
        // simply stops — checked before chi is taken, so the tick it fails on is free.
        if (ride.seat.isInWater() || overWater(ride.level, ride.seat.position())) {
            rider.displayClientMessage(
                    Component.literal("§bYour Air Scooter cannot cross water!"), true);
            return false;
        }

        BendingData data = rider.getData(ModAttachments.BENDING_DATA);

        int chiThisTick = perTick(CHI_PER_SECOND, ride.ticks);
        if (data.getCurrentChi() < chiThisTick) {
            rider.displayClientMessage(Component.literal("§bYour Air Scooter runs out of Chi."), true);
            return false;
        }

        if (chiThisTick > 0) data.consumeChi(chiThisTick);
        AbilitySupport.grantXp(data, perTick(XP_PER_SECOND, ride.ticks));

        // Every 4 ticks, matching the channelled abilities: enough for a responsive
        // chi bar without a packet every tick.
        if (ride.ticks % 4 == 0) {
            AbilitySupport.syncData(rider, data);
        }

        steer(ride, rider, data);
        drawBall(ride);

        ride.ticks++;
        return true;
    }

    /**
     * Moves the seat, and with it the rider.
     *
     * Travel is along where the rider is LOOKING, flattened: the vertical half of the
     * look vector is ignored on purpose, because height is the terrain's business
     * here, not the camera's. Looking up should not fly the scooter into the sky.
     */
    private static void steer(Ride ride, ServerPlayer rider, BendingData data) {
        Vec3 look = rider.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);

        double speed = data.hasUpgrade(AirScooter.SLIPSTREAM)
                ? TRAVEL_SPEED * SLIPSTREAM_MULTIPLIER
                : TRAVEL_SPEED;

        if (heading.lengthSqr() < 1.0E-4) {
            heading = Vec3.ZERO; // staring straight up or down: hold position
        } else {
            heading = heading.normalize().scale(speed);
        }

        double targetY = targetHeight(ride, heading);
        double rise = targetY - ride.seat.getY();
        double dy = rise > 0.0
                ? Math.min(rise, LIFT_SPEED)      // climbing over a rise
                : Math.max(rise, -GLIDE_SPEED);   // gliding down over a drop

        Vec3 motion = new Vec3(heading.x, dy, heading.z);
        ride.seat.setDeltaMovement(motion);

        // move() rather than setPos, so the seat is stopped by walls like anything
        // else. The rider is a passenger and does not collide on their own, so this
        // is the only thing keeping a scooter out of the side of a hill.
        ride.seat.move(MoverType.SELF, motion);

        // Kept facing the way it travels, which is what the rider's body follows.
        ride.seat.setYRot(rider.getYRot());
    }

    /**
     * The height the scooter wants to be at: half a block over the ground, reading
     * both underfoot and a little way ahead.
     *
     * Looking ahead is what turns "stops dead at a step" into "rides up over it" —
     * by the time the seat reaches the rise it has already started climbing.
     */
    private static double targetHeight(Ride ride, Vec3 heading) {
        Vec3 here = ride.seat.position();
        Vec3 ahead = here.add(heading.scale(LOOK_AHEAD_TICKS));

        double under = surfaceAt(ride.level, here, ride.seat.getY());
        double front = surfaceAt(ride.level, ahead, ride.seat.getY());

        double surface = Math.max(under, front);
        if (Double.isNaN(under) && Double.isNaN(front)) {
            // Nothing to ride over within reach — out over a canyon. Keep gliding
            // down until the ground comes back up into range.
            return ride.seat.getY() - GLIDE_SPEED;
        }
        if (Double.isNaN(under)) surface = front;
        if (Double.isNaN(front)) surface = under;

        return surface + HOVER;
    }

    /**
     * The top of the highest surface in this column that could be ridden on, or NaN
     * if there is none within reach.
     *
     * A "surface" is a block you could stand on: solid, with space above it. That
     * second half is what stops the scooter climbing sheer walls — every block of a
     * five-block wall has another block on top of it, so none of them qualifies, the
     * search falls through to the ground the wall stands on, and the seat simply
     * bumps into the wall the way it should.
     */
    private static double surfaceAt(ServerLevel level, Vec3 pos, double fromY) {
        int x = net.minecraft.util.Mth.floor(pos.x);
        int z = net.minecraft.util.Mth.floor(pos.z);
        int start = net.minecraft.util.Mth.floor(fromY) + SCAN_UP;

        for (int y = start; y >= start - (SCAN_UP + SCAN_DOWN); y--) {
            BlockPos ground = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(ground);
            if (!state.blocksMotion()) continue;

            if (!level.getBlockState(ground.above()).blocksMotion()) {
                return y + 1.0;
            }
        }

        return Double.NaN;
    }

    /**
     * Whether the scooter is over water.
     *
     * Looks straight down from the rider's feet and reports on whichever it meets
     * first, water or something solid. Meeting water first is what "over water" means
     * — a pond with a stone bed still counts, because the water is what the scooter
     * would be riding across.
     *
     * The seat's own position is checked too, for the case of drifting into water
     * from the side rather than out over it.
     */
    private static boolean overWater(ServerLevel level, Vec3 from) {
        int x = net.minecraft.util.Mth.floor(from.x);
        int z = net.minecraft.util.Mth.floor(from.z);
        int start = net.minecraft.util.Mth.floor(from.y);

        for (int y = start; y >= start - (SCAN_DOWN + 1); y--) {
            BlockPos pos = new BlockPos(x, y, z);

            if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }

            if (level.getBlockState(pos).blocksMotion()) return false;
        }

        return false;
    }

    /** The ball of air being ridden, churning under the rider. */
    private static void drawBall(Ride ride) {
        double cx = ride.seat.getX();
        double cy = ride.seat.getY();
        double cz = ride.seat.getZ();

        double spin = (ride.ticks % 8) / 8.0 * Math.PI * 2.0;
        for (int i = 0; i < 6; i++) {
            double angle = spin + (Math.PI * 2.0 * i / 6);
            ride.level.sendParticles(ParticleTypes.CLOUD,
                    cx + Math.cos(angle) * 0.5, cy - 0.2, cz + Math.sin(angle) * 0.5,
                    1, 0.05, 0.05, 0.05, 0.01);
        }

        ride.level.sendParticles(ParticleTypes.CLOUD, cx, cy - 0.3, cz, 2, 0.25, 0.05, 0.25, 0.02);

        if (ride.ticks % 12 == 0) {
            ride.level.sendParticles(ParticleTypes.SMALL_GUST, cx, cy - 0.2, cz, 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /**
     * Takes down one ride's leftovers. The rider may legitimately be gone by now
     * (disconnected, dead, or in another world), so everything here copes with null.
     */
    private static void dismantle(Ride ride, ServerPlayer rider) {
        if (rider != null && rider.getVehicle() == ride.seat) {
            rider.stopRiding();
        }

        if (!ride.seat.isRemoved()) {
            ride.seat.discard();
        }

        if (rider != null) {
            ride.level.playSound(null, rider.getX(), rider.getY(), rider.getZ(),
                    SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.6F, 1.3F);
            ride.level.sendParticles(ParticleTypes.CLOUD,
                    rider.getX(), rider.getY() + 0.2, rider.getZ(), 15, 0.4, 0.1, 0.4, 0.1);
        }
    }

    /**
     * Ends a ride when its rider leaves — death, disconnect, or a change of
     * dimension. Looked up by UUID rather than by entity, since on a dimension change
     * the ServerPlayer handed to us is a different object from the one that started.
     */
    public static void forgetPlayer(ServerPlayer player) {
        Ride ride = find(player.getUUID());
        if (ride == null) return;

        ACTIVE.remove(ride);
        dismantle(ride, player);
    }

    /**
     * Drops every ride in a level that is going away.
     *
     * These are held in a plain static list rather than by the world, so nothing else
     * would ever clear them, and a live seat would keep a dead ServerLevel reachable.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(ride -> {
            if (ride.level != level) return false;

            Entity rider = level.getEntity(ride.riderId);
            dismantle(ride, rider instanceof ServerPlayer player ? player : null);
            return true;
        });
    }

    private static Ride find(UUID riderId) {
        for (Ride ride : ACTIVE) {
            if (ride.riderId.equals(riderId)) return ride;
        }
        return null;
    }

    /**
     * Splits a per-second rate across the ticks of a second without drift, the same
     * way AbilityHandler pays for a channel: any 20 consecutive ticks add up to
     * exactly the rate, however badly 20 divides it.
     */
    private static int perTick(int ratePerSecond, int tick) {
        long rate = ratePerSecond;
        long t = Math.max(0, tick);
        return (int) ((rate * (t + 1)) / 20L - (rate * t) / 20L);
    }
}
