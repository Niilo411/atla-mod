package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.spirit.SpiritBiomeSource;
import com.minecraft.atlamod.spirit.TempleStructure;
import com.minecraft.atlamod.spirit.island.SpiritIslands;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Spirit temples as a real STRUCTURE, which is what makes {@code /locate} work.
 *
 * This replaced a Feature that did the same job. A feature can scatter something at a
 * rarity and that is all: the game keeps no record of where it went, so nothing can ever
 * be asked to find one. A structure is registered, placed on a grid by a structure set,
 * remembered per chunk, and therefore locatable — and the spacing is vanilla's own rather
 * than a rarity roll, so "about as often as ruined portals" is now literally the same
 * numbers rather than the same average.
 *
 * It still does not build anything itself. {@link SpiritTemplePiece} hands that to
 * {@link TempleStructure#placeAt}, which stays the only thing in the mod that places a
 * temple block.
 */
public class SpiritTempleStructure extends Structure {

    public static final MapCodec<SpiritTempleStructure> CODEC = simpleCodec(SpiritTempleStructure::new);

    public SpiritTempleStructure(StructureSettings settings) {
        super(settings);
    }

    /** How far the ground under a temple may rise or fall across its footprint. */
    private static final int MAX_SLOPE = 3;

    /** Kept clear of the salts the islands, the shrines and the ore draw on. */
    private static final long RARITY_SEED = 0x7E3D19A5L;

    /**
     * Whether the settings want a temple at this candidate site at all.
     *
     * IT CAN ONLY EVER MAKE TEMPLES RARER, never more common, and that is worth being
     * plain about. Which chunks are offered as candidates at all is decided by the
     * structure SET — {@code spacing: 40, separation: 15} in the data pack — and nothing a
     * config value can do will offer a site the grid did not. So this is a second refusal
     * on top of that grid: at 100 every offered site is taken, which is exactly the
     * behaviour before the setting existed, and at 50 about half of them are.
     *
     * DERIVED FROM THE CHUNK, not from a Random, so two things hold that a roll would
     * break: the same chunk reaches the same answer however many times it is asked, and
     * {@code /locate} agrees with what actually generated. Vanilla calls this both while
     * generating and while searching, and a site that said yes to the locator and no to
     * the generator would send a player to an empty field.
     *
     * A hash of the chunk rather than of the world seed, matching every other placement
     * decision in this mod — see {@link com.minecraft.atlamod.spirit.island.SpiritIslands}'
     * note on why its seed is a constant.
     */
    private static boolean wantedIn(ChunkPos chunkPos) {
        int chance = com.minecraft.atlamod.AtlaConfig.templeChance();

        if (chance >= 100) return true;
        if (chance <= 0) return false;

        long h = RARITY_SEED ^ (chunkPos.x * 0x9E3779B97F4A7C15L) ^ (chunkPos.z * 0xC2B2AE3D27D4EB4FL);

        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);

        return Math.floorMod(h, 100L) < chance;
    }

    /**
     * Where, if anywhere, a temple goes in this chunk.
     *
     * NOTHING HERE READS A BLOCK, and that is the constraint that shapes it. A structure
     * decides its position long before the chunk it sits in exists, so "is the ground
     * flat enough" has to be answered from the generator's own height sampling rather
     * than by looking. That is cheap and, more importantly, forces no chunk to generate.
     *
     * Nine columns are sampled — the centre, the corners of the footprint and the edge
     * midpoints. If they disagree by more than {@value #MAX_SLOPE} the site is a slope or
     * a cliff edge and is refused, which is what stops a temple hanging half out of the
     * ground. A column with no ground under it at all refuses too, which is what keeps
     * them out of the Spirit World's void.
     *
     * Sea level rejects the rest: WORLD_SURFACE_WG counts water as surface, so without it
     * temples would sit on top of oceans. The Spirit World's sea level is the bottom of
     * the world, so the test never bites there.
     *
     * THE RARITY SETTING IS CHECKED FIRST, above everything else, for the reason every
     * ordered guard in this codebase is cheapest-first: it is one hash, where the tests
     * under it sample nine columns of terrain and load a structure template. See
     * {@link #wantedIn} for what it can and cannot do.
     */
    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();

        if (!wantedIn(chunkPos)) return Optional.empty();

        int centreX = chunkPos.getMiddleBlockX();
        int centreZ = chunkPos.getMiddleBlockZ();

        int floor = surfaceAt(context, centreX, centreZ);
        if (floor == NONE) return Optional.empty();
        if (floor < context.chunkGenerator().getSeaLevel()) return Optional.empty();

        int lowest = floor;
        int highest = floor;

        // Which temple belongs here decides how much flat ground it needs, and the two
        // are different sizes. The biome source is the only thing here that knows which
        // dimension this is — a GenerationContext carries no dimension of its own.
        var template = TempleStructure.templateFor(context.structureTemplateManager(),
                context.chunkGenerator().getBiomeSource() instanceof SpiritBiomeSource);
        if (template == null) return Optional.empty();

        for (BlockPos offset : TempleStructure.footprintSamples(template)) {
            int height = surfaceAt(context, centreX + offset.getX(), centreZ + offset.getZ());
            if (height == NONE) return Optional.empty();

            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }

        if (highest - lowest > MAX_SLOPE) return Optional.empty();

        BlockPos origin = originIn(chunkPos, floor);

        BoundingBox bounds = TempleStructure.boundingBoxAt(template, origin);

        return Optional.of(new GenerationStub(origin,
                builder -> builder.addPiece(new SpiritTemplePiece(origin, bounds))));
    }

    /**
     * Where in a chunk a temple's floor centre sits.
     *
     * The one place that decides it, because two very different things need the same
     * answer. This method puts a temple there; {@link com.minecraft.atlamod.spirit.SpiritWorld}
     * works BACKWARDS from it when a portal has to find the nearest temple to arrive at,
     * since the game's structure locator reports a chunk's MIN corner and the temple is
     * eight blocks further in. Two copies of that offset would be a portal that put people
     * down next to a temple rather than inside it.
     */
    public static BlockPos originIn(ChunkPos chunkPos, int floorY) {
        return new BlockPos(chunkPos.getMiddleBlockX(), floorY, chunkPos.getMiddleBlockZ());
    }

    /** No ground in this column. */
    private static final int NONE = Integer.MIN_VALUE;

    /**
     * The surface height of one column, without touching a block.
     *
     * The Spirit World needs a different answer from everywhere else, because its TERRAIN
     * IS A FEATURE: the noise generator makes nothing at all there, so asking it how high
     * the ground is would report void over every island in the dimension and no temple
     * would ever generate. {@link SpiritIslands} is asked instead — the same function the
     * island feature uses to decide where to put the blocks, so the two agree exactly.
     *
     * Which world it is comes from the biome source, since a GenerationContext carries no
     * dimension of its own and a spirit biome source is only ever used by the one.
     */
    private static int surfaceAt(GenerationContext context, int x, int z) {
        if (context.chunkGenerator().getBiomeSource() instanceof SpiritBiomeSource) {
            int surface = SpiritIslands.surfaceAt(x, z);
            return surface == SpiritIslands.NO_GROUND ? NONE : surface;
        }

        int surface = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());

        return surface <= context.heightAccessor().getMinBuildHeight() + 2 ? NONE : surface;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SPIRIT_TEMPLE.get();
    }
}
