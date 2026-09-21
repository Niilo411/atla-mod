package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.spirit.island.IslandStyle;
import com.minecraft.atlamod.spirit.island.SpiritIslands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Gives every spirit island its own biome, decided by which island a position is on.
 *
 * WHY THIS EXISTS AT ALL: a skybox is a property of the DIMENSION in vanilla, not of a
 * biome, and there is no per-biome sky anywhere in the game. The only way to have one
 * island under a nether sky and the next under an end sky is for the client to be able to
 * ask "what am I standing on?" — and the only thing the client is told about a position,
 * without inventing a packet, is its BIOME. So islands need real biomes, and real biomes
 * need a biome source that varies by position. Biomes also carry fog and grass colour and
 * decide what the spawner puts there, so a desert island looking dry and a snowy one
 * looking pale both come from the same mechanism.
 *
 * It is NOT a chunk generator. The terrain is still vanilla's noise generator producing
 * nothing, and the islands are still a Feature. This only answers "which biome is here",
 * which the original {@code minecraft:fixed} source answered with one constant.
 *
 * IT AGREES WITH THE FEATURE BY CONSTRUCTION. Both ask {@link SpiritIslands}, which is a
 * pure function of position — so the biome at a block and the blocks actually there can
 * never drift apart. That mattered enough to be worth a class of its own: the two run at
 * completely different points in world generation and a disagreement between them would
 * be invisible in the code and obvious in the game.
 */
public class SpiritBiomeSource extends BiomeSource {

    /**
     * Biomes given as a MAP rather than one codec field each.
     *
     * There are twelve island styles and the number grows every time a variant is added.
     * A field apiece would mean editing the codec, the constructor, the field list and the
     * dimension JSON together for every new look; a map keyed on the style's own name
     * means only the JSON changes. Keys are the enum constant lowercased — "plains",
     * "nether_wastes" — plus "void" for the open air between islands.
     */
    public static final MapCodec<SpiritBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Biome.CODEC.fieldOf("void").forGetter(source -> source.empty),
                    Codec.unboundedMap(Codec.STRING, Biome.CODEC)
                            .fieldOf("islands").forGetter(source -> source.islands)
            ).apply(instance, SpiritBiomeSource::new));

    private final Holder<Biome> empty;
    private final Map<String, Holder<Biome>> islands;

    public SpiritBiomeSource(Holder<Biome> empty, Map<String, Holder<Biome>> islands) {
        this.empty = empty;
        this.islands = islands;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    /**
     * Every biome this source can return.
     *
     * The void biome is always included even if nothing else is, because a dimension whose
     * biome source claims no biomes at all fails to load.
     */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        List<Holder<Biome>> all = new ArrayList<>(islands.values());
        all.add(empty);

        return all.stream();
    }

    /**
     * The biome at a position.
     *
     * COORDINATES ARE QUARTS, not blocks — a quart is a 4x4x4 box, which is the resolution
     * vanilla stores biomes at. Multiplying by 4 is what turns the question back into one
     * SpiritIslands can answer, and forgetting it would spread every island over four
     * times its real footprint.
     */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        SpiritIslands.Island island = SpiritIslands.coveringOrNull(x << 2, z << 2);
        if (island == null) return empty;

        return forStyle(island.style());
    }

    /**
     * This style's biome, or the void biome if the dimension JSON has no entry for it.
     *
     * Falling back rather than failing: a missing entry gives an island that looks right
     * and reports the wrong biome, which is a visible oddity. Throwing here would instead
     * break world generation on a worker thread, which is much harder to read.
     */
    private Holder<Biome> forStyle(IslandStyle style) {
        return islands.getOrDefault(style.name().toLowerCase(Locale.ROOT), empty);
    }
}
