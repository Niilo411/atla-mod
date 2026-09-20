package com.minecraft.atlamod.spirit.island;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * World-generation features the mod adds.
 *
 * The feature registered here is the CODE. What it is actually used for is data:
 * data/atlamod/worldgen/configured_feature/spirit_island.json names this type, the
 * placed_feature beside it says where to run it, and the spirit_world biome lists that.
 * Each of those is a separate file for a reason — it is vanilla's own pipeline, and
 * keeping to it means the island generation can be retuned by editing JSON rather than
 * by rebuilding the mod.
 */
public final class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Atlamod.MODID);

    /** Must match the "type" in worldgen/configured_feature/spirit_island.json. */
    public static final DeferredHolder<Feature<?>, SpiritIslandFeature> SPIRIT_ISLAND =
            FEATURES.register("spirit_island",
                    () -> new SpiritIslandFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
