package com.minecraft.atlamod;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Spirit armor: what it is, and the one thing it does that vanilla armor cannot.
 *
 * ITS STATS ARE BORROWED WHOLE, and the split is the design. Defence and toughness are
 * IRON's exactly (2/6/5/2, no toughness), while durability is DIAMOND's exactly
 * (363/528/495/429). That is deliberate rather than a compromise: the armour is not meant
 * to win fights, it is meant to be worn for a long time while it feeds a bender's chi. A
 * set that also protected like diamond would simply be diamond with a bonus.
 *
 * The durability comes from the item Properties rather than the material, which is how
 * vanilla does it too — {@code ArmorItem.Type.getDurability(33)} is literally the call
 * every diamond piece makes.
 *
 * THE CHI BONUS IS PER PIECE AND ADDITIVE. Each piece worn is worth 46.5% on top of the
 * base regen rate, so a full set is 186% faster — which is what takes an empty pool from
 * the base 100 seconds to fill down to 35. Applied in the regen block in
 * {@code ServerEvents}, which is the only place that knows how much chi is being handed
 * back — the same argument Lightning Strength's doubled regen makes.
 *
 * That is a far bigger bonus than the set started with (+10% a piece, 71 seconds), and it
 * is a deliberate retune to a stated TIME rather than a stated rate.
 *
 * NOTHING HERE IS A MOB EFFECT OR AN ATTRIBUTE. Chi is the mod's own resource and vanilla
 * has no attribute for it, so there is nothing to hang a modifier on; the regen tick asks
 * what is being worn instead. That also means the bonus appears and disappears the instant
 * a piece is taken off, with no effect to expire and no state of ours to keep in step.
 */
public final class SpiritArmor {

    /**
     * What one piece adds to chi regeneration, in TENTHS of a percent of the base rate.
     *
     * TENTHS RATHER THAN WHOLE PERCENT because the target is a time, not a rate, and whole
     * percent cannot reach it. Base regen fills an empty pool in 100 seconds; a full set is
     * meant to do it in 35, which needs 100/35 = 2.857x, so the set has to add 185.7% and a
     * piece 46.43%. Whole percent lands on 35.2 or 34.7 seconds either side; 46.5% a piece
     * gives 34.97, which is 35 to the nearest tenth of a second.
     *
     * The carry in {@link com.minecraft.atlamod.BendingData#getChiRegenCarry} is what
     * actually makes a fractional percentage land exactly — see the note there.
     */
    public static final int REGEN_BONUS_PER_PIECE_TENTHS = 465;

    /** What the whole set adds. Derived, so the two can never be quoted differently. */
    public static final int FULL_SET_BONUS_TENTHS = REGEN_BONUS_PER_PIECE_TENTHS * 4;

    /** The denominator the bonus is measured against: 1000 tenths of a percent = 100%. */
    public static final int BONUS_SCALE = 1000;

    /** The four slots a spirit piece can occupy. */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SpiritArmor() {
    }

    /**
     * Whether this is a piece of spirit armour.
     *
     * Asked of the MATERIAL rather than by listing the four items, so a fifth piece added
     * later is counted without this method being touched.
     */
    public static boolean isSpiritPiece(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armor
                && armor.getMaterial() == Atlamod.SPIRIT_ARMOR_MATERIAL;
    }

    /** How many pieces of spirit armour this entity is wearing, 0 to 4. */
    public static int wornPieces(LivingEntity entity) {
        int worn = 0;

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (isSpiritPiece(entity.getItemBySlot(slot))) worn++;
        }
        return worn;
    }

    /**
     * What this entity's armour adds to chi regen, in tenths of a percent on top of the base.
     *
     * 0 with nothing worn, which is what keeps an unarmoured bender's regen bit-for-bit
     * what it has always been.
     */
    public static int regenBonusTenths(LivingEntity entity) {
        return wornPieces(entity) * REGEN_BONUS_PER_PIECE_TENTHS;
    }
}
