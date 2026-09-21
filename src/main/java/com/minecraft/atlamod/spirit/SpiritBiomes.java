package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration of the biome source that picks between the Spirit World's biomes.
 *
 * THE ISLAND BIOMES THEMSELVES ARE NOT LISTED HERE. Each one is named by the
 * {@link com.minecraft.atlamod.spirit.island.IslandStyle} that uses it, so a style's
 * palette and its biome are declared together and cannot be given different answers by two
 * files. Only the VOID biome lives here, because no style owns it — it is the open air
 * between islands.
 */
public final class SpiritBiomes {

    /**
     * Registry for biome source CODECS, which is a different registry from biome sources.
     * What is registered is the way to READ one out of JSON.
     */
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Atlamod.MODID);

    static {
        BIOME_SOURCES.register("spirit", () -> SpiritBiomeSource.CODEC);
    }

    /** Open air between the islands. Belongs to no style. */
    public static final ResourceKey<Biome> VOID = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_void"));

    private SpiritBiomes() {
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
    }
}
