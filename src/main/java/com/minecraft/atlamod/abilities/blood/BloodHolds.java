package com.minecraft.atlamod.abilities.blood;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything currently being bled by Blood freeze.
 *
 * The hold itself is a Stunned effect and needs no help, but the BLEEDING does: two hp
 * a second for two seconds has to land twice, once a second, rather than as a single
 * blow on the cast. Tracking it here is what spreads it out.
 *
 * Small and single-purpose on purpose — Blood freeze is cast and forgotten, so there
 * is nothing for the ability class to own once the effect is applied.
 */
public final class BloodHolds {

    private static final List<Held> ACTIVE = new ArrayList<>();

    private BloodHolds() {
    }

    private static final class Held {
        final ServerLevel level;
        final UUID casterId;
        final LivingEntity victim;
        final float damagePerSecond;
        int ticksLeft;
        int ticks;

        Held(ServerLevel level, UUID casterId, LivingEntity victim,
             int ticksLeft, float damagePerSecond) {
            this.level = level;
            this.casterId = casterId;
            this.victim = victim;
            this.ticksLeft = ticksLeft;
            this.damagePerSecond = damagePerSecond;
        }
    }

    /** Starts bleeding a victim for a while. */
    public static void hold(ServerLevel level, ServerPlayer caster, LivingEntity victim,
                            int ticks, float damagePerSecond) {
        // A second cast on the same victim replaces the first rather than stacking, so
        // two benders cannot double the bleed by both holding the same target.
        ACTIVE.removeIf(held -> held.victim == victim);

        ACTIVE.add(new Held(level, caster.getUUID(), victim, ticks, damagePerSecond));
    }

    /** Iterates a SNAPSHOT — this kills, and a death handler can call back in here. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Held held : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(held)) continue;

            if (!advance(held, server)) {
                ACTIVE.remove(held);
            }
        }
    }

    private static boolean advance(Held held, MinecraftServer server) {
        if (held.ticksLeft-- <= 0) return false;
        if (!held.victim.isAlive()) return false;

        held.ticks++;

        ServerPlayer caster = server.getPlayerList().getPlayer(held.casterId);
        if (caster == null) return false;

        Blood.wrench(held.level, held.victim, 2);

        // On a one-second beat rather than per tick, for the reason wind tunnel
        // documents: per-tick hits are only spaced out by invulnerability frames, and
        // that stops holding the moment anything else resets them.
        if (held.ticks % 20 != 0) return true;

        held.victim.hurt(caster.damageSources().indirectMagic(caster, caster),
                held.damagePerSecond);

        return true;
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(held -> held.casterId.equals(player.getUUID()) || held.victim == player);
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(held -> held.level == level);
    }
}
