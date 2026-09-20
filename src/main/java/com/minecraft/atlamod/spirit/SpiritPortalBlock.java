package com.minecraft.atlamod.spirit;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The lit spirit portal: vanilla's nether portal shape, blue, and going somewhere else.
 *
 * It is NOT a subclass of NetherPortalBlock. That class hardcodes the nether — where it
 * sends you, the shape it rebuilds itself into, and the fact that it can be lit by a
 * flint and steel — and every one of those is wrong here.
 *
 * THE BLUE IS NOT A TEXTURE. The block reuses vanilla's own block/nether_portal texture
 * through a model of ours that marks its faces with "tintindex": 0, and a block colour
 * handler registered on the client answers that tint with blue. See
 * SpiritPortalColors. Nothing is shipped but two small JSON files.
 *
 * THE ONE MINUTE TIMER IS A SCHEDULED BLOCK TICK, not a countdown held in memory.
 * Vanilla saves scheduled ticks with the chunk, so a portal keeps its remaining time
 * across a save, a restart, or the chunk simply unloading while nobody is near it. A
 * static map of deadlines would lose all three and leave a portal that never closes —
 * which is the exact bug the Spirit World side is supposed to be alone in having.
 */
public class SpiritPortalBlock extends Block {

    public static final MapCodec<SpiritPortalBlock> CODEC = simpleCodec(SpiritPortalBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** How long an overworld-side portal stays open: exactly one minute. */
    public static final int OPEN_TICKS = 20 * 60;

    private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public SpiritPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * The one minute is up: tear the whole portal out, not just this block.
     *
     * Every block of the portal is given the same scheduled tick, so whichever fires
     * first does the work and the rest find nothing left to do. Clearing them one at a
     * time as their own ticks came round would leave a portal visibly dissolving in
     * pieces, and a player standing in the last one would still be teleported.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        collapse(level, pos);
    }

    /**
     * Removes every portal block connected to this one.
     *
     * A flood fill rather than the frame's own geometry, because by the time this runs
     * the frame may have been mined and there would be nothing to ask. The portal blocks
     * themselves are the only reliable record of where the portal was.
     */
    public static void collapse(Level level, BlockPos start) {
        Block portal = com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get();
        if (!level.getBlockState(start).is(portal)) return;

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty()) {
            BlockPos at = queue.removeFirst();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (seen.contains(next)) continue;
                if (!level.getBlockState(next).is(portal)) continue;

                seen.add(next);
                queue.add(next);
            }
        }
    }

    /**
     * Walking in sends you through, once the entity is actually inside the shape.
     *
     * The portal cooldown is vanilla's own and is what stops an arrival bouncing
     * straight back: a player set down in a temple is standing in the far portal, and
     * without the cooldown the two would throw them back and forth every tick.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (entity.isOnPortalCooldown()) {
            // Re-arming while they remain inside is what makes the cooldown a "since you
            // last left a portal" timer rather than a fixed delay after arriving.
            entity.setPortalCooldown();
            return;
        }
        SpiritTravel.through(entity, pos);
    }

    /** Blue haze drifting out of the portal, matching the block's own colour. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(80) != 0) return;

        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(),
                0.0, 0.02, 0.0);
    }
}
