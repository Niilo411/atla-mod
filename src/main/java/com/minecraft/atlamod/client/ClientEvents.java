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

    /** What the server was last told about the meditate key, and when. */
    private static boolean lastMeditateSent = false;
    private static int meditateResendTicks = 0;

    /**
     * Tells the server about the meditate key — but only when there is something to say.
     *
     * THIS USED TO SEND A PACKET EVERY TICK, for every player, whether or not anybody was
     * touching the key: twenty packets a second per player doing nothing but repeating
     * "not meditating". It now sends on a CHANGE, the same shape the ability hold keys
     * already use, plus a refresh once a second while the key is actually held. The
     * refresh is there because the server can clear its own copy without being asked — a
     * respawn or a dimension change builds a fresh BendingData with meditating false —
     * and a player still holding the key should pick back up within a second rather than
     * having to let go and press it again.
     */
    private static void syncMeditate() {
        boolean down = KeyBindings.MEDITATE.isDown();

        if (down != lastMeditateSent || (down && --meditateResendTicks <= 0)) {
            PacketDistributor.sendToServer(new MeditatePacket(down));
            lastMeditateSent = down;
            meditateResendTicks = 20;
        }
    }

    /**
     * The four ability KeyMappings, in slot order.
     *
     * KeyBindings' fields are static and never reassigned, so this array is invariant
     * — it does not need to be rebuilt from scratch every client tick the way it was.
     */
    private static final net.minecraft.client.KeyMapping[] ABILITY_KEYS = {
            KeyBindings.ABILITY_1, KeyBindings.ABILITY_2, KeyBindings.ABILITY_3, KeyBindings.ABILITY_4
    };

    /**
     * Holds the local player flat while they are on a ride that lies down — Water
     * Surf, and nothing else so far.
     *
     * The rider's own client is the one copy of them the server cannot settle. The
     * server's forced pose reaches everybody watching, and the synched swimming flag
     * reaches their RemotePlayer copies, but the LOCAL player recomputes its own pose
     * from scratch every tick in Player#updatePlayerPose and clears the swimming flag
     * in updateSwimming on the way — so from the inside the bender stood bolt upright
     * while everyone else saw them surfing.
     *
     * NeoForge's forced pose is the hook that ends that argument: updatePlayerPose
     * returns it immediately and looks at nothing else. Set from a POST tick, so it
     * lands after the player's own tick has had its say, and rendering — which happens
     * between ticks — sees ours.
     *
     * Only ever cleared back from SWIMMING, so this cannot stamp on a forced pose set
     * by anything else.
     */
    private static void holdRidingPose(net.minecraft.client.Minecraft mc) {
        if (mc.player == null) return;

        boolean laying = mc.player.getVehicle() instanceof com.minecraft.atlamod.BendingSeat seat
                && seat.isLaying();

        if (laying) {
            mc.player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        } else if (mc.player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) {
            mc.player.setForcedPose(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        // Counted down here rather than in the camera event, which fires once per FRAME
        // and would run the shake down at whatever rate the machine happens to render.
        ClientShake.tick();
        ClientFlash.tick();

        holdRidingPose(mc);

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
            syncMeditate();
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
            for (int i = 0; i < 4; i++) {
                boolean held = ABILITY_KEYS[i].isDown();
                if (held != lastAbilityHeld[i]) {
                    int slot = isShiftDown ? i + 4 : i;
                    PacketDistributor.sendToServer(new com.minecraft.atlamod.network.AbilityHoldPacket(slot, held));
                    lastAbilityHeld[i] = held;
                }
            }
        }
        if (mc.player != null && mc.level != null) {

            // The client's own copy of "passives do not work here". The server sets the
            // same flag from its player tick; this side needs it because the menu and the
            // blood level readout both ask whether a passive is equipped, and a client
            // that thought one was still running would disagree with the server.
            mc.player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA)
                    .setPassivesSuppressed(
                            com.minecraft.atlamod.spirit.SpiritWorld.isSpiritWorld(mc.level));

            // 4. Check Meditation Hold (M key)
            {
                // The block above already asked for this tick whenever no screen is
                // open; this covers the one case its guard skips, a screen that IS open.
                if (mc.screen != null) syncMeditate();
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
        // Stunned comes FIRST and returns: there is nothing to reverse once the
        // input has been thrown away, and a victim carrying both effects should be
        // held still rather than held still backwards.
        if (event.getEntity().hasEffect(com.minecraft.atlamod.ModEffects.STUNNED)) {
            net.minecraft.client.player.Input stunned = event.getInput();
            stunned.forwardImpulse = 0.0F;
            stunned.leftImpulse = 0.0F;
            stunned.up = false;
            stunned.down = false;
            stunned.left = false;
            stunned.right = false;
            stunned.jumping = false;
            stunned.shiftKeyDown = false;
            return;
        }

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

    /**
     * Silences the world while the local player is Deafened.
     *
     * This is the WHOLE of that effect: sound exists only on the client, so the server
     * can no more mute somebody than it can read their movement keys. It cancels every
     * sound the game tries to play, which is what "cannot hear anything" asks for.
     *
     * Deliberately NOT filtered by category. Muting effects but leaving music, or
     * leaving the UI, would be a half-deafness nobody asked for — and the effect runs
     * for twenty-five seconds, so it ends well before it could be mistaken for the
     * game's audio having broken.
     */
    @SubscribeEvent
    public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.hasEffect(com.minecraft.atlamod.ModEffects.DEAFENED)) {
            event.setSound(null);
        }
    }

    /**
     * Forgets who was wearing bending armor on the way out of a world.
     *
     * The set is keyed on entity id, and ids start again in the next world — without
     * this, whichever entity happened to be handed a matching number would turn up
     * wearing stone.
     */
    @SubscribeEvent
    public static void onLoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBendingArmor.clear();
        ClientShake.clear();
        ClientFlash.clear();

        // The next server starts out believing nobody is meditating.
        lastMeditateSent = false;
    }

    /**
     * Carries the client's bending data onto the new player on respawn.
     *
     * The client builds a brand new LocalPlayer when you respawn, and NeoForge does not
     * copy attachments onto it — so until the server's resync arrived, the new body held
     * a blank copy with every key slot EMPTY. Anything that read it in that gap (the menu,
     * the HUD) saw the abilities as unbound. Handing the old copy across closes the gap;
     * the server's resync still follows and has the final say.
     */
    @SubscribeEvent
    public static void onClientRespawn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.Clone event) {
        var attachment = com.minecraft.atlamod.ModAttachments.BENDING_DATA;
        event.getNewPlayer().setData(attachment, event.getOldPlayer().getData(attachment));
    }

    /**
     * Shakes the camera while an Earthquake is running under this player.
     *
     * Done here rather than by moving the player: the view is the only thing that
     * should move, and nudging the entity would fight the server about where they are.
     */
    @SubscribeEvent
    public static void onCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (!ClientShake.active()) return;

        float partial = (float) event.getPartialTick();
        event.setYaw(event.getYaw() + ClientShake.offset(0, partial));
        event.setPitch(event.getPitch() + ClientShake.offset(1, partial));
        event.setRoll(event.getRoll() + ClientShake.offset(2, partial));
    }
}
