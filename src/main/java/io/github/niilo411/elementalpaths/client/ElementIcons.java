package io.github.niilo411.elementalpaths.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Element emblems for the UI.
 *
 * Only elements listed here have artwork. Anything missing falls back to the old
 * "?" box rather than being blitted anyway, because a texture Minecraft cannot find
 * renders as the black and magenta checkerboard — which looks far more broken than
 * an honest placeholder.
 *
 * Adding an element is a PNG in textures/gui/elements/ plus a line in ICONS.
 */
public final class ElementIcons {

    /**
     * Side length the source PNGs are expected to be. Anything square works as long
     * as this matches, since the icon is scaled to whatever size the caller asks for.
     */
    private static final int SOURCE_SIZE = 256;

    private static final Map<String, ResourceLocation> ICONS = Map.ofEntries(
            Map.entry("fire", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/fire_icon.png")),
            Map.entry("water", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/water_icon.png")),
            Map.entry("air", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/air_icon.png")),
            Map.entry("earth", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/earth_icon.png")),

            // The sub-elements, worn in the HUD badge once one is switched to with [Y].
            // Each wears its parent's ring colour.
            Map.entry("lightning", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/lightning_icon.png")),
            Map.entry("combustion", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/combustion_icon.png")),
            Map.entry("ice", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/ice_icon.png")),
            Map.entry("blood", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/blood_icon.png")),
            Map.entry("sound", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/sound_icon.png")),
            Map.entry("gravity", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/gravity_icon.png")),
            Map.entry("metal", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/metal_icon.png")),
            Map.entry("lava", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/lava_icon.png")),

            // Not a channeling art, but it is chosen from the same screen and worn in the
            // same HUD badge, so it needs an emblem for exactly the same reasons. Ours
            // rather than anyone's — see tools/GenChiIcon.java.
            Map.entry("nochanneling", ResourceLocation.fromNamespaceAndPath("elemental_paths", "textures/gui/elements/nochanneling_icon.png"))
    );

    private ElementIcons() {
    }

    /** Whether this element has artwork to draw. */
    public static boolean has(String element) {
        return element != null && ICONS.containsKey(element.toLowerCase());
    }

    /**
     * Draws the element emblem into a square of {@code size} at {@code x, y}.
     *
     * Scaled through the pose stack rather than by the blit arguments: blit's width
     * arguments set the source region as well as the drawn size, so it cannot resize
     * on its own. Scaling here means the PNG can stay whatever resolution it was
     * drawn at instead of having to match every place it appears.
     */
    public static void draw(GuiGraphics graphics, String element, int x, int y, int size) {
        ResourceLocation icon = ICONS.get(element.toLowerCase());
        if (icon == null) return;

        float scale = size / (float) SOURCE_SIZE;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        graphics.blit(icon, 0, 0, 0, 0.0F, 0.0F,
                SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE);

        graphics.pose().popPose();
    }
}
