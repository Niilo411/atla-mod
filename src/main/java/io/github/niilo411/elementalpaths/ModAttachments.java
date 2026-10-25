package io.github.niilo411.elementalpaths;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "elemental_paths");

    public static final Supplier<AttachmentType<ChannelingData>> CHANNELING_DATA = ATTACHMENT_TYPES.register(
            "channeling_data",
            () -> AttachmentType.builder(() -> new ChannelingData())
                    .serialize(ChannelingData.CODEC) // Saves to hard drive
                    .copyOnDeath()                // <--- ADDS BULLETPROOF DEATH SAVING
                    .build()
    );

    /**
     * The Paragon cycle. Held by the OVERWORLD, not by a player — whose turn it is
     * and who currently holds the title are facts about the world, and have to
     * outlive any one player being logged in.
     *
     * No copyOnDeath here: levels do not die.
     */
    public static final Supplier<AttachmentType<io.github.niilo411.elementalpaths.paragon.ParagonState>> PARAGON_STATE =
            ATTACHMENT_TYPES.register(
                    "paragon_state",
                    () -> AttachmentType.builder(() -> new io.github.niilo411.elementalpaths.paragon.ParagonState())
                            .serialize(io.github.niilo411.elementalpaths.paragon.ParagonState.CODEC)
                            .build()
            );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}

