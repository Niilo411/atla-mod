package com.minecraft.atlamod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EquipAbilityPacket(int slot, String abilityName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EquipAbilityPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "equip_ability"));

    public static final StreamCodec<FriendlyByteBuf, EquipAbilityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            EquipAbilityPacket::slot,
            ByteBufCodecs.STRING_UTF8,
            EquipAbilityPacket::abilityName,
            EquipAbilityPacket::new
    );

    @Override
    public CustomPacketPayload.Type<EquipAbilityPacket> type() {
        return TYPE;
    }

    public static void handle(EquipAbilityPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);

                // Update the slot with the new ability (or clear it if empty string)
                data.setEquippedAbility(payload.slot(), payload.abilityName());

                // Save changes to the world file
                player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);
            }
        });
    }
}