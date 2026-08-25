package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingSeat;
import com.minecraft.atlamod.ModEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Everything currently pinned in place by an Earth trap.
 *
 * The holding is done exactly the way Air Scooter and Water Surf move a bender, only
 * standing still: the victim is made a PASSENGER of a stationary seat. A passenger's
 * own movement input is never consulted — the vehicle decides where they are — so a
 * trapped player simply cannot walk, and a trapped mob cannot either, with no effects
 * to fight over and nothing for the server to correct.
 *
 * That replaced an earlier version built out of Slowness and the shields' RootedPacket,
 * which needed two different mechanisms for players and mobs and left a client-side
 * flag that could stick. This needs neither.
 *
 * The one thing vanilla would still allow is shifting off, so
 * {@link #holdsSeat} lets the dismount be refused for as long as the trap is running.
 * Every release removes the entry BEFORE letting go, so the refusal cannot outlive it.
 */
public final class EarthTraps {

    private static final List<Trapped> ACTIVE = new ArrayList<>();

    private EarthTraps() {
    }

    private static final class Trapped {
        final ServerLevel level;
        final UUID victimId;
        final BendingSeat seat;
        int ticksLeft;

        Trapped(ServerLevel level, UUID victimId, BendingSeat seat, int ticksLeft) {
            this.level = level;
            this.victimId = victimId;
            this.seat = seat;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Pins a victim where they stand for {@code ticks}. */
    public static void hold(LivingEntity victim, int ticks) {
        if (!(victim.level() instanceof ServerLevel level)) return;

        // Already in a boat, on a horse, or riding anything else. Pulling them out of
        // it to trap them would break whatever put them there, so leave them be.
        if (victim.isPassenger()) return;

        // A second trap on the same victim replaces the first rather than stacking.
        release(victim.getUUID());

        BendingSeat seat = new BendingSeat(ModEntities.BENDING_SEAT.get(), level);
        // Standing, not sitting: they are stuck in the ground, not riding anything.
        seat.setSeated(false);
        seat.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);

        if (!level.addFreshEntity(seat)) return;

        if (!victim.startRiding(seat, true)) {
            seat.discard();
            return;
        }

        ACTIVE.add(new Trapped(level, victim.getUUID(), seat, ticks));
    }

    /** Whether this entity is a trap seat that is still holding someone. */
    public static boolean holdsSeat(Entity vehicle) {
        for (Trapped trap : ACTIVE) {
            if (trap.seat == vehicle) return true;
        }
        return false;
    }

    /**
     * Runs every trap in the world. Called once per server tick.
     *
     * Iterates a SNAPSHOT for the same reason Rides does: mounting and releasing
     * victims runs game code that can reach back here — a trapped player dying fires
     * the death handler, which calls forgetPlayer and removes from ACTIVE — and
     * mutating the list under its own iterator crashes the server tick loop.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Trapped trap : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(trap)) continue;

            Entity found = trap.level.getEntity(trap.victimId);
            LivingEntity victim = (found instanceof LivingEntity living) ? living : null;

            if (victim == null || !victim.isAlive() || trap.ticksLeft-- <= 0) {
                ACTIVE.remove(trap);
                dismantle(trap, victim);
                continue;
            }

            // Belt and braces: the dismount is refused, but anything else that pulled
            // them off the seat would otherwise leave them free with the trap still
            // ticking. Put them back on it.
            if (victim.getVehicle() != trap.seat && !trap.seat.isRemoved()) {
                victim.startRiding(trap.seat, true);
            }
        }
    }

    /** Lets one victim go and takes the seat away. Copes with a victim already gone. */
    private static void dismantle(Trapped trap, LivingEntity victim) {
        if (victim != null && victim.getVehicle() == trap.seat) {
            victim.stopRiding();
        }
        if (!trap.seat.isRemoved()) {
            trap.seat.discard();
        }
    }

    /** Frees a victim by id, if they are held. */
    private static void release(UUID victimId) {
        Iterator<Trapped> traps = ACTIVE.iterator();
        while (traps.hasNext()) {
            Trapped trap = traps.next();
            if (!trap.victimId.equals(victimId)) continue;

            // Removed from the list FIRST, so the dismount refusal no longer applies
            // to this seat by the time we let go of it.
            traps.remove();

            Entity found = trap.level.getEntity(victimId);
            dismantle(trap, found instanceof LivingEntity living ? living : null);
        }
    }

    /** Frees a player who is leaving — death, disconnect or a change of dimension. */
    public static void forgetPlayer(ServerPlayer player) {
        release(player.getUUID());
    }

    /**
     * Drops every trap in a level that is going away, freeing whoever it can still
     * find. Nothing else holds these.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(trap -> {
            if (trap.level != level) return false;

            Entity found = level.getEntity(trap.victimId);
            dismantle(trap, found instanceof LivingEntity living ? living : null);
            return true;
        });
    }
}
