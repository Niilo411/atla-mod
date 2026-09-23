package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The settings file, sent to everyone connected after it changes.
 *
 * NeoForge's own ConfigFilePayload does exactly this, but only during the CONFIGURATION
 * phase — while a player is joining — and cannot be sent to somebody already playing.
 * This is the same bytes in a play-phase packet. See SettingsAccess.broadcast.
 */
public record SettingsFilePacket(String fileName, byte[] contents) implements CustomPacketPayload {

    public static final Type<SettingsFilePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "settings_file"));

    public static final StreamCodec<ByteBuf, SettingsFilePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SettingsFilePacket::fileName,
            ByteBufCodecs.BYTE_ARRAY, SettingsFilePacket::contents,
            SettingsFilePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
