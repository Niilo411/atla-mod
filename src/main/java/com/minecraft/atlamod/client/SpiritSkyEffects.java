package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.spirit.island.IslandStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * A sky that changes with the island you are standing on.
 *
 * VANILLA HAS NO PER-BIOME SKY. A skybox belongs to the DIMENSION: DimensionType names an
 * effects id, the client looks up one DimensionSpecialEffects for the whole world, and
 * that is the end of it. So "nether islands get a nether sky" cannot be data, and cannot
 * be done by any amount of biome JSON.
 *
 * What makes it possible without a mixin is that {@code skyType()} is an ordinary
 * overridable getter rather than a final one. So this registers ONE effects object for
 * the dimension and has it answer differently depending on where the player is — it is
 * consulted every frame during sky rendering, so a changing answer is honoured
 * immediately.
 *
 * Reading the player's position inside a getter is not beautiful, but the alternative is
 * a mixin into LevelRenderer, and this stays entirely inside APIs NeoForge supports.
 *
 * THE FOG DELEGATES THE SAME WAY, because a nether sky over overworld-blue fog looks
 * broken rather than deliberate. Everything visual is asked of the vanilla effects object
 * for the matching dimension, so each island looks like the place it is imitating.
 */
@EventBusSubscriber(modid = Atlamod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpiritSkyEffects extends DimensionSpecialEffects {

    /** Must match the "effects" field in data/atlamod/dimension_type/spirit_world.json. */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_world");

    private static final DimensionSpecialEffects OVERWORLD = new OverworldEffects();
    private static final DimensionSpecialEffects NETHER = new NetherEffects();
    private static final DimensionSpecialEffects END = new EndEffects();

    /**
     * The wasteland's dead, starless dark — and the reason it is not simply "night".
     *
     * NIGHT IS NOT AVAILABLE, and that is a limitation of the game rather than a choice.
     * A sky is drawn from the LEVEL's time of day, which is one value for a whole
     * dimension, so a single biome cannot be at a different hour from its neighbours. The
     * previous attempt pinned the client's clock per biome and it flickered, because the
     * server rebroadcasts the real time once a second and that packet lands between the
     * ticks that would correct it. There is no hook between the two without a mixin.
     *
     * So this drops time out of the picture entirely. SkyType.NONE is the nether's mode:
     * no dome, no sun, no moon, no stars — just fog in every direction. Given a near-black
     * fog and the nether's flat lighting, the result is a lightless void overhead that
     * never changes and cannot flicker, because nothing about it is derived from a clock.
     *
     * What is lost is stars. What is kept is that it reads as a dead place, it is stable,
     * and it is nothing like the End islands' sky, which is a textured purple starfield.
     */
    private static final DimensionSpecialEffects WASTELAND = new DimensionSpecialEffects(
            Float.NaN, true, SkyType.NONE, false, true) {

        /** Very nearly black, with just enough blue left in it to read as night air. */
        private static final Vec3 FOG = new Vec3(0.035, 0.035, 0.05);

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return FOG;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }
    };

    public SpiritSkyEffects() {
        // The values passed here are only the defaults for anything not overridden
        // below. Overworld's are used because most islands are overworld-skied, and
        // because its cloud height is the one that makes sense above a floating world.
        super(OverworldEffects.CLOUD_LEVEL, true, SkyType.NORMAL, false, false);
    }

    @SubscribeEvent
    public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new SpiritSkyEffects());
    }

    /**
     * Which look the player is currently under.
     *
     * Three islands differ — nether, end, and the wasteland's starless dark. Everything
     * else, including the crimson mountains, the warped swamps and the open void between
     * islands, gets the ordinary overworld sky.
     *
     * Falls back to overworld whenever there is no player or no level yet, which happens
     * during loading screens and between dimensions.
     */
    private static DimensionSpecialEffects current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return OVERWORLD;

        var biome = minecraft.level.getBiome(minecraft.player.blockPosition());
        IslandStyle style = IslandStyle.forBiome(biome);

        // Open void between islands, or some biome that is not ours at all.
        if (style == null) return OVERWORLD;

        // Keyed on the FAMILY, not the individual biome, so every nether variant gets the
        // nether sky without this having to list them — and a variant added later is
        // covered the moment it names its family.
        return switch (style.family()) {
            case NETHER -> NETHER;
            case END -> END;
            case WASTELAND -> WASTELAND;
            default -> OVERWORLD;
        };
    }

    @Override
    public SkyType skyType() {
        return current().skyType();
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return current().getBrightnessDependentFogColor(fogColor, brightness);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return current().isFoggyAt(x, y);
    }

    @Override
    public boolean constantAmbientLight() {
        return current().constantAmbientLight();
    }

    @Override
    public boolean forceBrightLightmap() {
        return current().forceBrightLightmap();
    }

    /**
     * Sunrise colours, which only the overworld sky has at all.
     *
     * Asked of the CURRENT effects rather than always of the overworld, because a nether
     * or end island showing a sunrise on a sky that has no sun would be a stray band of
     * orange hanging in the dark.
     */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return current().getSunriseColor(timeOfDay, partialTicks);
    }

    /**
     * Clouds are left at the overworld's height everywhere, on purpose.
     *
     * Letting this follow the current island would mean taking the nether's answer over a
     * nether island, and the nether has no cloud plane at all — so the clouds would blink
     * out and back as a player crossed between islands.
     */
    @Override
    public float getCloudHeight() {
        return OverworldEffects.CLOUD_LEVEL;
    }
}
