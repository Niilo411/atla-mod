package com.minecraft.atlamod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible footing, laid under a bender for as long as they need it and gone
 * straight after.
 *
 * Written for Water Surf, where freezing the water would work but plainly looks like
 * ice: this sits in the AIR block above the surface instead, so the water underneath
 * stays exactly as it was and visible. Air Scooter uses the same block for the same
 * underlying reason — a REAL block carries the player, so the client walks on it
 * normally and there is nothing for the server to correct.
 *
 * The two want different heights, which is what {@link #HEIGHT} is for: a sliver for
 * surfing, which puts the walking surface within a couple of pixels of the waterline,
 * and half a block for the scooter, which is exactly the height it hovers at. It
 * renders nothing either way, and removes itself on a scheduled tick so a bender
 * leaves no trail behind them.
 */
public class SurfPlatformBlock extends Block {

    /** Height in sixteenths. Only the two values the abilities actually use exist. */
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 8);

    /** One sixteenth: Water Surf, riding the waterline. */
    public static final int SURF_HEIGHT = 1;

    /** Eight sixteenths: Air Scooter, hovering half a block up. */
    public static final int SCOOTER_HEIGHT = 8;

    /** Ticks before it goes. Long enough to run on, short enough to feel like a wake. */
    private static final int LIFETIME = 20;

    public SurfPlatformBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HEIGHT, SURF_HEIGHT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEIGHT);
    }

    /** Never drawn: the point is that the player sees water or air, not a platform. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0.0, 0.0, 0.0, 16.0, state.getValue(HEIGHT), 16.0);
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
