package io.github.niilo411.elementalpaths.network;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Sent whenever a client's hold-state for an ability slot CHANGES (key down or key up).
// Used for channeled abilities like Fire Breath that run continuously while held.
public record AbilityHoldPacket(int slot, boolean isHeld) implements CustomPacketPayload {
    public static final Type<AbilityHoldPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("elemental_paths", "ability_hold"));

    public static final StreamCodec<FriendlyByteBuf, AbilityHoldPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityHoldPacket::slot,
            ByteBufCodecs.BOOL, AbilityHoldPacket::isHeld,
            AbilityHoldPacket::new
    );

    @Override
    public Type<? extends AbilityHoldPacket> type() {
        return TYPE;
    }

    public static void handle(AbilityHoldPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
                String ability = data.getEquippedAbility(payload.slot());
                io.github.niilo411.elementalpaths.AbilityHandler.executeAbilityHold(player, data, ability, payload.isHeld());
            }
        });
    }
}
