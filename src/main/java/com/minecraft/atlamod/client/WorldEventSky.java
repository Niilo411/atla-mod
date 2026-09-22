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
 * EVERYTHING IN THE SKY IS DRAWN RATHER THAN TINTED, and none of it needed a mixin or a
 * replacement {@code DimensionSpecialEffects}. All of it happens at {@code AFTER_SKY},
 * which fires once vanilla's own sky is down and before anything solid is laid over it:
 * the comet is ADDED where there was nothing, and the moon and sun are TAKEN BACK OUT and
 * redrawn as we want them. Only the overall colour of the light is still fog.
 *
 * THE ONE FACT THE WHOLE FILE TURNS ON: vanilla's sun and moon textures are 100% opaque —
 * measured, not assumed. Their shape lives entirely in their BRIGHTNESS, and it is the
 * additive blend that makes their dark parts invisible. So ordinary alpha blending cannot
 * recolour them: painting a red moon over the white one with alpha paints a red SQUARE,
 * and the first black sun was a hard black rectangle hanging in the sky. Anything that
 * wants to change one of them has to work in brightness too — see {@link #subtractive()}.
 *
 * SO RECOLOURING IS TWO PASSES: subtract what vanilla drew, then add what should be there
 * instead. The blood moon takes the white moon out and puts a red one back, craters and
 * phase intact. The eclipse takes the sun out and puts nothing back, which is what an
 * eclipse is.
 *
 * THE COMET USED TO BE A TINT and it was the wrong answer. Washing the whole world orange
 * for a full day is exhausting to play under and still does not put a comet anywhere — you
 * could look straight up and see nothing.
 *
 * WHAT IS STILL FOG is the colour of the light itself, which is the one thing fog is
 * actually for: the night going red under a blood moon, and the world going dark under an
 * eclipse. TINTED, NEVER REPLACED — every colour is blended TOWARDS what vanilla already
 * chose, so weather, biome, time of day and depth all still show through. A flat colour
 * would look identical underwater, in a cave, and at sunset.
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

    /** Vanilla's own textures and sizes, so the overdraw lands exactly on top. */
    private static final ResourceLocation SUN =
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON =
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    private static final float SUN_SIZE = 30.0F;
    private static final float MOON_SIZE = 20.0F;

    /**
     * The blood moon's colour, MULTIPLIED over the moon's own texture.
     *
     * The RED CHANNEL IS LEFT AT FULL, which is what keeps the moon as bright in red as
     * vanilla's is in white. An earlier attempt scaled all three down and the moon came
     * out a dark smudge. Green and blue near zero are what make it red rather than pink.
     */
    private static final float MOON_RED = 1.0F;
    private static final float MOON_GREEN = 0.13F;
    private static final float MOON_BLUE = 0.10F;

    /** How many stars come out during the eclipse. A scattering, not a full night. */
    private static final int ECLIPSE_STARS = 120;

    /** Fixed, so the same stars come out every time. Not vanilla's, so they are not its. */
    private static final long STAR_SEED = 0x5EED57A45L;

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

        // Only where there is an ordinary sky to draw on. The Nether has none, the End
        // has its own, and the Spirit World borrows whichever the island underneath
        // wants — and the moon and sun this paints over are not there to paint over.
        if (mc.level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return;

        // Underwater and in lava vanilla draws no sky at all, so there would be nothing
        // behind any of this but fog.
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float clear = 1.0F - mc.level.getRainLevel(partial);
        float eclipse = WorldEvents.eclipseDepth(mc.level);
        boolean bloodMoon = WorldEvents.isActive(mc.level, WorldEvents.Event.BLOOD_MOON);
        boolean comet = WorldEvents.isActive(mc.level, WorldEvents.Event.SOZINS_COMET);

        if (!comet && !bloodMoon && eclipse <= 0.0F) return;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);

        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());

        if (comet) {
            pose.pushPose();

            // Applied in this order so the vertices are tilted away from the zenith FIRST
            // and then swung around the compass — the other way round would swing the
            // tilt itself.
            pose.mulPose(Axis.YP.rotationDegrees(COMET_COMPASS));
            pose.mulPose(Axis.XP.rotationDegrees(COMET_TILT));

            drawComet(pose.last().pose(), clear);
            pose.popPose();
        }

        if (bloodMoon || eclipse > 0.0F) {
            pose.pushPose();

            // VANILLA'S OWN CELESTIAL FRAME, copied exactly from LevelRenderer.renderSky.
            // Everything drawn in here lands on top of what vanilla put there — the moon
            // where the moon is, the sun where the sun is — and turns with the day the
            // same way, because it is the same two rotations.
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(mc.level.getTimeOfDay(partial) * 360.0F));

            Matrix4f matrix = pose.last().pose();

            // STARS FIRST, so the black disc of the eclipse covers the ones behind it
            // rather than shining through.
            if (eclipse > 0.0F) {
                drawEclipseStars(matrix, eclipse * clear);
                drawBlackSun(matrix, eclipse * clear);
            }

            if (bloodMoon) {
                drawRedMoon(matrix, mc.level.getMoonPhase(), clear);
            }

            pose.popPose();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The comet: ADDED to the sky, so it glows rather than covering.
     *
     * Rain hides it, exactly as rain hides the sun and the moon. Without that it would
     * burn cheerfully through a thunderstorm.
     */
    private static void drawComet(Matrix4f matrix, float clear) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, COMET);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clear);

        quad(matrix, COMET_SIZE, SKY_DISTANCE);
    }

    /**
     * The blood moon: vanilla's moon drawn again, in red, exactly over itself.
     *
     * ORDINARY ALPHA BLENDING rather than the additive the rest of the sky uses, and that
     * is the whole trick. Additive can only ever ADD light, so a red pass over a white
     * moon would come out white with a red rim; alpha blending COVERS, so the red moon
     * simply replaces the white one and nothing else on the sky is touched.
     *
     * THE TEXTURE IS MULTIPLIED BY THE SHADER COLOUR, so this is not a flat red disc —
     * the moon keeps its own light and shade and the craters are still there, in red.
     *
     * The phase UVs are vanilla's, so a crescent blood moon is a crescent.
     */
    private static void drawRedMoon(Matrix4f matrix, int phase, float clear) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);

        // PASS ONE: take vanilla's white moon back out of the sky.
        subtractive();
        RenderSystem.setShaderColor(clear, clear, clear, 1.0F);
        moonQuad(matrix, phase);

        // PASS TWO: add ours in its place. Additive, like every other light in the sky,
        // and the texture is multiplied by the shader colour — so the moon keeps its own
        // craters and shading and simply arrives red instead of white.
        additive();
        RenderSystem.setShaderColor(MOON_RED * clear, MOON_GREEN * clear, MOON_BLUE * clear, 1.0F);
        moonQuad(matrix, phase);
    }

    /**
     * The moon's quad, at vanilla's size, winding and phase UVs.
     *
     * Drawn twice per blood moon — see {@link #drawRedMoon} — which is the whole reason
     * it is a method: the two passes must land on exactly the same pixels, and two copies
     * of this winding would be two chances for them not to.
     */
    private static void moonQuad(Matrix4f matrix, int phase) {
        int column = phase % 4;
        int row = phase / 4 % 2;

        float u0 = column / 4.0F;
        float v0 = row / 2.0F;
        float u1 = (column + 1) / 4.0F;
        float v1 = (row + 1) / 2.0F;

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // Wound and UV-mapped exactly as vanilla winds the moon — the moon hangs at
        // NEGATIVE y, opposite the sun, and its u runs backwards.
        buffer.addVertex(matrix, -MOON_SIZE, -SKY_DISTANCE, MOON_SIZE).setUv(u1, v1);
        buffer.addVertex(matrix, MOON_SIZE, -SKY_DISTANCE, MOON_SIZE).setUv(u0, v1);
        buffer.addVertex(matrix, MOON_SIZE, -SKY_DISTANCE, -MOON_SIZE).setUv(u0, v0);
        buffer.addVertex(matrix, -MOON_SIZE, -SKY_DISTANCE, -MOON_SIZE).setUv(u1, v0);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /**
     * The eclipse: the sun's own disc, painted black over itself.
     *
     * The shader colour is black with the eclipse's depth as its ALPHA, so the sun does
     * not switch off — it darkens as the moon slides across and comes back as it leaves.
     *
     * DRAWN A SHADE LARGER THAN THE SUN. At exactly the sun's size the two quads fight
     * over the same pixels at the rim and a bright fringe survives all the way through
     * totality; a little over covers it. The extra is small enough that the black disc
     * still reads as the moon in front rather than as a hole.
     */
    private static void drawBlackSun(Matrix4f matrix, float depth) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SUN);

        // The sun's own light, taken back out of the sky in proportion to how far the
        // moon has crossed it. Nothing is added in its place — what is left where the sun
        // was is the black of the eclipsed sky, which is what an eclipse looks like.
        subtractive();
        RenderSystem.setShaderColor(depth, depth, depth, 1.0F);

        quad(matrix, SUN_SIZE, SKY_DISTANCE);
    }

    /**
     * Vanilla's own blend for everything in the sky: the source is ADDED.
     *
     * Which is why both of vanilla's sky textures are fully opaque and still have shape —
     * their dark parts add nothing and so are invisible. That fact is the whole reason
     * for the two-pass work above.
     */
    private static void additive() {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }

    /**
     * The opposite: the sky is MULTIPLIED DOWN by what the texture is bright.
     *
     * {@code dst = dst * (1 - src)}. Where the texture is white the sky goes black, where
     * it is black the sky is untouched, and in between it feathers — which is exactly how
     * to take something back out that was put in additively.
     *
     * THIS IS WHY NEITHER THE MOON NOR THE SUN IS DRAWN WITH ORDINARY ALPHA BLENDING.
     * Both textures are 100% opaque — measured — so alpha blending them paints a SQUARE,
     * not a disc: the first attempt at a black sun put a hard black rectangle in the sky.
     * Their shape lives in their brightness, so only a blend that reads brightness can
     * respect it.
     */
    private static void subtractive() {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    /**
     * A scattering of stars, brightening as the eclipse deepens.
     *
     * DRAWN IN THE CELESTIAL FRAME, so they turn with the sky the way real stars do
     * rather than hanging fixed over the player.
     *
     * OURS RATHER THAN VANILLA'S. Its star buffer is private to the level renderer and
     * its brightness is driven by the time of day, which is exactly what has to be
     * ignored here — the whole point is stars at noon. A hundred and twenty of our own is
     * a scattering rather than a night sky, which is what "a few" asks for.
     */
    private static void drawEclipseStars(Matrix4f matrix, float brightness) {
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);

        float[] corners = stars();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

        for (int i = 0; i < corners.length; i += 3) {
            buffer.addVertex(matrix, corners[i], corners[i + 1], corners[i + 2]);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** A square on the sky dome, centred on the axis and {@code distance} up it. */
    private static void quad(Matrix4f matrix, float half, float distance) {
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        buffer.addVertex(matrix, -half, distance, -half).setUv(0.0F, 0.0F);
        buffer.addVertex(matrix, half, distance, -half).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, half, distance, half).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, -half, distance, half).setUv(0.0F, 1.0F);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** The star field's corners, worked out once and kept. */
    private static float[] starCorners;

    /**
     * Builds the star quads, using vanilla's own construction.
     *
     * Each star is a point on a sphere of radius 100 with a small square built FACING THE
     * ORIGIN — which is what the quaternion does, rotating the square's own normal onto
     * the direction of the star. Without it the squares would all face one way and half
     * of them would be edge-on and invisible.
     *
     * Built ONCE and kept, because it is the same sky every time: a hundred and twenty
     * stars is four hundred and eighty vertices of trigonometry that would otherwise be
     * redone every frame of every eclipse.
     */
    private static float[] stars() {
        if (starCorners != null) return starCorners;

        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(STAR_SEED);
        java.util.List<Float> built = new java.util.ArrayList<>(ECLIPSE_STARS * 12);

        while (built.size() < ECLIPSE_STARS * 12) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float y = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float size = 0.15F + random.nextFloat() * 0.1F;

            // Rejected outside the unit sphere so the directions are evenly spread; a
            // cube taken as-is would crowd the corners. Too near the middle and the
            // normalise is unstable.
            float lengthSq = x * x + y * y + z * z;
            if (lengthSq <= 0.010000001F || lengthSq >= 1.0F) continue;

            org.joml.Vector3f centre = new org.joml.Vector3f(x, y, z).normalize(SKY_DISTANCE);
            float spin = (float) (random.nextDouble() * Math.PI * 2.0);

            org.joml.Quaternionf facing = new org.joml.Quaternionf()
                    .rotateTo(new org.joml.Vector3f(0.0F, 0.0F, -1.0F), centre)
                    .rotateZ(spin);

            addCorner(built, centre, size, -size, facing);
            addCorner(built, centre, size, size, facing);
            addCorner(built, centre, -size, size, facing);
            addCorner(built, centre, -size, -size, facing);
        }

        float[] corners = new float[built.size()];
        for (int i = 0; i < corners.length; i++) corners[i] = built.get(i);

        starCorners = corners;
        return corners;
    }

    private static void addCorner(java.util.List<Float> into, org.joml.Vector3f centre,
                                  float dx, float dy, org.joml.Quaternionf facing) {
        org.joml.Vector3f corner = new org.joml.Vector3f(dx, dy, 0.0F).rotate(facing).add(centre);

        into.add(corner.x);
        into.add(corner.y);
        into.add(corner.z);
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
