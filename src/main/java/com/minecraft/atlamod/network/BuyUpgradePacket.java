package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server. Buys one ability upgrade.
 *
 * The server re-checks everything the menu already checked. The client is only
 * asking; it is not to be trusted about whether the ability is unlocked, whether
 * the upgrade exists, or whether the levels are there to spend.
 */
public record BuyUpgradePacket(String abilityName, String upgradeKey) implements CustomPacketPayload {

    public static final Type<BuyUpgradePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "buy_upgrade"));

    public static final StreamCodec<ByteBuf, BuyUpgradePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BuyUpgradePacket::abilityName,
            ByteBufCodecs.STRING_UTF8, BuyUpgradePacket::upgradeKey,
            BuyUpgradePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BuyUpgradePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Ability ability = AbilityRegistry.get(payload.abilityName());
            if (ability == null) return;

            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // The upgrade must belong to an ability the player actually owns.
            if (!data.getUnlockedAbilities().contains(payload.abilityName())) return;
            if (data.hasUpgrade(payload.upgradeKey())) return;

            AbilityUpgrade upgrade = null;
            for (AbilityUpgrade candidate : ability.getUpgrades()) {
                if (candidate.key().equals(payload.upgradeKey())) {
                    upgrade = candidate;
                    break;
                }
            }
            if (upgrade == null) return;

            // Upgrades can stand behind one another. Checked here and not only in the
            // menu: the client is asking, not deciding.
            if (upgrade.requires() != null && !data.hasUpgrade(upgrade.requires())) return;

            if (data.getLevel() < upgrade.cost()) return;

            data.setLevel(data.getLevel() - upgrade.cost());
            data.unlockUpgrade(upgrade.key());
            player.setData(ModAttachments.BENDING_DATA, data);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new SyncUpgradesPacket(data.getUnlockedUpgrades()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
        });
    }
}
