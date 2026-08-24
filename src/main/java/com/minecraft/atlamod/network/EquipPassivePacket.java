package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server. Puts a passive into one of the four slots, or clears it. */
public record EquipPassivePacket(int slot, String passive) implements CustomPacketPayload {

    public static final Type<EquipPassivePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "equip_passive"));

    public static final StreamCodec<ByteBuf, EquipPassivePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EquipPassivePacket::slot,
            ByteBufCodecs.STRING_UTF8, EquipPassivePacket::passive,
            EquipPassivePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EquipPassivePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // Only something the player has actually unlocked may be slotted.
            String passive = payload.passive();
            if (passive != null && !passive.isEmpty()
                    && !data.getUnlockedAbilities().contains(passive)) {
                return;
            }

            data.setEquippedPassive(payload.slot(), passive);
            player.setData(ModAttachments.BENDING_DATA, data);
        });
    }
}
