package com.minecraft.atlamod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "atlamod", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final KeyMapping MEDITATE = new KeyMapping(
            "key.atlamod.meditate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.atlamod"
    );
    public static final KeyMapping ABILITY_1 = new KeyMapping("key.atlamod.ability1", GLFW.GLFW_KEY_Z, "key.categories.atlamod");
    public static final KeyMapping ABILITY_2 = new KeyMapping("key.atlamod.ability2", GLFW.GLFW_KEY_X, "key.categories.atlamod");
    public static final KeyMapping ABILITY_3 = new KeyMapping("key.atlamod.ability3", GLFW.GLFW_KEY_C, "key.categories.atlamod");
    public static final KeyMapping ABILITY_4 = new KeyMapping("key.atlamod.ability4", GLFW.GLFW_KEY_V, "key.categories.atlamod");

    public static final KeyMapping UPGRADE_MENU = new KeyMapping(
            "key.atlamod.upgrade_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.atlamod"
    );

    public static final KeyMapping SWITCH_ELEMENT = new KeyMapping(
            "key.atlamod.switch_element",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.atlamod"
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MEDITATE);
        event.register(ABILITY_1);
        event.register(ABILITY_2);
        event.register(ABILITY_3);
        event.register(ABILITY_4);
        event.register(UPGRADE_MENU);
        event.register(SWITCH_ELEMENT);
    }
}