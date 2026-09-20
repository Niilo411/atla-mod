package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * What makes the spirit portal BLUE without a texture of our own.
 *
 * The portal's model is vanilla's nether portal geometry wearing vanilla's own
 * block/nether_portal texture, with one thing added: its faces carry "tintindex": 0.
 * A tint index is the game asking "what colour should this face be multiplied by?",
 * and this handler is the answer. It is the same mechanism grass, leaves and water use
 * to be different colours in different biomes off one grey texture.
 *
 * So the whole recolour is two small JSON models plus the constant below, and the mod
 * ships no portal art at all — the pixels come from the player's own copy of the game.
 *
 * Note the texture is purple to begin with, so the tint MULTIPLIES rather than replaces:
 * the red channel has to be cut hard to get blue out of it, which is why the value is
 * not simply 0x0000FF. Killing red entirely would make the portal's swirls black where
 * the texture is reddest, so a little is left in.
 */
@EventBusSubscriber(modid = Atlamod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpiritPortalColors {

    /** Multiplied onto the purple nether portal texture to land on blue. */
    private static final int BLUE = 0x3060FF;

    private SpiritPortalColors() {
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> BLUE, Atlamod.SPIRIT_PORTAL.get());
    }
}
