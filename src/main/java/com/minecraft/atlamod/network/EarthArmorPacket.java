package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client. Says whether a given player is wearing Earth armor.
 *
 * Needed because mob effects are NOT synced to onlookers: vanilla sends a player's
 * effects only to that player, so without this the stone suit would be visible to
 * nobody except the bender wearing it. The entity id is sent rather than a UUID
 * because the client renderer has the entity in hand and looks it up by id.
 */
public record EarthArmorPacket(int entityId, boolean active) implements CustomPacketPayload {

    public static final Type<EarthArmorPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "earth_armor"));

    public static final StreamCodec<ByteBuf, EarthArmorPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EarthArmorPacket::entityId,
            ByteBufCodecs.BOOL, EarthArmorPacket::active,
            EarthArmorPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
