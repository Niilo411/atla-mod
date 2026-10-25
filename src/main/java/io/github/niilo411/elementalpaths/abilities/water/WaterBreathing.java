package io.github.niilo411.elementalpaths.abilities.water;

import io.github.niilo411.elementalpaths.abilities.PassiveAbility;

/**
 * Masterclass / Water. Passive. The channeler simply does not run out of air.
 *
 * Kept topped up rather than granted as a potion effect, so nothing can dispel it
 * and it never shows a timer. The tick loop in ServerEvents does the topping up.
 *
 * It also answers Drown: a channeler who cannot run out of air cannot be drowned, so
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
