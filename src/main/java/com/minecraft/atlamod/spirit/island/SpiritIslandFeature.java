package com.minecraft.atlamod.spirit.island;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Random;

/**
 * Builds the Spirit World's floating islands.
 *
 * A FEATURE rather than a chunk generator, which changes one thing fundamentally and it
 * is worth understanding before touching this class.
 *
 * A FEATURE IS ONLY ALLOWED TO WRITE INTO ITS OWN CHUNK. Writing outside it forces the
 * neighbouring chunk to generate early, which forces ITS neighbours, and the cascade can
 * hang world generation outright. An island is up to 250 blocks across — sixteen chunks —
 * so a feature that simply drew a whole island from wherever it was invoked would be the
 * worst possible version of that.
 *
 * So islands are not "placed" at all. This walks the 256 columns of its own chunk and
 * asks {@link SpiritIslands} what belongs in each one. Two chunks either side of an
 * island's edge get the same answers from the same pure functions, so the halves meet
 * exactly, nothing is remembered between chunks, and nothing is written outside them.
 *
 * WHAT GOES WHERE IS NOT DECIDED HERE. SpiritIslands owns that, because the biome source
 * has to reach the identical conclusion — see its class note.
 */
public class SpiritIslandFeature extends Feature<NoneFeatureConfiguration> {

    public SpiritIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        int chunkMinX = origin.getX();
        int chunkMinZ = origin.getZ();

        boolean any = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = chunkMinX; x < chunkMinX + 16; x++) {
            for (int z = chunkMinZ; z < chunkMinZ + 16; z++) {
                any |= ground(level, x, z, cursor);
            }
        }

        // Shrines are NOT laid here. They were, briefly, and moved out to a real structure
        // so that /locate could find them — see SpiritShrines. The ordering this used to
        // guarantee by hand still holds: this feature runs at RAW_GENERATION and the shrine
        // structure at SURFACE_STRUCTURES, so the island is always under it.
        return any;
    }

    /**
     * One column of island, if any island claims this one.
     *
     * The claim is asked of SpiritIslands rather than worked out here, which is what
     * makes overlapping islands behave: a contested column belongs to exactly one of
     * them, so they butt up against each other instead of interleaving, and the blocks
     * always match the biome reported at that position.
     */
    private static boolean ground(WorldGenLevel level, int x, int z, BlockPos.MutableBlockPos cursor) {
        SpiritIslands.Island island = SpiritIslands.coveringOrNull(x, z);
        if (island == null) return false;

        double inward = 1.0 - (island.distanceTo(x, z) / island.edgeAt(x, z));

        IslandStyle style = island.style();

        // Asked of SpiritIslands rather than worked out here, because the temple structure
        // has to know the same height BEFORE any of this runs — see surfaceAt.
        int surface = SpiritIslands.surfaceOf(island, x, z);

        // The underside, tapering to nothing at the rim so the island hangs in a keel.
        int thickness = 2 + (int) Math.round(SpiritIslands.MAX_THICKNESS * inward * inward);

        for (int y = surface - thickness; y <= surface; y++) {
            cursor.set(x, y, z);

            BlockState state;
            if (y == surface) {
                state = style.top();
            } else if (y > surface - 4) {
                state = style.filler();
            } else {
                state = style.deep();
            }

            // Ore LAST, so it overrides whatever the palette would have put here — a vein
            // reaching the top layer replaces the grass, which is what makes a surface
            // vein something you can actually spot. Asked per block rather than grown
            // outwards, which is what keeps veins whole across chunk borders; see
            // SpiritOre.
            if (SpiritOre.at(x, y, z, surface)) {
                state = com.minecraft.atlamod.Atlamod.SPIRIT_ORE.get().defaultBlockState();
            }

            level.setBlock(cursor, state, Block.UPDATE_CLIENTS);
        }

        // Its own stream, seeded on the column, so decoration is identical whichever
        // chunk happens to draw this block.
        style.decorate(level, cursor, x, surface, z,
                new Random(SpiritIslands.mix(island.seed(), x, z)));
        return true;
    }
}
