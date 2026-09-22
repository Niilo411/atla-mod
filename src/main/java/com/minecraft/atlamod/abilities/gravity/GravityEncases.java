package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The damage-over-time half of Encase.
 *
 * The lock itself is {@link com.minecraft.atlamod.abilities.earth.EarthTraps}' own
 * passenger trick, and the cage is sixteen ordinary {@link
 * com.minecraft.atlamod.abilities.earth.EarthWorks#raiseFor} calls that stand
 * themselves back down on their own timer — neither needed a manager of its own.
 * What was left over is "one damage a second while it lasts", which is all this
 * class does.
 */
public final class GravityEncases {

    private static final int HIT_EVERY = 20; // once a second
    private static final float DAMAGE = 1.0F;

    private static final List<Encased> ACTIVE = new ArrayList<>();

    private GravityEncases() {
    }

    private static final class Encased {
        final ServerLevel level;
        final UUID victimId;
        final UUID casterId;
        int ticksLeft;
        int age;

        Encased(ServerLevel level, UUID victimId, UUID casterId, int ticksLeft) {
            this.level = level;
            this.victimId = victimId;
            this.casterId = casterId;
            this.ticksLeft = ticksLeft;
        }
    }

    public static void start(ServerPlayer caster, LivingEntity victim, int ticks) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        ACTIVE.add(new Encased(level, victim.getUUID(), caster.getUUID(), ticks));
    }

    /** Iterates a SNAPSHOT: the tick damages the victim, which can kill it. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Encased encased : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(encased)) continue;

            if (!advance(encased, server)) {
                ACTIVE.remove(encased);
            }
        }
    }

    private static boolean advance(Encased encased, MinecraftServer server) {
        if (encased.ticksLeft-- <= 0) return false;
        encased.age++;

        if (!(encased.level.getEntity(encased.victimId) instanceof LivingEntity victim) || !victim.isAlive()) {
            return false;
        }

        if (encased.age % HIT_EVERY == 0) {
            ServerPlayer caster = server.getPlayerList().getPlayer(encased.casterId);
            // No armor to reduce it — indirectMagic bypasses armor points entirely,
            // which is what the design's "no matter how much armor points they have"
            // actually needs.
            if (caster != null) {
                victim.hurt(caster.damageSources().indirectMagic(caster, caster), DAMAGE);
            } else {
                victim.hurt(victim.damageSources().magic(), DAMAGE);
            }
        }

        return true;
    }

    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(encased -> encased.victimId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(encased -> encased.level == level);
    }
}
