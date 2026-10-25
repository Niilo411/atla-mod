package io.github.niilo411.elementalpaths.abilities.fire;

import io.github.niilo411.elementalpaths.abilities.PassiveAbility;

/**
 * Masterclass / Fire. Passive. Nothing that burns can hurt the channeler any more.
 *
 * Covers the whole IS_FIRE tag rather than a list of sources, so it takes in
 * standing in fire, lava, magma blocks, burning after being lit, and every fire
 * ability in the mod — including another channeler's blue fire, and the channeler's own.
 *
 * Handled in two places, because damage and burning are separate things in
 * Minecraft: ServerEvents cancels the damage, and the tick loop keeps clearing the
 * fire ticks so the player does not stand there visibly alight taking nothing.
 */
public class FireImmunity implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "Fire immunity";

    @Override
    public String getName() {
        return "Fire immunity";
    }

    @Override
    public String getDescription() {
        return "Fire, lava and burning cannot hurt you";
    }
}
