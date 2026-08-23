package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UnlockAbilityPacket(String abilityName, int cost) implements CustomPacketPayload {
    public static final Type<UnlockAbilityPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "unlock_ability"));

    public static final StreamCodec<FriendlyByteBuf, UnlockAbilityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            UnlockAbilityPacket::abilityName,
            ByteBufCodecs.VAR_INT,
            UnlockAbilityPacket::cost,
            UnlockAbilityPacket::new
    );

    @Override
    public Type<? extends UnlockAbilityPacket> type() {
        return TYPE;
    }

    public static void handle(UnlockAbilityPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BendingData data = player.getData(ModAttachments.BENDING_DATA);

                // Check if player has enough level/XP and doesn't already have it
                if (data.getLevel() >= payload.cost() && !data.getUnlockedAbilities().contains(payload.abilityName())) {

                    // --- SUBTRACT THE COST FROM THEIR LEVEL ---
                    data.setLevel(data.getLevel() - payload.cost());

                    data.unlockAbility(payload.abilityName());
                    player.setData(ModAttachments.BENDING_DATA, data);

                    // Sync updated data back to client INCLUDING EQUIPPED ABILITIES so they don't reset!
                    PacketDistributor.sendToPlayer(player, new SyncBendingDataPacket(
                            data.getMainElement(),
                            data.getActiveElement(),
                            data.getUnlockedElements(),
                            data.hasChosenElement(),
                            data.getUnlockedAbilities(),
                            data.getEquippedAbilities()
                    ));

                    PacketDistributor.sendToPlayer(player, new SyncStatsPacket(
                            data.getXp(),
                            data.getLevel(),
                            data.getCurrentChi()
                    ));
                }
            }
        });
    }
}