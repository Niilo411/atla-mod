package com.minecraft.atlamod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible footing under a surfing waterbender.
 *
 * Freezing the water would work, but it looks like ice rather than like running on
 * water. Instead this sits in the AIR block directly above the surface, so the water
 * underneath stays exactly as it was and visible — nothing is replaced, and there is
 * no ice to see.
 *
 * It is only a sliver tall, which puts the walking surface within a couple of pixels
 * of the waterline, and it renders nothing at all. It removes itself on a scheduled
 * tick, so a bender leaves no trail behind them.
 */
public class SurfPlatformBlock extends Block {

    /**
     * One sixteenth of a block. Standing on it puts the bender fractionally above the
     * waterline, which is far less noticeable than standing a whole block up would be.
     */
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    /** Ticks before it goes. Long enough to run on, short enough to feel like a wake. */
    private static final int LIFETIME = 20;

    public SurfPlatformBlock(Properties properties) {
        super(properties);
    }

    /** Never drawn: the point is that the player sees water, not a platform. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** No outline either, or an invisible block would still light up when looked at. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        level.scheduleTick(pos, this, LIFETIME);
    }

    /** Its time is up. Nothing tracks these, so each one has to see itself out. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }
}
