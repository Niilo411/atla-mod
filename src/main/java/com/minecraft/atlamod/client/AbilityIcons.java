package com.minecraft.atlamod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Artwork for individual abilities, shown on their skill tree nodes.
 *
 * The same shape as {@link ElementIcons} and for the same reason: only abilities
 * listed here have art, and anything missing falls back to the old "?" box rather
 * than being blitted anyway — a texture Minecraft cannot find renders as the black
 * and magenta checkerboard, which looks far more broken than an honest placeholder.
 * With over sixty abilities and seven pictures, that fallback is the normal case for
 * now rather than an edge case.
 *
 * Adding an ability's icon is a PNG in textures/gui/abilities/ plus a line in ICONS.
 *
 * The KEY is the ability's display name lowercased, which is the same key the
 * registry and the cooldowns use — so the name in the skill tree, the name in
 * AbilityRegistry and the name here can never drift apart without the icon simply
 * not appearing.
 */
public final class AbilityIcons {

    /**
     * Side length the source PNGs are expected to be.
     *
     * Same 256 as the element emblems. Anything square works as long as this matches,
     * since the icon is scaled to whatever size the caller asks for — but every icon
     * shares this one constant, so they all have to agree.
     */
    private static final int SOURCE_SIZE = 256;

    private static final String FOLDER = "textures/gui/abilities/";

    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    static {
        // --- FIRE ---
        put("Fire leap", "fire_leap");
        put("Fire whip", "fire_whip");
        put("Fire push", "fire_push");
        put("Fire shield", "fire_shield");
        put("Fire spikes", "fire_spikes");
        put("Ignite", "ignite");
        put("blue fire", "blue_fire");
    }

    private AbilityIcons() {
    }

    /**
     * Registers one ability's art.
     *
     * The file name is given separately from the ability name because the two cannot
     * always match: a ResourceLocation path may only contain lowercase letters,
     * digits and a few punctuation marks, so "Fire whip" has to live on disk as
     * fire_whip.png.
     */
    private static void put(String ability, String file) {
        ICONS.put(ability.toLowerCase(Locale.ROOT),
                ResourceLocation.fromNamespaceAndPath("atlamod", FOLDER + file + ".png"));
    }

    /** Whether this ability has artwork to draw. */
    public static boolean has(String ability) {
        return ability != null && ICONS.containsKey(ability.toLowerCase(Locale.ROOT));
    }

    /**
     * Draws the ability's icon into a square of {@code size} at {@code x, y}.
     *
     * Scaled through the pose stack rather than by the blit arguments, exactly as
     * ElementIcons does: blit's width arguments set the source REGION as well as the
     * drawn size, so it cannot resize on its own. Scaling here means the art can stay
     * whatever resolution it was drawn at instead of having to match every place it
     * appears.
     */
    public static void draw(GuiGraphics graphics, String ability, int x, int y, int size) {
        ResourceLocation icon = ICONS.get(ability.toLowerCase(Locale.ROOT));
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
