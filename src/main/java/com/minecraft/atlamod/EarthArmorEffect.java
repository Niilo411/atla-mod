package com.minecraft.atlamod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Earth armor — a shell of stone over the bender, worth ten armor points.
 *
 * No behaviour of its own: the armor is an ATTRIBUTE MODIFIER hung on the effect in
 * ModEffects, which vanilla applies and removes with the effect itself. That is the
 * whole reason this is a MobEffect rather than a countdown on BendingData — the
 * duration, the removal, the cleanup on death, and the inventory timer all come for
 * free, and the ten points stack on top of whatever the bender is already wearing
 * because an ADD_VALUE modifier is exactly that.
 *
 * The stone LOOK is separate, and client side: see EarthArmorLayer.
 */
public class EarthArmorEffect extends MobEffect {

    /** Weathered stone grey, for the effect's particles. */
    private static final int COLOR = 0x8C8C8C;

    public EarthArmorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
