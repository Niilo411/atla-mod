package io.github.niilo411.elementalpaths.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client. Says whether a given player is wearing a given channeling armor suit.
 *
 * Needed because mob effects are NOT synced to onlookers: vanilla sends a player's
 * effects only to that player, so without this a stone or steel suit would be visible
 * to nobody except the channeler wearing it.
 *
 * The entity id is sent rather than a UUID because the client renderer has the entity
 * in hand and looks it up by id. The suit is sent as its ordinal, which is why
 * ChannelingArmorSuit's constants must only ever be APPENDED to — reordering them would
 * silently repaint everyone mid-session on a mismatched client.
 */
public record ChannelingArmorPacket(int entityId, int suit, boolean active) implements CustomPacketPayload {

    public static final Type<ChannelingArmorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("elemental_paths", "channeling_armor"));

    public static final StreamCodec<ByteBuf, ChannelingArmorPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChannelingArmorPacket::entityId,
            ByteBufCodecs.VAR_INT, ChannelingArmorPacket::suit,
            ByteBufCodecs.BOOL, ChannelingArmorPacket::active,
            ChannelingArmorPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
