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

            // Only something the player has actually unlocked may be slotted, and only
            // something this world's settings still allow. Both let an EMPTY name past,
            // since that is the player clearing the slot — and a slot holding a passive
            // that was disabled after it went in has to be emptiable.
            String passive = payload.passive();
            if (passive != null && !passive.isEmpty()
                    && !data.getUnlockedAbilities().contains(passive)) {
                return;
            }
            if (!com.minecraft.atlamod.AtlaConfig.abilityEnabled(passive)) return;

            data.setEquippedPassive(payload.slot(), passive);
            player.setData(ModAttachments.BENDING_DATA, data);
        });
    }
}
