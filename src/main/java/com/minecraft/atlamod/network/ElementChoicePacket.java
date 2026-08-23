package com.minecraft.atlamod.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import io.netty.buffer.ByteBuf;

public record ElementChoicePacket(String element) implements CustomPacketPayload {
    public static final Type<ElementChoicePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "element_choice"));

    // This encodes and decodes the string data for transmission
    public static final StreamCodec<ByteBuf, ElementChoicePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ElementChoicePacket::element,
            ElementChoicePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
