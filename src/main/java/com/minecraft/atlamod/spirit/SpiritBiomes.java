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
 * The Spirit World's biome keys, and the registration of the biome source that picks
 * between them.
 *
 * The biomes themselves are data — data/atlamod/worldgen/biome/ — and the keys here are
 * how code refers to them. Only the SKY needs to: see SpiritSkyEffects, which asks which
 * biome the player is standing in to decide what to draw above them.
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

    /** Open air between the islands. */
    public static final ResourceKey<Biome> VOID = key("spirit_void");

    public static final ResourceKey<Biome> OVERWORLD = key("spirit_overworld");
    public static final ResourceKey<Biome> NETHER = key("spirit_nether");
    public static final ResourceKey<Biome> END = key("spirit_end");
    public static final ResourceKey<Biome> CRIMSON = key("spirit_crimson");
    public static final ResourceKey<Biome> WARPED = key("spirit_warped");
    public static final ResourceKey<Biome> WASTELAND = key("spirit_wasteland");

    private SpiritBiomes() {
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, name));
    }

    public static void register(IEventBus modEventBus) {
        BIOME_SOURCES.register(modEventBus);
    }
}
