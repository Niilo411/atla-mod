package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.WaterCanteenItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Where a waterbender's water comes from.
 *
 * Standing near open water, bending is free. Away from it, every ability drinks a
 * unit from a canteen — and with no canteen and no river, there is nothing to bend
 * and the cast is refused.
 */
public final class WaterSupply {

    /** How far open water can be and still be usable directly. */
    public static final int WATER_SEARCH_RADIUS = 15;

    private WaterSupply() {
    }

    /**
     * Takes the water an ability needs, or explains why it cannot.
     *
     * Called before chi is spent, so a refused cast costs the player nothing.
     *
     * @return true if the ability may proceed
     */
    public static boolean tryConsume(ServerPlayer player) {
        if (hasWaterNearby(player)) {
            return true;
        }

        ItemStack canteen = findUsableCanteen(player);
        if (canteen.isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "§bNo water nearby, and no water in your canteen!"), true);
            return false;
        }

        WaterCanteenItem.drink(canteen);
        return true;
    }

    /** Whether there is open water within reach to bend directly. */
    public static boolean hasWaterNearby(ServerPlayer player) {
        Level level = player.level();
        BlockPos centre = player.blockPosition();

        // Searched as expanding shells rather than a flat triple loop, so standing at
        // the edge of a lake — the common case — bails out almost immediately instead
        // of walking the whole 31-cube every time an ability is cast.
        for (int radius = 0; radius <= WATER_SEARCH_RADIUS; radius++) {
            if (shellHasWater(level, centre, radius)) {
                return true;
            }
        }
        return false;
    }

    /** Checks only the surface of the cube at this radius; inner ones came earlier. */
    private static boolean shellHasWater(Level level, BlockPos centre, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean onShell = Math.abs(dx) == radius
                            || Math.abs(dy) == radius
                            || Math.abs(dz) == radius;
                    if (!onShell) continue;

                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (level.getFluidState(pos).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The first canteen in the player's inventory with anything left in it. */
    private static ItemStack findUsableCanteen(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Atlamod.WATER_CANTEEN.get()) && !WaterCanteenItem.isEmpty(stack)) {
                return stack;
            }
        }

        // Off-hand counts too, since that is where a canteen naturally lives.
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(Atlamod.WATER_CANTEEN.get()) && !WaterCanteenItem.isEmpty(offhand)) {
            return offhand;
        }

        return ItemStack.EMPTY;
    }
}
