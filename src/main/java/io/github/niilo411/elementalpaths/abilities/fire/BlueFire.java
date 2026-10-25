package io.github.niilo411.elementalpaths.abilities.fire;

import io.github.niilo411.elementalpaths.abilities.PassiveAbility;

/**
 * Masterclass / Fire. Passive. Everything the channeler burns turns blue, and every
 * fire ability hits twice as hard.
 *
 * The colour can't come from vanilla soul fire: SoulFireBlock#canSurvive only
 * accepts soul sand and soul soil underneath, so it would refuse to exist anywhere
 * else. Blue fire is ChannelingFireBlock with BLUE set — see ChannelingFire#placeGrounded.
 * Particles switch from FLAME to SOUL_FIRE_FLAME via ChannelingFire#flame.
 */
public class BlueFire implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "blue fire";

    /** Flat damage anything standing in blue fire takes, per hit (3 hearts). */
    public static final float CONTACT_DAMAGE = 6.0F;

    /** How much harder every fire ability hits while this is equipped. */
    public static final float DAMAGE_MULTIPLIER = 2.0F;

    @Override
    public String getName() {
        return "blue fire";
    }

    @Override
    public String getDescription() {
        return "Fire burns blue, and all fire abilities deal double damage";
    }
}
