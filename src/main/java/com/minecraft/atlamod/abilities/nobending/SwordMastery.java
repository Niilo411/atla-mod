package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.PassiveAbility;

import java.util.List;

/**
 * No bending / Offensive. Passive. A little more weight behind every swing of a
 * sword, for a path that otherwise trades in fists and feet.
 *
 * The MIRROR of {@link com.minecraft.atlamod.abilities.metal.ToughKnuckles} rather
 * than a copy of it, and the difference is the whole point of each: Tough knuckles
 * raises an EMPTY hand up to a floor, because it is what punching becomes once
 * bending is off the table. This is the opposite case — a bender who picked up a
 * sword instead — so it ADDS to whatever the sword and its enchantments already
 * hit for, rather than replacing anything. The two can never both apply to the
 * same swing: one requires an empty hand, this requires a sword in it.
 *
 * THREE CHAINED UPGRADES, the same shape {@link QuickHands} (this path's other new
 * passive) and {@code Mine}'s Obsidian Breaker / Timber use — each requires the one
 * before it. Each tier's bonus is a SETTING (AtlaConfig's noBendingPassives.
 * swordMastery section, in tenths of a damage point), shipped small and linear —
 * half a heart a step. A sword already hits hard on its own, so "a little more" is
 * a real floor here, not a starting point to escalate from the way Quick Hands'
 * mining speed did the first time round.
 */
public class SwordMastery implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "sword mastery";

    public static final String HONED_EDGE = "sword_mastery_honed_edge";
    public static final String PRACTICED_FORM = "sword_mastery_practiced_form";
    public static final String MASTER_DUELIST = "sword_mastery_master_duelist";

    @Override
    public String getName() {
        return "Sword Mastery";
    }

    @Override
    public String getDescription() {
        return "A little more damage with a sword while equipped — each upgrade adds a"
                + " little more again, up to three upgrades (exact amounts are a server"
                + " setting)";
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(
                new AbilityUpgrade(
                        HONED_EDGE,
                        "Honed Edge",
                        "A little more sword damage still",
                        5),
                new AbilityUpgrade(
                        PRACTICED_FORM,
                        "Practiced Form",
                        "More again",
                        10,
                        HONED_EDGE),
                new AbilityUpgrade(
                        MASTER_DUELIST,
                        "Master Duelist",
                        "More again",
                        15,
                        PRACTICED_FORM));
    }

    /**
     * How much this bender's sword hits add on top of whatever it already hit for,
     * highest owned upgrade first. Callers are expected to have already checked
     * {@link BendingData#hasPassiveEquipped} and that a sword is what landed the
     * blow — this is purely "how much", not "whether".
     *
     * Reads AtlaConfig's tenths-of-a-point values rather than a fixed constant, so
     * a server owner can retune every tier.
     */
    public static float bonusFor(BendingData data) {
        if (data.hasUpgrade(MASTER_DUELIST)) return AtlaConfig.swordMasteryMasterDuelistBonusTenths() / 10.0F;
        if (data.hasUpgrade(PRACTICED_FORM)) return AtlaConfig.swordMasteryPracticedFormBonusTenths() / 10.0F;
        if (data.hasUpgrade(HONED_EDGE)) return AtlaConfig.swordMasteryHonedEdgeBonusTenths() / 10.0F;
        return AtlaConfig.swordMasteryBaseBonusTenths() / 10.0F;
    }
}
