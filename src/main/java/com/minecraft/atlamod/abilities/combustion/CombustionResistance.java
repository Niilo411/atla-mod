package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Right / Combustion. Passive. Explosions do the bender far less harm — their own
 * included.
 *
 * The design lists this ability by name and then says "Wip", so everything below the
 * name is INVENTED. The reading taken is the obvious one: a combustion bender who
 * could not survive being near their own work would be unable to use half the element,
 * and the misfire on a cancelled charge makes that a constant hazard rather than an
 * occasional one.
 *
 * Applied in the damage handler rather than as an attribute modifier, because vanilla
 * has no explosion-resistance attribute to modify — see ServerEvents.onIncomingDamage.
 */
public class CombustionResistance implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "combustion resistance";

    /**
     * What explosion damage is multiplied by while this is equipped.
     *
     * INVENTED. Deliberately not zero: immunity to a bender's own nuke would leave the
     * ten second wind-up as the only cost it had, and a misfire that could simply be
     * ignored would stop being a reason to finish what you started.
     */
    public static final float DAMAGE_MULTIPLIER = 0.25F;

    @Override
    public String getName() {
        return "Combustion resistance";
    }

    @Override
    public String getDescription() {
        return "Explosions deal you a quarter of their damage, your own included";
    }
}
