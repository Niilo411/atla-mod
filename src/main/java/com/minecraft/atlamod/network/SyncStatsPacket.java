package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import io.netty.buffer.ByteBuf;

/**
 * The numbers the HUD draws: the level, the xp, and both halves of the chi gauge.
 *
 * {@code bonusMaxChi} is what the spirit shrines have added. It is sent rather than the
 * finished maximum so the client keeps the same fields the server does and works the
 * maximum out with the same method — and it doubles as the chi bar's COLOUR, since the
 * number of shrines drawn from is read back out of it. See {@link BendingData#getShrinesUsed}.
 *
 * Built through {@link #of} everywhere rather than by hand. There are a dozen places that
 * send it, and a field added to this record used to mean a dozen edits and a dozen chances
 * to send a stale figure.
 */
public record SyncStatsPacket(int xp, int level, int currentChi, int bonusMaxChi) implements CustomPacketPayload {
    public static final Type<SyncStatsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "sync_stats"));

    public static final StreamCodec<ByteBuf, SyncStatsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncStatsPacket::xp,
            ByteBufCodecs.INT, SyncStatsPacket::level,
            ByteBufCodecs.INT, SyncStatsPacket::currentChi,
            ByteBufCodecs.INT, SyncStatsPacket::bonusMaxChi,
            SyncStatsPacket::new
    );

    /** Everything this packet carries, taken straight off a player's data. */
    public static SyncStatsPacket of(BendingData data) {
        return new SyncStatsPacket(data.getXp(), data.getLevel(),
                data.getCurrentChi(), data.getBonusMaxChi());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
