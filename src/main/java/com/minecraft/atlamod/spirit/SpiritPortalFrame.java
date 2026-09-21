package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finding and lighting a spirit portal's frame.
 *
 * This class deliberately knows NOTHING about temples. It looks for a shape in the world
 * — a ring of {@link TempleStructure#FRAME_BLOCK} around an empty opening — which means a
 * temple loaded from a hand-made .nbt file and one a player laid by hand are equally
 * findable. That is the whole reason portal activation is not wired to the temple builder.
 *
 * A FRAME CARRIES ITS OWN SIZE rather than reading it from shared constants, and that is
 * what lets the two hand-built temples differ: the overworld's opening is three wide by
 * three tall and the Spirit World's is five by four. Nothing here assumes either. The
 * finder MEASURES the opening it has found, so a temple rebuilt to a different size needs
 * no code change at all.
 */
public final class SpiritPortalFrame {

    /** The largest opening that will be recognised, in either direction. */
    private static final int MAX_SPAN = 9;

    private SpiritPortalFrame() {
    }

    /**
     * A located frame: its lowest, most negative interior block, the axis its plane runs
     * along, and the size of the opening.
     */
    public record Frame(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {

        /** Every block inside the opening, which is what gets filled when it lights. */
        public List<BlockPos> interior() {
            List<BlockPos> out = new ArrayList<>(width * height);

            for (int w = 0; w < width; w++) {
                for (int h = 0; h < height; h++) {
                    out.add(step(bottomLeft, axis, w, h));
                }
            }
            return out;
        }

        /** The middle of the bottom row, which is where a temple's marker block sits. */
        public BlockPos bottomCentre() {
            return step(bottomLeft, axis, width / 2, 0);
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
     * looking at it.
     *
     * The search is a cube of block reads and is not cheap, so it is only ever run at the
     * moment a player completes the activation sequence — never on a tick. The cheap test
     * comes first: a candidate must be AIR with frame material directly beneath it, which
     * rejects almost everything without measuring anything.
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

                    if (!isOpen(level, cursor)) continue;
                    if (!isFrame(level, cursor.below())) continue;

                    for (Direction.Axis axis : new Direction.Axis[]{ Direction.Axis.X, Direction.Axis.Z }) {
                        BlockPos bottomLeft = cursor.immutable();

                        // Only the true bottom-left corner is measured. Every other block
                        // of the opening also sits on frame material, and without this the
                        // same portal would be found once per block along its bottom row.
                        if (isFrame(level, step(bottomLeft, axis, -1, 0))) {
                            Frame frame = measure(level, bottomLeft, axis);
                            if (frame == null) continue;

                            double distance = centre.distSqr(bottomLeft);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = frame;
                            }
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Measures the opening that starts at this corner, or null if it is not a frame.
     *
     * The size is DISCOVERED rather than assumed, which is what lets two temples have
     * different portals. The opening is walked out along the axis and upward until frame
     * material stops it, and the whole ring is then checked — so a random air pocket with
     * one block of prismarine under it is rejected, and only a genuinely enclosed opening
     * is accepted. That check matters because prismarine is also a decorative block in
     * these temples.
     *
     * The four SIDES are required and the corners are not. A hand-built frame may leave
     * its corners out the way a vanilla nether portal does, and refusing that would be a
     * rule nothing announces.
     */
    private static Frame measure(LevelAccessor level, BlockPos bottomLeft, Direction.Axis axis) {
        int width = 0;
        while (width < MAX_SPAN && isOpen(level, step(bottomLeft, axis, width, 0))) width++;

        int height = 0;
        while (height < MAX_SPAN && isOpen(level, step(bottomLeft, axis, 0, height))) height++;

        // A portal has to be something you can walk through.
        if (width < 2 || height < 2 || width >= MAX_SPAN || height >= MAX_SPAN) return null;

        // The whole opening must be empty. Anything in it — including our own portal
        // blocks, which is how an already-lit portal is rejected — disqualifies it.
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                if (!isOpen(level, step(bottomLeft, axis, w, h))) return null;
            }
        }

        // Floor and lintel.
        for (int w = 0; w < width; w++) {
            if (!isFrame(level, step(bottomLeft, axis, w, -1))) return null;
            if (!isFrame(level, step(bottomLeft, axis, w, height))) return null;
        }

        // Both uprights.
        for (int h = 0; h < height; h++) {
            if (!isFrame(level, step(bottomLeft, axis, -1, h))) return null;
            if (!isFrame(level, step(bottomLeft, axis, width, h))) return null;
        }

        return new Frame(bottomLeft, axis, width, height);
    }

    private static boolean isFrame(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(TempleStructure.FRAME_BLOCK);
    }

    /**
     * Whether this block counts as part of an opening rather than as something filling it.
     *
     * Air, obviously — and a leftover {@link TempleStructure#MARKER_BLOCK}, which is a
     * REPAIR for temples that generated before the marker was being cleared properly. It
     * matters because the alternative is not an untidy doorway but a dead one: the walk
     * below stops at anything solid, so a single block left in the bottom row measures the
     * opening as one wide and the whole frame is rejected as not a portal. Those temples
     * could never be lit by anybody.
     *
     * Nothing has to clear the marker afterwards, because {@link #light} writes a portal
     * block over every square of the opening and takes it with the rest.
     *
     * A player who deliberately stands a stripped oak log inside a prismarine frame of
     * their own will have it swallowed when the portal lights. That is the entire cost of
     * this, and it is a fair trade for frames that already exist in someone's world.
     */
    private static boolean isOpen(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(TempleStructure.MARKER_BLOCK);
    }

    /**
     * Fills the opening with portal blocks.
     *
     * Flag 2 (clients only, no neighbour updates) so that filling it one block at a time
     * does not have each portal block immediately reconsider whether its neighbours still
     * support it while the rest is still empty.
     *
     * Takes a {@link LevelAccessor} rather than a Level so WORLD GENERATION can call it: a
     * spirit temple built by the structure is lit as it is placed, and a structure piece is
     * only ever handed a WorldGenLevel. Nothing here needs a full Level.
     */
    public static void light(LevelAccessor level, Frame frame) {
        light(level, frame, null);
    }

    /** The same, writing only inside {@code clip}, which world generation needs. */
    public static void light(LevelAccessor level, Frame frame, BoundingBox clip) {
        BlockState portal = com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get().defaultBlockState()
                .setValue(SpiritPortalBlock.AXIS, frame.axis());

        for (BlockPos pos : frame.interior()) {
            if (clip != null && !clip.isInside(pos)) continue;
            level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
        }
    }
}
