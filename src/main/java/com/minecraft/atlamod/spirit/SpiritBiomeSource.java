package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.spirit.island.IslandStyle;
import com.minecraft.atlamod.spirit.island.SpiritIslands;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Gives every spirit island its own biome, decided by which island a position is on.
 *
 * WHY THIS EXISTS AT ALL: a skybox is a property of the DIMENSION in vanilla, not of a
 * biome, and there is no per-biome sky anywhere in the game. The only way to have one
 * island under a nether sky and the next under an end sky is for the client to be able
 * to ask "what am I standing on?" — and the only thing the client is told about a
 * position, without inventing a packet, is its BIOME. So islands need real biomes, and
 * real biomes need a biome source that varies by position.
 *
 * It is NOT a chunk generator. The terrain is still vanilla's noise generator producing
 * nothing, and the islands are still a Feature. This only answers the question "which
 * biome is here", which the previous {@code minecraft:fixed} source answered with one
 * constant.
 *
 * IT AGREES WITH THE FEATURE BY CONSTRUCTION. Both ask {@link SpiritIslands}, which is a
 * pure function of position — so the biome at a block and the blocks actually there can
 * never drift apart. That mattered enough to be worth a class of its own: the two run at
 * completely different points in world generation and a disagreement between them would
 * be invisible in the code and obvious in the game.
 */
public class SpiritBiomeSource extends BiomeSource {

    public static final MapCodec<SpiritBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Biome.CODEC.fieldOf("void").forGetter(source -> source.empty),
                    Biome.CODEC.fieldOf("overworld").forGetter(source -> source.overworld),
                    Biome.CODEC.fieldOf("nether").forGetter(source -> source.nether),
                    Biome.CODEC.fieldOf("end").forGetter(source -> source.end),
                    Biome.CODEC.fieldOf("crimson").forGetter(source -> source.crimson),
                    Biome.CODEC.fieldOf("warped").forGetter(source -> source.warped),
                    Biome.CODEC.fieldOf("wasteland").forGetter(source -> source.wasteland)
            ).apply(instance, SpiritBiomeSource::new));

    private final Holder<Biome> empty;
    private final Holder<Biome> overworld;
    private final Holder<Biome> nether;
    private final Holder<Biome> end;
    private final Holder<Biome> crimson;
    private final Holder<Biome> warped;
    private final Holder<Biome> wasteland;

    public SpiritBiomeSource(Holder<Biome> empty, Holder<Biome> overworld, Holder<Biome> nether,
                             Holder<Biome> end, Holder<Biome> crimson, Holder<Biome> warped,
                             Holder<Biome> wasteland) {
        this.empty = empty;
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;
        this.crimson = crimson;
        this.warped = warped;
        this.wasteland = wasteland;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(empty, overworld, nether, end, crimson, warped, wasteland);
    }

    /**
     * The biome at a position.
     *
     * COORDINATES ARE QUARTS, not blocks — a quart is a 4x4x4 box, which is the
     * resolution vanilla stores biomes at. Multiplying by 4 is what turns the question
     * back into one SpiritIslands can answer, and forgetting it would spread every island
     * over four times its real footprint.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        SpiritIslands.Island island = SpiritIslands.coveringOrNull(x << 2, z << 2);

        return island == null ? empty : forStyle(island.style());
    }

    private Holder<Biome> forStyle(IslandStyle style) {
        return switch (style) {
            case OVERWORLD -> overworld;
            case NETHER -> nether;
            case END -> end;
            case CRIMSON -> crimson;
            case WARPED -> warped;
            case WASTELAND -> wasteland;
        };
    }
}
