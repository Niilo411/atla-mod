package io.github.niilo411.elementalpaths.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Carries the player's blood channeling level and xp to their client.
 *
 * A packet of its own, like the passives, upgrades and Paragon ones, because
 * SyncChannelingDataPacket is already at the six fields StreamCodec.composite takes. The
 * HUD's blood level is the only thing that reads it.
 */
public record SyncBloodPacket(int bloodXp, int bloodLevel) implements CustomPacketPayload {

    public static final Type<SyncBloodPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("elemental_paths", "sync_blood"));

    public static final StreamCodec<ByteBuf, SyncBloodPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncBloodPacket::bloodXp,
            ByteBufCodecs.INT, SyncBloodPacket::bloodLevel,
            SyncBloodPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
