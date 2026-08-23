package com.minecraft.atlamod.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import io.netty.buffer.ByteBuf;

public record SyncStatsPacket(int xp, int level, int currentChi) implements CustomPacketPayload {
    public static final Type<SyncStatsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "sync_stats"));

    public static final StreamCodec<ByteBuf, SyncStatsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncStatsPacket::xp,
            ByteBufCodecs.INT, SyncStatsPacket::level,
            ByteBufCodecs.INT, SyncStatsPacket::currentChi,
            SyncStatsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
