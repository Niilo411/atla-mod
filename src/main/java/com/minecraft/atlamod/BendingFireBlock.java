package com.minecraft.atlamod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Fire that an ability laid down, where vanilla fire won't do the job.
 *
 * Two things vanilla can't give us. Fire cannot sit on top of fire —
 * FireBlock#canSurvive wants a face-sturdy block below or a flammable neighbour,
 * and a fire block is neither — so Taller Fire's upper half would delete itself on
 * its first scheduled tick. And soul fire, the only blue fire in the game, only
 * survives on soul sand or soul soil, so Blue Fire can't use it anywhere else.
 *
 * BaseFireBlock does NOT override canSurvive, so extending it directly gives fire
 * that survives anywhere, which solves both. It hurts and ignites like real fire
 * (BaseFireBlock's doing) and still counts as IS_FIRE, so BendingFire's damage
 * multipliers apply to it. It does not spread.
 *
 * STACKED tells it which job it has: a stacked block is the top half of something
 * and dies with the fire beneath it, while an unstacked one is a fire in its own
 * right and simply burns out after a while.
 */
public class BendingFireBlock extends BaseFireBlock {

    public static final BooleanProperty BLUE = BooleanProperty.create("blue");
    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");

    public static final MapCodec<BendingFireBlock> CODEC = simpleCodec(BendingFireBlock::new);

    /** How often a stacked block checks that its base is still burning. */
    private static final int CHECK_INTERVAL = 40;

    /** How long an unstacked block burns before going out (30 seconds). */
    private static final int LIFETIME = 600;

    public BendingFireBlock(Properties properties) {
        super(properties, 1.0F);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BLUE, Boolean.FALSE)
                .setValue(STACKED, Boolean.FALSE));
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BLUE, STACKED);
    }

    /** It is fire an ability placed, not a fire that goes looking for fuel. */
    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        level.scheduleTick(pos, this, state.getValue(STACKED) ? CHECK_INTERVAL : LIFETIME);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(STACKED)) {
            // A fire in its own right: its time is simply up.
            level.removeBlock(pos, false);
            return;
        }

        // A top half outlives nothing. Without this it would hang in the air after
        // its base burned out, since it is built not to care what is below it.
        if (!(level.getBlockState(pos.below()).getBlock() instanceof BaseFireBlock)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    /** The state to place for a given colour and role. */
    public static BlockState stateFor(boolean blue, boolean stacked) {
        return Atlamod.BENDING_FIRE.get().defaultBlockState()
                .setValue(BLUE, blue)
                .setValue(STACKED, stacked);
    }
}
