package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Masterclass / Water. Passive. The bender simply does not run out of air.
 *
 * Kept topped up rather than granted as a potion effect, so nothing can dispel it
 * and it never shows a timer. The tick loop in ServerEvents does the topping up.
 *
 * It also answers Drown: a bender who cannot run out of air cannot be drowned, so
 * Drownings drops any victim wearing this rather than fighting it every tick.
 */
public class WaterBreathing implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "water breathing";

    @Override
    public String getName() {
        return "water breathing";
    }

    @Override
    public String getDescription() {
        return "You never run out of air, and cannot be drowned";
    }
}
