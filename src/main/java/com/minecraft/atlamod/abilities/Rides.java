package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.BendingSeat;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every bender currently being carried by an ability — Air Scooter across the ground,
 * Water Surf across the sea, Earth dig through the rock.
 *
 * Element-agnostic on purpose, like HeldBlocks and BendingProjectiles: the rides differ
 * only in where they travel, what makes one stop, and what they cost, so everything
 * else — seating the rider, steering by their crosshair, billing, and the half dozen
 * ways a ride can end — is written once. {@link Kind} holds the differences.
 *
 * Held in a static map rather than on player data, because a ride owns an ENTITY and
 * an entity reference has no business being serialised into player NBT. Nothing here
 * survives a restart, which is what is wanted: the seat is not saved either, so a
 * crash mid-ride leaves nothing behind to clean up.
 *
 * Every route out of a ride comes through {@link #stop}: pressing the key again,
 * running out of chi, leaving the surface the ride needs, shifting off the seat,
 * dying, disconnecting, changing dimension and the level unloading. There is no other
 * way to end one, so none of them can leave a player stuck seated or a seat orphaned.
 */
public final class Rides {

    /** How far up and down a column is searched for the surface to ride on. */
    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 6;

    /** How far ahead the surface is read, in ticks of travel, so a rise is met early. */
    private static final int LOOK_AHEAD_TICKS = 5;

    /** Fastest a ride will sink when the surface drops away — the glide. */
    private static final double GLIDE_SPEED = 0.12;

    /** Fastest it climbs to get over a rise. Brisker than the drop, so steps feel crisp. */
    private static final double LIFT_SPEED = 0.25;

    /** Sprinting speed, near enough: vanilla sprint is about 0.28 blocks a tick. */
    private static final double TRAVEL_SPEED = 0.29;

    /** What either ride's speed upgrade multiplies that by. */
    private static final double UPGRADED_MULTIPLIER = 2.0;

    /**
     * How much quicker the drill is than the surface rides.
     *
     * Earth dig is earthbending's way of getting around, so it has to beat running or
     * nobody would use it: 0.29 x 1.25 is about 7 blocks a second against a sprint's
     * 5.6. It was briefly slower than WALKING, which made it useless for the one thing
     * it exists to do.
     */
    private static final double DRILL_SPEED_SCALE = 1.25;

    /** How long a drill gets to bury itself before it is called a failure. */
    private static final int BURROW_GRACE = 40;

    /** How long a drill's rider drifts safely after being set down. */
    private static final int DIG_LANDING_GRACE = 100;

    private static final List<Ride> ACTIVE = new ArrayList<>();

    private Rides() {
    }

    /**
     * The rides, and the handful of things that actually differ between them: where
     * they travel, what makes one stop, and what it costs.
     */
    public enum Kind {
        /**
         * Air Scooter. Rides a full block above solid ground, and refuses water —
         * a ball of air is a thing for solid ground.
         */
        AIR_SCOOTER(5, 1, 1.0, true) {
            @Override
            double surfaceAt(ServerLevel level, double x, double z, double fromY) {
                int bx = Mth.floor(x);
                int bz = Mth.floor(z);
                int start = Mth.floor(fromY) + SCAN_UP;

                for (int y = start; y >= start - (SCAN_UP + SCAN_DOWN); y--) {
                    BlockPos ground = new BlockPos(bx, y, bz);
                    if (!level.getBlockState(ground).blocksMotion()) continue;

                    // A surface is solid WITH SPACE ABOVE IT. That definition is what
                    // stops a scooter walking up sheer walls: every block of a five
                    // block wall has another on top of it, so none qualifies, the
                    // search falls through to the ground the wall stands on, and the
                    // seat bumps into the wall the way it should.
                    if (!level.getBlockState(ground.above()).blocksMotion()) {
                        return y + 1.0;
                    }
                }
                return Double.NaN;
            }

            @Override
            boolean stillValid(Ride ride, ServerPlayer rider) {
                if (ride.seat.isInWater() || overWater(ride.level, ride.seat.position())) {
                    rider.displayClientMessage(
                            Component.literal("§bYour Air Scooter cannot cross water!"), true);
                    return false;
                }
                return true;
            }
        },

        /**
         * Water Surf. Rides ON the waterline, and needs water under it — walk it onto
         * the beach and it sets the bender down.
         */
        WATER_SURF(10, 3, 0.0, false) {
            @Override
            double surfaceAt(ServerLevel level, double x, double z, double fromY) {
                int bx = Mth.floor(x);
                int bz = Mth.floor(z);
                int start = Mth.floor(fromY) + SCAN_UP;

                for (int y = start; y >= start - (SCAN_UP + SCAN_DOWN); y--) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;

                    // The TOP of the water: the first water block with something other
                    // than water above it. Deeper ones are below the waterline, and
                    // riding those would put the bender underwater.
                    if (!level.getFluidState(pos.above()).is(FluidTags.WATER)) {
                        return y + 1.0;
                    }
                }
                return Double.NaN;
            }

            @Override
            boolean stillValid(Ride ride, ServerPlayer rider) {
                if (Double.isNaN(surfaceAt(ride.level, ride.seat.getX(), ride.seat.getZ(),
                        ride.seat.getY()))) {
                    rider.displayClientMessage(
                            Component.literal("§bThere is no water left to surf!"), true);
                    return false;
                }
                return true;
            }
        },

        /**
         * Earth dig. A drill: it goes exactly where the rider looks, straight through
         * whatever is in the way, and runs until it surfaces.
         *
         * Earthbending's way of getting about — a badgermole tunnelling, and quicker
         * than a sprint, so it is worth using to travel rather than only to escape.
         */
        EARTH_DIG(5, 1, 0.0, false) {
            /**
             * Underground or not — which is NOT the same question as "is there rock
             * here".
             *
             * The obvious test, whether the block at the rider's head is solid, cannot
             * work: the drill takes those blocks out itself, so a tick later it is
             * always standing in the air it just made and the ride ends immediately.
             * What actually matters is whether there is still world overhead, which is
             * what canSeeSky answers — a tunnel keeps its ceiling, a surfaced drill
             * does not.
             */
            @Override
            boolean stillValid(Ride ride, ServerPlayer rider) {
                BlockPos head = BlockPos.containing(
                        ride.seat.getX(), ride.seat.getY() + 1.5, ride.seat.getZ());

                if (!ride.level.canSeeSky(head)) {
                    ride.submerged = true;
                    return true;
                }

                if (ride.submerged) {
                    rider.displayClientMessage(
                            Component.literal("§6You surface."), true);
                    return false;
                }

                // Still burrowing in from the top. Given a couple of seconds to get
                // under; a drill that never manages it is pointed at nothing useful.
                if (ride.ticks > BURROW_GRACE) {
                    rider.displayClientMessage(
                            Component.literal("§6You cannot get underground here!"), true);
                    return false;
                }

                return true;
            }

            /** Straight down the look vector — the one ride where up and down are the rider's. */
            @Override
            Vec3 velocity(Ride ride, ServerPlayer rider, double speed) {
                return rider.getLookAngle().normalize().scale(speed * DRILL_SPEED_SCALE);
            }

            /**
             * Clears the ground the seat is about to move into.
             *
             * Without this the seat simply grinds against the rock: the drill has to
             * take the blocks out itself, and it takes the whole box it is about to
             * occupy rather than one block, or the corners catch.
             */
            @Override
            void beforeMove(Ride ride, ServerPlayer rider, Vec3 motion) {
                AABB next = ride.seat.getBoundingBox().move(motion).inflate(0.1);

                for (BlockPos pos : BlockPos.betweenClosed(
                        BlockPos.containing(next.minX, next.minY, next.minZ),
                        BlockPos.containing(next.maxX, next.maxY, next.maxZ))) {

                    BlockState state = ride.level.getBlockState(pos);
                    if (state.isAir()) continue;

                    // Fluids are left alone. They do not block the seat anyway, so
                    // there is nothing to gain by taking them — and breaking them
                    // would let a drill quietly empty an ocean on its way past.
                    if (!state.getFluidState().isEmpty()) continue;

                    // Bedrock and its friends stop the drill dead, as they should.
                    if (state.getDestroySpeed(ride.level, pos) < 0.0F) continue;

                    // NOT dropped. At seven blocks a second through stone this would
                    // be hundreds of item entities a trip — a tunnel full of rubble to
                    // wade back through, and a mining tool by accident. Earth dig is
                    // for travelling; Mine is the ability that gives you the blocks.
                    ride.level.destroyBlock(pos.immutable(), false, rider);
                }
            }
        };

        final int chiPerSecond;
        final int xpPerSecond;

        /** How far above the surface the rider's feet sit. */
        final double hover;

        /** Whether the rider is drawn seated. Surfing is done standing up. */
        final boolean seated;

        Kind(int chiPerSecond, int xpPerSecond, double hover, boolean seated) {
            this.chiPerSecond = chiPerSecond;
            this.xpPerSecond = xpPerSecond;
            this.hover = hover;
            this.seated = seated;
        }

        /**
         * The Y the rider's feet want to be at in this column, or NaN if none.
         * Unused by rides that do not follow a surface.
         */
        double surfaceAt(ServerLevel level, double x, double z, double fromY) {
            return Double.NaN;
        }

        /** Checked every tick; false ends the ride. */
        abstract boolean stillValid(Ride ride, ServerPlayer rider);

        /** How far and which way the seat moves this tick. */
        Vec3 velocity(Ride ride, ServerPlayer rider, double speed) {
            return surfaceVelocity(ride, rider, speed);
        }

        /** Run just before the seat moves, for a ride that has to clear its own path. */
        void beforeMove(Ride ride, ServerPlayer rider, Vec3 motion) {
        }
    }

    static final class Ride {
        final ServerLevel level;
        final UUID riderId;
        final BendingSeat seat;
        final Kind kind;
        final String speedUpgrade;
        int ticks;

        /**
         * Earth dig only: whether the drill has actually got below ground yet.
         *
         * A drill starts on the surface with open sky overhead, so "you have surfaced"
         * cannot end the ride until it has been underground at least once — the same
         * shape as Air jump waiting to see the player leave the ground before its
         * landing check is allowed to fire.
         */
        boolean submerged;

        Ride(ServerLevel level, UUID riderId, BendingSeat seat, Kind kind, String speedUpgrade) {
            this.level = level;
            this.riderId = riderId;
            this.seat = seat;
            this.kind = kind;
            this.speedUpgrade = speedUpgrade;
        }
    }

    /** Whether this player is on any ride at all. */
    public static boolean isRiding(ServerPlayer player) {
        return find(player.getUUID()) != null;
    }

    /** Whether this player is on this particular ride. */
    public static boolean isRiding(ServerPlayer player, Kind kind) {
        Ride ride = find(player.getUUID());
        return ride != null && ride.kind == kind;
    }

    /**
     * Puts a player on a ride.
     *
     * @param speedUpgrade upgrade key that doubles the travel speed, or null for none
     * @return false if they could not be seated, in which case nothing was changed and
     *         the caller should not charge them for it
     */
    public static boolean start(ServerPlayer player, Kind kind, String speedUpgrade) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (isRiding(player)) return false;

        // Already in a boat, on a horse, or riding anything else at all. Stealing the
        // player out of another vehicle is a good way to break whatever put them there.
        if (player.isPassenger()) {
            player.displayClientMessage(
                    Component.literal("§bYou cannot do that while riding something else!"), true);
            return false;
        }

        BendingSeat seat = new BendingSeat(ModEntities.BENDING_SEAT.get(), level);
        seat.setSeated(kind.seated);
        seat.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);

        if (!level.addFreshEntity(seat)) return false;

        if (!player.startRiding(seat, true)) {
            seat.discard();
            return false;
        }

        ACTIVE.add(new Ride(level, player.getUUID(), seat, kind, speedUpgrade));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                kind == Kind.AIR_SCOOTER
                        ? SoundEvents.BREEZE_WHIRL
                        : SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS, 0.9F, 1.3F);
        return true;
    }

    /**
     * Takes a player off whatever they are riding. Safe to call on anyone, at any
     * time, however the ride is ending — that is the point of it.
     */
    public static void stop(ServerPlayer player) {
        Ride ride = find(player.getUUID());
        if (ride == null) return;

        ACTIVE.remove(ride);
        dismantle(ride, player);
    }

    /**
     * Runs every ride in the world. Called once per server tick.
     *
     * Iterates a SNAPSHOT, not the live list, and that is not tidiness — it is a crash
     * fix. Ticking a ride runs game code that can come straight back here: a rider
     * killed mid-tick fires LivingDeathEvent, whose handler calls forgetPlayer, which
     * removes from ACTIVE while this loop is still walking it. An iterator over the
     * live list throws ConcurrentModificationException at that point and takes the
     * whole server tick loop down with it.
     *
     * Any manager whose tick can kill, hurt or dismount something needs the same
     * treatment; a plain iterator is only safe when nothing downstream can reach back.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Ride ride : List.copyOf(ACTIVE)) {
            // Something else may have ended this ride since the snapshot was taken.
            if (!ACTIVE.contains(ride)) continue;

            ServerPlayer rider = server.getPlayerList().getPlayer(ride.riderId);

            if (!advance(ride, rider)) {
                ACTIVE.remove(ride);
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

        // Checked before chi is taken, so the tick a ride fails on is free.
        if (!ride.kind.stillValid(ride, rider)) return false;

        BendingData data = rider.getData(ModAttachments.BENDING_DATA);

        int chiThisTick = perTick(ride.kind.chiPerSecond, ride.ticks);
        if (data.getCurrentChi() < chiThisTick) {
            rider.displayClientMessage(Component.literal("§bYou are out of Chi."), true);
            return false;
        }

        // No ride should ever bank fall damage. The rider is a passenger being carried
        // by the seat, so any distance counted against them is an accident of the
        // vehicle moving, not something they fell.
        rider.fallDistance = 0.0F;

        if (chiThisTick > 0) data.consumeChi(chiThisTick);
        AbilitySupport.grantXp(data, perTick(ride.kind.xpPerSecond, ride.ticks));

        // Every 4 ticks, matching the channelled abilities: enough for a responsive
        // chi bar without a packet every tick.
        if (ride.ticks % 4 == 0) {
            AbilitySupport.syncData(rider, data);
        }

        steer(ride, rider, data);
        draw(ride);

        ride.ticks++;
        return true;
    }

    /**
     * Moves the seat, and with it the rider.
     *
     * Travel is along where the rider is LOOKING, flattened: the vertical half of the
     * look vector is ignored on purpose, because height is the surface's business
     * here, not the camera's. Looking up should not fly the ride into the sky.
     */
    private static void steer(Ride ride, ServerPlayer rider, BendingData data) {
        double speed = ride.speedUpgrade != null && data.hasUpgrade(ride.speedUpgrade)
                ? TRAVEL_SPEED * UPGRADED_MULTIPLIER
                : TRAVEL_SPEED;

        Vec3 motion = ride.kind.velocity(ride, rider, speed);

        // A chance to clear the way before moving into it. Only the drill uses it;
        // for the surface rides a wall is supposed to stop them.
        ride.kind.beforeMove(ride, rider, motion);

        ride.seat.setDeltaMovement(motion);

        // move() rather than setPos, so the seat is stopped by walls like anything
        // else. The rider is a passenger and does not collide on their own, so this
        // is the only thing keeping a ride out of the side of a hill.
        ride.seat.move(MoverType.SELF, motion);

        // Kept facing the way it travels, which is what the rider's body follows.
        ride.seat.setYRot(rider.getYRot());
    }

    /**
     * The surface rides' movement: along where the rider LOOKS, flattened, with the
     * height taken from the ground rather than the camera.
     *
     * The vertical half of the look vector is ignored on purpose — looking up should
     * not fly a scooter into the sky. Earth dig overrides this precisely because a
     * drill is the one ride where the camera SHOULD decide which way is down.
     */
    private static Vec3 surfaceVelocity(Ride ride, ServerPlayer rider, double speed) {
        Vec3 look = rider.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);

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

        return new Vec3(heading.x, dy, heading.z);
    }

    /**
     * The height the ride wants to be at, reading both underfoot and a little way
     * ahead.
     *
     * Looking ahead is what turns "stops dead at a step" into "rides up over it" — by
     * the time the seat reaches the rise it has already started climbing.
     */
    private static double targetHeight(Ride ride, Vec3 heading) {
        Vec3 here = ride.seat.position();
        Vec3 ahead = here.add(heading.scale(LOOK_AHEAD_TICKS));

        double under = ride.kind.surfaceAt(ride.level, here.x, here.z, ride.seat.getY());
        double front = ride.kind.surfaceAt(ride.level, ahead.x, ahead.z, ride.seat.getY());

        if (Double.isNaN(under) && Double.isNaN(front)) {
            // Nothing to ride over within reach — out over a canyon. Keep gliding down
            // until the surface comes back up into range.
            return ride.seat.getY() - GLIDE_SPEED;
        }

        double surface;
        if (Double.isNaN(under)) surface = front;
        else if (Double.isNaN(front)) surface = under;
        else surface = Math.max(under, front);

        return surface + ride.kind.hover;
    }

    /**
     * Whether there is water straight down from {@code from} before anything solid.
     *
     * A pond with a stone bed counts, since the water is what would be crossed.
     */
    private static boolean overWater(ServerLevel level, Vec3 from) {
        int x = Mth.floor(from.x);
        int z = Mth.floor(from.z);
        int start = Mth.floor(from.y);

        for (int y = start; y >= start - (SCAN_DOWN + 1); y--) {
            BlockPos pos = new BlockPos(x, y, z);

            if (level.getFluidState(pos).is(FluidTags.WATER)) return true;
            if (level.getBlockState(pos).blocksMotion()) return false;
        }

        return false;
    }

    /** What is holding the rider up, drawn under them. */
    private static void draw(Ride ride) {
        double cx = ride.seat.getX();
        double cy = ride.seat.getY();
        double cz = ride.seat.getZ();

        if (ride.kind == Kind.AIR_SCOOTER) {
            double spin = (ride.ticks % 8) / 8.0 * Math.PI * 2.0;
            for (int i = 0; i < 6; i++) {
                double angle = spin + (Math.PI * 2.0 * i / 6);
                ride.level.sendParticles(ParticleTypes.CLOUD,
                        cx + Math.cos(angle) * 0.5, cy - 0.2, cz + Math.sin(angle) * 0.5,
                        1, 0.05, 0.05, 0.05, 0.01);
            }
            ride.level.sendParticles(ParticleTypes.CLOUD, cx, cy - 0.3, cz, 2, 0.25, 0.05, 0.25, 0.02);
            return;
        }

        // A wake thrown up either side, so it reads as riding the water rather than
        // standing on it.
        ride.level.sendParticles(ParticleTypes.SPLASH, cx, cy + 0.05, cz, 6, 0.45, 0.05, 0.45, 0.06);
        if (ride.ticks % 4 == 0) {
            ride.level.sendParticles(ParticleTypes.BUBBLE, cx, cy - 0.1, cz, 3, 0.3, 0.05, 0.3, 0.01);
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

        if (rider == null) return;

        // Getting off a drill usually means being let go partway up your own shaft,
        // which is a long drop you did not choose. Slow Falling rather than a flag of
        // our own: vanilla resets fall distance every tick it is held, so the descent
        // is safe and the effect sees itself out with nothing to remember.
        if (ride.kind == Kind.EARTH_DIG) {
            rider.fallDistance = 0.0F;
            rider.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                    DIG_LANDING_GRACE, 0, false, false, true));
        }

        ride.level.playSound(null, rider.getX(), rider.getY(), rider.getZ(),
                ride.kind == Kind.AIR_SCOOTER
                        ? SoundEvents.BREEZE_DEFLECT
                        : SoundEvents.AMBIENT_UNDERWATER_EXIT,
                SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    /**
     * Ends a ride when its rider leaves — death, disconnect, or a change of dimension.
     * Looked up by UUID rather than by entity, since on a dimension change the
     * ServerPlayer handed to us is a different object from the one that started.
     */
    public static void forgetPlayer(ServerPlayer player) {
        stop(player);
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
     * way AbilityHandler pays for a channel.
     */
    private static int perTick(int ratePerSecond, int tick) {
        long rate = ratePerSecond;
        long t = Math.max(0, tick);
        return (int) ((rate * (t + 1)) / 20L - (rate * t) / 20L);
    }
}
