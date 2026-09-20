package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finding and lighting a spirit portal's frame.
 *
 * This class deliberately knows NOTHING about temples. It looks for a shape in the
 * world — a ring of {@link TempleStructure#FRAME_BLOCK} around an empty hole — which
 * means a temple built by {@link TempleStructure#placeAt} and one loaded from a
 * hand-made .nbt file are equally findable, and a player who builds a frame by hand
 * gets one too. That is the whole reason portal activation is not wired to the temple
 * builder: the placeholder can be thrown away without this noticing.
 *
 * The only things shared with the temple are the frame BLOCK and the hole's size, and
 * those are the contract rather than an implementation detail.
 */
public final class SpiritPortalFrame {

    private SpiritPortalFrame() {
    }

    /**
     * A located frame: the lowest, most negative interior block, and the axis the plane
     * runs along.
     */
    public record Frame(BlockPos bottomLeft, Direction.Axis axis) {

        /** Every block inside the hole, which is what gets filled when it lights. */
        public List<BlockPos> interior() {
            List<BlockPos> out = new ArrayList<>(TempleStructure.PORTAL_WIDTH * TempleStructure.PORTAL_HEIGHT);

            for (int w = 0; w < TempleStructure.PORTAL_WIDTH; w++) {
                for (int h = 0; h < TempleStructure.PORTAL_HEIGHT; h++) {
                    out.add(step(bottomLeft, axis, w, h));
                }
            }
            return out;
        }
    }

    /** Moves {@code w} blocks along the frame's width and {@code h} blocks up it. */
    private static BlockPos step(BlockPos from, Direction.Axis axis, int w, int h) {
        return axis == Direction.Axis.X ? from.offset(w, h, 0) : from.offset(0, h, w);
    }

    /**
     * Looks for an UNLIT frame near {@code centre}, nearest first.
     *
     * The reach is given separately for horizontal and vertical because they want very
     * different figures: wide enough to cover a whole temple from its doorway, but only a
     * few blocks up and down, since a portal is always about at the feet of whoever is
     * looking at it. One cube of the horizontal radius would be several times the work for
     * nothing.
     *
     * Nearest first matters when a temple has more than one frame, or when somebody has
     * built a second one nearby: the portal that lights should be the one being stood
     * in front of.
     *
     * The search is a cube of block reads and is not cheap, so it is only ever run at
     * the moment a player completes the activation sequence — never on a tick. The
     * cheap test comes first: a candidate interior block must be AIR with frame
     * material directly beneath it, which rejects almost everything without looking at
     * the other fifteen positions.
     */
    public static Optional<Frame> findInactiveNear(LevelAccessor level, BlockPos centre,
                                                   int radius, int verticalRadius) {
        Frame best = null;
        double bestDistance = Double.MAX_VALUE;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);

                    if (!level.getBlockState(cursor).isAir()) continue;
                    if (!isFrame(level, cursor.below())) continue;

                    for (Direction.Axis axis : new Direction.Axis[]{ Direction.Axis.X, Direction.Axis.Z }) {
                        BlockPos bottomLeft = cursor.immutable();
                        if (!isUnlitFrame(level, bottomLeft, axis)) continue;

                        double distance = centre.distSqr(bottomLeft);
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new Frame(bottomLeft, axis);
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Whether this position is the bottom-left interior block of a complete, empty frame.
     *
     * The four SIDES are required and the corners are not. TempleStructure fills its
     * corners because they cost nothing, but a hand-built structure may leave them out
     * the way a vanilla nether portal does, and refusing that frame would be a rule
     * nothing announces.
     */
    private static boolean isUnlitFrame(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        int width = TempleStructure.PORTAL_WIDTH;
        int height = TempleStructure.PORTAL_HEIGHT;

        // The hole has to be completely empty. Anything in it — including our own portal
        // blocks, which is how an already-lit portal is rejected — disqualifies it.
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                if (!level.getBlockState(step(bottomLeft, axis, w, h)).isAir()) return false;
            }
        }

        // Floor and lintel.
        for (int w = 0; w < width; w++) {
            if (!isFrame(level, step(bottomLeft, axis, w, -1))) return false;
            if (!isFrame(level, step(bottomLeft, axis, w, height))) return false;
        }

        // Both uprights.
        for (int h = 0; h < height; h++) {
            if (!isFrame(level, step(bottomLeft, axis, -1, h))) return false;
            if (!isFrame(level, step(bottomLeft, axis, width, h))) return false;
        }

        return true;
    }

    private static boolean isFrame(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(TempleStructure.FRAME_BLOCK);
    }

    /**
     * Fills the hole with portal blocks.
     *
     * Flag 2 (clients only, no neighbour updates) so that filling the hole one block at
     * a time does not have each portal block immediately reconsider whether its
     * neighbours still support it while the rest of the frame is still empty.
     *
     * Takes a {@link LevelAccessor} rather than a Level so WORLD GENERATION can call it:
     * a spirit temple built by the feature is lit as it is placed, and a feature is only
     * ever handed a WorldGenLevel. Nothing here needs a full Level — it is block writes
     * and nothing else.
     */
    public static void light(LevelAccessor level, Frame frame) {
        light(level, frame, null);
    }

    /**
     * The same, writing only inside {@code clip}, which world generation needs.
     */
    public static void light(LevelAccessor level, Frame frame,
                             net.minecraft.world.level.levelgen.structure.BoundingBox clip) {
        BlockState portal = com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get().defaultBlockState()
                .setValue(SpiritPortalBlock.AXIS, frame.axis());

        for (BlockPos pos : frame.interior()) {
            if (clip != null && !clip.isInside(pos)) continue;
            level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
        }
    }
}
