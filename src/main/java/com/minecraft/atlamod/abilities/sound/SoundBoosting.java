package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Left / Sound. Passive. Every AIR and SOUND ability the bender has is sharpened:
 * a quarter more damage, effects that last a quarter longer, and cooldowns and
 * charge times a quarter shorter.
 *
 * The broadest passive in the mod by a distance -- it touches two whole elements
 * rather than one ability -- and its four halves live in three different places
 * because there is no single hook that could carry all of them:
 *
 *  - the damage and the effect durations are applied BY the abilities, through
 *    {@link Sound#damage} and {@link Sound#duration}, since a damage-handler rule
 *    could not tell an air ability from any other indirectMagic in the mod;
 *  - the cooldowns and charge times are applied by the DISPATCHER, through
 *    {@link Sound#shorten}, because those are read in places no ability touches and
 *    asking each one to shorten its own would be a rule broken by the next ability
 *    anybody added.
 *
 * Which abilities count is decided by ElementPaths: whichever tree an ability sits
 * in is the element it belongs to.
 */
public class SoundBoosting implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "sound boosting";

    @Override
    public String getName() {
        return "Sound boosting";
    }

    @Override
    public String getDescription() {
        return "Air and sound abilities deal 25% more damage, their effects last 25% longer, "
                + "and their cooldowns and charge times are 25% shorter";
    }
}
