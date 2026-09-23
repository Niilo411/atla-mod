package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * An operator on a dedicated server changing the mod's settings from their settings
 * screen. Carries every value, keyed by its TOML path — see SettingsAccess.snapshot.
 *
 * Every value rather than only the one that moved, so the server's file ends up exactly
 * what the screen shows however many changes arrive, in whatever order. The server checks
 * the sender and validates every value before writing anything.
 */
public record UpdateSettingsPacket(CompoundTag values) implements CustomPacketPayload {

    public static final Type<UpdateSettingsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "update_settings"));

    public static final StreamCodec<ByteBuf, UpdateSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, UpdateSettingsPacket::values,
            UpdateSettingsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
