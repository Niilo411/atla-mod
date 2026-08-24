package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client. Tells the client to stop accepting movement input, for abilities
 * that hold the player in place.
 *
 * Needed because the rooting abilities live in transient server-side state the client
 * never sees. Without it the client keeps walking and the server keeps yanking it
 * back, which rubber-bands instead of holding still.
 */
public record RootedPacket(boolean rooted) implements CustomPacketPayload {

    public static final Type<RootedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "rooted"));

    public static final StreamCodec<ByteBuf, RootedPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RootedPacket::rooted,
            RootedPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
