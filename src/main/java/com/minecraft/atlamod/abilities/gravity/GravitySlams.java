package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everyone currently lifted by a Gravity Slam, waiting for the forced slam back down.
 *
 * A manager rather than a countdown on BendingData, unlike Bass Bounce's identical
 * shape — the difference is who gets slammed. Bass Bounce always throws its OWN
 * caster, so a field on the caster's own data is enough; Gravity Slam launches
 * whoever is caught in the blast, who is very often not the caster at all, and
 * BendingData has no meaning for a mob. Tracking a list of victims by id, the way
 * EarthGrabs and LightningBalls do, is what lets this work on anything living.
 */
public final class GravitySlams {

    /** How hard the forced slam drives victims back down. */
    private static final double SLAM_SPEED = -1.4;

    /** How long a victim is given to reach the top of the lift before being slammed. */
    private static final int LIFT_TICKS = 12;

    private static final float DAMAGE = 8.0F;

    private static final List<Slammed> ACTIVE = new ArrayList<>();

    private GravitySlams() {
    }

    private static final class Slammed {
        final ServerLevel level;
        final UUID victimId;
        final UUID casterId;
        int ticksLeft;

        Slammed(ServerLevel level, UUID victimId, UUID casterId, int ticksLeft) {
            this.level = level;
            this.victimId = victimId;
            this.casterId = casterId;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Lifts a victim and arms their forced slam. */
    public static void lift(LivingEntity victim, ServerPlayer caster, double launchSpeed) {
        if (!(victim.level() instanceof ServerLevel level)) return;

        victim.setDeltaMovement(victim.getDeltaMovement().x, launchSpeed, victim.getDeltaMovement().z);
        victim.hurtMarked = true;
        victim.fallDistance = 0.0F;

        ACTIVE.add(new Slammed(level, victim.getUUID(), caster.getUUID(), LIFT_TICKS));
    }

    /**
     * Advances every lift in the world. Called once per server tick.
     *
     * Iterates a SNAPSHOT: the slam damages its victims, which can kill, which fires
     * a death handler that reaches back here — the same trap every other manager in
     * this mod that touches entities guards against.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Slammed slammed : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(slammed)) continue;

            if (!advance(slammed, server)) {
                ACTIVE.remove(slammed);
            }
        }
    }

    private static boolean advance(Slammed slammed, MinecraftServer server) {
        if (!(slammed.level.getEntity(slammed.victimId) instanceof LivingEntity victim) || !victim.isAlive()) {
            return false;
        }

        if (slammed.ticksLeft-- > 0) return true;

        ServerPlayer caster = server.getPlayerList().getPlayer(slammed.casterId);
        slam(slammed.level, victim, caster);
        return false;
    }

    private static void slam(ServerLevel level, LivingEntity victim, ServerPlayer caster) {
        victim.setDeltaMovement(victim.getDeltaMovement().x, SLAM_SPEED, victim.getDeltaMovement().z);
        victim.hurtMarked = true;

        // The caster is who this damage is attributed to, whether or not they are
        // still online to see it land — the same call Bass Bounce's slam makes.
        if (caster != null) {
            victim.hurt(caster.damageSources().indirectMagic(caster, caster), DAMAGE);
        } else {
            victim.hurt(victim.damageSources().magic(), DAMAGE);
        }

        Vec3 at = victim.position();
        Gravity.burst(level, at, 25, 0.5);
        Gravity.warp(level, at, 1.1F);
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        // A victim leaving mid-lift is simply dropped rather than slammed — there is
        // nothing left to slam. A caster leaving does not cancel the lift: the
        // victim is already committed to it, the same way a thrown Ice Bomb outlives
        // the bender who threw it.
        ACTIVE.removeIf(slammed -> slammed.victimId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(slammed -> slammed.level == level);
    }
}
