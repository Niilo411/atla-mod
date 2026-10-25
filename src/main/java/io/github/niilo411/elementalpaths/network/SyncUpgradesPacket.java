package io.github.niilo411.elementalpaths.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client. Which ability upgrades the player has bought.
 *
 * Its own packet rather than another field on SyncChannelingDataPacket, which is
 * already at the six fields StreamCodec.composite allows.
 */
public record SyncUpgradesPacket(List<String> unlockedUpgrades) implements CustomPacketPayload {

    public static final Type<SyncUpgradesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("elemental_paths", "sync_upgrades"));

    public static final StreamCodec<ByteBuf, SyncUpgradesPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncUpgradesPacket::unlockedUpgrades,
            SyncUpgradesPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
