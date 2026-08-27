package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Right / Lava. Passive. Lava cannot touch the bender — and nothing else changes.
 *
 * The design's own words are "fire resistance to Lava only", and the "only" is the
 * whole ability. Fire immunity over in the fire masterclass cancels the entire IS_FIRE
 * tag: fire blocks, being alight, magma, every fire ability and lava all at once. This
 * cancels exactly one damage type, so a lavabender wearing it still burns in an
 * ordinary fire, still takes a Fire Breath in the face, and still cooks standing on
 * magma. What they can do is walk through their own work.
 *
 * That makes it the counterpart of Combustion resistance rather than of Fire immunity —
 * an element whose abilities are a constant hazard to their own caster needs some way
 * of surviving them, and every lava ability except the throw drops something the bender
 * is standing next to.
 *
 * Two halves, in two places, for the reason Fire immunity documents: the damage is
 * cancelled in the damage handler, and the BURNING is cleared in the player tick.
 * Cancelling only the damage would leave the bender standing in lava wreathed in flames
 * and taking nothing, which reads as a bug rather than as protection. The fire ticks are
 * cleared only while they are actually IN lava, which is what keeps this "lava only"
 * rather than a quiet second copy of Fire immunity.
 */
public class LavaResistance implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "lava resistance";

    @Override
    public String getName() {
        return "Lava resistance";
    }

    @Override
    public String getDescription() {
        return "Lava cannot burn or hurt you. Ordinary fire still can";
    }
}
