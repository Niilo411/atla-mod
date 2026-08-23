package com.minecraft.atlamod;

import com.minecraft.atlamod.network.SyncBendingDataPacket;
import com.minecraft.atlamod.network.SyncStatsPacket;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

@EventBusSubscriber(modid = Atlamod.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bend")
                // ADD ELEMENT COMMAND
                .then(Commands.literal("add")
                        .then(Commands.argument("element", word())
                                .executes(context -> {
                                    String element = getString(context, "element");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BendingData data = player.getData(ModAttachments.BENDING_DATA);

                                    if (!data.getUnlockedElements().contains(element)) {
                                        data.getUnlockedElements().add(element);
                                        if (data.getActiveElement().isEmpty()) data.setActiveElement(element);
                                        player.setData(ModAttachments.BENDING_DATA, data);

                                        PacketDistributor.sendToPlayer(player, new SyncBendingDataPacket(
                                                data.getMainElement(),
                                                data.getActiveElement(),
                                                data.getUnlockedElements(),
                                                data.hasChosenElement(),
                                                data.getUnlockedAbilities(),
                                                data.getEquippedAbilities()
                                        ));
                                    }
                                    return 1;
                                })
                        )
                )
                // REMOVE ELEMENT COMMAND
                .then(Commands.literal("remove")
                        .then(Commands.argument("element", word())
                                .executes(context -> {
                                    String element = getString(context, "element");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BendingData data = player.getData(ModAttachments.BENDING_DATA);

                                    if (data.getUnlockedElements().contains(element)) {
                                        data.getUnlockedElements().remove(element);
                                        if (data.getActiveElement().equals(element)) {
                                            data.setActiveElement(data.getUnlockedElements().isEmpty() ? "" : data.getUnlockedElements().get(0));
                                        }
                                        player.setData(ModAttachments.BENDING_DATA, data);

                                        PacketDistributor.sendToPlayer(player, new SyncBendingDataPacket(
                                                data.getMainElement(),
                                                data.getActiveElement(),
                                                data.getUnlockedElements(),
                                                data.hasChosenElement(),
                                                data.getUnlockedAbilities(),
                                                data.getEquippedAbilities()
                                        ));
                                    }
                                    return 1;
                                })
                        )
                )
                // LEVEL COMMAND
                .then(Commands.literal("level")
                        .then(Commands.argument("amount", integer(1))
                                .executes(context -> {
                                    int amount = getInteger(context, "amount");
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BendingData data = player.getData(ModAttachments.BENDING_DATA);

                                    // Add the levels and sync it back to the client
                                    data.setLevel(data.getLevel() + amount);
                                    player.setData(ModAttachments.BENDING_DATA, data);

                                    PacketDistributor.sendToPlayer(player, new SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
                                    return 1;
                                })
                        )
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncBendingDataPacket(
                    data.getMainElement() == null ? "" : data.getMainElement(),
                    data.getActiveElement() == null ? "" : data.getActiveElement(),
                    data.getUnlockedElements(),
                    data.hasChosenElement(),
                    data.getUnlockedAbilities(),
                    data.getEquippedAbilities() // <--- CRUCIAL: Sends your saved keybinds on join!
            ));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncStatsPacket(
                    data.getXp(),
                    data.getLevel(),
                    data.getCurrentChi()
            ));
        }
    }
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Copies your data to your new body when respawning OR traveling to the Nether
        var oldData = event.getOriginal().getData(ModAttachments.BENDING_DATA);
        var newData = event.getEntity().getData(ModAttachments.BENDING_DATA);

        newData.setMainElement(oldData.getMainElement());
        newData.setActiveElement(oldData.getActiveElement());
        newData.setHasChosenElement(oldData.hasChosenElement());

        newData.getUnlockedElements().clear();
        newData.getUnlockedElements().addAll(oldData.getUnlockedElements());

        newData.setXp(oldData.getXp());
        newData.setLevel(oldData.getLevel());
        newData.setCurrentChi(oldData.getCurrentChi());

        newData.getUnlockedAbilities().clear();
        newData.getUnlockedAbilities().addAll(oldData.getUnlockedAbilities());

        newData.getEquippedAbilities().clear();
        newData.getEquippedAbilities().addAll(oldData.getEquippedAbilities());

        event.getEntity().setData(ModAttachments.BENDING_DATA, newData);
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Forces the UI to reappear on your screen after walking through a portal
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncBendingDataPacket(
                    data.getMainElement() == null ? "" : data.getMainElement(),
                    data.getActiveElement() == null ? "" : data.getActiveElement(),
                    data.getUnlockedElements(),
                    data.hasChosenElement(),
                    data.getUnlockedAbilities(),
                    data.getEquippedAbilities()
            ));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncStatsPacket(
                    data.getXp(),
                    data.getLevel(),
                    data.getCurrentChi()
            ));
        }
    }

    /**
     * Cancels all incoming damage while the player is channeling an ability that
     * grants invulnerability (Fire Shield).
     *
     * Done as an event cancel rather than Entity#setInvulnerable because that flag
     * is persisted in player NBT — logging out mid-shield would otherwise leave the
     * player invincible permanently. Cancelling here also removes the damage's
     * knockback along with the damage.
     */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        if (AbilityHandler.blocksDamage(data, event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // --- UNIVERSAL COOLDOWN TICKER (Must be at the very top!) ---
            data.tickCooldowns();
            player.setData(ModAttachments.BENDING_DATA, data);

            // --- CHI REGEN ---
            if (player.tickCount % 20 == 0) {
                if (data.getCurrentChi() < data.getMaxChi()) {
                    // Divide max Chi by 100 to get exactly 1% regen per second (100 seconds to full)
                    int regenAmount = Math.max(1, data.getMaxChi() / 100);

                    data.setCurrentChi(Math.min(data.getMaxChi(), data.getCurrentChi() + regenAmount));

                    // Save the data and sync it to the UI
                    player.setData(ModAttachments.BENDING_DATA, data);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
                }
            }

            // --- FIRE WHIP VISUALS ---
            // Runs every tick (was previously trapped inside the 20-tick Chi regen block,
            // which made the whip flicker once per second instead of trailing smoothly).
            if (data.isFireWhipping()) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    // Spawn particles slightly in front of the player to look like a held whip
                    double px = player.getX() + look.x * 1.5;
                    double py = player.getY() + 1.2 + look.y * 1.5;
                    double pz = player.getZ() + look.z * 1.5;

                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, px, py, pz, 2, 0.1, 0.1, 0.1, 0.02);
                }
            }

            // --- TWO-PHASE ABILITY VISUALS (Everyone can see this!) ---
            // Independent of Fire Whip - you can be holding a charged Fireball without whipping.
            if (data.getActiveTwoPhaseAbility() != null && data.getActiveTwoPhaseAbility().equalsIgnoreCase("fireball")) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();

                    // Push it a bit further out so it isn't inside your head
                    double px = player.getX() + look.x * 2.0;
                    double py = player.getY() + 1.2 + look.y * 2.0;
                    double pz = player.getZ() + look.z * 2.0;

                    // Cranked up to 10 particles with more spread so it looks like a ball of fire!
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, px, py, pz, 10, 0.3, 0.3, 0.3, 0.05);
                }
            }

            // --- MEDITATING LOGIC ---
            if (data.isMeditating()) {
                data.setMeditateTickTimer(data.getMeditateTickTimer() + 1);
                player.setData(ModAttachments.BENDING_DATA, data);

                player.setDeltaMovement(0, player.getDeltaMovement().y < 0 ? -0.08 : 0, 0);
                player.hurtMarked = true;

                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    if (data.getMeditateTickTimer() % 5 == 0) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    }
                }

                // Give 2 XP every 1 second (20 ticks)
                if (data.getMeditateTickTimer() % 20 == 0) {
                    data.setXp(data.getXp() + 2);

                    if (data.getXp() >= 200) {
                        data.setLevel(data.getLevel() + 1);
                        data.setXp(0);
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncStatsPacket(data.getXp(), data.getLevel(), data.getCurrentChi()));
                }
            }

            // --- FIRE LEAP TICKS ---
            // Fire Leap ends itself on landing, so it isn't a channeled ability;
            // its per-tick trail logic lives on the ability class all the same.
            if (data.isFireLeaping()) {
                com.minecraft.atlamod.abilities.fire.FireLeap.tick(player, data);
            }

            // --- CHANNELED ABILITY TICK ---
            // Generic: drives whichever channeled ability the player is holding.
            if (data.isChanneling()) {
                AbilityHandler.tickChanneled(player, data);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // Give the client 5 ticks to load the new body before sending the UI sync
            player.getServer().tell(new net.minecraft.server.TickTask(player.getServer().getTickCount() + 5, () -> {
                BendingData data = player.getData(ModAttachments.BENDING_DATA);

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncBendingDataPacket(
                        data.getMainElement() == null ? "" : data.getMainElement(),
                        data.getActiveElement() == null ? "" : data.getActiveElement(),
                        data.getUnlockedElements(),
                        data.hasChosenElement(),
                        data.getUnlockedAbilities(),
                        data.getEquippedAbilities()
                ));

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncStatsPacket(
                        data.getXp(),
                        data.getLevel(),
                        data.getCurrentChi()
                ));
            }));
        }
    }
}