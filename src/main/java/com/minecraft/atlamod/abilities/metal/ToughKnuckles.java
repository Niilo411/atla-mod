package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Right / Metal. Passive. The bender's bare fists hit as hard as an iron sword.
 *
 * Applied in the damage handler rather than by an attribute modifier, and the
 * distinction matters: this replaces what an EMPTY hand is worth, it does not add to
 * whatever is being held. A bender swinging a netherite axe is unaffected -- the
 * passive is about punching, and stacking it onto a weapon would make it a flat
 * damage buff that happened to be called knuckles.
 *
 * Shares its hook with Compressed punches, which is checked FIRST because it is an
 * ability being actively held up rather than a permanent floor.
 */
public class ToughKnuckles implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "tough knuckles";

    /** What an iron sword deals. */
    public static final float PUNCH_DAMAGE = 6.0F;

    @Override
    public String getName() {
        return "Tough knuckles";
    }

    @Override
    public String getDescription() {
        return "Your bare fists hit as hard as an iron sword";
    }
}
