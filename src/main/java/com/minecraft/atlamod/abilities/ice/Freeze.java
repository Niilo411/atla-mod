package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Left / Ice. Seals whatever the bender is looking at inside two blocks of ice for
 * ten seconds — held completely still, and untouchable while it lasts.
 *
 * The immunity is not a drawback bolted on to balance the hold; it is what makes the
 * hold possible at all. Ice at head height means vanilla suffocation, so a victim who
 * could be hurt would simply be killed by the shell. See {@link Frozens}.
 *
 * What that turns the ability into is a decision rather than a combo: ten seconds
 * where a target cannot act and cannot be finished. Cheap (50 chi) because using it
 * at the wrong moment is its own punishment.
 */
public class Freeze implements Ability {

    /** How far the freeze reaches, in blocks. */
    private static final double REACH = 15.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /** Ten seconds, as specced. */
    private static final int FREEZE_TICKS = 200;

    @Override
    public String getName() {
        return "Freeze";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    /** No cooldown. Freezing the wrong thing is the cost. */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    /**
     * Refuses the cast with nothing in front of the bender, or on something already
     * sealed. Checked before chi is spent, so neither costs anything.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        return target != null && !Frozens.isFrozen(target);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return; // Moved between the check and here.

        Frozens.freeze(level, target, FREEZE_TICKS);
    }
}
