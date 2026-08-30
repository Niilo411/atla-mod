package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.PassiveAbility;

import java.util.List;

/**
 * Air / CENTRE. Passive. Ordinary meditation gathers experience far faster, and the
 * further along the bender already is the faster it goes.
 *
 * Two things make this ability unusual, and both are firsts for the mod.
 *
 * It belongs to NO path — it sits in the middle of the four arms and is bought outright
 * for 20 levels, so it is available whichever way a bender has gone. That is the point
 * of it: it is not a technique, it is practice.
 *
 * And it is a passive that MODIFIES SOMETHING ELSE rather than doing anything itself.
 * Meditation already exists on its own key and has since long before this — holding it
 * roots the bender and pays a flat 2 xp a second at any level. This changes the rate of
 * that, and nothing more. There is no key to press and nothing new to learn: the thing
 * you already do simply gets better.
 *
 * The rate rewards a bender who already has levels rather than one who needs them:
 * 2 a second to start, stepping up by 2 every ten levels — 4 at level 10, 6 at 20,
 * 8 at 30 — and capping at 10 from level 40 until Pure peace takes the ceiling off.
 */
public class AdvancedMeditating implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "advanced meditating";

    /** Key of the upgrade that lifts the ceiling off the rate. */
    public static final String PURE_PEACE = "advanced_meditating_pure_peace";

    /**
     * What meditation pays without this passive, and what it starts at with it.
     *
     * Two a second is what ordinary meditation has always given, so this is never worse
     * than the thing it improves on. It also matters that the floor is not zero: the
     * ability costs 20 LEVELS, which are SPENT, so a bender can be sitting at level 0
     * the moment they unlock it.
     */
    private static final int BASE = 2;

    /** How many levels buy each step up. */
    private static final int LEVELS_PER_STEP = 10;

    /** How much a step is worth. */
    private static final int PER_STEP = 2;

    /** The ceiling, until Pure peace is bought. Reached at level 40. */
    private static final int CAP = 10;

    @Override
    public String getName() {
        return "Advanced meditating";
    }

    @Override
    public String getDescription() {
        return "Meditating gathers 2 more XP a second for every 10 levels you have, up to 10";
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(new AbilityUpgrade(
                PURE_PEACE,
                "Pure peace",
                "No ceiling on the rate — it keeps climbing with your level",
                40));
    }

    /**
     * What meditation pays this bender per second.
     *
     * The one place the meditation rate is decided, whether or not this passive is
     * equipped — so ServerEvents' meditation block just asks, rather than having to
     * know the rule itself.
     */
    public static int meditationRate(BendingData data) {
        if (!data.hasPassiveEquipped(KEY)) return BASE;

        // Integer division on purpose: the design says "every ten levels", so the rate
        // STEPS at each boundary rather than creeping up level by level. 2 a second up
        // to level 9, 4 from 10, 6 from 20, 8 from 30, 10 from 40.
        int rate = BASE + ((data.getLevel() / LEVELS_PER_STEP) * PER_STEP);

        if (!data.hasUpgrade(PURE_PEACE)) {
            rate = Math.min(CAP, rate);
        }

        return rate;
    }
}
