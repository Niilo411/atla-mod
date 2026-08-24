package com.minecraft.atlamod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client. Drives the charge meter at the top of the screen.
 *
 * @param ability display name, blank when nothing is charging or armed
 * @param held    ticks charged so far
 * @param total   ticks needed for a full charge
 * @param armed   true once charged and waiting on the left click (Fireball)
 */
public record ChargeStatusPacket(String ability, int held, int total, boolean armed)
        implements CustomPacketPayload {

    public static final Type<ChargeStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "charge_status"));

    public static final StreamCodec<ByteBuf, ChargeStatusPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ChargeStatusPacket::ability,
            ByteBufCodecs.INT, ChargeStatusPacket::held,
            ByteBufCodecs.INT, ChargeStatusPacket::total,
            ByteBufCodecs.BOOL, ChargeStatusPacket::armed,
            ChargeStatusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
