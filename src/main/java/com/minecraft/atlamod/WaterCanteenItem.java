package com.minecraft.atlamod;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A canteen of water. Waterbending away from open water draws on this instead.
 *
 * The water level rides on the stack's damage value rather than a data component,
 * which buys the durability bar as a gauge for free — twenty units, so one ability
 * costs exactly the 5% the design calls for.
 *
 * It is deliberately never damaged through hurtAndBreak: the damage value is set
 * directly, so an empty canteen is empty rather than destroyed. Running dry should
 * send you looking for a river, not cost you the item.
 */
public class WaterCanteenItem extends Item {

    /** Units in a full canteen. Twenty makes each ability cost exactly 5%. */
    public static final int CAPACITY = 20;

    /** Blue, so the gauge reads as water rather than as wear. */
    private static final int BAR_COLOUR = 0x3388FF;

    public WaterCanteenItem(Properties properties) {
        super(properties.stacksTo(1).durability(CAPACITY));
    }

    /** How many ability-uses of water are left in this canteen. */
    public static int getWater(ItemStack stack) {
        return CAPACITY - stack.getDamageValue();
    }

    public static boolean isEmpty(ItemStack stack) {
        return getWater(stack) <= 0;
    }

    /** Fills it to the brim. */
    public static void fill(ItemStack stack) {
        stack.setDamageValue(0);
    }

    /**
     * Spends one unit. Set directly rather than through hurtAndBreak, which would
     * destroy the canteen once it ran out.
     */
    public static void drink(ItemStack stack) {
        stack.setDamageValue(Math.min(CAPACITY, stack.getDamageValue() + 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!canReachWater(level, player)) {
            return InteractionResultHolder.pass(stack);
        }

        if (getWater(stack) >= CAPACITY) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            fill(stack);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Whether there is water to scoop: either the player is standing in it, or they
     * are looking at some within reach. The raycast asks for source blocks only, so
     * the thin edge of a flow does not count as a refill.
     */
    private static boolean canReachWater(Level level, Player player) {
        if (player.isInWater()) return true;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        return hit.getType() == HitResult.Type.BLOCK
                && level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER);
    }

    /** Always shown, so an empty canteen looks empty rather than looking like no canteen. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getWater(stack) / (float) CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }
}
