package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingSeat;
import com.minecraft.atlamod.ModEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everyone currently circling a Gravity Orbit.
 *
 * The lock is Earth Trap's trick — a victim made a PASSENGER of a stationary seat,
 * so their own movement and attacks are never consulted — but the seat here is not
 * stationary. Each victim gets their OWN seat, moved every tick to a point on a
 * circle around the caster, exactly the way LightningBalls steers a ball towards the
 * crosshair; only here the target point orbits rather than chases a look direction.
 *
 * BendingSeat is registered with updateInterval(1) for the rides that already move
 * it every tick (Air Scooter, Water Surf, Earth Trap's own stationary use), so a
 * moving seat needs none of the hasImpulse trick FallingBlockEntity abilities do.
 */
public final class GravityOrbits {

    /** How fast the ring turns, in radians per tick. */
    private static final double ANGULAR_SPEED = 0.1;

    private static final List<Orbit> ACTIVE = new ArrayList<>();

    private GravityOrbits() {
    }

    private static final class Orbit {
        final ServerLevel level;
        final UUID ownerId;
        final UUID victimId;
        final BendingSeat seat;
        final double startAngle;
        final double radius;
        int ticksLeft;
        int age;

        Orbit(ServerLevel level, UUID ownerId, UUID victimId, BendingSeat seat,
              double startAngle, double radius, int ticksLeft) {
            this.level = level;
            this.ownerId = ownerId;
            this.victimId = victimId;
            this.seat = seat;
            this.startAngle = startAngle;
            this.radius = radius;
            this.ticksLeft = ticksLeft;
        }
    }

    /**
     * Pulls a victim into orbit around {@code owner}, starting at {@code startAngle}
     * so several victims caught by the same cast spread evenly around the ring
     * instead of stacking on top of each other.
     */
    public static void capture(ServerPlayer owner, LivingEntity victim,
                                double radius, double startAngle, int ticks) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (victim.isPassenger()) return;

        BendingSeat seat = new BendingSeat(ModEntities.BENDING_SEAT.get(), level);
        seat.setSeated(false);
        seat.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);

        if (!level.addFreshEntity(seat)) return;

        if (!victim.startRiding(seat, true)) {
            seat.discard();
            return;
        }

        ACTIVE.add(new Orbit(level, owner.getUUID(), victim.getUUID(), seat, startAngle, radius, ticks));
    }

    /**
     * Advances every orbit in the world. Called once per server tick.
     *
     * Iterates a SNAPSHOT, the same reason every other manager here does: releasing
     * a victim can run game code (a dismount) that reaches back into this list.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Orbit orbit : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(orbit)) continue;

            if (!advance(orbit, server)) {
                ACTIVE.remove(orbit);
                release(orbit);
            }
        }
    }

    private static boolean advance(Orbit orbit, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(orbit.ownerId);
        if (owner == null || owner.level() != orbit.level || !owner.isAlive()) return false;
        if (orbit.seat.isRemoved()) return false;

        Entity found = orbit.level.getEntity(orbit.victimId);
        if (!(found instanceof LivingEntity victim) || !victim.isAlive()) return false;

        if (orbit.ticksLeft-- <= 0) return false;

        // Belt and braces, the same as EarthTraps: something else pulling the victim
        // off the seat gets put straight back on it.
        if (victim.getVehicle() != orbit.seat) {
            victim.startRiding(orbit.seat, true);
        }

        orbit.age++;
        double angle = orbit.startAngle + ANGULAR_SPEED * orbit.age;
        double x = owner.getX() + Math.cos(angle) * orbit.radius;
        double z = owner.getZ() + Math.sin(angle) * orbit.radius;
        double y = owner.getY() + 1.0;

        orbit.seat.moveTo(x, y, z, orbit.seat.getYRot() + 6.0F, 0.0F);

        Gravity.hum(orbit.level, new Vec3(x, y, z), 3, 0.15);

        return true;
    }

    /** Whether this entity is an orbit seat — nothing consults it yet, kept for parity. */
    public static boolean holdsSeat(Entity vehicle) {
        for (Orbit orbit : ACTIVE) {
            if (orbit.seat == vehicle) return true;
        }
        return false;
    }

    private static void release(Orbit orbit) {
        Entity found = orbit.level.getEntity(orbit.victimId);
        if (found instanceof LivingEntity victim && victim.getVehicle() == orbit.seat) {
            victim.stopRiding();
        }
        if (!orbit.seat.isRemoved()) {
            orbit.seat.discard();
        }
    }

    /** Ends every orbit this player OWNS — used when the channel is cancelled early. */
    public static void releaseAllOwnedBy(ServerPlayer owner) {
        for (Orbit orbit : List.copyOf(ACTIVE)) {
            if (!orbit.ownerId.equals(owner.getUUID())) continue;
            ACTIVE.remove(orbit);
            release(orbit);
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        for (Orbit orbit : List.copyOf(ACTIVE)) {
            if (!orbit.ownerId.equals(player.getUUID()) && !orbit.victimId.equals(player.getUUID())) continue;
            ACTIVE.remove(orbit);
            release(orbit);
        }
    }

    public static void forgetLevel(ServerLevel level) {
        for (Orbit orbit : List.copyOf(ACTIVE)) {
            if (orbit.level != level) continue;
            ACTIVE.remove(orbit);
            release(orbit);
        }
    }
}
