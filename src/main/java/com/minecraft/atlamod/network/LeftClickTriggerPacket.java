package com.minecraft.atlamod.network;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.server.level.ServerPlayer;

public record LeftClickTriggerPacket() implements CustomPacketPayload {
    public static final Type<LeftClickTriggerPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "left_click_trigger"));
    public static final StreamCodec<FriendlyByteBuf, LeftClickTriggerPacket> STREAM_CODEC = StreamCodec.unit(new LeftClickTriggerPacket());

    @Override
    public Type<? extends LeftClickTriggerPacket> type() { return TYPE; }

    public static void handle(LeftClickTriggerPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BendingData data = player.getData(ModAttachments.BENDING_DATA);

                // The Avatar's way home: meditate five seconds in the Spirit World, then
                // punch the air. Checked FIRST and it CONSUMES the click, because the same
                // press would otherwise also throw whatever happened to be armed — and
                // somebody sitting in meditation asking for a portal did not mean to loose
                // a fireball at the same moment.
                if (com.minecraft.atlamod.spirit.SpiritPortals.tryReturn(player, data)) return;

                // Compressed punches throws a wave on EVERY click, whether or not
                // anything is armed — it is a ranged attack in its own right, not a
                // bonus on a two-phase release. Fired before the two-phase routing so
                // holding something armed does not swallow the punch.
                if (data.isPunchingCompressed()) {
                    com.minecraft.atlamod.abilities.sound.CompressedPunches.punch(player, data);
                }

                // Route it to the master handler!
                com.minecraft.atlamod.AbilityHandler.executeLeftClickPhase(player, data);
            }
        });
    }
}
