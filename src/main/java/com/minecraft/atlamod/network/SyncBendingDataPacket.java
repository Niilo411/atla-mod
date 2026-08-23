package com.minecraft.atlamod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public record SyncBendingDataPacket(
        String mainElement,
        String activeElement,
        List<String> unlockedElements,
        boolean hasChosen,
        List<String> unlockedAbilities,
        List<String> equippedAbilities
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncBendingDataPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("atlamod", "sync_bending_data"));

    // Custom manual encoder/decoder to guarantee absolutely no data is ever dropped by the network
    public static final StreamCodec<FriendlyByteBuf, SyncBendingDataPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.mainElement() == null ? "" : packet.mainElement());
                buf.writeUtf(packet.activeElement() == null ? "" : packet.activeElement());
                buf.writeCollection(packet.unlockedElements(), FriendlyByteBuf::writeUtf);
                buf.writeBoolean(packet.hasChosen());
                buf.writeCollection(packet.unlockedAbilities(), FriendlyByteBuf::writeUtf);

                // BRUTE FORCE: Transmit exactly 8 slots individually, bypassing all list truncating bugs
                for (int i = 0; i < 8; i++) {
                    if (packet.equippedAbilities() != null && packet.equippedAbilities().size() > i) {
                        String ability = packet.equippedAbilities().get(i);
                        buf.writeUtf(ability == null ? "" : ability);
                    } else {
                        buf.writeUtf(""); // Force empty slots to transmit!
                    }
                }
            },
            buf -> {
                String main = buf.readUtf();
                String active = buf.readUtf();
                List<String> unlE = buf.readList(FriendlyByteBuf::readUtf);
                boolean chosen = buf.readBoolean();
                List<String> unlA = buf.readList(FriendlyByteBuf::readUtf);

                // BRUTE FORCE: Read exactly 8 slots directly from the bytes
                List<String> equipA = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    equipA.add(buf.readUtf());
                }

                return new SyncBendingDataPacket(main, active, unlE, chosen, unlA, equipA);
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends SyncBendingDataPacket> type() {
        return TYPE;
    }
}
