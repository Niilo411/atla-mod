package io.github.niilo411.elementalpaths.abilities.blood;

import io.github.niilo411.elementalpaths.abilities.PassiveAbility;

/**
 * Right / Blood. Passive. The channeler has a blood channeling LEVEL of their own, shown top
 * left, and it decides who may channel whom.
 *
 * The only ability in the mod that changes what OTHER people can do to you. The rule
 * is a strict pecking order:
 *
 *  - somebody with a LOWER blood level than yours cannot blood channel you at all;
 *  - and you cannot blood channel anybody with a HIGHER blood level than yours.
 *
 * Enforced in one place, {@link Blood#canChannel}, which every blood channeling ability that
 * picks a target calls — so the rule cannot be forgotten by the next one added.
 *
 * The level runs off its own experience track ({@code ChannelingData.bloodXp}), which is
 * the whole reason that track exists. 200 blood xp is a blood level, exactly as 200
 * ordinary xp is an ordinary one, but ONLY blood channeling abilities pay into it: a figure
 * that also went up from fire channeling would make the comparison meaningless.
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
        return "Shows your blood channeling level. Weaker blood channelers cannot touch you, "
                + "and you cannot touch stronger ones";
    }
}
