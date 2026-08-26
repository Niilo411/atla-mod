package com.minecraft.atlamod.network;

import com.minecraft.atlamod.network.EquipAbilityPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "atlamod", bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0.0");

        // 1. Element Choice Packet (Client -> Server)
        registrar.playToServer(
                ElementChoicePacket.TYPE,
                ElementChoicePacket.STREAM_CODEC,
                (ElementChoicePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);

                            // Read BEFORE setMainElement, which sets the flag itself —
                            // asking afterwards would always say they had already chosen.
                            boolean firstChoice = !data.hasChosenElement();

                            data.setMainElement(payload.element());
                            data.setActiveElement(payload.element());
                            if (!data.getUnlockedElements().contains(payload.element())) {
                                data.getUnlockedElements().add(payload.element());
                            }

                            // Picking your element starts you at level 1 rather than 0.
                            // Gated on it being the FIRST choice so re-sending this packet
                            // can't be used to farm levels, and taken as a floor rather
                            // than an assignment so it can never demote anyone.
                            if (firstChoice) {
                                data.setLevel(Math.max(data.getLevel(), 1));
                            }

                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);

                            // The client's own copy of the level has to be told: it set
                            // the element locally for an instant response, but the level
                            // is decided here, and without this the HUD reads "Lvl: 0"
                            // until something else happens to sync stats.
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                                        new SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
                            }
                        }
                    });
                }
        );

// 2. Switch Element Packet (Client -> Server)
        registrar.playToServer(
                SwitchElementPacket.TYPE,
                SwitchElementPacket.STREAM_CODEC,
                (SwitchElementPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setActiveElement(payload.newElement());
                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);
                        }
                    });
                });
                // --- EQUIP ABILITY PACKET (Client -> Server) ---
                registrar.playToServer(
                        com.minecraft.atlamod.network.EquipAbilityPacket.TYPE,
                        com.minecraft.atlamod.network.EquipAbilityPacket.STREAM_CODEC,
                        com.minecraft.atlamod.network.EquipAbilityPacket::handle
                );

        // 3. Meditate Packet (Client -> Server)
        registrar.playToServer(
                MeditatePacket.TYPE,
                MeditatePacket.STREAM_CODEC,
                (MeditatePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setMeditating(payload.isStarting());
                            if (!payload.isStarting()) {
                                data.setMeditateTickTimer(0);
                            }
                            // deleted: registrar.playToServer(...) — was invalid at runtime and crashed the game
                        }
                    });
                }
        );

        // 4. Use Ability Packet (Client -> Server)
        registrar.playToServer(
                UseAbilityPacket.TYPE,
                UseAbilityPacket.STREAM_CODEC,
                UseAbilityPacket::handle
        );

        // 5. Unlock Ability Packet (Client -> Server)
        registrar.playToServer(
                UnlockAbilityPacket.TYPE,
                UnlockAbilityPacket.STREAM_CODEC,
                UnlockAbilityPacket::handle
        );
        registrar.playToServer(
                LeftClickTriggerPacket.TYPE,
                LeftClickTriggerPacket.STREAM_CODEC,
                LeftClickTriggerPacket::handle
        );

        // 6. Ability Hold Packet (Client -> Server) — for channeled abilities like Fire Breath
        registrar.playToServer(
                AbilityHoldPacket.TYPE,
                AbilityHoldPacket.STREAM_CODEC,
                AbilityHoldPacket::handle
        );

        // 7. Sync Stats Packet (Server -> Client)
        registrar.playToClient(
                SyncStatsPacket.TYPE,
                SyncStatsPacket.STREAM_CODEC,
                (SyncStatsPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setXp(payload.xp());
                            data.setLevel(payload.level());
                            data.setCurrentChi(payload.currentChi());
                        }
                    });
                }
        );

// 8. Sync Bending Data Packet (Server -> Client)
        registrar.playToClient(
                com.minecraft.atlamod.network.SyncBendingDataPacket.TYPE,
                com.minecraft.atlamod.network.SyncBendingDataPacket.STREAM_CODEC,
                (com.minecraft.atlamod.network.SyncBendingDataPacket payload, net.neoforged.neoforge.network.handling.IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setMainElement(payload.mainElement());
                            data.setActiveElement(payload.activeElement());

                            data.getUnlockedElements().clear();
                            if (payload.unlockedElements() != null) data.getUnlockedElements().addAll(payload.unlockedElements());

                            data.getUnlockedAbilities().clear();
                            if (payload.unlockedAbilities() != null) data.getUnlockedAbilities().addAll(payload.unlockedAbilities());

                            // Perfect 1:1 overwrite using the titanium vault setter
                            if (payload.equippedAbilities() != null) {
                                data.setAllEquippedAbilities(payload.equippedAbilities());
                            }

                            // Lock it in for the UI
                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);

                            if (!payload.hasChosen()) {
                                com.minecraft.atlamod.client.ClientEvents.needsToOpenMenu = true;
                            }
                        }
                    });
                }
        );

        // --- CHARGE STATUS (Server -> Client) : drives the HUD charge meter ---
        // Top-level inside register(), NOT nested in another handler lambda —
        // nesting it would throw "Cannot register payload after registration phase".
        registrar.playToClient(
                ChargeStatusPacket.TYPE,
                ChargeStatusPacket.STREAM_CODEC,
                (ChargeStatusPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> com.minecraft.atlamod.client.ClientChargeState.update(
                            payload.ability(), payload.held(), payload.total(), payload.armed()));
                }
        );

        // --- EQUIP PASSIVE (Client -> Server) ---
        registrar.playToServer(
                EquipPassivePacket.TYPE,
                EquipPassivePacket.STREAM_CODEC,
                EquipPassivePacket::handle
        );

        // --- SYNC PASSIVES (Server -> Client) ---
        registrar.playToClient(
                SyncPassivesPacket.TYPE,
                SyncPassivesPacket.STREAM_CODEC,
                (SyncPassivesPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setAllEquippedPassives(payload.equippedPassives());
                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);
                        }
                    });
                }
        );

        // --- ROOTED (Server -> Client) : abilities that hold the player still ---
        registrar.playToClient(
                RootedPacket.TYPE,
                RootedPacket.STREAM_CODEC,
                (RootedPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() ->
                            com.minecraft.atlamod.client.ClientRootState.set(payload.rooted()));
                }
        );

        // --- EARTH ARMOR (Server -> Client) : who is wearing the stone suit ---
        registrar.playToClient(
                EarthArmorPacket.TYPE,
                EarthArmorPacket.STREAM_CODEC,
                (EarthArmorPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> com.minecraft.atlamod.client.ClientEarthArmor.set(
                            payload.entityId(), payload.active()));
                }
        );

        // --- EARTHQUAKE (Server -> Client) : shake the receiving player's camera ---
        registrar.playToClient(
                EarthquakePacket.TYPE,
                EarthquakePacket.STREAM_CODEC,
                (EarthquakePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() ->
                            com.minecraft.atlamod.client.ClientShake.start(payload.ticks()));
                }
        );

        // --- BUY UPGRADE (Client -> Server) ---
        registrar.playToServer(
                BuyUpgradePacket.TYPE,
                BuyUpgradePacket.STREAM_CODEC,
                BuyUpgradePacket::handle
        );

        // --- SCREEN FLASH (Server -> Client) : Lightning stun's white-out ---
        registrar.playToClient(
                ScreenFlashPacket.TYPE,
                ScreenFlashPacket.STREAM_CODEC,
                (ScreenFlashPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() ->
                            com.minecraft.atlamod.client.ClientFlash.start(payload.ticks()));
                }
        );

        // --- SYNC AVATAR (Server -> Client) : drives the HUD lives counter ---
        // Top-level inside register(), NOT nested in another handler's lambda.
        registrar.playToClient(
                SyncAvatarPacket.TYPE,
                SyncAvatarPacket.STREAM_CODEC,
                (SyncAvatarPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setAvatar(payload.avatar());
                            data.setAvatarLives(payload.lives());
                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);
                        }
                    });
                }
        );

        // --- SYNC UPGRADES (Server -> Client) ---
        registrar.playToClient(
                SyncUpgradesPacket.TYPE,
                SyncUpgradesPacket.STREAM_CODEC,
                (SyncUpgradesPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
                            data.setAllUnlockedUpgrades(payload.unlockedUpgrades());
                            player.setData(com.minecraft.atlamod.ModAttachments.BENDING_DATA, data);
                        }
                    });
                }
        );
    }
}