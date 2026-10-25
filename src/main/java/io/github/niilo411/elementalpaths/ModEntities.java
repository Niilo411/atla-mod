package io.github.niilo411.elementalpaths;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own entities.
 *
 * Only one so far, and it exists to be sat on rather than to be seen. Note that
 * every entity type registered here MUST also get a client renderer registered in
 * ModEntityRenderers — an entity that spawns without one takes the client down.
 */
public final class ModEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElementalPaths.MODID);

    /** What a channeler rides on — Gale Ride and Water Surf both. See ChannelingSeat. */
    public static final DeferredHolder<EntityType<?>, EntityType<ChannelingSeat>> CHANNELING_SEAT =
            ENTITIES.register("channeling_seat",
                    () -> EntityType.Builder.<ChannelingSeat>of(ChannelingSeat::new, MobCategory.MISC)
                            .sized(ChannelingSeat.SIZE.width(), ChannelingSeat.SIZE.height())
                            // Follows the rider, who is by definition a player being
                            // tracked already; updated every tick because the seat is
                            // what carries them and any lag in it is felt directly.
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSummon()
                            .fireImmune()
                            .build("channeling_seat"));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
