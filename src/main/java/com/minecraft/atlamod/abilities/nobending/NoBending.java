package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ElementPaths;
import net.minecraft.world.entity.LivingEntity;

/**
 * What it means to have no bending, and the rules that follow from it.
 *
 * A NON-BENDER IS SOMEBODY WHO HAS NO BENDING ART AT ALL — not somebody who once picked
 * "no bending" off the selection screen. The test is the unlocked element list: hold the
 * no-bending path and nothing else, and you are a non-bender; hold anything else as well
 * and you are a bender who has also learned to block chi.
 *
 * THIS USED TO ASK THE MAIN ELEMENT, and that was wrong in both directions. Somebody who
 * picked no bending and was then GIVEN firebending by command kept a main element of
 * "nobending" for ever, so they were a firebender with no chi bar and no way to fill a
 * pool they could now spend from. And granting no bending by command had to overwrite the
 * main element to register at all, which silently took a bender's chi away. Asking "do you
 * actually bend anything" answers both at once and needs no special case in either command:
 *
 * <pre>
 *   picked no bending                      -> no chi        (nothing else unlocked)
 *   picked no bending, then given fire     -> chi comes back (fire is a bending art)
 *   picked fire, then given no bending     -> chi stays      (fire is still there)
 *   given no bending having nothing at all -> no chi
 * </pre>
 *
 * THREE THINGS ARE TAKEN AWAY, and they are all the same thing said three ways: a
 * non-bender has no chi. So chi does not regenerate ({@code ServerEvents}' regen block),
 * meditating is refused (which exists only to fill a pool they do not have), and the HUD
 * draws no chi bar. Their two abilities cost nothing, which is what makes them castable
 * at all rather than a special case in the dispatcher.
 *
 * SO XP HAS TO COME FROM SOMEWHERE ELSE. Every other path earns it by casting and by
 * meditating, and a non-bender does neither often enough to progress — two abilities on
 * long cooldowns is not an income. They earn it by KILLING, scaled by how much the thing
 * they killed was worth (see {@link #xpForKill}), and by MINING — a flat amount per
 * block, since digging is otherwise the one kind of physical effort in the game that
 * pays every other path something and this one nothing.
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

    /**
     * XP for a single block mined, whatever the block. Flat rather than scaled to
     * anything, unlike a kill — a block has no "maximum health" to measure it by, and
     * this is meant as a slow trickle alongside mining's own ordinary rewards (Spirit
     * Ore's own XP still applies on top of this, for anyone breaking it), not a second
     * income to rival killing.
     */
    public static final int XP_PER_BLOCK_MINED = 1;

    /** A zombie's maximum health. The reference the scale divides by. */
    private static final float ZOMBIE_HEALTH = 20.0F;

    private NoBending() {
    }

    /**
     * Whether this player has no bending art at all.
     *
     * Holding the no-bending path is necessary but not sufficient — one single other
     * element and the answer is no, whatever order the two arrived in. Somebody who has
     * chosen nothing yet is NOT a non-bender either: an empty list is a player who has not
     * reached the selection screen, not one who turned it down.
     */
    public static boolean is(BendingData data) {
        if (data == null) return false;

        boolean hasPath = false;
        for (String element : data.getUnlockedElements()) {
            if (!ElementPaths.isNoBending(element)) return false;
            hasPath = true;
        }
        return hasPath;
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
