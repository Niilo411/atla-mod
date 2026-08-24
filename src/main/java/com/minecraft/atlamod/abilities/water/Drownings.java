package com.minecraft.atlamod.abilities.water;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Everything currently being drowned by a waterbender.
 *
 * Drowning has to be driven from here rather than left to vanilla, because vanilla
 * only drowns things that are underwater and refills their air the moment they are
 * not. Emptying a victim's lungs on dry land would otherwise do nothing at all: the
 * bubbles would be back before the next tick.
 *
 * So for as long as it lasts the air is held at nothing and the damage is dealt on
 * the same beat vanilla uses, wherever the victim happens to be standing.
 */
public final class Drownings {

    /** Vanilla drowns for one heart every second; this matches it. */
    private static final float DAMAGE = 2.0F;
    private static final int DAMAGE_INTERVAL = 20;

    private static final List<Drowning> ACTIVE = new ArrayList<>();

    private Drownings() {
    }

    private static final class Drowning {
        final ServerLevel level;
        final UUID victimId;
        int ticksLeft;
        int untilDamage = DAMAGE_INTERVAL;

        Drowning(ServerLevel level, UUID victimId, int ticksLeft) {
            this.level = level;
            this.victimId = victimId;
            this.ticksLeft = ticksLeft;
        }
    }

    /** Empties a victim's lungs and keeps them empty for {@code ticks}. */
    public static void start(LivingEntity victim, int ticks) {
        if (!(victim.level() instanceof ServerLevel level)) return;

        victim.setAirSupply(0);

        // A second casting on the same victim replaces the first rather than stacking,
        // so the drowning lasts as long as the newer one says and no longer.
        ACTIVE.removeIf(drowning -> drowning.victimId.equals(victim.getUUID()));
        ACTIVE.add(new Drowning(level, victim.getUUID(), ticks));
    }

    /** Runs every drowning in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Drowning> drownings = ACTIVE.iterator();
        while (drownings.hasNext()) {
            if (!advance(drownings.next())) {
                drownings.remove();
            }
        }
    }

    /** @return false once this drowning is finished with */
    private static boolean advance(Drowning drowning) {
        Entity found = drowning.level.getEntity(drowning.victimId);
        if (!(found instanceof LivingEntity victim) || !victim.isAlive()) {
            return false;
        }

        if (drowning.ticksLeft-- <= 0) {
            return false;
        }

        // Held at nothing, or vanilla would refill it the moment the victim surfaced.
        victim.setAirSupply(0);

        if (--drowning.untilDamage <= 0) {
            drowning.untilDamage = DAMAGE_INTERVAL;
            victim.hurt(victim.damageSources().drown(), DAMAGE);
        }

        drowning.level.sendParticles(ParticleTypes.BUBBLE_POP,
                victim.getX(), victim.getEyeY(), victim.getZ(), 4, 0.25, 0.25, 0.25, 0.01);

        return true;
    }

    /**
     * Drops every drowning in a level that is going away.
     *
     * These are held in a plain static list rather than by the world, so nothing else
     * would ever clear them.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(drowning -> drowning.level == level);
    }
}
