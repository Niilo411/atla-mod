package io.github.niilo411.elementalpaths.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a player that they are (or are no longer) the Paragon, and how many lives
 * they have left. The HUD's counter is the only thing that reads it.
 *
 * A packet of its own rather than another field on SyncChannelingDataPacket, which is
 * already at the six fields StreamCodec.composite takes -- the same reason the
 * passives and upgrades have their own.
 */
public record SyncParagonPacket(boolean paragon, int lives) implements CustomPacketPayload {

    public static final Type<SyncParagonPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("elemental_paths", "sync_paragon"));

    public static final StreamCodec<ByteBuf, SyncParagonPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncParagonPacket::paragon,
            ByteBufCodecs.INT, SyncParagonPacket::lives,
            SyncParagonPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
