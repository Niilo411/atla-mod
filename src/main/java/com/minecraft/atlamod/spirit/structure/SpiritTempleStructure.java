package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.spirit.SpiritBiomeSource;
import com.minecraft.atlamod.spirit.TempleStructure;
import com.minecraft.atlamod.spirit.island.SpiritIslands;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
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
     */
    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();

        int centreX = chunkPos.getMiddleBlockX();
        int centreZ = chunkPos.getMiddleBlockZ();

        int floor = surfaceAt(context, centreX, centreZ);
        if (floor == NONE) return Optional.empty();
        if (floor < context.chunkGenerator().getSeaLevel()) return Optional.empty();

        int lowest = floor;
        int highest = floor;

        for (BlockPos offset : TempleStructure.footprintSamples()) {
            int height = surfaceAt(context, centreX + offset.getX(), centreZ + offset.getZ());
            if (height == NONE) return Optional.empty();

            lowest = Math.min(lowest, height);
            highest = Math.max(highest, height);
        }

        if (highest - lowest > MAX_SLOPE) return Optional.empty();

        BlockPos origin = originIn(chunkPos, floor);

        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new SpiritTemplePiece(origin))));
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
