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
     * Advances everything in flight. These are tracked in a static list rather than
     * being real entities, so nothing else ticks them.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        com.minecraft.atlamod.abilities.BendingProjectiles.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.water.Drownings.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.water.Tsunamis.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.Rides.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.air.AirSpouts.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.earth.EarthWorks.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.earth.EarthWalls.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.earth.EarthTraps.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.earth.EarthGrabs.tickAll(event.getServer());

        com.minecraft.atlamod.abilities.lightning.LightningBalls.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.ice.IceWorks.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.ice.Frozens.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.ice.IceBombs.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.ice.FreezingBeams.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.sound.BassWaves.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.sound.SoundWalls.tickAll(event.getServer());

        // Keeps a running cycle looking for an Avatar when nobody holds the title.
        // Rate-limited inside, and does nothing at all once one is in place.
        com.minecraft.atlamod.avatar.Avatar.tickCycle(event.getServer());
    }

    /**
     * Drops shots belonging to a level that is going away. Nothing else holds them, so
     * without this a shot fired into an unloading dimension would keep a dead
     * ServerLevel alive for as long as the server ran.
     */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.minecraft.atlamod.abilities.BendingProjectiles.forgetLevel(level);
            com.minecraft.atlamod.abilities.HeldBlocks.forgetLevel(level);
            com.minecraft.atlamod.abilities.water.WaterSpheres.forgetLevel(level);
            com.minecraft.atlamod.abilities.water.Drownings.forgetLevel(level);
            com.minecraft.atlamod.abilities.water.Tsunamis.forgetLevel(level);
            com.minecraft.atlamod.abilities.Rides.forgetLevel(level);
            com.minecraft.atlamod.abilities.air.AirSpouts.forgetLevel(level);
            com.minecraft.atlamod.abilities.earth.EarthWorks.forgetLevel(level);
            com.minecraft.atlamod.abilities.earth.EarthWalls.forgetLevel(level);
            com.minecraft.atlamod.abilities.earth.EarthTraps.forgetLevel(level);
            com.minecraft.atlamod.abilities.earth.EarthGrabs.forgetLevel(level);
            com.minecraft.atlamod.abilities.lightning.LightningBalls.forgetLevel(level);
            com.minecraft.atlamod.abilities.ice.Frozens.forgetLevel(level);
            com.minecraft.atlamod.abilities.ice.IceBombs.forgetLevel(level);
            com.minecraft.atlamod.abilities.ice.FreezingBeams.forgetLevel(level);
            com.minecraft.atlamod.abilities.sound.BassWaves.forgetLevel(level);
            com.minecraft.atlamod.abilities.sound.SoundWalls.forgetLevel(level);
            // Melts LAST: the two above hand their own blocks back through IceWorks,
            // so anything they release still gets settled by this sweep.
            com.minecraft.atlamod.abilities.ice.IceWorks.forgetLevel(level);
        }
    }
    /**
     * Puts the Lightningbending Scroll in the weaponsmith's book, for 64 copper.
     *
     * Offered at levels 1 AND 3 rather than at one level, because a villager picks
     * only a couple of trades at random from each level's pool — one entry would be
     * a coin flip per weaponsmith. Two entries give a fresh smith a good chance of
     * having it and a levelled one a second, without the same trade showing up twice
     * in the same tier.
     */
    @SubscribeEvent
    public static void onVillagerTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.COPPER_INGOT, 64),
                    new net.minecraft.world.item.ItemStack(Atlamod.LIGHTNING_SCROLL.get()),
                    2,   // how many times it can be bought before restocking
                    12,  // villager xp for the trade
                    0.05F));
        }
    }

    /**
     * Puts the Icebending Scroll in the fisherman's book, for a Heart of the Sea.
     *
     * Same two levels as the weaponsmith's, for the same reason: a villager picks only
     * a couple of trades at random from each level's pool, so one entry would be a coin
     * flip per fisherman.
     */
    @SubscribeEvent
    public static void onFishermanTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.FISHERMAN) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.HEART_OF_THE_SEA, 1),
                    new net.minecraft.world.item.ItemStack(Atlamod.ICE_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Soundbending Scroll in the fletcher's book, for 32 feathers.
     *
     * Same two levels as the other two scrolls, for the same reason: a villager picks
     * only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onFletcherTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.FLETCHER) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.FEATHER, 32),
                    new net.minecraft.world.item.ItemStack(Atlamod.SOUND_SCROLL.get()),
                    2,
                    12,
                    0.05F));
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
                // AVATAR COMMANDS
                //
                // Op-gated, unlike the three above. Naming the Avatar decides who on
                // the server holds every element at once, which is not something any
                // player should be able to hand themselves.
                //
                // "cycle" is a literal and the player is an argument, so Brigadier
                // tries the literal first and there is no ambiguity between
                // "/bend avatar cycle ..." and "/bend avatar <player>" even for a
                // player unlucky enough to be called "cycle".
                .then(Commands.literal("avatar")
                        .requires(source -> source.hasPermission(2))

                        // /bend avatar cycle start|stop
                        .then(Commands.literal("cycle")
                                .then(Commands.literal("start")
                                        .executes(context -> {
                                            var server = context.getSource().getServer();
                                            com.minecraft.atlamod.avatar.Avatar.startCycle(server);

                                            var state = com.minecraft.atlamod.avatar.Avatar.state(server);
                                            if (state.hasAvatar()) {
                                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                        "Avatar cycle started at " + state.getCycleElement() + "."), true);
                                            } else {
                                                // Not a failure. The search already skipped
                                                // every element in turn, so reaching here
                                                // means nobody online could be the Avatar at
                                                // all — the cycle is running and will take
                                                // the first qualifying player to turn up.
                                                // Said plainly so it doesn't read as the
                                                // command having done nothing.
                                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                        "Avatar cycle started, but nobody online has chosen an element. "
                                                                + "Waiting — it rests on " + state.getCycleElement() + "."), true);
                                            }
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("stop")
                                        .executes(context -> {
                                            com.minecraft.atlamod.avatar.Avatar.stopCycle(
                                                    context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                    "Avatar cycle stopped. Nobody is the Avatar."), true);
                                            return 1;
                                        })
                                )
                        )

                        // /bend avatar remove
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    var server = context.getSource().getServer();
                                    var state = com.minecraft.atlamod.avatar.Avatar.state(server);

                                    if (!state.hasAvatar()) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "There is no Avatar."));
                                        return 0;
                                    }

                                    com.minecraft.atlamod.avatar.Avatar.revokeCurrent(server);
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "The Avatar has been removed."), true);

                                    // A running cycle keeps running: the Avatar was taken
                                    // away, not defeated, so the search resumes on the SAME
                                    // element rather than moving on. "cycle stop" is the
                                    // command for ending the cycle itself.
                                    com.minecraft.atlamod.avatar.Avatar.findAvatar(server);
                                    return 1;
                                })
                        )

                        // /bend avatar <player>
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument
                                            .getPlayer(context, "player");

                                    com.minecraft.atlamod.avatar.Avatar.grant(
                                            context.getSource().getServer(), target);

                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            target.getGameProfile().getName() + " is now the Avatar."), true);
                                    return 1;
                                })
                        )
                )
        );
    }


    /**
     * Puts down anything a player was carrying when they die or disconnect. The block
     * is out of the world while held, so without this it would simply cease to exist.
     */


    /**
     * Refuses to let anyone shift out of an Earth trap.
     *
     * The trap works by making its victim a passenger, which is what stops them
     * moving — but vanilla lets a passenger dismount whenever it likes, so without
     * this the whole ability would last exactly as long as it took to press shift.
     * EarthTraps drops the seat from its list BEFORE releasing anyone, so a genuine
     * release is never caught by this.
     */
    /**
     * Holds anything Stunned completely still.
     *
     * This is the server's half of the effect, and for MOBS it is the whole of it: a
     * mob has no movement keys to throw away, so the client-side block in
     * ClientEvents does nothing for it and the server has to stop it directly.
     * Zeroing the velocity alone is not enough either — a pathfinding mob simply sets
     * a new one next tick — so the navigation is stopped as well.
     *
     * For PLAYERS this is the same belt-and-braces pairing the shields' rooting uses:
     * the client already refuses its own input, and the server zeroing the velocity
     * covers momentum the player was already carrying when the stun landed.
     *
     * Downward motion is deliberately left alone in both cases, so a stun is not also
     * a hover — a victim stunned in mid-air still falls.
     */
    @SubscribeEvent
    public static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living)) return;
        if (living.level().isClientSide()) return;
        if (!living.hasEffect(com.minecraft.atlamod.ModEffects.STUNNED)) return;

        net.minecraft.world.phys.Vec3 motion = living.getDeltaMovement();
        living.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);

        if (living instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        } else if (living instanceof ServerPlayer stunnedPlayer) {
            // A player's client owns their position, so it has to be told.
            stunnedPlayer.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (!event.isDismounting()) return;

        if (com.minecraft.atlamod.abilities.earth.EarthTraps.holdsSeat(event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }
    }
    /**
     * Tells a player about someone else's Earth armor the moment they come into view.
     *
     * The per-tick broadcast only fires when the armor goes on or off, so without this
     * anyone who walked up to an already-armored bender — or logged in near one, or
     * came back into render distance — would see them in ordinary clothes until the
     * effect ended.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;

        if (target.hasEffect(com.minecraft.atlamod.ModEffects.EARTH_ARMOR)) {
            PacketDistributor.sendToPlayer(watcher,
                    new com.minecraft.atlamod.network.EarthArmorPacket(target.getId(), true));
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.minecraft.atlamod.abilities.HeldBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.water.WaterSpheres.collapse(player);
            com.minecraft.atlamod.abilities.Rides.forgetPlayer(player);
            com.minecraft.atlamod.abilities.air.AirSpouts.forgetPlayer(player);
            com.minecraft.atlamod.abilities.earth.EarthTraps.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lightning.LightningBalls.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.IceBombs.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.FreezingBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.Frozens.forgetEntity(player);
            com.minecraft.atlamod.abilities.sound.BassWaves.forgetPlayer(player);
            com.minecraft.atlamod.abilities.sound.SoundWalls.forgetPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        // Anything at all, not just players: a mob sealed in ice can still be killed
        // by something that bypasses invulnerability, and its shell has to come down
        // with it rather than standing there empty until its timer runs out.
        com.minecraft.atlamod.abilities.ice.Frozens.forgetEntity(event.getEntity());

        if (event.getEntity() instanceof ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // Earth armor's stone suit is drawn from a client-side set keyed on entity
            // id, and a respawned player REUSES its id on both sides — so a death would
            // otherwise leave the art on a bender who no longer has the effect. The
            // per-tick broadcast cannot catch it either: the flag it compares against is
            // transient and comes back false, so it sees no change and says nothing.
            // Told explicitly here instead.
            data.setEarthArmorShown(false);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    player, new com.minecraft.atlamod.network.EarthArmorPacket(player.getId(), false));
            com.minecraft.atlamod.abilities.HeldBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.water.WaterSpheres.collapse(player);
            com.minecraft.atlamod.abilities.Rides.forgetPlayer(player);
            com.minecraft.atlamod.abilities.air.AirSpouts.forgetPlayer(player);
            com.minecraft.atlamod.abilities.earth.EarthTraps.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lightning.LightningBalls.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.IceBombs.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.FreezingBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.Frozens.forgetEntity(player);
            com.minecraft.atlamod.abilities.sound.BassWaves.forgetPlayer(player);
            com.minecraft.atlamod.abilities.sound.SoundWalls.forgetPlayer(player);

            // One of the Avatar's three lives. Deliberately last: it can strip the
            // title and pass the cycle on, and the cleanup above should run for a
            // dying Avatar exactly as it does for anyone else.
            com.minecraft.atlamod.avatar.Avatar.onDeath(player);
        }
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

            // Fills in the HUD's lives counter, and takes the title off anyone who
            // comes back still flagged when the world says somebody else has it —
            // which is how the title is revoked from a player who was OFFLINE.
            com.minecraft.atlamod.avatar.Avatar.checkOnLogin(player);
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

        // The Avatar too. copyOnDeath covers a death, but this event also fires on a
        // dimension change, where it does not — without this, walking into the Nether
        // would quietly cost a player the title along with the elements it granted.
        newData.setAvatar(oldData.isAvatar());
        newData.setAvatarLives(oldData.getAvatarLives());
        newData.setPreAvatarElements(oldData.getPreAvatarElements());

        event.getEntity().setData(ModAttachments.BENDING_DATA, newData);
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Forces the UI to reappear on your screen after walking through a portal
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // A scooter cannot follow its rider through a portal: the seat belongs to
            // the level they left, so the ride ends at the threshold.
            com.minecraft.atlamod.abilities.Rides.forgetPlayer(player);
            com.minecraft.atlamod.abilities.air.AirSpouts.forgetPlayer(player);
            com.minecraft.atlamod.abilities.earth.EarthTraps.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lightning.LightningBalls.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.IceBombs.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.FreezingBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.ice.Frozens.forgetEntity(player);
            com.minecraft.atlamod.abilities.sound.BassWaves.forgetPlayer(player);
            com.minecraft.atlamod.abilities.sound.SoundWalls.forgetPlayer(player);

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

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.SyncAvatarPacket(
                            data.isAvatar(), data.getAvatarLives()));
        }
    }

    /**
     * Air Jump: no fall damage while its window is open.
     *
     * Cancelled at LivingFallEvent rather than at the damage event, because
     * cancelling here also suppresses the landing sound and the puff of dust —
     * a bender who steps out of a 20 block drop should not thud like a sack.
     *
     * This is the second of two guards, and deliberately not the only one. It relies
     * on the window still being open at the exact moment the landing is processed,
     * and that depends on tick ordering: the world ticks (where AirJump.tick runs)
     * BEFORE the connection tick (where a player's movement, and so their landing, is
     * handled). AirJump.tick also holds the player's fallDistance at zero for the
     * whole flight, which needs no such assumption.
     */
    @SubscribeEvent
    public static void onLivingFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        if (data.getAirJumpTicks() > 0) {
            event.setCanceled(true);
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
        // Sealed in ice by Freeze: nothing gets through, and this is checked FIRST
        // because a shell is more absolute than any of the rules below it.
        //
        // Not optional, either — the ice sits where the victim's eyes are, so vanilla
        // suffocation would kill anything encased within seconds. The immunity is what
        // makes the ability a hold rather than an execution. See Frozens.
        //
        // BYPASSES_INVULNERABILITY still lands, so the void and /kill are unaffected,
        // exactly as they are for the shields.
        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && com.minecraft.atlamod.abilities.ice.Frozens.isFrozen(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

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

        // Compressed punches: a punch that actually LANDS hits for 10 rather than
        // whatever the bender's fists are worth.
        //
        // Done here because this is where melee damage is decided — the wave that goes
        // out on the same click is a separate thing, thrown from the left-click packet.
        // Keyed on the source having a player ATTACKER and not being a projectile,
        // which is the mod's existing test for "somebody hit this by hand" (see Air
        // Aura). Set rather than added, so it does not stack with a weapon.
        if (event.getSource().getEntity() instanceof ServerPlayer puncher
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            BendingData puncherData = puncher.getData(ModAttachments.BENDING_DATA);
            if (puncherData.isPunchingCompressed()) {
                event.setAmount(com.minecraft.atlamod.abilities.sound.Sound.damage(puncherData,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.PUNCH_DAMAGE));
            }
        }

        // Blue Fire doubles what the bender's own FIRE abilities hit for, and nothing
        // else.
        //
        // IS_FIRE with a player behind it is a close match for exactly that: every fire
        // ability in the mod damages through damageSources().inFire(), while the fire
        // sources a player can cause without bending — a Fire Aspect burn, a lit block,
        // spilled lava — all arrive with no attacker attached and so never qualify.
        //
        // Explosions used to be included as well, to catch Fireball. That was too broad:
        // it doubled any explosion the player caused, TNT included, which is not a fire
        // ability by any reading.
        net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer attackingPlayer
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {

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

            // --- WATER BREATHING PASSIVE ---
            // Topped up rather than granted as a potion effect, so nothing can dispel
            // it and no timer is ever shown.
            if (player.getAirSupply() < player.getMaxAirSupply()
                    && data.hasPassiveEquipped(
                            com.minecraft.atlamod.abilities.water.WaterBreathing.KEY)) {
                player.setAirSupply(player.getMaxAirSupply());
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

                    // Lightning Strength doubles it, which is what turns the usual
                    // 100 seconds to a full pool into the 50 the passive promises.
                    // Applied here because this is the only place that knows how much
                    // is being handed back.
                    if (data.hasPassiveEquipped(
                            com.minecraft.atlamod.abilities.lightning.LightningStrength.KEY)) {
                        regenAmount *= com.minecraft.atlamod.abilities.lightning
                                .LightningStrength.CHI_REGEN_MULTIPLIER;
                    }

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

            // --- ARMED TWO-PHASE ABILITY ---
            // Lets it draw what it is holding, and runs down the window for the ones
            // that have a time limit.
            if (!data.getActiveTwoPhaseAbility().isEmpty()) {
                AbilityHandler.tickArmedTwoPhase(player, data);
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

            // --- AIR JUMP TICK ---
            // Runs the fall-protection window and ends it on landing. Same shape as
            // Fire Leap and Fire Rain: cast once, then a countdown on the data.
            if (data.getAirJumpTicks() > 0) {
                com.minecraft.atlamod.abilities.air.AirJump.tick(player, data);
            }

            // --- FLIGHT PASSIVE TICK ---
            // Runs unconditionally: taking flight away when the passive is unequipped
            // or the chi runs out is as much this call's job as granting it.
            com.minecraft.atlamod.abilities.air.Flight.tick(player, data);

            // --- BENDING LOCKOUT (Deafen) ---
            if (data.getBendingLockedTicks() > 0) {
                data.setBendingLockedTicks(data.getBendingLockedTicks() - 1);
            }

            // --- SOUND TOGGLES, BILLED BY THE SECOND ---
            // Compressed punches and Sound wall are toggles rather than channels, so
            // the dispatcher's channel billing does not reach them — they are charged
            // here instead, on the same one-second beat, and switch themselves off the
            // moment the chi runs out.
            if (data.isPunchingCompressed()) {
                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.XP_PER_SECOND)) {
                    data.setPunchingCompressed(false);
                }
            }

            if (com.minecraft.atlamod.abilities.sound.SoundWalls.has(player)) {
                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.sound.SoundWall.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.sound.SoundWall.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.sound.SoundWalls.drop(player);
                }
            }

            // --- BASS BOUNCE TICK ---
            // Cast and left running, like Fire Leap: the hop happens now and the slam
            // happens on landing.
            if (data.getBassBounceTicks() > 0) {
                com.minecraft.atlamod.abilities.sound.BassBounce.tick(player, data);
            }

            // --- LIGHTNING STRENGTH PASSIVE ---
            // Runs unconditionally, like Flight: taking the Speed back off when the
            // passive is unequipped is as much this call's job as granting it.
            com.minecraft.atlamod.abilities.lightning.LightningStrength.tick(player, data);

            // --- AVATAR LAST STAND ---
            // Resistance and Regeneration below three hearts, taken back off above
            // it. Runs unconditionally: taking the buffs away when the Avatar heals
            // up — or loses the title outright — is as much this call's job as
            // granting them.
            com.minecraft.atlamod.avatar.Avatar.tick(player, data);

            // --- EARTH ARMOR VISUAL ---
            // Broadcast only when it changes. Effects are synced to their owner alone,
            // so onlookers learn about the stone suit from here or not at all.
            boolean armored = player.hasEffect(com.minecraft.atlamod.ModEffects.EARTH_ARMOR);
            if (armored != data.isEarthArmorShown()) {
                data.setEarthArmorShown(armored);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        player, new com.minecraft.atlamod.network.EarthArmorPacket(player.getId(), armored));
            }
        }
    }

    /**
     * Charges a per-second sound toggle, on the same beat chi regen uses.
     *
     * Returns false when the bender can no longer afford it, which is the caller's cue
     * to switch the toggle off. Spending goes through consumeChi so the regen delay is
     * re-armed each second, exactly as it is for a channel — a toggle that quietly
     * regenerated its own upkeep would be free.
     *
     * @return true if the toggle may keep running
     */
    private static boolean chargeSoundToggle(net.minecraft.server.level.ServerPlayer player,
                                             BendingData data, int chiPerSecond, int xpPerSecond) {
        if (player.tickCount % 20 != 0) return true;

        if (data.getCurrentChi() < chiPerSecond) return false;

        data.consumeChi(chiPerSecond);
        com.minecraft.atlamod.abilities.AbilitySupport.grantXp(data, xpPerSecond);
        com.minecraft.atlamod.abilities.AbilitySupport.syncData(player, data);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // Belt and braces alongside the death handler: a client that somehow still
            // believes in the stone suit is corrected the moment the bender is back.
            BendingData armorData = player.getData(ModAttachments.BENDING_DATA);
            armorData.setEarthArmorShown(false);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    player, new com.minecraft.atlamod.network.EarthArmorPacket(player.getId(), false));

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

                // The lives counter needs the same treatment as the passives: the
                // server still knows, but the client's copy is rebuilt on respawn,
                // and a death is exactly when that number has just changed.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.minecraft.atlamod.network.SyncAvatarPacket(
                                data.isAvatar(), data.getAvatarLives()));
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
