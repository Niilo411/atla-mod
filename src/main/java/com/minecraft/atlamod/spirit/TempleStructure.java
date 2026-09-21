package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/**
 * The spirit temple, and the ONE place that knows how it is built.
 *
 * It is loaded from a hand-made .nbt file now rather than laid out in code, and this class
 * is where that swap landed — exactly as it was designed to. Nothing outside it places a
 * temple block, and nothing outside it knows what a temple is made of.
 *
 * TWO STRUCTURES, ONE PER DIMENSION. The overworld and the Spirit World have different
 * temples, and {@link #placeAt} picks between them from the level it is given. They are
 * not the same size and their portals are not the same size either, which is why nothing
 * here is a constant: everything a caller needs is measured from the template that was
 * actually placed.
 *
 * THE PORTAL IS FOUND BY A MARKER BLOCK. Each structure has exactly one
 * {@link #MARKER_BLOCK} at the bottom-centre of its portal opening. It is read out of the
 * TEMPLATE rather than by searching the world afterwards, which matters during world
 * generation: a temple is placed once per chunk it touches and clipped to that chunk, so a
 * search of the world would miss a marker that fell in a neighbouring chunk. The template
 * always has it. The marker is then replaced with air so nothing is left standing in the
 * portal.
 *
 * NOTHING OUTSIDE THIS CLASS PLACES A TEMPLE BLOCK, and the portal system still finds
 * frames by looking for the SHAPE in the world ({@link SpiritPortalFrame}) rather than by
 * asking the temple where they are — so a frame a player builds by hand works too.
 */
public final class TempleStructure {

    /**
     * What the portal frame is built from, in both structures.
     *
     * This is the contract between the temple and the portal system: the frame finder
     * looks for a ring of it around an empty opening. Prismarine is also used decoratively
     * in both temples, which is safe because a frame is only accepted when the WHOLE ring
     * encloses the opening — see SpiritPortalFrame.measure.
     */
    public static final Block FRAME_BLOCK = Blocks.PRISMARINE;

    /**
     * The single block marking the bottom-centre of a structure's portal opening.
     *
     * Chosen because it appears exactly once in each structure and nowhere else, so
     * finding it cannot be ambiguous. It never survives placement — it is read from the
     * template and then written over with air.
     */
    public static final Block MARKER_BLOCK = Blocks.STRIPPED_OAK_WOOD;

    private static final ResourceLocation OVERWORLD_TEMPLE =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_temple_overworld");

    private static final ResourceLocation SPIRIT_TEMPLE =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_temple_spiritworld");

    private TempleStructure() {
    }

    /**
     * Everything a caller can need to know about a temple that has just been placed.
     *
     * @param origin  the corner the structure was placed from, as passed in
     * @param portal  the portal opening, sized and oriented as the structure built it
     * @param arrival where a player arriving through the portal should stand
     */
    public record Temple(BlockPos origin, SpiritPortalFrame.Frame portal, BlockPos arrival) {

        /** The lowest, most negative interior block of the portal opening. */
        public BlockPos portalBottomLeft() {
            return portal.bottomLeft();
        }

        /** The axis the portal plane runs along. */
        public Direction.Axis portalAxis() {
            return portal.axis();
        }
    }

    /** Builds the temple for this level at {@code origin}. */
    public static Temple placeAt(WorldGenLevel level, BlockPos origin) {
        return placeAt(level, origin, null);
    }

    /**
     * The same, writing only inside {@code clip}.
     *
     * A temple crosses chunk borders, and world generation builds it once per chunk it
     * touches, each time telling it to stay inside that chunk — writing outside would
     * force the neighbour to generate early, and that cascade can hang generation
     * outright. A structure template takes the box directly in its place settings, which
     * is the whole reason this was cheap to keep when the .nbt arrived.
     *
     * A null clip means "everywhere", which is what the command and the Spirit World's own
     * temple use: they run in a world that already exists.
     *
     * RETURNS NULL IF THE TEMPLATE IS MISSING. That is a data problem — a renamed or
     * absent .nbt — and every caller has to cope rather than assume, because failing here
     * would break world generation on a worker thread where it is far harder to read.
     */
    public static Temple placeAt(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        StructureTemplate template = templateFor(level);
        if (template == null) return null;

        // MEASURED UNCLIPPED, PLACED CLIPPED — and they have to be two separate settings
        // objects, which is subtle enough to have already cost one bug.
        //
        // StructureTemplate.filterBlocks honours the bounding box in the settings it is
        // handed, so measuring through the clip hands back only the share of the portal
        // that falls in THIS chunk. The overworld temple's frame straddles a chunk border,
        // so the pass that owns the marker saw an upright missing, walked past where the
        // opening stops, and worked out a portal several blocks wider than the real one —
        // at which point the marker was cleared from a position it was never in and the
        // block was left standing in the doorway.
        //
        // The measurement describes the TEMPLATE, not this pass's share of it, so it must
        // never be clipped. Only the writing is.
        StructurePlaceSettings measuring = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

        BlockPos marker = markerOf(template, origin, measuring);
        SpiritPortalFrame.Frame portal = portalOf(template, origin, measuring, marker);

        StructurePlaceSettings placing = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);
        if (clip != null) placing.setBoundingBox(clip);

        template.placeInWorld(level, origin, origin, placing,
                level.getRandom(), Block.UPDATE_CLIENTS);

        // The marker is scenery once it has been read, and it is cleared by POSITION rather
        // than by re-deriving the middle of the measured opening: the two agree for an
        // odd-width portal and quietly disagree for an even-width one. Subject to the clip
        // like every other write, so the pass that owns that block is the one that clears
        // it — and every chunk a temple touches gets a pass, so exactly one always does.
        if (marker != null && (clip == null || clip.isInside(marker))) {
            level.setBlock(marker, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        return new Temple(origin, portal, arrivalFor(template, origin, portal));
    }

    /**
     * How big the temple for this level is, or a sane guess if the template is missing.
     *
     * For callers that need to place a temple AROUND something — the command puts the
     * player in the middle of the room, and a structure is placed from its corner, so it
     * has to know how far back the corner is.
     */
    public static net.minecraft.core.Vec3i sizeFor(WorldGenLevel level) {
        StructureTemplate template = templateFor(level);
        return template == null ? new BlockPos(16, 10, 16) : template.getSize();
    }

    /** Which structure belongs in this level. */
    private static StructureTemplate templateFor(WorldGenLevel level) {
        return templateFor(level.getLevel().getServer().getStructureManager(),
                SpiritWorld.isSpiritWorld(level.getLevel()));
    }

    /**
     * The same, for callers that have no level to ask.
     *
     * World generation decides where a structure goes long before the chunk exists, so it
     * has a template manager and a biome source but no ServerLevel at all — see
     * SpiritTempleStructure. Both routes end here so there is only ever one answer to
     * "which temple belongs where".
     */
    public static StructureTemplate templateFor(StructureTemplateManager templates, boolean spiritWorld) {
        return templates.get(spiritWorld ? SPIRIT_TEMPLE : OVERWORLD_TEMPLE).orElse(null);
    }

    /**
     * The portal opening in this template, in world coordinates, or null if it has none.
     *
     * The marker gives the bottom-CENTRE. The opening's size and axis are then measured
     * out of the template's own blocks — walking along each axis while the marker's row is
     * air, and upward the same way — so the two temples having different portals costs
     * nothing here.
     */
    private static SpiritPortalFrame.Frame portalOf(StructureTemplate template, BlockPos origin,
                                                    StructurePlaceSettings settings, BlockPos centre) {
        if (centre == null) return null;

        // Gathered ONCE. filterBlocks walks every block in the template, and the measuring
        // below asks about a couple of dozen positions — doing that as a fresh scan each
        // time would be tens of thousands of comparisons per temple placed.
        java.util.Set<BlockPos> frame = new java.util.HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, FRAME_BLOCK)) {
            frame.add(info.pos());
        }

        // WHICH WAY THE PLANE RUNS is decided by which direction hits frame material
        // SOONEST, not by probing a fixed distance. A fixed probe cannot work when the two
        // temples have different openings: two blocks out is an upright in the overworld's
        // three-wide portal and still open air in the Spirit World's five-wide one.
        //
        // Along the plane the uprights stop the walk within a few blocks. Across it there
        // is the room, or the outdoors, so nothing stops it at all.
        int alongX = nearestFrame(frame, centre, Direction.Axis.X);
        int alongZ = nearestFrame(frame, centre, Direction.Axis.Z);
        if (alongX > MAX_SPAN && alongZ > MAX_SPAN) return null;

        Direction.Axis axis = alongX <= alongZ ? Direction.Axis.X : Direction.Axis.Z;

        // Measured out in each direction separately rather than assuming the marker is
        // exactly central, so an opening of even width still comes out right.
        int left = openFor(frame, centre, axis, -1);
        int right = openFor(frame, centre, axis, 1);
        int up = openFor(frame, centre, Direction.Axis.Y, 1);

        BlockPos bottomLeft = axis == Direction.Axis.X
                ? centre.offset(-left, 0, 0) : centre.offset(0, 0, -left);

        return new SpiritPortalFrame.Frame(bottomLeft, axis, left + right + 1, up + 1);
    }

    /**
     * Where this template's portal marker lands in the world, or null if it has none.
     *
     * Read out of the TEMPLATE rather than by searching the world, which is what makes it
     * work during generation: the block may not have been written yet, or may belong to a
     * chunk this pass is not allowed to look at. The template always has it.
     *
     * The settings passed in must be unclipped — see {@link #placeAt}.
     */
    private static BlockPos markerOf(StructureTemplate template, BlockPos origin,
                                     StructurePlaceSettings settings) {
        List<StructureTemplate.StructureBlockInfo> markers =
                template.filterBlocks(origin, settings, MARKER_BLOCK);

        return markers.isEmpty() ? null : markers.get(0).pos();
    }

    /** The furthest a portal opening may run before it is not taken to be one. */
    private static final int MAX_SPAN = 9;

    /** How far the nearest frame block is along this axis, either way. */
    private static int nearestFrame(java.util.Set<BlockPos> frame, BlockPos from, Direction.Axis axis) {
        int nearest = MAX_SPAN + 1;

        for (int sign : new int[]{ -1, 1 }) {
            for (int step = 1; step <= MAX_SPAN; step++) {
                if (frame.contains(offset(from, axis, step * sign))) {
                    nearest = Math.min(nearest, step);
                    break;
                }
            }
        }
        return nearest;
    }

    /** How many blocks the opening continues in one direction before the frame stops it. */
    private static int openFor(java.util.Set<BlockPos> frame, BlockPos from,
                               Direction.Axis axis, int sign) {
        int open = 0;

        for (int step = 1; step <= MAX_SPAN; step++) {
            if (frame.contains(offset(from, axis, step * sign))) break;
            open++;
        }
        return open;
    }

    private static BlockPos offset(BlockPos from, Direction.Axis axis, int d) {
        return switch (axis) {
            case X -> from.offset(d, 0, 0);
            case Y -> from.offset(0, d, 0);
            case Z -> from.offset(0, 0, d);
        };
    }

    /**
     * Where a player arriving through the portal is set down.
     *
     * Two blocks clear of the portal plane, on whichever side the template has open air —
     * which is the room rather than the wall behind it. Falling back to the structure's
     * middle if there is no portal at all, because something has to happen and a player
     * dropped into a wall is worse than one standing in the middle of the room.
     */
    private static BlockPos arrivalFor(StructureTemplate template, BlockPos origin,
                                       SpiritPortalFrame.Frame portal) {
        if (portal == null) {
            BlockPos size = new BlockPos(template.getSize());
            return origin.offset(size.getX() / 2, 1, size.getZ() / 2);
        }

        BlockPos centre = portal.bottomCentre();
        Direction step = portal.axis() == Direction.Axis.X
                ? Direction.SOUTH : Direction.EAST;

        BlockPos ahead = centre.relative(step, 2);
        BlockPos behind = centre.relative(step.getOpposite(), 2);

        // Whichever side is inside the building. Measured against the structure's own
        // bounds rather than by reading the world, which may not be built yet.
        BlockPos size = new BlockPos(template.getSize());
        BoundingBox bounds = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1,
                origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1);

        return bounds.isInside(ahead) ? ahead : behind;
    }

    /**
     * Whether a temple is already standing here.
     *
     * Tests for the portal frame at the position the structure puts one, which is the one
     * thing a temple cannot be without and that nothing else in a void dimension would put
     * there. Layout knowledge, so it belongs in this class.
     */
    public static boolean isPresentAt(WorldGenLevel level, BlockPos origin) {
        StructureTemplate template = templateFor(level);
        if (template == null) return false;

        BlockPos size = new BlockPos(template.getSize());
        BlockPos middle = origin.offset(size.getX() / 2, 1, size.getZ() / 2);

        return !level.getBlockState(middle.below()).isAir();
    }

    /**
     * Where a temple's portal is, WITHOUT building anything.
     *
     * The same answer {@link #placeAt} returns, for a temple already standing. Needed by
     * the Spirit World's own temple, which is repaired rather than rebuilt on every
     * arrival.
     */
    public static Temple describeAt(WorldGenLevel level, BlockPos origin) {
        StructureTemplate template = templateFor(level);
        if (template == null) return null;

        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE);
        SpiritPortalFrame.Frame portal =
                portalOf(template, origin, settings, markerOf(template, origin, settings));

        return new Temple(origin, portal, arrivalFor(template, origin, portal));
    }

    /**
     * Everything a temple placed here would occupy.
     *
     * World generation needs it before a single block is placed, to know which chunks the
     * temple reaches into.
     */
    public static BoundingBox boundingBoxAt(WorldGenLevel level, BlockPos origin) {
        return boundingBoxAt(templateFor(level), origin);
    }

    /** The same, for a template already in hand. */
    public static BoundingBox boundingBoxAt(StructureTemplate template, BlockPos origin) {

        // A generous guess when the template is missing, so a structure with no data still
        // occupies a sane area rather than a single block.
        BlockPos size = template == null ? new BlockPos(24, 12, 24) : new BlockPos(template.getSize());

        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1,
                origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1);
    }

    /**
     * The eight points across the footprint that a site is judged by, besides its corner.
     *
     * Offsets from the structure ORIGIN, which is the corner a template is placed from —
     * NOT from the middle. That distinction is the whole point of the method: a caller
     * deciding whether the ground is flat enough must sample the ground the temple will
     * actually stand on, and a centre-relative box would have covered half the footprint
     * and half the field beside it.
     *
     * The origin corner itself is left out because the caller samples it anyway to decide
     * the floor height; these eight plus that one cover the footprint.
     */
    public static List<BlockPos> footprintSamples(WorldGenLevel level) {
        return footprintSamples(templateFor(level));
    }

    /** The same, for a template already in hand. */
    public static List<BlockPos> footprintSamples(StructureTemplate template) {
        int sizeX = template == null ? 16 : template.getSize().getX();
        int sizeZ = template == null ? 16 : template.getSize().getZ();

        List<BlockPos> out = new java.util.ArrayList<>(8);
        int[] xs = { 0, sizeX / 2, sizeX - 1 };
        int[] zs = { 0, sizeZ / 2, sizeZ - 1 };

        for (int x : xs) {
            for (int z : zs) {
                if (x == 0 && z == 0) continue;
                out.add(new BlockPos(x, 0, z));
            }
        }
        return out;
    }
}
