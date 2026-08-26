package com.minecraft.atlamod;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "atlamod");

    public static final Supplier<AttachmentType<BendingData>> BENDING_DATA = ATTACHMENT_TYPES.register(
            "bending_data",
            () -> AttachmentType.builder(() -> new BendingData())
                    .serialize(BendingData.CODEC) // Saves to hard drive
                    .copyOnDeath()                // <--- ADDS BULLETPROOF DEATH SAVING
                    .build()
    );

    /**
     * The Avatar cycle. Held by the OVERWORLD, not by a player — whose turn it is
     * and who currently holds the title are facts about the world, and have to
     * outlive any one player being logged in.
     *
     * No copyOnDeath here: levels do not die.
     */
    public static final Supplier<AttachmentType<com.minecraft.atlamod.avatar.AvatarState>> AVATAR_STATE =
            ATTACHMENT_TYPES.register(
                    "avatar_state",
                    () -> AttachmentType.builder(() -> new com.minecraft.atlamod.avatar.AvatarState())
                            .serialize(com.minecraft.atlamod.avatar.AvatarState.CODEC)
                            .build()
            );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}

