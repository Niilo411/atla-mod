package com.minecraft.atlamod.client;

import net.minecraft.util.Mth;

/**
 * What colour the HUD's chi bar is filled in, which is a record of how many spirit shrines
 * the bender has drawn from.
 *
 * A CALCULATED HUE ROTATION RATHER THAN A FIXED PALETTE, and the reason is that there is no
 * ceiling on the count. Shrines are scattered across a dimension that generates forever, so
 * a hand-picked list of colours would have to either run out — leaving the bar stuck once
 * it had, which is exactly the feedback this exists to give — or repeat, which reads as a
 * bug. A rotation has an answer for every count and needs no table kept in step with
 * anything.
 *
 * THE STEP IS THE GOLDEN ANGLE, which is the whole trick. Rotating by a simple fraction —
 * a tenth of the circle, say — puts the tenth shrine back on the first one's colour. The
 * golden ratio's conjugate never repeats, and better than that, it leaves EVERY pair of
 * consecutive counts far apart on the wheel: the gap is about 222 degrees, so each shrine
 * is a colour plainly different from the one before it as well as from every one before
 * that. It is the same reason it is used for picking chart colours.
 *
 * The saturation and brightness are held fixed so every colour in the sequence reads as a
 * bar on a busy screen. Hue is the only thing that moves.
 */
public final class ChiBarColors {

    /** The bar before any shrine: the blue it has always been. */
    private static final int BASE = 0xFF00AAFF;

    /** BASE's own hue, so a bender with no shrines is left exactly as they were. */
    private static final float BASE_HUE = 0.5667F;

    /** The golden ratio's conjugate: 1 / phi, about 222.5 degrees around the wheel. */
    private static final float STEP = 0.618034F;

    private static final float SATURATION = 0.85F;
    private static final float BRIGHTNESS = 1.0F;

    private ChiBarColors() {
    }

    /** The fill colour for a bender who has drawn from {@code shrines} shrines. */
    public static int forShrines(int shrines) {
        if (shrines <= 0) return BASE;

        float hue = (BASE_HUE + shrines * STEP) % 1.0F;

        // Opaque: hsvToRgb answers with the three colour channels only.
        return 0xFF000000 | Mth.hsvToRgb(hue, SATURATION, BRIGHTNESS);
    }
}
