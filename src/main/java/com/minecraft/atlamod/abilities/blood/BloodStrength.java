package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.abilities.PassiveAbility;

/**
 * Right / Blood. Passive. The bender has a bloodbending LEVEL of their own, shown top
 * left, and it decides who may bend whom.
 *
 * The only ability in the mod that changes what OTHER people can do to you. The rule
 * is a strict pecking order:
 *
 *  - somebody with a LOWER blood level than yours cannot bloodbend you at all;
 *  - and you cannot bloodbend anybody with a HIGHER blood level than yours.
 *
 * Enforced in one place, {@link Blood#canBend}, which every bloodbending ability that
 * picks a target calls — so the rule cannot be forgotten by the next one added.
 *
 * The level runs off its own experience track ({@code BendingData.bloodXp}), which is
 * the whole reason that track exists. 200 blood xp is a blood level, exactly as 200
 * ordinary xp is an ordinary one, but ONLY bloodbending abilities pay into it: a figure
 * that also went up from firebending would make the comparison meaningless.
 *
 * ONE INTERPRETATION worth flagging: the level accumulates whether or not this passive
 * is in a slot, but the PROTECTION only applies to somebody actually carrying it. A
 * passive that worked from the inventory would not be a passive — and it means the
 * defence is a real choice of slot rather than something everyone has for free.
 */
public class BloodStrength implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "blood strength";

    @Override
    public String getName() {
        return "Blood strength";
    }

    @Override
    public String getDescription() {
        return "Shows your bloodbending level. Weaker bloodbenders cannot touch you, "
                + "and you cannot touch stronger ones";
    }
}
