package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Left / Metal. HELD: pulls raw iron out of the ground for as long as the key is down,
 * a piece every two seconds.
 *
 * The only ability in the mod that MAKES anything, which is why its rate is the whole
 * design: 5 chi a second for one raw iron every two is 10 chi a piece, so a full pool
 * is worth a stack or so. A slow trickle rather than a mine, deliberately — Mine is
 * earthbending's ability for actually digging.
 *
 * A CHANNEL rather than a toggle, and the difference is more than the input: the
 * dispatcher already owns every part of running one. It drains the chi spread evenly
 * across each second, trickles the xp, stops when the chi runs out, and syncs on its
 * own schedule — all of which this ability was doing by hand from the player tick as a
 * toggle, and none of which it has to do now.
 *
 * It takes nothing OUT of the world. The iron is drawn from the ground in the fiction
 * but no block is touched, because an ability that really consumed the ground would be
 * either a duplication glitch or a way to delete terrain, depending on which half you
 * got wrong.
 */
public class Extract implements ChanneledAbility {

    /** Registry key. */
    public static final String KEY = "extract";

    /** One piece of raw iron every this many ticks. */
    private static final int YIELD_EVERY = 40; // 2 seconds

    @Override
    public String getName() {
        return "Extract";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 5;
    }

    @Override
    public double getXpPerSecond() {
        return 1.0;
    }

    /** No cooldown and no cap: the chi is the whole limit. */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Metal.scrape((ServerLevel) player.level(), player.position(), 0.7F, 0.9F);
    }

    /**
     * Hands over one piece every two seconds.
     *
     * The count comes from the channel's own tick, so nothing has to be reset when the
     * key is let go — starting again starts the two seconds again, which is the honest
     * behaviour for something billed by the second.
     */
    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // A steady trickle of sparks so the ability visibly does something in the
        // second and a half between pieces.
        if (data.getChannelTicks() % 5 == 0) {
            Metal.spark(level, player.position(), 3, 0.4);
        }

        // Zero is the first tick of the channel; yielding there would hand over a
        // piece before any chi had been spent on it.
        if (data.getChannelTicks() == 0) return;
        if (data.getChannelTicks() % YIELD_EVERY != 0) return;

        // Added to the inventory rather than dropped at the feet, so a bender
        // extracting over a cliff or a lake does not lose what they paid for. If there
        // is genuinely no room it falls back to dropping, which is vanilla's own
        // behaviour for a full inventory.
        ItemStack raw = new ItemStack(Items.RAW_IRON);
        if (!player.getInventory().add(raw)) {
            player.drop(raw, false);
        }

        Metal.spark(level, player.position(), 8, 0.4);
        Metal.scrape(level, player.position(), 0.4F, 1.6F);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        Metal.scrape((ServerLevel) player.level(), player.position(), 0.6F, 1.4F);
    }
}
