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

    /**
     * Units in a full canteen. A setting; twenty by default, which makes each ability
     * cost exactly 5%.
     *
     * ANSWERED LIVE RATHER THAN STAMPED ON THE ITEM, which is what makes it configurable
     * at all. Durability is a data component set when the stack is made, so a canteen
     * crafted before a change would keep the old capacity for ever — and the item itself
     * is registered during mod construction, long before a server config exists to read.
     * NeoForge routes {@code ItemStack#getMaxDamage} through {@link #getMaxDamage(ItemStack)}
     * for exactly this, so overriding it below makes every canteen in the world, including
     * ones already sitting in a chest, answer the current setting.
     */
    public static int capacity() {
        return AtlaConfig.canteenCapacity();
    }

    /** Blue, so the gauge reads as water rather than as wear. */
    private static final int BAR_COLOUR = 0x3388FF;

    public WaterCanteenItem(Properties properties) {
        // The component still has to be set to SOMETHING for the stack to be damageable
        // at all; the override below is what decides the figure in use.
        super(properties.stacksTo(1).durability(20));
    }

    /**
     * The live capacity, which is what everything asking a stack for its maximum gets.
     *
     * Vanilla's own damage clamping, the durability bar and the tooltip all read through
     * here, so lowering the setting cannot leave a canteen reporting more than it holds.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return capacity();
    }

    /**
     * How many ability-uses of water are left in this canteen.
     *
     * Clamped at zero, because lowering the capacity below what a canteen has already
     * drunk would otherwise give a NEGATIVE reading — which {@link #isEmpty} would answer
     * correctly but the bar would draw backwards.
     */
    public static int getWater(ItemStack stack) {
        return Math.max(0, capacity() - stack.getDamageValue());
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
        stack.setDamageValue(Math.min(capacity(), stack.getDamageValue() + 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!canReachWater(level, player)) {
            return InteractionResultHolder.pass(stack);
        }

        if (getWater(stack) >= capacity()) {
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
        return Math.round(13.0F * getWater(stack) / (float) capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }
}
