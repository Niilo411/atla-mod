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
        // The Spirit World's own wildlife. Vanilla's spawner cannot produce it — see
        // SpiritSpawner — so it is driven from here, and returns immediately for every
        // level but the one.
        var spirit = com.minecraft.atlamod.spirit.SpiritWorld.level(event.getServer());
        if (spirit != null) com.minecraft.atlamod.spirit.SpiritSpawner.tick(spirit);

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
        com.minecraft.atlamod.abilities.metal.MetalShields.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.combustion.CombustionBeams.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.blood.BloodHolds.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.blood.FleshShields.tickAll(event.getServer());

        // The ten-second window a chi blocker has to land their five hits.
        com.minecraft.atlamod.abilities.nobending.ChiBlocks.tickAll(event.getServer());

        com.minecraft.atlamod.abilities.lava.LavaRivers.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.lava.LavaGeysers.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.lava.LavaTsunamis.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.lava.LavaRains.tickAll(event.getServer());
        // Ticked LAST of the lava group, for the reason MetalWorks and IceWorks are:
        // the four above pour their own blocks through LavaWorks, so anything they lay
        // this tick is already on its timer by the time the sweep runs.
        com.minecraft.atlamod.abilities.lava.LavaWorks.tickAll(event.getServer());
        // Ticked LAST of the metal pair: the shields hand their own blocks back
        // through MetalWorks, so anything they release still gets settled here.
        com.minecraft.atlamod.abilities.metal.MetalWorks.tickAll(event.getServer());

        com.minecraft.atlamod.abilities.gravity.GravitySlams.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.gravity.GravityOrbits.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.gravity.GravityEncases.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.gravity.GravityCrushes.tickAll(event.getServer());
        com.minecraft.atlamod.abilities.gravity.MeteorBlocks.tickAll(event.getServer());

        // Tells everybody when the sky changes. Checked once a second — see the class.
        com.minecraft.atlamod.events.WorldEventAnnouncer.tick(event.getServer());

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
            com.minecraft.atlamod.abilities.metal.MetalShields.forgetLevel(level);
            com.minecraft.atlamod.abilities.combustion.CombustionBeams.forgetLevel(level);
            com.minecraft.atlamod.abilities.blood.BloodHolds.forgetLevel(level);
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.forgetLevel(level);
            com.minecraft.atlamod.abilities.blood.BloodPuppets.forgetLevel(level);
            com.minecraft.atlamod.abilities.blood.FleshShields.forgetLevel(level);
            com.minecraft.atlamod.abilities.metal.MetalWorks.forgetLevel(level);

            com.minecraft.atlamod.abilities.lava.LavaRivers.forgetLevel(level);
            com.minecraft.atlamod.abilities.lava.LavaGeysers.forgetLevel(level);
            com.minecraft.atlamod.abilities.lava.LavaTsunamis.forgetLevel(level);
            com.minecraft.atlamod.abilities.lava.LavaRains.forgetLevel(level);
            // Cools LAST: the four above hand their own blocks back through LavaWorks,
            // so anything they release still gets settled by this sweep. It matters more
            // here than anywhere else — lava left behind in an unloading level would be
            // an unbreakable block nothing in the game could ever remove.
            com.minecraft.atlamod.abilities.lava.LavaWorks.forgetLevel(level);

            // Melts LAST: the two above hand their own blocks back through IceWorks,
            // so anything they release still gets settled by this sweep.
            com.minecraft.atlamod.abilities.ice.IceWorks.forgetLevel(level);

            com.minecraft.atlamod.abilities.gravity.GravitySlams.forgetLevel(level);
            com.minecraft.atlamod.abilities.gravity.GravityOrbits.forgetLevel(level);
            com.minecraft.atlamod.abilities.gravity.GravityEncases.forgetLevel(level);
            com.minecraft.atlamod.abilities.gravity.GravityCrushes.forgetLevel(level);
            com.minecraft.atlamod.abilities.gravity.MeteorBlocks.forgetLevel(level);
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
     * Puts the Bloodbending Scroll in the cleric's book, for 5 rabbit feet.
     *
     * Same two levels as the other five scrolls, for the same reason: a villager picks
     * only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onClericTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.CLERIC) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.RABBIT_FOOT, 5),
                    new net.minecraft.world.item.ItemStack(Atlamod.BLOOD_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Lavabending Scroll in the shepherd's book, for 5 nether bricks.
     *
     * Same two levels as the other six scrolls, for the same reason: a villager picks
     * only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onShepherdTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.NETHER_BRICK, 5),
                    new net.minecraft.world.item.ItemStack(Atlamod.LAVA_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Combustionbending Scroll in the armorer's book, for 32 gunpowder.
     *
     * Same two levels as the other four scrolls, for the same reason: a villager picks
     * only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onArmorerTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.ARMORER) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.GUNPOWDER, 32),
                    new net.minecraft.world.item.ItemStack(Atlamod.COMBUSTION_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Metalbending Scroll in the mason's book, for 4 iron blocks.
     *
     * Same two levels as the other three scrolls, for the same reason: a villager
     * picks only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onMasonTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.MASON) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.IRON_BLOCK, 4),
                    new net.minecraft.world.item.ItemStack(Atlamod.METAL_SCROLL.get()),
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

    /**
     * Puts the Gravitybending Scroll in the cartographer's book, for 6 pistons.
     *
     * Same two levels as every other scroll, for the same reason: a villager picks
     * only a couple of trades at random from each level's pool.
     */
    @SubscribeEvent
    public static void onCartographerTrades(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() != net.minecraft.world.entity.npc.VillagerProfession.CARTOGRAPHER) return;

        for (int level : new int[] { 1, 3 }) {
            event.getTrades().get(level).add(new net.neoforged.neoforge.common.BasicItemListing(
                    new net.minecraft.world.item.ItemStack(
                            net.minecraft.world.item.Items.PISTON, 6),
                    new net.minecraft.world.item.ItemStack(Atlamod.GRAVITY_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Pushes a player's element list back to their own client.
     *
     * Pulled out when /bend add and /bend remove gained a target: the packet has to go
     * to the player who was CHANGED rather than to whoever typed the command, and two
     * hand-written copies of a six-field packet is two places to get that wrong.
     */
    private static void syncElements(ServerPlayer player, BendingData data) {
        PacketDistributor.sendToPlayer(player, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()
        ));
    }

    /**
     * Suggests the elements the mod actually has abilities for.
     *
     * Shared by add and remove. On add this is the better half of gating the command — a
     * refusal tells you afterwards that you were wrong, where a suggestion means you
     * never were.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestElements(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
                com.minecraft.atlamod.abilities.ElementPaths.bendable(), builder);
    }

    /**
     * /bend add &lt;targets&gt; &lt;element&gt;, also reachable as /bend element add.
     *
     * Built by a method rather than written inline because it is registered twice. Each
     * call returns a FRESH node tree, which is what Brigadier needs — a single node
     * cannot hang under two parents — so the two spellings share this implementation
     * instead of being two copies that can drift apart.
     *
     * The target is NOT optional, and there is deliberately no second "just me" form
     * beside it. Brigadier would have to tell "/bend add fire" from "/bend add Steve
     * fire" by trying one branch and falling through to the other, which makes a typo in
     * an element name read as a missing player instead of a bad element. @s is two
     * characters and says exactly what it means.
     */
    /**
     * /bend event &lt;name&gt; — moves the clock to the moment that event begins.
     *
     * IT SETS THE TIME RATHER THAN SETTING A FLAG, and that is the only honest way to do
     * it. Every world event is derived from the clock — see {@link com.minecraft.atlamod.events.WorldEvents} —
     * so there is nothing to switch on: no field says an event is running, and inventing
     * one would be a second source of truth that could disagree with the sky. Moving the
     * clock to where the event already happens makes it happen for exactly the same
     * reason it ever does, run its natural length, and end by itself.
     *
     * FORWARDS ONLY. A start that has already gone by today is not the next one, and
     * winding the clock back would take a day off everything else in the world that
     * counts them — sleep, crops, villager restocks, and this mod's own other events.
     * The cost is that "start it now" can jump several days, which is said plainly in the
     * reply rather than happening silently.
     *
     * EVERY DIMENSION IS SET, which is what vanilla's own /time set does. Skipping the
     * others would leave the Nether and the End on a different day from the Overworld,
     * and this mod asks whichever level is to hand when it wants the time.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> startEvent() {
        return Commands.literal("event")
                .then(Commands.argument("name", word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(com.minecraft.atlamod.events.WorldEvents.Event.values())
                                        .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                                        .toList(),
                                builder))
                        .executes(context -> {
                            String name = getString(context, "name");

                            com.minecraft.atlamod.events.WorldEvents.Event chosen = null;
                            for (com.minecraft.atlamod.events.WorldEvents.Event value
                                    : com.minecraft.atlamod.events.WorldEvents.Event.values()) {
                                if (value.name().equalsIgnoreCase(name)) chosen = value;
                            }

                            if (chosen == null) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                        "There is no event called \"" + name + "\". Try one of: "
                                                + java.util.Arrays.stream(
                                                        com.minecraft.atlamod.events.WorldEvents.Event.values())
                                                .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                                                .reduce((a, b) -> a + ", " + b).orElse("")));
                                return 0;
                            }

                            var server = context.getSource().getServer();
                            var overworld = server.overworld();

                            long now = overworld.getDayTime();

                            // ALREADY RUNNING is answered rather than obeyed. nextStart
                            // only ever looks forward, so asking for an event you are
                            // standing in would skip a whole period — eleven days for the
                            // eclipse — to reach the next one. Nobody typing "start it"
                            // during it means that, and restarting would mean winding the
                            // clock back, which takes a day off everything else that
                            // counts them.
                            final com.minecraft.atlamod.events.WorldEvents.Event running = chosen;
                            if (com.minecraft.atlamod.events.WorldEvents.isActive(overworld, chosen)) {
                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                        running.title() + " is already happening."), false);
                                return 1;
                            }

                            long start = com.minecraft.atlamod.events.WorldEvents.nextStart(now, chosen);

                            // Every dimension, the way /time set does it.
                            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                                level.setDayTime(start);
                            }

                            final com.minecraft.atlamod.events.WorldEvents.Event started = chosen;
                            long skippedDays = (start - now) / 24000L;
                            int minutes = com.minecraft.atlamod.events.WorldEvents.length(chosen) / 1200;

                            // Said rather than assumed: a settings file with the event
                            // switched off would otherwise leave the clock moved and
                            // nothing whatsoever happening, which reads as the command
                            // being broken rather than as the setting doing its job.
                            if (!com.minecraft.atlamod.events.WorldEvents.isEnabled(chosen)) {
                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                        "Time moved to " + started.title() + ", but that event is switched"
                                                + " off in this world's settings, so nothing will happen."), true);
                                return 1;
                            }

                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    started.title() + " begins now"
                                            + (skippedDays > 0 ? " (skipped " + skippedDays + " day"
                                                    + (skippedDays == 1 ? "" : "s") + ")" : "")
                                            + ". It runs about " + minutes + " minutes."), true);
                            return 1;
                        })
                );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> addElement() {
        return Commands.literal("add")
                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                        .then(Commands.argument("element", word())
                                .suggests((context, builder) -> suggestElements(builder))
                                .executes(context -> {
                                    // Lowercased before anything else looks at it.
                                    // ElementPaths switches on a lowercased name and the
                                    // skill tree keys off the stored string, so a granted
                                    // "Fire" would be a second element sitting beside
                                    // "fire" with no tree of its own — the same broken
                                    // state an invented element leaves behind.
                                    String element = getString(context, "element")
                                            .toLowerCase(java.util.Locale.ROOT);

                                    // Only elements the mod actually has abilities for.
                                    // This used to take any word at all, so "/bend add @s
                                    // grass" granted an element with no tree, no abilities
                                    // and no emblem, which the game then had no way to do
                                    // anything with or to explain.
                                    if (!com.minecraft.atlamod.abilities.ElementPaths.exists(element)) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "There is no element called \"" + element + "\". Try one of: "
                                                        + String.join(", ",
                                                        com.minecraft.atlamod.abilities.ElementPaths.bendable())));
                                        return 0;
                                    }

                                    int changed = 0;

                                    for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument
                                            .getPlayers(context, "targets")) {
                                        BendingData data = player.getData(ModAttachments.BENDING_DATA);
                                        if (data.getUnlockedElements().contains(element)) continue;

                                        data.getUnlockedElements().add(element);
                                        if (data.getActiveElement().isEmpty()) data.setActiveElement(element);

                                        // NO BENDING IS GRANTED LIKE ANYTHING ELSE now,
                                        // and the special case that used to sit here is
                                        // gone. It overwrote the recipient's main element
                                        // so the path would register, which meant gifting
                                        // it to a firebender silently took their chi away
                                        // — the thing this command has no business doing.
                                        // Nothing needs overwriting any more: what makes
                                        // somebody a non-bender is having no bending art
                                        // at all, which the unlocked list above answers by
                                        // itself. See NoBending.
                                        //
                                        // A player who has not reached the selection
                                        // screen yet DOES take the gift as their own,
                                        // whatever it is. Without this their main element
                                        // stays empty, so the screen still opens on their
                                        // next login and overwrites whatever was given.
                                        if (!data.hasChosenElement()) {
                                            data.setMainElement(element);
                                            data.setActiveElement(element);
                                        }

                                        player.setData(ModAttachments.BENDING_DATA, data);
                                        syncElements(player, data);
                                        changed++;
                                    }

                                    final int total = changed;
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "Gave " + element + " to " + total + " player(s)."), true);
                                    return changed;
                                })
                        )
                );
    }

    /**
     * /bend remove &lt;targets&gt; &lt;element&gt;, also reachable as /bend element remove.
     *
     * Deliberately NOT gated the way add is. Saves made before add was gated may be
     * holding an element that does not exist, and taking it back off them is the only way
     * to clean that up — a remove that only accepted real elements would refuse to undo
     * the exact mess that gate exists to prevent. The suggestions still list the real
     * ones, since that is what is nearly always wanted.
     *
     * The match is case-INSENSITIVE, and the stored spelling is what gets removed. Add
     * lowercases now but did not always, so an old save may be holding "Fire"; asking the
     * player to work out which case it was written in would be a puzzle with no clue.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> removeElement() {
        return Commands.literal("remove")
                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                        .then(Commands.argument("element", word())
                                .suggests((context, builder) -> suggestElements(builder))
                                .executes(context -> {
                                    String element = getString(context, "element");
                                    int changed = 0;

                                    for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument
                                            .getPlayers(context, "targets")) {
                                        BendingData data = player.getData(ModAttachments.BENDING_DATA);

                                        String held = null;
                                        for (String named : data.getUnlockedElements()) {
                                            if (named.equalsIgnoreCase(element)) {
                                                held = named;
                                                break;
                                            }
                                        }
                                        if (held == null) continue;

                                        data.getUnlockedElements().remove(held);
                                        if (data.getActiveElement().equalsIgnoreCase(held)) {
                                            data.setActiveElement(data.getUnlockedElements().isEmpty()
                                                    ? "" : data.getUnlockedElements().get(0));
                                        }
                                        player.setData(ModAttachments.BENDING_DATA, data);
                                        syncElements(player, data);
                                        changed++;
                                    }

                                    final int total = changed;
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "Took " + element + " from " + total + " player(s)."), true);
                                    return changed;
                                })
                        )
                );
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bend")
                // EVERY /bend command needs permission level 2, which is exactly
                // "cheats enabled in singleplayer, or op on a server" — vanilla ties
                // those two together, so one check covers both.
                //
                // On the ROOT rather than on each subcommand: Brigadier applies a
                // requires() to everything beneath it, so nothing added under /bend
                // later can be left ungated by being forgotten. It also hides the whole
                // command from the suggestion list for anyone who cannot use it, rather
                // than offering commands that will only refuse.
                .requires(source -> source.hasPermission(2))

                // ELEMENT COMMANDS — /bend add|remove <targets> <element>, and the same
                // two again under /bend element.
                //
                // Both spellings exist on purpose and neither is a copy: addElement()
                // and removeElement() each build a fresh node tree, so there is one
                // implementation behind all four entry points. The grouped form reads
                // better beside /bend avatar and is what anyone looking for "the command
                // that removes an element" would reach for; the ungrouped pair is what
                // already existed, and dropping it would break every note, macro and
                // habit built on it for no gain.
                .then(addElement())
                .then(removeElement())
                .then(Commands.literal("element")
                        .then(addElement())
                        .then(removeElement())
                )

                // WORLD EVENT COMMAND — /bend event <name>
                .then(startEvent())

                // TEMPLE COMMAND — /bend temple
                //
                // Builds one where you stand, for testing and for anyone who would rather
                // not go looking. It kept working unchanged when the procedural temple was
                // replaced by hand-built .nbt structures, which was the point of putting
                // every temple behind TempleStructure.placeAt in the first place.
                .then(Commands.literal("temple")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            // The structure is placed from its CORNER, so the player is
                            // put at the middle of it rather than inside a wall. One below
                            // their feet, since the temple's own floor is its bottom layer.
                            var size = com.minecraft.atlamod.spirit.TempleStructure
                                    .sizeFor(player.serverLevel());
                            var corner = player.blockPosition().below()
                                    .offset(-size.getX() / 2, 0, -size.getZ() / 2);

                            var temple = com.minecraft.atlamod.spirit.TempleStructure.placeAt(
                                    player.serverLevel(), corner);

                            // A missing or misnamed .nbt is the only way this happens, and
                            // it is worth saying out loud rather than reporting success
                            // over an empty patch of ground.
                            if (temple == null) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                        "No temple structure could be loaded. Check that the .nbt files are in "
                                                + "data/atlamod/structure/."));
                                return 0;
                            }

                            // Built in the Spirit World, it comes up already burning —
                            // nothing can bend there, so a dark frame would never open.
                            com.minecraft.atlamod.spirit.SpiritWorld.lightIfHere(
                                    player.serverLevel(), temple);

                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    com.minecraft.atlamod.spirit.SpiritWorld.isSpiritWorld(player.level())
                                            ? "Built a spirit temple. Its portal is already open."
                                            : "Built a spirit temple. Its portal is unlit — use "
                                            + com.minecraft.atlamod.spirit.SpiritPortals.REQUIRED_USES
                                            + " ability uses within "
                                            + (com.minecraft.atlamod.spirit.SpiritPortals.WINDOW_TICKS / 20)
                                            + "s, within "
                                            + com.minecraft.atlamod.spirit.SpiritPortals.RADIUS
                                            + " blocks of it, to open it."), true);
                            return temple == null ? 0 : 1;
                        })
                )
                // LEVEL COMMAND — /bend level <targets> <amount>
                .then(Commands.literal("level")
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .then(Commands.argument("amount", integer(1))
                                        .executes(context -> {
                                            int amount = getInteger(context, "amount");
                                            int changed = 0;

                                            for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument
                                                    .getPlayers(context, "targets")) {
                                                BendingData data = player.getData(ModAttachments.BENDING_DATA);

                                                // Bumps the level WITHOUT touching xp, so
                                                // the two can drift apart. That has always
                                                // been true of this command.
                                                data.setLevel(data.getLevel() + amount);
                                                player.setData(ModAttachments.BENDING_DATA, data);

                                                PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));
                                                changed++;
                                            }

                                            final int total = changed;
                                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                    "Gave " + amount + " level(s) to " + total + " player(s)."), true);
                                            return changed;
                                        })
                                )
                        )
                )
                // AVATAR COMMANDS
                //
                // The permission check is on the /bend root and covers these too, so
                // there is no second requires() here — one would only be a copy that
                // could drift out of step with it.
                //
                // "cycle" is a literal and the player is an argument, so Brigadier
                // tries the literal first and there is no ambiguity between
                // "/bend avatar cycle ..." and "/bend avatar <player>" even for a
                // player unlucky enough to be called "cycle".
                .then(Commands.literal("avatar")
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

                                    // Refused for a non-bender, and SAID so rather than
                                    // reporting a success that did not happen. Becoming
                                    // the Avatar hands over all four elements, which is
                                    // the exact opposite of what they chose.
                                    if (!com.minecraft.atlamod.avatar.Avatar.grant(
                                            context.getSource().getServer(), target)) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                target.getGameProfile().getName()
                                                        + " has no bending. The Avatar is always a bender."));
                                        return 0;
                                    }

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

        if (living.hasEffect(com.minecraft.atlamod.ModEffects.STUNNED)) {
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

        // The Spirit World's low-gravity tide, extended to mobs — off by default
        // (AtlaConfig's affectsMobs), and checked first because it is a single
        // volatile read that keeps this a no-op for every entity in the game
        // whenever the setting is left off. Players are handled separately, once
        // per player per tick, from ServerEvents' own player tick — not here, so
        // they are excluded rather than run through this path twice.
        if (com.minecraft.atlamod.AtlaConfig.spiritTideAffectsMobs()
                && !(living instanceof net.minecraft.world.entity.player.Player)) {
            com.minecraft.atlamod.spirit.SpiritGravity.tick(living);
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

        for (com.minecraft.atlamod.BendingArmorSuit suit
                : com.minecraft.atlamod.BendingArmorSuit.VALUES) {
            if (suit.isWornBy(target)) {
                PacketDistributor.sendToPlayer(watcher,
                        new com.minecraft.atlamod.network.BendingArmorPacket(
                                target.getId(), suit.ordinal(), true));
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A part-finished portal sequence is ten seconds of state and is not worth
            // keeping; the way home out of the Spirit World is transient by design and
            // falls back to the world spawn. See SpiritTravel.
            com.minecraft.atlamod.spirit.SpiritPortals.forget(player.getUUID());
            com.minecraft.atlamod.spirit.SpiritTravel.forget(player.getUUID());

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
            com.minecraft.atlamod.abilities.metal.MetalShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.combustion.CombustionBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodHolds.forgetPlayer(player);
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodPuppets.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.FleshShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lava.LavaRains.forgetPlayer(player);

            com.minecraft.atlamod.abilities.gravity.GravitySlams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityOrbits.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityEncases.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityCrushes.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);
        }
    }

    /**
     * A hit that actually landed, for Chi block's punch counter.
     *
     * ON Post RATHER THAN ON THE BIG ORDERED HANDLER ABOVE, and that is the whole reason
     * this is a handler of its own. {@code LivingIncomingDamageEvent} fires for damage
     * that is about to be TRIED, and half the rules in this file cancel it — a shield, an
     * aura, an ice shell. Counting there would let a chi blocker beat on somebody's raised
     * Water Shield five times and have it work. Post only fires for damage that got
     * through.
     *
     * DIRECT HITS ONLY. A punch is something you have to close the distance for, which is
     * the entire character of this path; letting an arrow or a thrown ability count would
     * hand the marked target's five hits to someone standing well back, and the mark would
     * become a ranged opener rather than a reason to get in close.
     */
    @SubscribeEvent
    public static void onDamageLanded(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        // isDirect() is "the thing that dealt this IS the thing that caused it" — true for
        // a fist or a sword, false for an arrow or any of this mod's projectiles, where
        // the direct entity is the shot and the causing entity is whoever loosed it.
        if (!event.getSource().isDirect()) return;

        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.countHit(attacker, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        // Anything at all, not just players: a mob sealed in ice can still be killed
        // by something that bypasses invulnerability, and its shell has to come down
        // with it rather than standing there empty until its timer runs out.
        com.minecraft.atlamod.abilities.ice.Frozens.forgetEntity(event.getEntity());

        // A non-bender earns XP by killing, since they cannot meditate and their two
        // abilities pay nothing. Granted to whoever landed the killing blow, scaled by
        // what they killed — see NoBending.xpForKill.
        //
        // Ordered cheapest first, like every handler here that fires for the whole world:
        // this runs for every death of every mob on the server, so the attacker is tested
        // before anything is read off them.
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && killer != event.getEntity()) {
            BendingData killerData = killer.getData(ModAttachments.BENDING_DATA);

            if (com.minecraft.atlamod.abilities.nobending.NoBending.is(killerData)) {
                com.minecraft.atlamod.abilities.AbilitySupport.grantXp(killerData,
                        com.minecraft.atlamod.abilities.nobending.NoBending.xpForKill(event.getEntity()));
                com.minecraft.atlamod.abilities.AbilitySupport.syncData(killer, killerData);
            }
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            // A bending armor suit is drawn from a client-side set keyed on entity
            // id, and a respawned player REUSES its id on both sides — so a death would
            // otherwise leave the art on a bender who no longer has the effect. The
            // per-tick broadcast cannot catch it either: the mask it compares against is
            // transient and comes back empty, so it sees no change and says nothing.
            // Told explicitly here instead.
            data.clearArmorSuitsShown();
            for (com.minecraft.atlamod.BendingArmorSuit suit
                    : com.minecraft.atlamod.BendingArmorSuit.VALUES) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        player, new com.minecraft.atlamod.network.BendingArmorPacket(
                                player.getId(), suit.ordinal(), false));
            }
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
            com.minecraft.atlamod.abilities.metal.MetalShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.combustion.CombustionBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodHolds.forgetPlayer(player);
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodPuppets.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.FleshShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lava.LavaRains.forgetPlayer(player);

            com.minecraft.atlamod.abilities.gravity.GravitySlams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityOrbits.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityEncases.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityCrushes.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);

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

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

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

            com.minecraft.atlamod.abilities.blood.Blood.sync(player, data);
        }
    }
    /**
     * Mining pays XP — Spirit Ore pays every bender, and a non-bender is ALSO paid a
     * trickle for any block at all.
     *
     * The SHARD Spirit Ore drops is a loot table and needs nothing here; this is only
     * the XP, which has nowhere else to come from — vanilla's own experience drops go
     * to the player's levels, and bending XP is a different pot entirely.
     *
     * Spirit Ore's own bonus is granted whoever mines it, without asking whether they
     * have chosen an element yet. XP banked before a choice is simply theirs when they
     * make one, and refusing it would mean the same ore was worth less to a player who
     * happened to arrive earlier. A non-bender breaking Spirit Ore gets BOTH: the ore's
     * own figure and the flat per-block one, since one is what the ore is worth and the
     * other is what mining itself is worth to a path with no other physical income.
     *
     * No longer ordered "block before player" the way this used to be: a non-bender's
     * flat XP applies to every block, so there is nothing left to reject before reading
     * the player. Still cheapest-check-first within that — the player type, then
     * NoBending.is (a couple of list entries at most).
     */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);
        int gained = 0;

        if (com.minecraft.atlamod.abilities.nobending.NoBending.is(data)) {
            gained += com.minecraft.atlamod.abilities.nobending.NoBending.XP_PER_BLOCK_MINED;
        }

        boolean spiritOre = event.getState().is(Atlamod.SPIRIT_ORE.get());
        if (spiritOre) {
            gained += com.minecraft.atlamod.spirit.island.SpiritOre.xpPerBlock();
        }

        if (gained <= 0) return;

        com.minecraft.atlamod.abilities.AbilitySupport.grantXp(data, gained);
        com.minecraft.atlamod.abilities.AbilitySupport.syncData(player, data);

        // On the action bar rather than in chat: a vein is several blocks and a line
        // each would bury whatever else the player was being told. Only shown for
        // Spirit Ore's own figure, matching what this already told a bender — a
        // non-bender's flat trickle from ordinary stone is not worth a line every
        // single block.
        if (spiritOre) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b+" + gained + " bending XP §7(" + data.getXp() + "/"
                            + com.minecraft.atlamod.abilities.AbilitySupport.xpPerLevel() + ")"), true);
        }
    }

    /**
     * Right clicking a spirit shrine's beacon.
     *
     * ORDERED CHEAPEST FIRST, and it has to be: this fires for every right click on every
     * block in the game, on both sides. The side, the hand, the block and the dimension are
     * all settled before {@link com.minecraft.atlamod.spirit.island.SpiritShrines#isShrine}
     * is asked anything, so an ordinary click never reaches the island arithmetic.
     *
     * MAIN HAND ONLY. Vanilla offers the main hand first and then the off hand, so handling
     * both would grant — or refuse — twice for one click.
     *
     * Cancelled outright rather than merely denying the block, which does two jobs at once:
     * the beacon's own screen never opens, and a block held in hand is not placed against
     * the shrine by someone who meant to use it.
     */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        net.minecraft.core.BlockPos pos = event.getPos();
        if (!event.getLevel().getBlockState(pos)
                .is(com.minecraft.atlamod.spirit.island.SpiritShrines.SHRINE_BLOCK)) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!com.minecraft.atlamod.spirit.island.SpiritShrines.isShrine(level, pos)) return;

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        com.minecraft.atlamod.spirit.island.SpiritShrines.use(player, pos);
    }

    /**
     * Every toggle goes out at the threshold.
     *
     * FIRED BEFORE THE MOVE, which is the whole reason this is its own handler rather
     * than more lines in the clone handler below. That one runs AFTER the change, on a
     * fresh BendingData whose transient flags have already come back false — so by the
     * time it looks, no toggle appears to be on and nothing would be switched off. Here
     * the player is still standing in the old level with their state intact.
     *
     * ASKS THE REGISTRY rather than naming the toggles, so one added later is covered
     * without this being touched. {@code isActive} answers false for everything that is
     * not a toggle and for every toggle that is not running, which is nearly all of them
     * nearly always — a hundred and six boolean reads on a portal, once.
     *
     * Each one goes out through its OWN deactivate, so whatever it does on the way down
     * still happens: Metal shield gives its blocks back, Compressed punches stamps its
     * cooldown, Fire Rocket closes the flight flags. Cleaning up by hand here would be a
     * second copy of every one of those.
     */
    /**
     * Forgets which world events were running when a server stops.
     *
     * Single player is one process that opens and closes worlds: without this, loading a
     * second world would start out believing the first one's sky was still overhead, and
     * the announcer would say nothing until it next changed.
     */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.minecraft.atlamod.events.WorldEventAnnouncer.forget();
    }

    @SubscribeEvent
    public static void onTravelToDimension(
            net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);

        for (com.minecraft.atlamod.abilities.Ability ability
                : com.minecraft.atlamod.abilities.AbilityRegistry.all().values()) {
            if (ability.isActive(player, data)) {
                ability.deactivate(player, data);
            }
        }

        player.setData(ModAttachments.BENDING_DATA, data);
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

        // The blood track too, for the same reason: this event fires on a dimension
        // change where copyOnDeath does not, and walking into the Nether should not
        // cost a bloodbender their standing.
        newData.setBloodXp(oldData.getBloodXp());
        newData.setBloodLevel(oldData.getBloodLevel());

        // The spirit shrines too, and this one is load-bearing on both routes: a death
        // that reset the bonus would take back permanent max chi that was earned by
        // crossing the Spirit World, and a dimension change that forgot the LIST would let
        // every shrine be drawn from a second time.
        newData.setBonusMaxChi(oldData.getBonusMaxChi());
        newData.setAllUsedShrines(oldData.getUsedShrines());

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
            com.minecraft.atlamod.abilities.metal.MetalShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.combustion.CombustionBeams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodHolds.forgetPlayer(player);
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.BloodPuppets.forgetPlayer(player);
            com.minecraft.atlamod.abilities.blood.FleshShields.forgetPlayer(player);
            com.minecraft.atlamod.abilities.lava.LavaRains.forgetPlayer(player);

            com.minecraft.atlamod.abilities.gravity.GravitySlams.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityOrbits.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityEncases.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravityCrushes.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);

            // The rocket cannot follow its rider through a portal either, and unlike a
            // scooter it leaves something behind if it is not put out: this event builds
            // a FRESH BendingData and copies the saved fields across by hand, so the
            // transient "is the rocket lit" flag comes over as false — while the vanilla
            // flight flags it opened are saved in player NBT and come over as they were.
            // Nothing else would ever close them, because nothing would believe the
            // rocket was still running. That is permanent creative flight from one trip
            // through a portal.
            //
            // The login and respawn nets cover the other two ways out; a dimension change
            // fires neither, so it needs its own. Creative and spectator are skipped
            // inside stopFlight, so legitimate flight is never taken away.
            if (!player.isCreative() && !player.isSpectator()
                    && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                com.minecraft.atlamod.abilities.fire.FireRocket.stopFlight(player);
            }

            BendingData data = player.getData(ModAttachments.BENDING_DATA);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.minecraft.atlamod.network.SyncBendingDataPacket(
                    data.getMainElement() == null ? "" : data.getMainElement(),
                    data.getActiveElement() == null ? "" : data.getActiveElement(),
                    data.getUnlockedElements(),
                    data.hasChosenElement(),
                    data.getUnlockedAbilities(),
                    data.getEquippedAbilities()
            ));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.minecraft.atlamod.network.SyncAvatarPacket(
                            data.isAvatar(), data.getAvatarLives()));

            com.minecraft.atlamod.abilities.blood.Blood.sync(player, data);
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
        // The Spirit World's low-gravity tide, checked FIRST and for whoever fell —
        // player or mob, since AtlaConfig's affectsMobs can extend the tide to both.
        // Vanilla charges for fall DISTANCE rather than for impact speed, so something
        // that drifted gently down would otherwise be billed exactly as if it had
        // plummeted. See SpiritGravity#cancelsFallDamage.
        if (com.minecraft.atlamod.spirit.SpiritGravity.cancelsFallDamage(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BendingData data = player.getData(ModAttachments.BENDING_DATA);

        // Two more ways to land for free, both player-only (BendingData has no
        // meaning for a mob) and both cancelling rather than reducing, for the same
        // reason as above — it takes the landing thud and the puff of dust with it.
        // The Gravitybending Scroll's confirmation window is the second — see
        // GravityScrollItem.
        if (data.getAirJumpTicks() > 0 || data.getGravityFallImmuneTicks() > 0) {
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

            // Repel Shield: not a cancel like the two full shields above, a division —
            // see ChanneledAbility#damageReductionFactor. Applied here, before anything
            // below has a chance to raise or lower the figure, so the shield always
            // divides the same blow the rest of this handler would otherwise see.
            double reduction = AbilityHandler.damageReductionFor(data, event.getSource());
            if (reduction > 1.0) {
                event.setAmount((float) (event.getAmount() / reduction));
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

            // Lava resistance: lava alone cannot touch them.
            //
            // Deliberately ONE damage type rather than the IS_FIRE tag Fire immunity
            // uses — the design's word is "Lava only", so a lavabender wearing this
            // still burns in an ordinary fire and still cooks on magma. Our own lava
            // hurts through vanilla's own lava source (see Lava.scorch), so this one
            // check covers real lava and every lava ability at once.
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                    && data.hasPassiveEquipped(
                            com.minecraft.atlamod.abilities.lava.LavaResistance.KEY)) {
                event.setCanceled(true);
                return;
            }
        }

        // Flesh shield: the wall of bodies takes the blow instead, split between
        // whoever is still standing in it.
        //
        // Checked here rather than in the ability because this is where damage is
        // decided, and it RETURNS rather than falling through — a blow the shield ate
        // never reached the bender, so none of the rules below it apply.
        if (event.getEntity() instanceof ServerPlayer shielded
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && com.minecraft.atlamod.abilities.blood.FleshShields.absorb(
                        shielded, event.getAmount(), event.getSource())) {
            event.setCanceled(true);
            return;
        }

        // The Combustionbending Scroll spares its own reader. Checked BEFORE the
        // resistance passive below, because this is a flat no rather than a reduction.
        if (event.getEntity() instanceof ServerPlayer reader
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                && reader.getData(ModAttachments.BENDING_DATA).getBlastImmuneTicks() > 0) {
            event.setCanceled(true);
            return;
        }

        // Combustion resistance: explosions do a quarter of their damage.
        //
        // Done here because vanilla has no explosion-resistance attribute to modify —
        // blast protection is an enchantment, not a number anything can be given. The
        // whole IS_EXPLOSION tag rather than a list of sources, so a bender's own
        // charges, their misfires, TNT, creepers and beds are all covered at once.
        if (event.getEntity() instanceof ServerPlayer blasted
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            BendingData blastedData = blasted.getData(ModAttachments.BENDING_DATA);
            if (blastedData.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.combustion.CombustionResistance.KEY)) {
                event.setAmount(event.getAmount()
                        * com.minecraft.atlamod.abilities.combustion.CombustionResistance.DAMAGE_MULTIPLIER);
            }
        }

        // Combustion nuke's damage cap: its four blasts together may take at most most
        // of a victim's maximum health, so a full-health target is left standing rather
        // than deleted.
        //
        // AFTER the resistance above, deliberately — the passive reduces the blow and
        // this clamps what is left, so wearing it still helps rather than being
        // swallowed by the cap. Every living thing is covered, mobs and the caster
        // included, and this does nothing at all unless a capped cast is going off at
        // this exact moment (see Combustion.capped).
        if (com.minecraft.atlamod.abilities.combustion.Combustion.isCapping()
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            float allowed = com.minecraft.atlamod.abilities.combustion.Combustion.cap(
                    event.getEntity(), event.getAmount());

            if (allowed <= 0.0F) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(allowed);
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
                // Compressed punches wins, because it is an ability being actively
                // held up and paid for by the second, where Tough knuckles is a
                // permanent floor. They are not added together.
                event.setAmount(com.minecraft.atlamod.abilities.sound.Sound.damage(puncherData,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.PUNCH_DAMAGE));

            } else if (puncherData.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.metal.ToughKnuckles.KEY)
                    && puncher.getMainHandItem().isEmpty()) {

                // Tough knuckles replaces what an EMPTY hand is worth, and only an
                // empty one: the passive is about punching, and letting it apply while
                // holding a weapon would make it a flat damage buff that happened to
                // be called knuckles. Raised to, never lowered from — a bare fist is
                // already worth more than this to somebody with a Strength potion.
                event.setAmount(Math.max(event.getAmount(),
                        com.minecraft.atlamod.abilities.metal.ToughKnuckles.PUNCH_DAMAGE));

            } else if (puncherData.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.nobending.SwordMastery.KEY)
                    && puncher.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem) {

                // Sword Mastery ADDS to what the sword and its enchantments already hit
                // for, the mirror of Tough knuckles' replace-an-empty-hand — a sword
                // already has a real damage figure of its own to build on, where a bare
                // fist does not.
                event.setAmount(event.getAmount()
                        + com.minecraft.atlamod.abilities.nobending.SwordMastery.bonusFor(puncherData));
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

        // A world event's damage multiplier, applied to ANY damage source rather than to
        // fire's alone — a blood moon lifts waterbending, which hits through indirectMagic
        // and drown, and neither is a tag this handler could key off on its own.
        //
        // WHICH ELEMENT is read off the attacker, where the dispatcher records it for the
        // duration of an ability's effect. That is exact for anything that damages as it
        // casts, which is nearly everything; a PROJECTILE lands long after its cast has
        // finished, so it carries its own copy taken at launch instead. See
        // BendingProjectiles and BendingData.castingElement.
        if (event.getSource().getEntity() instanceof ServerPlayer caster) {
            String casting = caster.getData(ModAttachments.BENDING_DATA).getCastingElement();

            if (!casting.isEmpty()) {
                float eventScale = com.minecraft.atlamod.events.WorldEvents
                        .damageMultiplier(caster.level(), casting);

                if (eventScale != 1.0F) event.setAmount(event.getAmount() * eventScale);
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

            // The Spirit World's tide of low gravity. Asked every tick for every player
            // because it is what takes the modifier back OFF someone who has walked out
            // of the dimension, which is as much its job as putting it on — and it does
            // nothing at all unless the answer has actually changed.
            com.minecraft.atlamod.spirit.SpiritGravity.tick(player);

            // --- FIRE IMMUNITY PASSIVE ---
            // Damage is cancelled in the damage handler, but burning is separate from
            // being hurt by it: without this the player stands there wreathed in
            // flames taking nothing, which reads as a bug rather than as immunity.
            if (player.isOnFire() && data.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.fire.FireImmunity.KEY)) {
                player.clearFire();
            }

            // --- LAVA RESISTANCE PASSIVE ---
            // The other half of it. Damage is cancelled in the damage handler, but
            // burning is separate from being hurt by it — without this a bender stands
            // in lava taking nothing while visibly ablaze, which reads as a bug rather
            // than as protection. The same split Fire immunity needs.
            //
            // Only while they are actually IN lava, which is what keeps this "lava
            // only" instead of a quiet second copy of Fire immunity: they leave with
            // nothing alight because it was cleared on the way, but an ordinary fire
            // still sets them burning and this never touches it. Our own lava never
            // ignites them in the first place (see Lava.scorch), so this is really
            // about the real stuff.
            if (player.isInLava() && data.hasPassiveEquipped(
                    com.minecraft.atlamod.abilities.lava.LavaResistance.KEY)) {
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

            // --- PASSIVES ARE SILENT IN THE SPIRIT WORLD ---
            // Set BEFORE anything below reads a passive, because almost everything below
            // does. Bending is already refused there; a passive is bending that happens
            // to need no cast, so leaving them running would have meant fire immunity,
            // flight and doubled chi regen all working in the one place nothing else does.
            data.setPassivesSuppressed(
                    com.minecraft.atlamod.spirit.SpiritWorld.isSpiritWorld(player.level()));

            // --- CHI BLOCKED ---
            // Counted down here rather than in the tracker, because the tracker only
            // knows about marks: a block outlives the mark that caused it, and could
            // in principle be applied by something that never marked anybody at all.
            com.minecraft.atlamod.abilities.nobending.ChiBlocks.tickBlocked(player, data);

            // --- CHI REGEN ---
            // Regen is held off for a few seconds after any chi is spent, so a cheap
            // ability can't be sustained indefinitely by regen alone. The countdown
            // runs every tick; the refill itself stays on the 1-second cadence.
            //
            // TWO THINGS SKIP IT ENTIRELY. A non-bender has no chi to refill — that is
            // what choosing no bending means, and the HUD draws them no bar either. And
            // a bender whose chi points have been struck is frozen where they stand,
            // which is the half of chi blocking that actually decides a fight: being
            // unable to cast for fifteen seconds is an inconvenience, coming out of it
            // with an empty pool is the punishment.
            //
            // Asked once and held rather than asked again later for the meditation
            // check below — same tick, same unlocked-elements list, so the answer
            // cannot have changed in between.
            boolean isNoBender = com.minecraft.atlamod.abilities.nobending.NoBending.is(data);
            if (isNoBender || data.isChiBlocked()) {
                // Nothing at all, deliberately — not even the delay countdown, which
                // exists only to pace a refill that is not going to happen.
                player.setData(ModAttachments.BENDING_DATA, data);
            } else if (data.getChiRegenDelay() > 0) {
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

                    // Spirit armor adds a fraction of a percent per piece worn, so the
                    // arithmetic runs in THOUSANDTHS and keeps whatever it could not hand
                    // over. Without the carry a 46.5% piece on a base of 6 would round away
                    // and the pieces would step unevenly — see BendingData's note.
                    //
                    // With nothing worn the bonus is 0, so this is exactly the old
                    // `regenAmount` and the carry never leaves zero.
                    int thousandths = regenAmount * (com.minecraft.atlamod.SpiritArmor.BONUS_SCALE
                            + com.minecraft.atlamod.SpiritArmor.regenBonusTenths(player))
                            + data.getChiRegenCarry();

                    regenAmount = thousandths / com.minecraft.atlamod.SpiritArmor.BONUS_SCALE;
                    data.setChiRegenCarry(thousandths % com.minecraft.atlamod.SpiritArmor.BONUS_SCALE);

                    data.setCurrentChi(Math.min(data.getMaxChi(), data.getCurrentChi() + regenAmount));

                    // Save the data and sync it to the UI
                    player.setData(ModAttachments.BENDING_DATA, data);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));
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
            // A non-bender cannot meditate. Meditation exists to fill a chi pool and to
            // earn XP from stillness, and they have neither — their XP comes from
            // killing instead (see NoBending). Stopped HERE rather than at the keybind
            // so it holds however the flag was set.
            if (data.isMeditating() && isNoBender) {
                data.setMeditating(false);
                data.setMeditateTickTimer(0);
                player.setData(ModAttachments.BENDING_DATA, data);
            }

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

                // XP every 1 second (20 ticks). The RATE is not fixed any more: the
                // Advanced meditating passive raises it with the bender's level, and
                // that one method is the only place the figure is decided — with the
                // passive unequipped it simply answers the old flat 2.
                if (data.getMeditateTickTimer() % 20 == 0) {
                    com.minecraft.atlamod.abilities.AbilitySupport.grantXp(data,
                            com.minecraft.atlamod.abilities.air.AdvancedMeditating.meditationRate(data));

                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));
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

            // --- COMBUSTION SCROLL BLAST IMMUNITY ---
            if (data.getBlastImmuneTicks() > 0) {
                data.setBlastImmuneTicks(data.getBlastImmuneTicks() - 1);
            }

            // --- GRAVITY SCROLL FALL IMMUNITY ---
            if (data.getGravityFallImmuneTicks() > 0) {
                data.setGravityFallImmuneTicks(data.getGravityFallImmuneTicks() - 1);
            }

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
                data.setCompressedPunchTicks(data.getCompressedPunchTicks() + 1);

                boolean outOfTime = data.getCompressedPunchTicks()
                        >= com.minecraft.atlamod.abilities.sound.CompressedPunches.MAX_TICKS;

                boolean affordable = chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.sound.CompressedPunches.XP_PER_SECOND);

                // Both endings go through the ability's own stop(), so the thirty
                // second cooldown is stamped whichever one arrives first — running out
                // of chi must not be a cheaper way to end it than running out of time.
                if (outOfTime || !affordable) {
                    com.minecraft.atlamod.abilities.sound.CompressedPunches.stop(player, data);
                }
            }

            // --- FIRE ROCKET (toggle) ---
            // Was a channel, so the dispatcher used to hold the flight open and drain the
            // chi. As a toggle both are this loop's job: the per-tick half keeps vanilla
            // from clearing the flight flag, and the per-second half bills it and puts the
            // rocket out when the pool runs dry.
            if (data.isFireRocketing()) {
                com.minecraft.atlamod.abilities.fire.FireRocket.tick(player, data);

                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.fire.FireRocket.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.fire.FireRocket.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.fire.FireRocket.stop(player, data);
                }
            }

            if (com.minecraft.atlamod.abilities.combustion.CombustionBeams.has(player)) {
                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.combustion.CombustionBeam.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.combustion.CombustionBeam.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.combustion.CombustionBeams.stop(player);
                }
            }

            if (com.minecraft.atlamod.abilities.metal.MetalShields.has(player)) {
                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.metal.MetalShield.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.metal.MetalShield.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.metal.MetalShields.drop(player);
                }
            }

            if (com.minecraft.atlamod.abilities.sound.SoundWalls.has(player)) {
                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.sound.SoundWall.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.sound.SoundWall.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.sound.SoundWalls.drop(player);
                }
            }

            // --- SPEED BOOST (toggle) ---
            if (com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.has(player)) {
                com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.tick(player);

                if (!chargeSoundToggle(player, data,
                        com.minecraft.atlamod.abilities.gravity.GravitySpeedBoost.CHI_PER_SECOND,
                        com.minecraft.atlamod.abilities.gravity.GravitySpeedBoost.XP_PER_SECOND)) {
                    com.minecraft.atlamod.abilities.gravity.GravitySpeedBoosts.stop(player);
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

            // --- QUICK HANDS PASSIVE ---
            // Runs unconditionally too, for the same reason: taking Haste back off
            // when the passive is unequipped is as much this call's job as granting it.
            com.minecraft.atlamod.abilities.nobending.QuickHands.tick(player, data);

            // --- AVATAR LAST STAND ---
            // Resistance and Regeneration below three hearts, taken back off above
            // it. Runs unconditionally: taking the buffs away when the Avatar heals
            // up — or loses the title outright — is as much this call's job as
            // granting them.
            com.minecraft.atlamod.avatar.Avatar.tick(player, data);

            // --- BENDING ARMOR VISUAL ---
            // Broadcast only when it changes. Effects are synced to their owner alone,
            // so onlookers learn about a stone or steel suit from here or not at all.
            for (com.minecraft.atlamod.BendingArmorSuit suit
                    : com.minecraft.atlamod.BendingArmorSuit.VALUES) {
                boolean armored = suit.isWornBy(player);
                if (armored != data.isArmorSuitShown(suit)) {
                    data.setArmorSuitShown(suit, armored);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                            player, new com.minecraft.atlamod.network.BendingArmorPacket(
                                    player.getId(), suit.ordinal(), armored));
                }
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
            // believes in a suit is corrected the moment the bender is back.
            BendingData armorData = player.getData(ModAttachments.BENDING_DATA);
            armorData.clearArmorSuitsShown();
            for (com.minecraft.atlamod.BendingArmorSuit suit
                    : com.minecraft.atlamod.BendingArmorSuit.VALUES) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new com.minecraft.atlamod.network.BendingArmorPacket(
                                player.getId(), suit.ordinal(), false));
            }

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

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

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

                com.minecraft.atlamod.abilities.blood.Blood.sync(player, data);
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
