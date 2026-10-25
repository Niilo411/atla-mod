package io.github.niilo411.elementalpaths.abilities;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.ModAttachments;
import io.github.niilo411.elementalpaths.network.SyncStatsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared bookkeeping that used to live as private statics on AbilityHandler.
 * Public so ability classes can sync mid-effect when they need to.
 */
public final class AbilitySupport {

    /** XP needed to gain a level. A setting; 200 by default, and never below 1. */
    public static int xpPerLevel() {
        return io.github.niilo411.elementalpaths.PathsConfig.xpPerLevel();
    }

    private AbilitySupport() {
    }

    /** Spends chi and grants XP. Returns false (and warns the player) if they can't afford it. */
    public static boolean consumeChiAndGiveXp(ServerPlayer player, ChannelingData data, int chiCost, int xpReward) {
        if (data.getCurrentChi() < chiCost) {
            player.displayClientMessage(
                    Component.literal("§cNot enough Chi! (Requires " + chiCost + ")"), true);
            return false;
        }

        data.consumeChi(chiCost);
        grantXp(data, xpReward);
        return true;
    }

    /**
     * Adds XP and rolls over into a level whenever the threshold is crossed.
     *
     * A LOOP THAT CARRIES THE REMAINDER, where this used to be a single {@code if} that
     * set the XP back to zero. Two things were wrong with that and the threshold becoming
     * a setting made both matter. A grant larger than the threshold only ever gave ONE
     * level, so a non-channeler killing a wither for 225 would gain one level and lose the
     * rest — and at a low configured threshold a single ore block could be worth several
     * levels and pay one. And levelling at 195 with a 15 XP grant threw away the 10 that
     * should have carried, which is a small loss but a steady one.
     */
    public static void grantXp(ChannelingData data, int amount) {
        if (amount <= 0) return;

        int perLevel = xpPerLevel();
        data.setXp(data.getXp() + amount);

        while (data.getXp() >= perLevel) {
            data.setLevel(data.getLevel() + 1);
            data.setXp(data.getXp() - perLevel);
        }
    }

    /** Persists the attachment and pushes the stat bar back to the client. */
    public static void syncData(ServerPlayer player, ChannelingData data) {
        player.setData(ModAttachments.CHANNELING_DATA, data);
        PacketDistributor.sendToPlayer(player,
                SyncStatsPacket.of(data));
    }
}
