package com.minecraft.atlamod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The armor suits that are DRAWN over a bender.
 *
 * Each one is a render layer painted on top of the player, not a change of equipment
 * — which is the whole reason Earth armor and Metal armor can both say "adds armor on
 * top of whatever you have on" and mean it. The real gear is untouched underneath and
 * merely hidden.
 *
 * This lives in COMMON code rather than in client/ because both sides need it: the
 * server asks {@link #isWornBy} every tick to decide what to broadcast, and the client
 * asks {@link #texture} when it draws.
 *
 * Adding a suit = one constant here, plus its two sheets under
 * textures/models/armor/. Nothing else needs to change: the packet carries an ordinal,
 * the per-tick broadcast loops over values(), and the layer draws whatever is top.
 */
public enum BendingArmorSuit {

    /** Earth armor — cobbled stone. */
    STONE("stone_layer_1.png") {
        @Override
        public boolean isWornBy(LivingEntity entity) {
            return entity.hasEffect(ModEffects.EARTH_ARMOR);
        }
    },

    /**
     * Metal armor — brushed steel.
     *
     * Both grades share one sheet deliberately. Diamond Plating is a difference of
     * fifteen armor points against twenty, not of what the suit is made of, so a
     * separate texture would be inventing a distinction the design does not draw.
     */
    METAL("metal_layer_1.png") {
        @Override
        public boolean isWornBy(LivingEntity entity) {
            return entity.hasEffect(ModEffects.METAL_ARMOR)
                    || entity.hasEffect(ModEffects.METAL_ARMOR_DIAMOND);
        }
    };

    private final ResourceLocation texture;

    BendingArmorSuit(String file) {
        this.texture = ResourceLocation.fromNamespaceAndPath(
                Atlamod.MODID, "textures/models/armor/" + file);
    }

    public ResourceLocation texture() {
        return texture;
    }

    /** Whether this entity currently has the effect that puts this suit on. */
    public abstract boolean isWornBy(LivingEntity entity);

    /**
     * Which suit wins when more than one is up at once.
     *
     * Nothing stops a bender holding Earth armor and Metal armor together — they are
     * different elements with independent cooldowns — and drawing both would z-fight,
     * since the two sheets occupy exactly the same model. LATER constants win, so
     * metal covers stone: it is the refined one, and it is the harder to have earned.
     */
    public static BendingArmorSuit best(BendingArmorSuit a, BendingArmorSuit b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
