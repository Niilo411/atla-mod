package com.minecraft.atlamod;

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
            DeferredRegister.create(Registries.ENTITY_TYPE, Atlamod.MODID);

    /** What a bender rides on — Air Scooter and Water Surf both. See BendingSeat. */
    public static final DeferredHolder<EntityType<?>, EntityType<BendingSeat>> BENDING_SEAT =
            ENTITIES.register("bending_seat",
                    () -> EntityType.Builder.<BendingSeat>of(BendingSeat::new, MobCategory.MISC)
                            .sized(BendingSeat.SIZE.width(), BendingSeat.SIZE.height())
                            // Follows the rider, who is by definition a player being
                            // tracked already; updated every tick because the seat is
                            // what carries them and any lag in it is felt directly.
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSummon()
                            .fireImmune()
                            .build("bending_seat"));

    private ModEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
