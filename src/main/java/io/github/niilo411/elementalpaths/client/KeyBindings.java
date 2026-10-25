package io.github.niilo411.elementalpaths;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "elemental_paths", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final KeyMapping MEDITATE = new KeyMapping(
            "key.elemental_paths.meditate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.elemental_paths"
    );
    public static final KeyMapping ABILITY_1 = new KeyMapping("key.elemental_paths.ability1", GLFW.GLFW_KEY_Z, "key.categories.elemental_paths");
    public static final KeyMapping ABILITY_2 = new KeyMapping("key.elemental_paths.ability2", GLFW.GLFW_KEY_X, "key.categories.elemental_paths");
    public static final KeyMapping ABILITY_3 = new KeyMapping("key.elemental_paths.ability3", GLFW.GLFW_KEY_C, "key.categories.elemental_paths");
    public static final KeyMapping ABILITY_4 = new KeyMapping("key.elemental_paths.ability4", GLFW.GLFW_KEY_V, "key.categories.elemental_paths");

    public static final KeyMapping UPGRADE_MENU = new KeyMapping(
            "key.elemental_paths.upgrade_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.elemental_paths"
    );

    public static final KeyMapping SWITCH_ELEMENT = new KeyMapping(
            "key.elemental_paths.switch_element",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.elemental_paths"
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