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

    /**
     * Advances water in flight. These are tracked in a static list rather than being
     * real entities, so nothing else ticks them.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.minecraft.atlamod.abilities.water.WaterProjectiles.tickAll(event.getServer());
    }

    /**
     * Drops shots belonging to a level that is going away. Nothing else holds them, so
     * without this a shot fired into an unloading dimension would keep a dead
     * ServerLevel alive for as long as the server ran.
     */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.minecraft.atlamod.abilities.water.WaterProjectiles.forgetLevel(level);
        }
    }
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

            // Clear any leftover charge meter. ClientChargeState is a static on the
            // client and survives a relog, so without this a player who logged out
            // mid-charge would come back to a stale bar stuck on their screen.
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.ChargeStatusPacket("", 0, 0, false));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.RootedPacket(false));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.SyncPassivesPacket(data.getEquippedPassives()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.SyncUpgradesPacket(data.getUnlockedUpgrades()));

            // Safety net: Fire Rocket grants flight through the vanilla ability
            // flags, and those are saved to player NBT. If the player disconnected
            // mid-flight, onStop() never ran and they would return able to fly
            // forever with nothing in the world to take it back.
            if (!player.isCreative() && !player.isSpectator()
                    && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                com.minecraft.atlamod.abilities.fire.FireRocket.stopFlight(player);
            }
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

        // Passives too. copyOnDeath already carries these through a death, but this
        // event also fires when changing dimension, where it does not — without this
        // walking into the Nether would silently unequip every passive.
        newData.setAllEquippedPassives(oldData.getEquippedPassives());

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
     * Two jobs on incoming damage:
     *
     * 1. Cancel it outright while the player is channeling an ability that grants
     *    invulnerability (Fire Shield). Done as an event cancel rather than
     *    Entity#setInvulnerable because that flag is persisted in player NBT —
     *    logging out mid-shield would otherwise leave the player invincible
     *    permanently. Cancelling here also drops the damage's knockback with it.
     *
     * 2. Scale fire damage for anything standing in fire an ability placed (Fire
     *    Ring, Ignite), by that fire's own multiplier. Applies to every living
     *    entity, not just players — the point is that it hurts what you burned.
     *
     * The shield is handled first and returns, so a shielded player standing in
     * their own ring still takes nothing.
     */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);
            if (AbilityHandler.blocksDamage(data, event.getSource())) {
                event.setCanceled(true);
                return;
            }

            // Fire immunity: nothing that burns gets through. Checked against the
            // whole IS_FIRE tag rather than a list of sources, so lava, magma, being
            // alight and every fire ability are all covered at once — including this
            // player's own blue fire, which otherwise hits for a flat 3 hearts.
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                    && data.hasPassiveEquipped(
                            com.minecraft.atlamod.abilities.fire.FireImmunity.KEY)) {
                event.setCanceled(true);
                return;
            }
        }

        // Blue Fire doubles what the bender's own fire abilities hit for.
        //
        // Keyed on the ATTACKER's passives, and limited to fire and explosion damage
        // they caused: every fire ability here damages through damageSources().inFire(),
        // and Fireball lands as an explosion. Doubling everything a player deals would
        // catch sword swings too, which isn't what "all abilities" meant.
        net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer attackingPlayer
                && (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                    || event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION))) {

            BendingData attackerData = attackingPlayer.getData(ModAttachments.BENDING_DATA);
            if (attackerData.hasPassiveEquipped(com.minecraft.atlamod.abilities.fire.BlueFire.KEY)) {
                event.setAmount(event.getAmount()
                        * com.minecraft.atlamod.abilities.fire.BlueFire.DAMAGE_MULTIPLIER);
            }
        }

        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        // From here down is damage from STANDING IN fire, not from being hit by an
        // ability that happens to burn. Fire block contact has no causing entity,
        // where ability damage carries the bender. Without this check, hitting a mob
        // that is standing in bending fire would have the ability's own damage
        // overwritten by the block's.
        if (event.getSource().getEntity() != null) return;

        // Blue fire burns at a flat rate rather than a multiple of ordinary fire:
        // 3 hearts a hit, and fire lands roughly once a second through the victim's
        // invulnerability frames. Checked off the block itself rather than off
        // BendingFire's tracking, so it holds for blue fire from any source —
        // including Firewall's, which is laid untracked at plain damage.
        net.minecraft.world.level.block.state.BlockState standingIn =
                level.getBlockState(event.getEntity().blockPosition());

        if (standingIn.getBlock() instanceof BendingFireBlock
                && standingIn.getValue(BendingFireBlock.BLUE)) {
            event.setAmount(com.minecraft.atlamod.abilities.fire.BlueFire.CONTACT_DAMAGE);
            return;
        }

        float multiplier = com.minecraft.atlamod.abilities.BendingFire.getMultiplier(
                level, event.getEntity().blockPosition());
        if (multiplier > 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // --- UNIVERSAL COOLDOWN TICKER (Must be at the very top!) ---
            data.tickCooldowns();

            // --- FIRE IMMUNITY PASSIVE ---
            // Damage is cancelled in the damage handler, but burning is separate from
            // being hurt by it: without this the player stands there wreathed in
            // flames taking nothing, which reads as a bug rather than as immunity.
            if (player.isOnFire() && data.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.fire.FireImmunity.KEY)) {
                player.clearFire();
            }
            player.setData(ModAttachments.BENDING_DATA, data);

            // --- CHI REGEN ---
            // Regen is held off for a few seconds after any chi is spent, so a cheap
            // ability can't be sustained indefinitely by regen alone. The countdown
            // runs every tick; the refill itself stays on the 1-second cadence.
            if (data.getChiRegenDelay() > 0) {
                data.setChiRegenDelay(data.getChiRegenDelay() - 1);
            } else if (player.tickCount % 20 == 0) {
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

                    serverLevel.sendParticles(com.minecraft.atlamod.abilities.BendingFire.flame(data), px, py, pz, 2, 0.1, 0.1, 0.1, 0.02);
                }
            }

            // --- ARMED TWO-PHASE ABILITY VISUALS ---
            // Generic: any armed two-phase ability shows a ball of fire being held,
            // so other players can see it coming rather than only the caster's HUD.
            if (!data.getActiveTwoPhaseAbility().isEmpty()) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    double px = player.getX() + look.x * 2.0;
                    double py = player.getY() + 1.2 + look.y * 2.0;
                    double pz = player.getZ() + look.z * 2.0;

                    serverLevel.sendParticles(com.minecraft.atlamod.abilities.BendingFire.flame(data),
                            px, py, pz, 10, 0.3, 0.3, 0.3, 0.05);
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

            // --- CHARGED ABILITY TICK ---
            // Drives the wind-up and fires the ability when it fills.
            if (data.isCharging()) {
                AbilityHandler.tickCharging(player, data);
            }

            // --- FIRE RAIN TICK ---
            // Cast and left running, like Fire Leap: a countdown on the data rather
            // than a channel, so nothing has to be held down for it to keep falling.
            if (data.getFireRainTicks() > 0) {
                com.minecraft.atlamod.abilities.fire.FireRain.tick(player, data);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // Same safety net as login: if the player died mid-rocket, onStop() never
            // ran. Vanilla usually rebuilds abilities from the gamemode on respawn,
            // but the cost of being wrong here is permanent creative flight.
            if (!player.isCreative() && !player.isSpectator()
                    && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                com.minecraft.atlamod.abilities.fire.FireRocket.stopFlight(player);
            }

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

                // The passive slots need their own packet: SyncBendingDataPacket is
                // already at six fields, which is as many as StreamCodec.composite
                // takes. Without this the server still applies the player's passives
                // but the menu shows every slot empty after a death.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.minecraft.atlamod.network.SyncPassivesPacket(data.getEquippedPassives()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.SyncUpgradesPacket(data.getUnlockedUpgrades()));

                // Dying mid-channel or mid-charge ends them server-side, but these two
                // are client-side statics that survive it — leaving a stale charge bar
                // on screen, or worse, a player who respawns unable to move.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.minecraft.atlamod.network.RootedPacket(false));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.minecraft.atlamod.network.ChargeStatusPacket("", 0, 0, false));
            }));
        }
    }
}