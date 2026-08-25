package com.minecraft.atlamod.client;

import com.minecraft.atlamod.KeyBindings;
import com.minecraft.atlamod.network.MeditatePacket;
import com.minecraft.atlamod.network.UseAbilityPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid = "atlamod", value = Dist.CLIENT)
public class ClientEvents {

    // Our flag that the Server can now trigger!
    public static boolean needsToOpenMenu = false;

    private static boolean wasLeftClicking = false;
    private static final boolean[] lastAbilityHeld = new boolean[4];
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // 1. Safely open the menu if the server flagged it AND no other screen is open
        if (needsToOpenMenu && mc.screen == null && mc.level != null && mc.player != null) {
            mc.setScreen(new ElementSelectionScreen());
            needsToOpenMenu = false;
        }

        // Only run player-specific checks if the player is actively in a world AND no menus/loading screens are open!
        if (mc.player != null && mc.level != null && mc.screen == null) {

            // --- UNIVERSAL LEFT CLICK DETECTOR ---
            boolean isLeftClicking = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (isLeftClicking && !wasLeftClicking) {
                // We just clicked in the game world! Tell the server!
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.minecraft.atlamod.network.LeftClickTriggerPacket());
            }
            wasLeftClicking = isLeftClicking; // Remember for next tick

            // 4. Check Meditation Hold (M key)
            boolean isMeditateKeyDown = KeyBindings.MEDITATE.isDown();
            PacketDistributor.sendToServer(new MeditatePacket(isMeditateKeyDown));
            boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();

            while (KeyBindings.ABILITY_1.consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 4 : 0));
            }
            while (KeyBindings.ABILITY_2.consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 5 : 1));
            }
            while (KeyBindings.ABILITY_3.consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 6 : 2));
            }
            while (KeyBindings.ABILITY_4.consumeClick()) {
                PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 7 : 3));
            }
            // --- CHANNELED ABILITY HOLD DETECTION (Fire Breath, future held abilities) ---
            net.minecraft.client.KeyMapping[] abilityKeys = { KeyBindings.ABILITY_1, KeyBindings.ABILITY_2, KeyBindings.ABILITY_3, KeyBindings.ABILITY_4 };
            for (int i = 0; i < 4; i++) {
                boolean held = abilityKeys[i].isDown();
                if (held != lastAbilityHeld[i]) {
                    int slot = isShiftDown ? i + 4 : i;
                    PacketDistributor.sendToServer(new com.minecraft.atlamod.network.AbilityHoldPacket(slot, held));
                    lastAbilityHeld[i] = held;
                }
            }
        }
        if (mc.player != null && mc.level != null) {

            // 4. Check Meditation Hold (M key)
            if (mc.player != null && mc.level != null) {
                boolean isMeditateKeyDown = KeyBindings.MEDITATE.isDown();
                PacketDistributor.sendToServer(new MeditatePacket(isMeditateKeyDown));
                boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();

                while (KeyBindings.ABILITY_1.consumeClick()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.minecraft.atlamod.network.UseAbilityPacket(isShiftDown ? 4 : 0));
                }
                while (KeyBindings.ABILITY_2.consumeClick()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.minecraft.atlamod.network.UseAbilityPacket(isShiftDown ? 5 : 1));
                }
                while (KeyBindings.ABILITY_3.consumeClick()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.minecraft.atlamod.network.UseAbilityPacket(isShiftDown ? 6 : 2));
                }
                while (KeyBindings.ABILITY_4.consumeClick()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.minecraft.atlamod.network.UseAbilityPacket(isShiftDown ? 7 : 3));
                }

                var data = mc.player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                if (data.isMeditating()) {
                    // Completely freeze client inputs and velocity
                    mc.player.input.forwardImpulse = 0.0f;
                    mc.player.input.leftImpulse = 0.0f;
                    mc.player.input.jumping = false;
                    mc.player.input.shiftKeyDown = true;

                    // Stop local velocity so you don't slide
                    mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
                }

                // Abilities that hold you in place (the shields). The server pins the
                // player too; this stops the client trying to walk and being corrected
                // every tick, which would rubber-band rather than hold still.
                if (ClientRootState.isRooted()) {
                    mc.player.input.forwardImpulse = 0.0f;
                    mc.player.input.leftImpulse = 0.0f;
                    mc.player.input.jumping = false;
                    mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
                }
            }
            // 3. Check for 'N' key presses (Upgrade Menu)
            while (KeyBindings.UPGRADE_MENU.consumeClick()) {
                mc.setScreen(new UpgradeMenuScreen());
            }

            // 4. Check for 'Y' key presses (Switch Element)
            while (KeyBindings.SWITCH_ELEMENT.consumeClick()) {
                var player = mc.player;
                var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                var unlocked = data.getUnlockedElements();

                if (unlocked.size() > 1) {
                    int currentIndex = unlocked.indexOf(data.getActiveElement());
                    int nextIndex = (currentIndex + 1) % unlocked.size();
                    String nextElement = unlocked.get(nextIndex);

                    data.setActiveElement(nextElement);
                    PacketDistributor.sendToServer(new com.minecraft.atlamod.network.SwitchElementPacket(nextElement));
                }
                boolean isShiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();

                if (KeyBindings.ABILITY_1.consumeClick()) {
                    PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 4 : 0));
                }
                if (KeyBindings.ABILITY_2.consumeClick()) {
                    PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 5 : 1));
                }
                if (KeyBindings.ABILITY_3.consumeClick()) {
                    PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 6 : 2));
                }
                if (KeyBindings.ABILITY_4.consumeClick()) {
                    PacketDistributor.sendToServer(new UseAbilityPacket(isShiftDown ? 7 : 3));
                }

                    }
                }
            }

    /**
     * Disorientation (Air pull): the victim's movement keys are reversed.
     *
     * Movement input exists ONLY on the client — the server sees the resulting motion,
     * not which key produced it — so this is the one place the reversal can happen.
     * The effect itself is held on the entity server-side and vanilla syncs it to the
     * owning player's client, which is why simply asking hasEffect() here is enough
     * and no packet of our own is needed.
     *
     * Both the impulses and the direction booleans are flipped. The impulses are what
     * actually move the player; the booleans are what vanilla reads for things like
     * "is this player trying to sprint forward", so leaving them alone would give a
     * player who walks backwards but still sprints in the direction they pressed.
     */
    @SubscribeEvent
    public static void onMovementInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(com.minecraft.atlamod.ModEffects.DISORIENTATION)) return;

        net.minecraft.client.player.Input input = event.getInput();

        input.forwardImpulse = -input.forwardImpulse;
        input.leftImpulse = -input.leftImpulse;

        boolean up = input.up;
        boolean left = input.left;
        input.up = input.down;
        input.down = up;
        input.left = input.right;
        input.right = left;
    }
    }