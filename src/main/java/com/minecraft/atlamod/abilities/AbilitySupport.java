package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.network.SyncStatsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared bookkeeping that used to live as private statics on AbilityHandler.
 * Public so ability classes can sync mid-effect when they need to.
 */
public final class AbilitySupport {

    /** XP needed to gain a level. */
    public static final int XP_PER_LEVEL = 200;

    private AbilitySupport() {
    }

    /** Spends chi and grants XP. Returns false (and warns the player) if they can't afford it. */
    public static boolean consumeChiAndGiveXp(ServerPlayer player, BendingData data, int chiCost, int xpReward) {
        if (data.getCurrentChi() < chiCost) {
            player.displayClientMessage(
                    Component.literal("§cNot enough Chi! (Requires " + chiCost + ")"), true);
            return false;
        }

        data.consumeChi(chiCost);
        grantXp(data, xpReward);
        return true;
    }

    /** Adds XP and rolls over into a level when the threshold is crossed. */
    public static void grantXp(BendingData data, int amount) {
        if (amount <= 0) return;
        data.setXp(data.getXp() + amount);
        if (data.getXp() >= XP_PER_LEVEL) {
            data.setLevel(data.getLevel() + 1);
            data.setXp(0);
        }
    }

    /** Persists the attachment and pushes the stat bar back to the client. */
    public static void syncData(ServerPlayer player, BendingData data) {
        player.setData(ModAttachments.BENDING_DATA, data);
        PacketDistributor.sendToPlayer(player,
                new SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
    }
}
