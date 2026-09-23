package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a client whether the server it has joined is a DEDICATED one.
 *
 * The settings screen needs to know, and a client cannot work it out for itself: a LAN
 * world and a world hosted through the Essential mod both look like "a remote server"
 * from a guest's side, and on those only the host may edit, where on a dedicated server
 * any operator may. The permission LEVEL is not sent here — vanilla already keeps the
 * client's copy of that current, including an op or deop mid-session.
 */
public record ServerKindPacket(boolean dedicated) implements CustomPacketPayload {

    public static final Type<ServerKindPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "server_kind"));

    public static final StreamCodec<ByteBuf, ServerKindPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ServerKindPacket::dedicated,
            ServerKindPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
