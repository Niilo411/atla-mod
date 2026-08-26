package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Whites out the receiving player's screen for a moment. Lightning stun sends one
 * to its victim as the bolt lands.
 *
 * A packet because a flash has no server-side existence at all -- the same reason
 * EarthquakePacket exists for the camera shake. The server can only ask; the client
 * counts it down and draws it.
 */
public record ScreenFlashPacket(int ticks) implements CustomPacketPayload {

    public static final Type<ScreenFlashPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "screen_flash"));

    public static final StreamCodec<ByteBuf, ScreenFlashPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ScreenFlashPacket::ticks,
            ScreenFlashPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
