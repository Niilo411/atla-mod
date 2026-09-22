package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ElementPaths;
import net.minecraft.world.entity.LivingEntity;

/**
 * What it means to have no bending, and the rules that follow from it.
 *
 * A NON-BENDER IS DEFINED BY THEIR MAIN ELEMENT, not by having no abilities. The main
 * element is the one they picked on their first join and is never overwritten — the
 * ACTIVE element changes every time somebody presses [Y] — so it is the only field that
 * still answers "what did this person choose to be" after they have been granted
 * something else by command.
 *
 * THREE THINGS ARE TAKEN AWAY, and they are all the same thing said three ways: a
 * non-bender has no chi. So chi does not regenerate ({@code ServerEvents}' regen block),
 * meditating is refused (which exists only to fill a pool they do not have), and the HUD
 * draws no chi bar. Their two abilities cost nothing, which is what makes them castable
 * at all rather than a special case in the dispatcher.
 *
 * SO XP HAS TO COME FROM SOMEWHERE ELSE. Every other path earns it by casting and by
 * meditating, and a non-bender does neither often enough to progress — two abilities on
 * long cooldowns is not an income. They earn it by KILLING instead, scaled by how much
 * the thing they killed was worth: see {@link #xpForKill}.
 */
public final class NoBending {

    /**
     * XP for a zombie, which is the figure the whole scale is pinned to.
     *
     * Deliberately the same 15 a mid-tier ability pays, so a non-bender killing things
     * progresses at about the rate a bender casting things does. At 200 XP to a level
     * that is roughly thirteen zombies per level.
     */
    public static final int XP_PER_ZOMBIE = 15;

    /** A zombie's maximum health. The reference the scale divides by. */
    private static final float ZOMBIE_HEALTH = 20.0F;

    private NoBending() {
    }

    /** Whether this data belongs to somebody who chose no bending. */
    public static boolean is(BendingData data) {
        return data != null && ElementPaths.isNoBending(data.getMainElement());
    }

    /**
     * What killing this thing is worth.
     *
     * SCALED BY MAXIMUM HEALTH, not by the damage dealt, and the difference matters: a
     * victim already hurt by somebody else must be worth the same as one killed outright,
     * or the reward would go to whoever arrived last rather than to whoever did the work.
     * Maximum health is also the only measure of "how big was that" every living thing in
     * the game carries, including modded ones and other players.
     *
     * A zombie's 20 hearts-worth is the anchor at {@value #XP_PER_ZOMBIE}, so the rate is
     * three quarters of maximum health: a chicken (4) pays 3, a player (20) pays 15, an
     * iron golem (100) pays 75, and the wither (300) pays 225.
     *
     * Never zero. Something with a sliver of maximum health is still a kill, and an
     * ability that silently pays nothing reads as broken rather than as cheap.
     */
    public static int xpForKill(LivingEntity victim) {
        if (victim == null) return 0;

        float health = Math.max(1.0F, victim.getMaxHealth());

        return Math.max(1, Math.round(health / ZOMBIE_HEALTH * XP_PER_ZOMBIE));
    }
}
