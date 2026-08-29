package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerPlayer;

/**
 * Right / Ice. Summons a block of ice onto the crosshair; a left click lobs it a few
 * blocks ahead, where it sits for two seconds and then bursts.
 *
 * The delay is the ability. It cannot be aimed at anything moving with any
 * confidence, so it is a thing to put WHERE something is going to be — a doorway, a
 * choke, the ground under a fight — rather than a projectile.
 *
 * Its costs and its blast damage are the only figures in icebending that were not in
 * the design; see IceBombs.BLAST_DAMAGE.
 */
public class IceBomb implements TwoPhaseAbility {

    @Override
    public String getName() {
        return "Ice Bomb";
    }

    /** INVENTED: the design gives this ability no chi cost. In line with its siblings. */
    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    /** INVENTED: the design gives this ability no xp reward. */
    @Override
    public int getXpReward() {
        return 10;
    }

    /** INVENTED: the design gives this ability no cooldown. */
    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds, from the throw
    }

    /** Held until thrown — the bomb floats on the crosshair as long as you like. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /**
     * Nothing is drawn here on purpose: the bomb is a REAL block entity carried by
     * IceBombs, so it is already visible. Particles on top of it would only make the
     * thing that is actually there harder to see.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        IceBombs.throwIt(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        IceBombs.summon(player);
    }
}
