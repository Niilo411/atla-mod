package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;

public record UseAbilityPacket(int slot) implements CustomPacketPayload {
    public static final Type<UseAbilityPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "use_ability"));

    public static final StreamCodec<FriendlyByteBuf, UseAbilityPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, UseAbilityPacket::slot,
            UseAbilityPacket::new
    );

    @Override
    public Type<? extends UseAbilityPacket> type() {
        return TYPE;
    }

    public static void handle(UseAbilityPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BendingData data = player.getData(ModAttachments.BENDING_DATA);
                String ability = data.getEquippedAbility(payload.slot());

                // Send the ability straight to the brain!
                com.minecraft.atlamod.AbilityHandler.executeAbility(player, data, ability);
            }
        });
    }
}