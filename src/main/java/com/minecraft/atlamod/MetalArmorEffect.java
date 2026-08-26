package com.minecraft.atlamod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Metal armor -- a suit of iron, or of diamond once Diamond Plating is bought.
 *
 * Holds no behaviour: the armor is an attribute modifier declared on the effect in
 * ModEffects, which vanilla applies and removes in step with the effect itself. See
 * abilities/metal/MetalArmor.
 */
public class MetalArmorEffect extends MobEffect {

    /** Iron grey, for the swirl of particles around the wearer. */
    private static final int COLOR = 0xC8C8D2;

    public MetalArmorEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
