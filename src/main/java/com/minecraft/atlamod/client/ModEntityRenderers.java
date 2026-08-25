package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client renderers for the mod's entities.
 *
 * This is not optional bookkeeping: an entity type that reaches a client with no
 * renderer registered crashes it. The Air Scooter seat is meant to be invisible, but
 * "invisible" still has to be said out loud — hence NoopRenderer, which is vanilla's
 * own renderer that draws nothing.
 */
@EventBusSubscriber(modid = Atlamod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {

    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AIR_SCOOTER_SEAT.get(), NoopRenderer::new);
    }
}
