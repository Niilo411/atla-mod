package com.minecraft.atlamod.spirit;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    /**
     * Whether the far side of this portal comes out on an ISLAND rather than in a temple.
     *
     * False for every portal in a frame, which is all of them but one: a temple portal
     * leads to the nearest temple, and that is the whole navigation of the dimension. True
     * only for the free-standing portal an Avatar tears open while meditating, which has
     * no temple at either end and lands on the island nearest wherever it was opened.
     *
     * A BLOCKSTATE RATHER THAN A STATIC MAP OF POSITIONS, for exactly the reason the one
     * minute timer is a scheduled tick — see the class note. A map would be lost to a
     * restart, to the chunk unloading, and to a save; a blockstate is saved with the chunk
     * like any other block property and cannot go out of step with the block it describes.
     *
     * It changes nothing about how the portal LOOKS. Both values map to the same two
     * models, so the blockstate file gained variants but no art.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ISLAND =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("island");

    /** How long an overworld-side portal stays open: exactly one minute. */
    public static final int OPEN_TICKS = 20 * 60;

    private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public SpiritPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(ISLAND, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ISLAND);
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

    /**
     * A blue haze and a steady buzz, for as long as the portal stands.
     *
     * NO TIMER AND NOTHING TO STOP. The buzz is emitted by the portal BLOCKS themselves,
     * so it lasts exactly as long as they do — it starts the moment the frame fills and
     * is gone the instant the blocks are, whether that is the overworld's one minute
     * running out or somebody mining the frame out from under it. A sound started when the
     * portal opened and stopped on a countdown would be a second clock to keep in step
     * with the real one, and would outlive a portal that ended early.
     *
     * This is where vanilla puts a nether portal's hum too, at a 1 in 100 chance. The buzz
     * layer is far more frequent than that because it is meant to be continuous rather
     * than occasional, and is quiet enough to sit under everything else; the portal whoosh
     * over the top of it stays rare, so it punctuates instead of droning.
     *
     * Note animateTick is not called every tick for every block — the client picks random
     * positions near the player — so these chances are on top of that sampling, and the
     * rate they produce is lower than the numbers suggest.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        // The buzz. Pitched well down, which is what turns a beacon's clean hum into
        // something that sounds like current running through the air.
        if (random.nextInt(10) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    0.32F, 0.45F + random.nextFloat() * 0.1F, false);
        }

        // The portal underneath it, kept rare so it reads as character rather than noise.
        if (random.nextInt(70) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.28F, 0.5F + random.nextFloat() * 0.2F, false);
        }

        if (random.nextInt(80) != 0) return;

        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(),
                0.0, 0.02, 0.0);
    }
}
