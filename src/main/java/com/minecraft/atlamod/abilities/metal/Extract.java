package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Left / Metal. A TOGGLE that pulls raw iron out of the ground the bender is standing
 * on, a piece every two seconds, for as long as they can pay for it.
 *
 * The only ability in the mod that MAKES anything, which is why its rate is the whole
 * design: 5 chi a second for one raw iron every two is 10 chi a piece, so a full pool
 * is worth a stack or so. It is a slow trickle rather than a mine, and it is meant to
 * be — Mine is earthbending's ability for actually digging.
 *
 * It takes nothing OUT of the world. The iron is drawn from the ground in the fiction
 * but no block is touched, because an ability that really consumed the ground would
 * either be a duplication glitch or a way to delete terrain, depending on which half
 * you got wrong.
 */
public class Extract implements Ability {

    /** Registry key, also what the toggle is tracked by. */
    public static final String KEY = "extract";

    /** Chi drained per second while it runs. */
    public static final int CHI_PER_SECOND = 5;

    /** One piece of raw iron every this many ticks. */
    public static final int YIELD_EVERY = 40; // 2 seconds

    /** XP paid per second while it runs. */
    public static final int XP_PER_SECOND = 1;

    @Override
    public String getName() {
        return "Extract";
    }

    /** Nothing up front: billed by the second from the player tick. */
    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return data.isExtracting();
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        data.setExtracting(false);
        data.setExtractTicks(0);

        if (player.level() instanceof ServerLevel level) {
            Metal.scrape(level, player.position(), 0.6F, 1.4F);
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        data.setExtracting(true);
        data.setExtractTicks(0);

        if (player.level() instanceof ServerLevel level) {
            Metal.scrape(level, player.position(), 0.7F, 0.9F);
        }
    }

    /**
     * Hands over one piece when its two seconds are up.
     *
     * Added to the inventory rather than dropped at the feet, so a bender extracting
     * over a cliff or a lake does not lose what they paid for. If there is genuinely
     * no room it falls back to dropping, which is vanilla's own behaviour for a full
     * inventory.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        data.setExtractTicks(data.getExtractTicks() + 1);
        if (data.getExtractTicks() % YIELD_EVERY != 0) return;

        ItemStack raw = new ItemStack(Items.RAW_IRON);
        if (!player.getInventory().add(raw)) {
            player.drop(raw, false);
        }

        if (player.level() instanceof ServerLevel level) {
            Metal.spark(level, player.position(), 8, 0.4);
            Metal.scrape(level, player.position(), 0.4F, 1.6F);
        }
    }
}
