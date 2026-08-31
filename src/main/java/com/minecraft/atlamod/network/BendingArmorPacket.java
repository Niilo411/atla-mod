package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client. Says whether a given player is wearing a given bending armor suit.
 *
 * Needed because mob effects are NOT synced to onlookers: vanilla sends a player's
 * effects only to that player, so without this a stone or steel suit would be visible
 * to nobody except the bender wearing it.
 *
 * The entity id is sent rather than a UUID because the client renderer has the entity
 * in hand and looks it up by id. The suit is sent as its ordinal, which is why
 * BendingArmorSuit's constants must only ever be APPENDED to — reordering them would
 * silently repaint everyone mid-session on a mismatched client.
 */
public record BendingArmorPacket(int entityId, int suit, boolean active) implements CustomPacketPayload {

    public static final Type<BendingArmorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "bending_armor"));

    public static final StreamCodec<ByteBuf, BendingArmorPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BendingArmorPacket::entityId,
            ByteBufCodecs.VAR_INT, BendingArmorPacket::suit,
            ByteBufCodecs.BOOL, BendingArmorPacket::active,
            BendingArmorPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
