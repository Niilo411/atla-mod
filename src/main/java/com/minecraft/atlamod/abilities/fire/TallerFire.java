package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Balanced / Fire. Passive. While equipped, fire laid down by an ability is two
 * blocks tall instead of one — Firewall, Fire Ring, Fire Spikes, and Fire Blow
 * when that exists.
 *
 * The second block can't be vanilla fire: FireBlock#canSurvive needs a face-sturdy
 * block underneath or something flammable alongside, and fire is neither, so a
 * stacked vanilla fire deletes itself on its first scheduled tick. The upper half
 * is ModBlocks.TALL_FIRE instead — see BendingFire#placeGrounded.
 */
public class TallerFire implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "Taller fire";

    @Override
    public String getName() {
        return "Taller fire";
    }

    @Override
    public String getDescription() {
        return "Ability fire burns 2 blocks tall";
    }
}
