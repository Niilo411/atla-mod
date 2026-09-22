package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.events.WorldEvents;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * What the three world events look like.
 *
 * TWO DIFFERENT TECHNIQUES, because the three events want different things. The blood moon
 * and the eclipse are about the QUALITY of the light — everything should look wrong — and
 * that is the fog's job. Sozin's comet is an OBJECT in the sky, and no amount of tinting
 * makes an object; it is drawn.
 *
 * THE COMET USED TO BE A TINT and it was the wrong answer. Washing the whole world orange
 * for a full day is exhausting to play under and still does not put a comet anywhere — you
 * could look straight up and see nothing. A quad on the sky dome is a few pixels that are
 * actually there.
 *
 * WHY THE MOON IS STILL DONE WITH FOG. The comet is drawn by ADDING to the sky, which
 * needs no cooperation from anything vanilla already drew. Recolouring the MOON is the
 * opposite problem: the moon already exists, drawn from a texture this mod does not own,
 * by {@code DimensionSpecialEffects} — which is looked up once per dimension, so changing
 * it means replacing the overworld's effects object wholesale and taking over vanilla sky
 * rendering to alter one quad. Pushing the fog red turns the whole night red, and the moon
 * reads as red because everything around it does.
 *
 * TINTED, NEVER REPLACED. Every fog colour here is blended TOWARDS what vanilla already
 * chose rather than set outright, so weather, biome, time of day and depth all still show
 * through. A flat colour would look identical underwater, in a cave, and at sunset.
 */
@EventBusSubscriber(modid = Atlamod.MODID, value = Dist.CLIENT)
public final class WorldEventSky {

    /** How far the fog is dragged towards each event's colour, at its strongest. */
    private static final float BLOOD_MOON_STRENGTH = 0.75F;
    private static final float BLACK_SUN_STRENGTH = 0.90F;

    /** How strong the screen wash is at its deepest. Deliberately slight. */
    private static final float SCREEN_TINT = 0.16F;

    private static final ResourceLocation COMET = ResourceLocation.fromNamespaceAndPath(
            Atlamod.MODID, "textures/environment/sozins_comet.png");

    /**
     * Where the comet hangs, as two rotations away from straight overhead.
     *
     * FIXED, NOT FOLLOWING THE SUN, which is the whole reason it is two constants rather
     * than a function of the time. The comet is meant to be up for the entire day and
     * night, so anything derived from the clock would have it rise and set like everything
     * else up there. It simply sits in one part of the sky and stays.
     *
     * TILT is degrees away from the zenith — 0 would be directly overhead, 90 on the
     * horizon — so 52 puts it high enough to be seen without craning and low enough to
     * meet the eye. COMPASS swings it around so it does not share the sun's arc.
     */
    private static final float COMET_TILT = 52.0F;
    private static final float COMET_COMPASS = -40.0F;

    /**
     * Half the quad's width. The sun's is 30.
     *
     * Most of the sheet is tail and empty space, so the bright head lands at a few pixels
     * across — which is what was asked for. Raising this grows the whole streak, head
     * included, rather than just making it brighter.
     */
    private static final float COMET_SIZE = 24.0F;

    /** How far up the sky dome everything is drawn. Vanilla's own figure for sun and moon. */
    private static final float SKY_DISTANCE = 100.0F;

    private WorldEventSky() {
    }

    // ==========================================================================
    //  The comet, as an actual object
    // ==========================================================================

    /**
     * Draws the comet onto the sky dome.
     *
     * AFTER_SKY IS THE ONLY STAGE THIS WORKS AT. It fires immediately after vanilla's own
     * sky has been drawn and before the fog is set up for terrain, so the sky dome is
     * there to draw onto and nothing solid has been laid over it yet.
     *
     * ITS POSE STACK IS NULL AT THIS STAGE — NeoForge passes none — so one is built from
     * the model view matrix, which is exactly what {@code LevelRenderer.renderSky} does
     * for the sun and the moon a few lines earlier.
     *
     * ADDITIVE, using the same blend function vanilla uses for the sun. That is what makes
     * the texture's alpha behave as a brightness rather than an opacity: the comet is
     * ADDED to the sky, so it glows on a dark one and cannot punch a dark hole in a bright
     * one. It is also why the texture has no background to speak of.
     *
     * EVERY PIECE OF RENDER STATE IS PUT BACK exactly as renderSky left it — colour white,
     * depth mask on, blend off, default blend func. Leaving any of them changed would not
     * show up here; it would show up as something unrelated rendering wrongly later in the
     * frame, which is the worst kind of bug to trace.
     */
    @SubscribeEvent
    public static void onRenderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!WorldEvents.isActive(mc.level, WorldEvents.Event.SOZINS_COMET)) return;

        // Only where there is an ordinary sky to put it in. The Nether has none, the End
        // has its own, and the Spirit World borrows whichever the island underneath wants
        // — a comet over a nether island would be somebody else's sky with ours on top.
        if (mc.level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return;

        // Underwater and in lava vanilla does not draw the sky at all, so there would be
        // nothing behind the comet but fog.
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());

        // Applied in this order so the vertices are tilted away from the zenith FIRST and
        // then swung around the compass — the other way round would swing the tilt itself.
        pose.mulPose(Axis.YP.rotationDegrees(COMET_COMPASS));
        pose.mulPose(Axis.XP.rotationDegrees(COMET_TILT));

        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, COMET);

        // Rain hides it, exactly as it hides the sun and the moon. Without this the comet
        // would burn cheerfully through a thunderstorm.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F - mc.level.getRainLevel(partial));

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        buffer.addVertex(matrix, -COMET_SIZE, SKY_DISTANCE, -COMET_SIZE).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, COMET_SIZE, SKY_DISTANCE, -COMET_SIZE).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, COMET_SIZE, SKY_DISTANCE, COMET_SIZE).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, -COMET_SIZE, SKY_DISTANCE, COMET_SIZE).setUv(0.0F, 1.0F);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    // ==========================================================================
    //  The two events that are about light rather than objects
    // ==========================================================================

    /**
     * The fog, pulled towards whichever event is running.
     *
     * THE COMET IS NOT HERE ANY MORE. It used to drag the fog orange for a whole day,
     * which washed the world out without putting a comet anywhere; it is drawn instead.
     * What remains are the two events that really are about the light itself.
     */
    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Level level = mc.level;

        if (WorldEvents.isActive(level, WorldEvents.Event.BLOOD_MOON)) {
            blend(event, 0.42F, 0.02F, 0.03F, BLOOD_MOON_STRENGTH);
        }

        // Scaled by how far through the crossing we are, so the sun dims and comes back
        // rather than switching. At the middle of the six minutes it is total.
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
     * be invisible — and the eclipse in particular is something you should notice from
     * inside a house. Kept slight: this sits over everything including the inventory.
     *
     * The comet has none. It is a thing in the sky rather than a change in the light, and
     * a permanent orange wash for a whole day is exactly what it was asked not to be.
     */
    public static void renderScreenTint(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        int colour = 0;

        if (WorldEvents.isActive(level, WorldEvents.Event.BLOOD_MOON)) {
            colour = tint(0xFF0A05, SCREEN_TINT);
        }

        float eclipse = WorldEvents.eclipseDepth(level);
        if (eclipse > 0.0F) {
            // Far heavier than the blood moon's, because "the sun went out" is the event.
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
