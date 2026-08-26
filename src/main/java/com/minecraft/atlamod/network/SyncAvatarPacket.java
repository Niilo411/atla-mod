package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a player that they are (or are no longer) the Avatar, and how many lives
 * they have left. The HUD's counter is the only thing that reads it.
 *
 * A packet of its own rather than another field on SyncBendingDataPacket, which is
 * already at the six fields StreamCodec.composite takes -- the same reason the
 * passives and upgrades have their own.
 */
public record SyncAvatarPacket(boolean avatar, int lives) implements CustomPacketPayload {

    public static final Type<SyncAvatarPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "sync_avatar"));

    public static final StreamCodec<ByteBuf, SyncAvatarPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncAvatarPacket::avatar,
            ByteBufCodecs.INT, SyncAvatarPacket::lives,
            SyncAvatarPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
