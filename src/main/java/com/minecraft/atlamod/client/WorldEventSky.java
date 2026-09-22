package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.events.WorldEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What the three world events look like.
 *
 * THE FOG IS THE SKY, as far as a mod without a mixin is concerned. A dimension's sun,
 * moon and stars are drawn by {@code DimensionSpecialEffects}, which is looked up once
 * per dimension — so recolouring the overworld's moon means replacing the overworld's
 * effects object wholesale, taking over vanilla sky rendering to change one quad. What
 * IS an ordinary hook is the fog colour, and fog is what the whole sky dome is washed
 * with at distance. Pushing it red turns the night red; the moon reads as red because
 * everything around it does.
 *
 * SO THE COMET IS NOT DRAWN AS AN OBJECT. That is the honest limit of this class and the
 * one thing it does not deliver: the comet's day is unmistakable — the sky burns orange
 * from dawn to dawn — but there is no streak overhead to look up at. Doing that properly
 * means rendering a textured quad on the sky dome through {@code RenderLevelStageEvent},
 * which is real work that cannot be checked without running the game.
 *
 * TINTED, NEVER REPLACED. Every colour here is blended TOWARDS the fog vanilla already
 * chose rather than set outright, so weather, biome, time of day and depth all still
 * show through. Setting a flat colour would make an event look identical underwater, in
 * a cave and on a hilltop at sunset.
 */
@EventBusSubscriber(modid = Atlamod.MODID, value = Dist.CLIENT)
public final class WorldEventSky {

    /** How far the fog is dragged towards each event's colour, at its strongest. */
    private static final float BLOOD_MOON_STRENGTH = 0.75F;
    private static final float COMET_STRENGTH = 0.55F;
    private static final float BLACK_SUN_STRENGTH = 0.90F;

    /** How strong the screen wash is at its deepest. Deliberately slight. */
    private static final float SCREEN_TINT = 0.16F;

    private WorldEventSky() {
    }

    /**
     * The fog, pulled towards whichever event is running.
     *
     * Only ever ONE at a time in practice — the comet's day is a multiple of six and the
     * eclipse's of twelve, so they can coincide, and when they do the eclipse wins because
     * it is checked last and is the more dramatic of the two. The blood moon is at night
     * and the other two are day events, so those never meet.
     */
    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;

        if (WorldEvents.isActive(level, WorldEvents.Event.BLOOD_MOON)) {
            blend(event, 0.42F, 0.02F, 0.03F, BLOOD_MOON_STRENGTH);
        }

        if (WorldEvents.isActive(level, WorldEvents.Event.SOZINS_COMET)) {
            blend(event, 0.85F, 0.33F, 0.07F, COMET_STRENGTH);
        }

        // LAST, and scaled by how far through the crossing we are, so the sun dims and
        // comes back rather than switching. At the middle of the six minutes it is total.
        float eclipse = WorldEvents.eclipseDepth(level);
        if (eclipse > 0.0F) {
            blend(event, 0.02F, 0.02F, 0.04F, BLACK_SUN_STRENGTH * eclipse);
        }
    }

    /** Moves the fog a fraction of the way towards a colour, leaving the rest as it was. */
    private static void blend(ViewportEvent.ComputeFogColor event,
                              float red, float green, float blue, float amount) {
        event.setRed(event.getRed() + (red - event.getRed()) * amount);
        event.setGreen(event.getGreen() + (green - event.getGreen()) * amount);
        event.setBlue(event.getBlue() + (blue - event.getBlue()) * amount);
    }

    /**
     * A wash of colour over the whole screen while an event runs.
     *
     * The fog only shows at distance, so indoors and underground an event would otherwise
     * be invisible — and the black sun in particular is something you should notice from
     * inside a house. Kept deliberately slight: this sits over everything including the
     * inventory, and a strong tint worn for a whole night would be exhausting rather than
     * atmospheric.
     *
     * Drawn by {@link ModHudOverlay}, which is already a registered layer — a second layer
     * for one rectangle would be a second thing to keep in order.
     */
    public static void renderScreenTint(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        int colour = 0;

        if (WorldEvents.isActive(level, WorldEvents.Event.BLOOD_MOON)) {
            colour = tint(0xFF0A05, SCREEN_TINT);
        }
        if (WorldEvents.isActive(level, WorldEvents.Event.SOZINS_COMET)) {
            colour = tint(0xFF7A1A, SCREEN_TINT * 0.8F);
        }

        float eclipse = WorldEvents.eclipseDepth(level);
        if (eclipse > 0.0F) {
            // Far heavier than the other two, because "the sun went out" is the event.
            colour = tint(0x000008, 0.55F * eclipse);
        }

        if (colour == 0) return;

        graphics.fill(0, 0,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), colour);
    }

    /** An RGB with an alpha put on it. */
    private static int tint(int rgb, float alpha) {
        int a = Math.round(Math.min(1.0F, Math.max(0.0F, alpha)) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
