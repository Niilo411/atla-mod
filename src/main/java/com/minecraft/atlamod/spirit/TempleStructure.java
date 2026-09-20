package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spirit temple, and the ONE place that knows how its blocks are laid out.
 *
 * THIS IS A PLACEHOLDER. It is meant to be replaced by a hand-built .nbt structure,
 * and the whole point of this class is that the swap is a rewrite of
 * {@link #placeAt} and nothing else. So two rules hold, and both are worth keeping
 * when the real structure arrives:
 *
 * NOTHING OUTSIDE THIS CLASS PLACES A TEMPLE BLOCK. The portal system does not know
 * what a temple is made of, how big it is, or where its walls are — it finds portal
 * frames by looking for them in the world ({@link SpiritPortalFrame}), so a temple
 * loaded from an .nbt file works exactly as well as one built here, as long as its
 * frame is {@link #FRAME_BLOCK} around a {@value #PORTAL_WIDTH}x{@value #PORTAL_HEIGHT}
 * hole.
 *
 * WHAT IT RETURNS IS THE INTERFACE. A caller that needs to know where the portal or
 * the arrival point is asks the returned {@link Temple} rather than working it out
 * from constants of its own. An .nbt version would fill the same record out of the
 * structure's own jigsaw blocks or a fixed offset, and every caller would be
 * unaffected.
 *
 * The room itself is deliberately plain. It is going to be thrown away.
 */
public final class TempleStructure {

    /** Interior width of the portal hole. One wider than a vanilla nether portal's 2. */
    public static final int PORTAL_WIDTH = 3;

    /** Interior height of the portal hole, which is a nether portal's 3. */
    public static final int PORTAL_HEIGHT = 3;

    /**
     * What the frame is built from.
     *
     * Deliberately NOT obsidian. A 3x3 hole in an obsidian frame is a perfectly legal
     * nether portal, so an obsidian temple frame could be lit with a flint and steel
     * and would send a player to the Nether instead — and our own frame finder would
     * have to tell the two apart by something other than the blocks. Crying obsidian
     * is vanilla, is not a nether portal material, and looks the part.
     */
    public static final Block FRAME_BLOCK = Blocks.CRYING_OBSIDIAN;

    // Room dimensions, measured from the centre of the floor.
    private static final int HALF_WIDTH = 6;
    private static final int HALF_DEPTH = 5;
    private static final int WALL_HEIGHT = 6;

    /** How far back from the centre the portal stands. */
    private static final int PORTAL_DEPTH_OFFSET = 4;

    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState WALL = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState PILLAR = Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState();
    private static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private TempleStructure() {
    }

    /**
     * Everything a caller can need to know about a temple that has just been built.
     *
     * @param origin          the floor block at the centre of the room, as passed in
     * @param portalBottomLeft the lowest, most negative interior block of the portal hole
     * @param portalAxis      the axis the portal plane runs along
     * @param arrival         where a player arriving through the portal should stand
     */
    public record Temple(BlockPos origin, BlockPos portalBottomLeft, Direction.Axis portalAxis, BlockPos arrival) {
    }

    /**
     * Builds a temple centred on {@code origin}, which is the floor block at its middle.
     *
     * The portal is left UNLIT — an empty frame with nothing in the hole. Lighting it is
     * the portal system's job and happens much later, if ever.
     *
     * Everything is written with {@link WorldGenLevel#setBlock} and flag 2 (send to
     * clients, no neighbour updates). No neighbour updates is what stops the floor
     * dropping gravel-style blocks or a torch popping off mid-build while the room is
     * still half there.
     */
    public static Temple placeAt(WorldGenLevel level, BlockPos origin) {
        return placeAt(level, origin, null);
    }

    /**
     * The same, but writing only inside {@code clip}.
     *
     * A temple is fifteen by thirteen and so crosses chunk borders. When it is built by
     * WORLD GENERATION it is built once per chunk it touches, each time being told to
     * write only into that chunk — writing outside would force the neighbour to generate
     * early, and that cascade can hang world generation outright.
     *
     * A null clip means "everywhere", which is what the command and the Spirit World's own
     * temple use: they run in a world that already exists, where there is nothing to
     * cascade into.
     *
     * The clip is the one thing an .nbt version would still have to honour, and it gets it
     * nearly free — a structure template takes a bounding box in its place settings for
     * exactly this reason.
     */
    public static Temple placeAt(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        hollowOut(level, origin, clip);
        floorAndCeiling(level, origin, clip);
        walls(level, origin, clip);
        lamps(level, origin, clip);

        BlockPos portalBottomLeft = portalBottomLeft(origin);
        frame(level, portalBottomLeft, clip);

        return new Temple(origin, portalBottomLeft, PORTAL_AXIS, arrival(origin));
    }

    /**
     * Everything a temple built here would occupy.
     *
     * Layout knowledge, so it lives here for the same reason {@link #describeAt} does —
     * and world generation needs it before a single block is placed, to know which chunks
     * the temple reaches into.
     */
    public static BoundingBox boundingBoxAt(BlockPos origin) {
        return new BoundingBox(
                origin.getX() - HALF_WIDTH - 1, origin.getY(), origin.getZ() - HALF_DEPTH - 1,
                origin.getX() + HALF_WIDTH + 1, origin.getY() + WALL_HEIGHT + 1, origin.getZ() + HALF_DEPTH + 1);
    }

    /**
     * Where a temple's portal and arrival point are, WITHOUT building anything.
     *
     * The same answer {@link #placeAt} returns, for a temple already standing. Part of
     * this class rather than the caller's because it is layout knowledge, and an .nbt
     * version would rewrite it alongside placeAt.
     */
    public static Temple describeAt(BlockPos origin) {
        return new Temple(origin, portalBottomLeft(origin), PORTAL_AXIS, arrival(origin));
    }

    /**
     * Whether a temple is already standing here.
     *
     * Tests the floor at the centre, which is the one block a temple cannot be without
     * and that nothing else in a void dimension would put there. Layout knowledge, so it
     * belongs here for the same reason {@link #describeAt} does.
     */
    public static boolean isPresentAt(WorldGenLevel level, BlockPos origin) {
        return level.getBlockState(origin).is(FLOOR.getBlock());
    }

    /**
     * The eight points around the centre that a site is judged by.
     *
     * The corners of the footprint and the midpoints of its edges, as offsets from the
     * temple centre. Layout knowledge, so it lives here for the same reason describeAt
     * does — whoever is deciding where a temple may stand should not have to know how big
     * one is.
     */
    public static java.util.List<BlockPos> footprintSamples() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>(8);
        int[] xs = { -HALF_WIDTH - 1, 0, HALF_WIDTH + 1 };
        int[] zs = { -HALF_DEPTH - 1, 0, HALF_DEPTH + 1 };

        for (int x : xs) {
            for (int z : zs) {
                if (x == 0 && z == 0) continue;
                out.add(new BlockPos(x, 0, z));
            }
        }
        return out;
    }

    /**
     * The axis the temple's portal plane runs along.
     *
     * X means the frame spans east-west and is walked through north-south, which is
     * what {@link #frame} builds.
     */
    private static final Direction.Axis PORTAL_AXIS = Direction.Axis.X;

    /** The lowest, most negative interior block of the portal hole. */
    private static BlockPos portalBottomLeft(BlockPos origin) {
        return origin.offset(-(PORTAL_WIDTH / 2), 1, -PORTAL_DEPTH_OFFSET);
    }

    /** Where an arriving player is set down: in the middle of the room, facing the portal. */
    private static BlockPos arrival(BlockPos origin) {
        return origin.offset(0, 1, 0);
    }

    private static void hollowOut(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = -HALF_DEPTH; z <= HALF_DEPTH; z++) {
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    set(level, origin.offset(x, y, z), AIR, clip);
                }
            }
        }
    }

    private static void floorAndCeiling(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        for (int x = -HALF_WIDTH - 1; x <= HALF_WIDTH + 1; x++) {
            for (int z = -HALF_DEPTH - 1; z <= HALF_DEPTH + 1; z++) {
                set(level, origin.offset(x, 0, z), FLOOR, clip);
                set(level, origin.offset(x, WALL_HEIGHT + 1, z), WALL, clip);
            }
        }
    }

    private static void walls(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        for (int y = 1; y <= WALL_HEIGHT; y++) {
            for (int x = -HALF_WIDTH - 1; x <= HALF_WIDTH + 1; x++) {
                set(level, origin.offset(x, y, -HALF_DEPTH - 1), WALL, clip);
                // The front wall carries the doorway, so it is drawn with a gap.
                if (!isDoorway(x, y)) {
                    set(level, origin.offset(x, y, HALF_DEPTH + 1), WALL, clip);
                }
            }
            for (int z = -HALF_DEPTH - 1; z <= HALF_DEPTH + 1; z++) {
                set(level, origin.offset(-HALF_WIDTH - 1, y, z), WALL, clip);
                set(level, origin.offset(HALF_WIDTH + 1, y, z), WALL, clip);
            }
        }
    }

    /** A 3 wide, 3 tall opening in the middle of the front wall, so the room can be left. */
    private static boolean isDoorway(int x, int y) {
        return x >= -1 && x <= 1 && y >= 1 && y <= 3;
    }

    private static void lamps(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        int[] xs = { -HALF_WIDTH, HALF_WIDTH };
        int[] zs = { -HALF_DEPTH, HALF_DEPTH };

        for (int x : xs) {
            for (int z : zs) {
                for (int y = 1; y <= WALL_HEIGHT - 1; y++) {
                    set(level, origin.offset(x, y, z), PILLAR, clip);
                }
                set(level, origin.offset(x, WALL_HEIGHT, z), LAMP, clip);
            }
        }
    }

    /**
     * The empty portal frame: a ring of {@link #FRAME_BLOCK} around a
     * {@value #PORTAL_WIDTH} by {@value #PORTAL_HEIGHT} hole, with the hole left as air.
     *
     * {@code bottomLeft} is the lowest, most negative INTERIOR block, so the ring runs
     * from one block outside it on every side. The corners are included, which a nether
     * portal's are not — they cost nothing and a frame with holes in its corners reads
     * as unfinished.
     */
    private static void frame(WorldGenLevel level, BlockPos bottomLeft, BoundingBox clip) {
        for (int w = -1; w <= PORTAL_WIDTH; w++) {
            for (int h = -1; h <= PORTAL_HEIGHT; h++) {
                boolean interior = w >= 0 && w < PORTAL_WIDTH && h >= 0 && h < PORTAL_HEIGHT;
                BlockPos at = bottomLeft.offset(w, h, 0);

                set(level, at, interior ? AIR : FRAME_BLOCK.defaultBlockState(), clip);
            }
        }
    }

    /**
     * One block, if the clip allows it.
     *
     * A null clip means no limit. Silently dropping everything outside the box is what
     * lets the whole temple be drawn once per chunk it touches, each pass keeping only
     * its own share — the pieces meet exactly because every pass computes the same
     * positions from the same origin.
     */
    private static void set(WorldGenLevel level, BlockPos pos, BlockState state, BoundingBox clip) {
        if (clip != null && !clip.isInside(pos)) return;

        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
