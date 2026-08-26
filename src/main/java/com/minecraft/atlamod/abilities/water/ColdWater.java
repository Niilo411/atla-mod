package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.PassiveAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Offensive / Water. Passive. Ice and snow count as water to bend from — but only the
 * block the bender is standing directly ON.
 *
 * Water's whole constraint is supply: away from a river or a lake, every ability
 * drinks a unit from a canteen, and a dry bender with an empty canteen cannot bend at
 * all. This widens that without removing it. A frozen lake, a snowfield or a
 * mountainside becomes bendable ground, where before it was as dry as a desert — a
 * waterbender stood on a sheet of ice being unable to bend was always the odd case.
 *
 * The "directly on" rule is what keeps it a passive rather than a free pass. Open
 * water works from fifteen blocks away; this works from zero. Snow within sight but
 * not underfoot is no help, so it is something to position for.
 */
public class ColdWater implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "cold water";

    @Override
    public String getName() {
        return "Cold water";
    }

    @Override
    public String getDescription() {
        return "Ice and snow you are standing on can be bent, like water or a canteen";
    }

    /**
     * Whether this bender is standing on something they can draw from.
     *
     * Checked at the block BELOW their feet, which is what "standing directly on"
     * means — and deliberately not at their own position, or a bender waist-deep in a
     * snow layer would qualify while one stood on solid ice would not.
     *
     * Also checks the block they are IN, because a snow LAYER is walked on rather
     * than stood above: a player on top of fresh snowfall has the layer at their feet
     * position, not beneath it, and refusing that would make the passive fail on the
     * single most obvious thing it should work on.
     */
    public static boolean standingOnSource(ServerPlayer player, BendingData data) {
        if (!data.hasPassiveEquipped(KEY)) return false;

        BlockPos feet = player.blockPosition();

        return isColdSource(player.level().getBlockState(feet.below()))
                || isColdSource(player.level().getBlockState(feet));
    }

    /**
     * Every form of ice and snow.
     *
     * Tags rather than a list of blocks: BlockTags.ICE covers ice, packed ice, blue
     * ice and frosted ice, and BlockTags.SNOW covers the block, the layer and powder
     * snow. Anything Mojang adds to either is included without a change here.
     */
    private static boolean isColdSource(BlockState state) {
        return state.is(BlockTags.ICE) || state.is(BlockTags.SNOW);
    }
}
