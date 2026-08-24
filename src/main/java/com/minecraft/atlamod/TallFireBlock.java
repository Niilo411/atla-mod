package com.minecraft.atlamod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The upper half of Taller Fire.
 *
 * Vanilla fire cannot sit on top of vanilla fire: FireBlock#canSurvive wants a
 * face-sturdy block below or a flammable neighbour, and a fire block is neither, so
 * a stacked vanilla fire removes itself on its first scheduled tick. BaseFireBlock
 * does not override canSurvive, so extending it directly gives a fire that survives
 * anywhere — which is the whole trick.
 *
 * It hurts and ignites exactly like normal fire (that is BaseFireBlock's doing) and
 * counts as IS_FIRE, so BendingFire's damage multiplier applies to it too. It does
 * not spread, and it follows the fire underneath it out of existence.
 */
public class TallFireBlock extends BaseFireBlock {

    public static final MapCodec<TallFireBlock> CODEC = simpleCodec(TallFireBlock::new);

    /** How often to check whether the fire below is still there. */
    private static final int CHECK_INTERVAL = 40;

    public TallFireBlock(Properties properties) {
        super(properties, 1.0F);
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    /** It is the top of someone else's fire, not a fire that spreads on its own. */
    @Override
    protected boolean canBurn(BlockState state) {
        return false;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }

    /**
     * Burns out when the fire underneath does. Without this the top half would
     * outlive its base and hang in the air permanently, since it is built not to
     * care what is below it.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockState(pos.below()).getBlock() instanceof BaseFireBlock)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }
}
