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

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}

