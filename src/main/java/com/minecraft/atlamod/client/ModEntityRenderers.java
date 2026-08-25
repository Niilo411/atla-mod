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
 * renderer registered crashes it. The bending seat is meant to be invisible, but
 * "invisible" still has to be said out loud — hence NoopRenderer, which is vanilla's
 * own renderer that draws nothing.
 */
@EventBusSubscriber(modid = Atlamod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModEntityRenderers {

    private ModEntityRenderers() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BENDING_SEAT.get(), NoopRenderer::new);
    }

    /**
     * Hangs the Earth armor layer on both player models.
     *
     * Both, because a player is rendered by one of two renderers depending on whether
     * their skin is the slim-armed kind — adding to only the default one would leave
     * half of all players unarmored.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (!(event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer)) {
                continue;
            }
            renderer.addLayer(new EarthArmorLayer<>(renderer, event.getContext().getModelSet()));
        }
    }
}
