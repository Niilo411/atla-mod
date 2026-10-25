package io.github.niilo411.elementalpaths.network;

import io.github.niilo411.elementalpaths.network.EquipAbilityPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "elemental_paths", bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.6.4");

        // 1. Element Choice Packet (Client -> Server)
        registrar.playToServer(
                ElementChoicePacket.TYPE,
                ElementChoicePacket.STREAM_CODEC,
                (ElementChoicePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);

                            // Read BEFORE setMainElement, which sets the flag itself —
                            // asking afterwards would always say they had already chosen.
                            boolean firstChoice = !data.hasChosenElement();

                            // ONLY a first choice is honoured. The screen is shown only to
                            // somebody who has not chosen, so this refuses nothing anybody
                            // can legitimately do — what it stops is a hand-sent packet
                            // rewriting a main element that is already set, which now
                            // matters in a way it did not before: no channeling is reachable
                            // from this screen and is meant to be a first-join decision or
                            // a gift from an operator, not something to switch into later.
                            if (!firstChoice) return;

                            // And only a path that actually exists, for the reason
                            // /paths add is gated: an invented one leaves a player holding
                            // something with no tree, no abilities and no emblem.
                            if (!io.github.niilo411.elementalpaths.abilities.ElementPaths.exists(payload.element())) return;

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

                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);

                            // The client's own copy of the level has to be told: it set
                            // the element locally for an instant response, but the level
                            // is decided here, and without this the HUD reads "Lvl: 0"
                            // until something else happens to sync stats.
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                                        SyncStatsPacket.of(data));
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
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setActiveElement(payload.newElement());
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);
                        }
                    });
                });
                // --- EQUIP ABILITY PACKET (Client -> Server) ---
                registrar.playToServer(
                        io.github.niilo411.elementalpaths.network.EquipAbilityPacket.TYPE,
                        io.github.niilo411.elementalpaths.network.EquipAbilityPacket.STREAM_CODEC,
                        io.github.niilo411.elementalpaths.network.EquipAbilityPacket::handle
                );

        // 3. Meditate Packet (Client -> Server)
        registrar.playToServer(
                MeditatePacket.TYPE,
                MeditatePacket.STREAM_CODEC,
                (MeditatePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
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
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setXp(payload.xp());
                            data.setLevel(payload.level());
                            data.setCurrentChi(payload.currentChi());

                            // The shrine bonus, which the HUD needs for two separate
                            // things: the maximum the chi bar is drawn against, and the
                            // colour it is drawn in.
                            data.setBonusMaxChi(payload.bonusMaxChi());
                        }
                    });
                }
        );

// 8. Sync Channeling Data Packet (Server -> Client)
        registrar.playToClient(
                io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket.TYPE,
                io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket.STREAM_CODEC,
                (io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket payload, net.neoforged.neoforge.network.handling.IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
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
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);

                            if (!payload.hasChosen()) {
                                io.github.niilo411.elementalpaths.client.ClientEvents.needsToOpenMenu = true;
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
                    context.enqueueWork(() -> io.github.niilo411.elementalpaths.client.ClientChargeState.update(
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
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setAllEquippedPassives(payload.equippedPassives());
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);
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
                            io.github.niilo411.elementalpaths.client.ClientRootState.set(payload.rooted()));
                }
        );

        // --- CHANNELING ARMOR (Server -> Client) : who is wearing which drawn suit ---
        registrar.playToClient(
                ChannelingArmorPacket.TYPE,
                ChannelingArmorPacket.STREAM_CODEC,
                (ChannelingArmorPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        io.github.niilo411.elementalpaths.ChannelingArmorSuit[] suits =
                                io.github.niilo411.elementalpaths.ChannelingArmorSuit.VALUES;
                        // Guarded because the ordinal comes off the wire: a client on a
                        // different version of the mod would otherwise index past the end.
                        if (payload.suit() < 0 || payload.suit() >= suits.length) return;
                        io.github.niilo411.elementalpaths.client.ClientChannelingArmor.set(
                                payload.entityId(), suits[payload.suit()], payload.active());
                    });
                }
        );

        // --- EARTHQUAKE (Server -> Client) : shake the receiving player's camera ---
        registrar.playToClient(
                EarthquakePacket.TYPE,
                EarthquakePacket.STREAM_CODEC,
                (EarthquakePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() ->
                            io.github.niilo411.elementalpaths.client.ClientShake.start(payload.ticks()));
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
                            io.github.niilo411.elementalpaths.client.ClientFlash.start(payload.ticks()));
                }
        );

        // --- SYNC BLOOD (Server -> Client) : drives the HUD blood level ---
        registrar.playToClient(
                SyncBloodPacket.TYPE,
                SyncBloodPacket.STREAM_CODEC,
                (SyncBloodPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setBloodXp(payload.bloodXp());
                            data.setBloodLevel(payload.bloodLevel());
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);
                        }
                    });
                }
        );

        // --- SYNC PARAGON (Server -> Client) : drives the HUD lives counter ---
        // Top-level inside register(), NOT nested in another handler's lambda.
        registrar.playToClient(
                SyncParagonPacket.TYPE,
                SyncParagonPacket.STREAM_CODEC,
                (SyncParagonPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null) {
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setParagon(payload.paragon());
                            data.setParagonLives(payload.lives());
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);
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
                            var data = player.getData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA);
                            data.setAllUnlockedUpgrades(payload.unlockedUpgrades());
                            player.setData(io.github.niilo411.elementalpaths.ModAttachments.CHANNELING_DATA, data);
                        }
                    });
                }
        );

        // --- SERVER KIND (Server -> Client) : whether ops may edit settings here ---
        registrar.playToClient(
                ServerKindPacket.TYPE,
                ServerKindPacket.STREAM_CODEC,
                (ServerKindPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> io.github.niilo411.elementalpaths.client.ClientSettingsAccess
                            .setDedicatedServer(payload.dedicated()));
                }
        );

        // --- UPDATE SETTINGS (Client -> Server) : an op editing a dedicated server ---
        registrar.playToServer(
                UpdateSettingsPacket.TYPE,
                UpdateSettingsPacket.STREAM_CODEC,
                (UpdateSettingsPacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> {
                        if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;

                        // Checked again here whatever the menu showed — the client is only
                        // ever asking. See SettingsAccess for who qualifies.
                        if (!io.github.niilo411.elementalpaths.SettingsAccess.canEdit(player)) return;

                        io.github.niilo411.elementalpaths.SettingsAccess.apply(payload.values());
                        io.github.niilo411.elementalpaths.PathsConfig.SPEC.save();
                        io.github.niilo411.elementalpaths.PathsConfig.refresh();
                        io.github.niilo411.elementalpaths.SettingsAccess.broadcast(player.getServer());
                    });
                }
        );

        // --- SETTINGS FILE (Server -> Client) : the settings as they now are ---
        registrar.playToClient(
                SettingsFilePacket.TYPE,
                SettingsFilePacket.STREAM_CODEC,
                (SettingsFilePacket payload, IPayloadContext context) -> {
                    context.enqueueWork(() -> io.github.niilo411.elementalpaths.client.ClientSettingsAccess
                            .receive(payload.fileName(), payload.contents()));
                }
        );
    }
}