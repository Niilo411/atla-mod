package io.github.niilo411.elementalpaths;

import io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket;
import io.github.niilo411.elementalpaths.network.SyncStatsPacket;
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

@EventBusSubscriber(modid = ElementalPaths.MODID)
public class ServerEvents {

    /**
     * Advances everything in flight. These are tracked in a static list rather than
     * being real entities, so nothing else ticks them.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // The Spirit Realm's own wildlife. Vanilla's spawner cannot produce it — see
        // SpiritSpawner — so it is driven from here, and returns immediately for every
        // level but the one.
        var spirit = io.github.niilo411.elementalpaths.spirit.SpiritRealm.level(event.getServer());
        if (spirit != null) io.github.niilo411.elementalpaths.spirit.SpiritSpawner.tick(spirit);

        io.github.niilo411.elementalpaths.abilities.ChannelingProjectiles.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.water.Drownings.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.water.Tsunamis.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.Rides.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.air.AirSpouts.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.earth.EarthWorks.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.earth.EarthWalls.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.earth.EarthGrabs.tickAll(event.getServer());

        io.github.niilo411.elementalpaths.abilities.lightning.LightningBalls.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.ice.IceWorks.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.ice.Frozens.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.ice.IceBombs.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.ice.FreezingBeams.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.sound.BassWaves.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.metal.MetalShields.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.blood.BloodHolds.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.blood.FleshShields.tickAll(event.getServer());

        // The ten-second window a chi blocker has to land their five hits.
        io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.tickAll(event.getServer());

        io.github.niilo411.elementalpaths.abilities.lava.LavaRivers.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.lava.LavaGeysers.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.lava.LavaTsunamis.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.lava.LavaRains.tickAll(event.getServer());
        // Ticked LAST of the lava group, for the reason MetalWorks and IceWorks are:
        // the four above pour their own blocks through LavaWorks, so anything they lay
        // this tick is already on its timer by the time the sweep runs.
        io.github.niilo411.elementalpaths.abilities.lava.LavaWorks.tickAll(event.getServer());
        // Ticked LAST of the metal pair: the shields hand their own blocks back
        // through MetalWorks, so anything they release still gets settled here.
        io.github.niilo411.elementalpaths.abilities.metal.MetalWorks.tickAll(event.getServer());

        io.github.niilo411.elementalpaths.abilities.gravity.GravitySlams.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.gravity.GravityPulls.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.gravity.GravityEncases.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.gravity.GravityCrushes.tickAll(event.getServer());
        io.github.niilo411.elementalpaths.abilities.gravity.MeteorBlocks.tickAll(event.getServer());

        // Tells everybody when the sky changes. Checked once a second — see the class.
        io.github.niilo411.elementalpaths.events.WorldEventAnnouncer.tick(event.getServer());

        // Keeps a running cycle looking for a Paragon when nobody holds the title.
        // Rate-limited inside, and does nothing at all once one is in place.
        io.github.niilo411.elementalpaths.paragon.Paragon.tickCycle(event.getServer());
    }

    /**
     * Drops shots belonging to a level that is going away. Nothing else holds them, so
     * without this a shot fired into an unloading dimension would keep a dead
     * ServerLevel alive for as long as the server ran.
     */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            io.github.niilo411.elementalpaths.abilities.ChannelingProjectiles.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.HeldBlocks.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.water.WaterSpheres.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.water.Drownings.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.water.Tsunamis.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.Rides.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.air.AirSpouts.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.earth.EarthWorks.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.earth.EarthWalls.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.earth.EarthGrabs.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.lightning.LightningBalls.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.ice.Frozens.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.ice.IceBombs.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.ice.FreezingBeams.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.sound.BassWaves.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.metal.MetalShields.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.blood.BloodHolds.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.blood.BloodPuppets.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.blood.FleshShields.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.metal.MetalWorks.forgetLevel(level);

            io.github.niilo411.elementalpaths.abilities.lava.LavaRivers.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.lava.LavaGeysers.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.lava.LavaTsunamis.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.lava.LavaRains.forgetLevel(level);
            // Cools LAST: the four above hand their own blocks back through LavaWorks,
            // so anything they release still gets settled by this sweep. It matters more
            // here than anywhere else — lava left behind in an unloading level would be
            // an unbreakable block nothing in the game could ever remove.
            io.github.niilo411.elementalpaths.abilities.lava.LavaWorks.forgetLevel(level);

            // Melts LAST: the two above hand their own blocks back through IceWorks,
            // so anything they release still gets settled by this sweep.
            io.github.niilo411.elementalpaths.abilities.ice.IceWorks.forgetLevel(level);

            io.github.niilo411.elementalpaths.abilities.gravity.GravitySlams.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityPulls.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityEncases.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityCrushes.forgetLevel(level);
            io.github.niilo411.elementalpaths.abilities.gravity.MeteorBlocks.forgetLevel(level);
        }
    }
    /**
     * Puts the Lightning Channeling Scroll in the weaponsmith's book, for 64 copper.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.LIGHTNING_SCROLL.get()),
                    2,   // how many times it can be bought before restocking
                    12,  // villager xp for the trade
                    0.05F));
        }
    }

    /**
     * Puts the Ice Channeling Scroll in the fisherman's book, for a Heart of the Sea.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.ICE_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Blood Channeling Scroll in the cleric's book, for 5 rabbit feet.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.BLOOD_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Lava Channeling Scroll in the shepherd's book, for 5 nether bricks.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.LAVA_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Combustion Channeling Scroll in the armorer's book, for 32 gunpowder.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.COMBUSTION_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Metal Channeling Scroll in the mason's book, for 4 iron blocks.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.METAL_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Sound Channeling Scroll in the fletcher's book, for 32 feathers.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.SOUND_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Puts the Gravity Channeling Scroll in the cartographer's book, for 6 pistons.
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
                    new net.minecraft.world.item.ItemStack(ElementalPaths.GRAVITY_SCROLL.get()),
                    2,
                    12,
                    0.05F));
        }
    }

    /**
     * Pushes a player's element list back to their own client.
     *
     * Pulled out when /paths add and /paths remove gained a target: the packet has to go
     * to the player who was CHANGED rather than to whoever typed the command, and two
     * hand-written copies of a six-field packet is two places to get that wrong.
     */
    private static void syncElements(ServerPlayer player, ChannelingData data) {
        PacketDistributor.sendToPlayer(player, new SyncChannelingDataPacket(
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
                io.github.niilo411.elementalpaths.abilities.ElementPaths.channelable(), builder);
    }

    /**
     * /paths add &lt;targets&gt; &lt;element&gt;, also reachable as /paths element add.
     *
     * Built by a method rather than written inline because it is registered twice. Each
     * call returns a FRESH node tree, which is what Brigadier needs — a single node
     * cannot hang under two parents — so the two spellings share this implementation
     * instead of being two copies that can drift apart.
     *
     * The target is NOT optional, and there is deliberately no second "just me" form
     * beside it. Brigadier would have to tell "/paths add fire" from "/paths add Steve
     * fire" by trying one branch and falling through to the other, which makes a typo in
     * an element name read as a missing player instead of a bad element. @s is two
     * characters and says exactly what it means.
     */
    /**
     * /paths event &lt;name&gt; — moves the clock to the moment that event begins.
     *
     * IT SETS THE TIME RATHER THAN SETTING A FLAG, and that is the only honest way to do
     * it. Every world event is derived from the clock — see {@link io.github.niilo411.elementalpaths.events.WorldEvents} —
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
                                java.util.Arrays.stream(io.github.niilo411.elementalpaths.events.WorldEvents.Event.values())
                                        .map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                                        .toList(),
                                builder))
                        .executes(context -> {
                            String name = getString(context, "name");

                            io.github.niilo411.elementalpaths.events.WorldEvents.Event chosen = null;
                            for (io.github.niilo411.elementalpaths.events.WorldEvents.Event value
                                    : io.github.niilo411.elementalpaths.events.WorldEvents.Event.values()) {
                                if (value.name().equalsIgnoreCase(name)) chosen = value;
                            }

                            if (chosen == null) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                        "There is no event called \"" + name + "\". Try one of: "
                                                + java.util.Arrays.stream(
                                                        io.github.niilo411.elementalpaths.events.WorldEvents.Event.values())
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
                            final io.github.niilo411.elementalpaths.events.WorldEvents.Event running = chosen;
                            if (io.github.niilo411.elementalpaths.events.WorldEvents.isActive(overworld, chosen)) {
                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                        running.title() + " is already happening."), false);
                                return 1;
                            }

                            long start = io.github.niilo411.elementalpaths.events.WorldEvents.nextStart(now, chosen);

                            // Every dimension, the way /time set does it.
                            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                                level.setDayTime(start);
                            }

                            final io.github.niilo411.elementalpaths.events.WorldEvents.Event started = chosen;
                            long skippedDays = (start - now) / 24000L;
                            int minutes = io.github.niilo411.elementalpaths.events.WorldEvents.length(chosen) / 1200;

                            // Said rather than assumed: a settings file with the event
                            // switched off would otherwise leave the clock moved and
                            // nothing whatsoever happening, which reads as the command
                            // being broken rather than as the setting doing its job.
                            if (!io.github.niilo411.elementalpaths.events.WorldEvents.isEnabled(chosen)) {
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
                                    // This used to take any word at all, so "/paths add @s
                                    // grass" granted an element with no tree, no abilities
                                    // and no emblem, which the game then had no way to do
                                    // anything with or to explain.
                                    if (!io.github.niilo411.elementalpaths.abilities.ElementPaths.exists(element)) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "There is no element called \"" + element + "\". Try one of: "
                                                        + String.join(", ",
                                                        io.github.niilo411.elementalpaths.abilities.ElementPaths.channelable())));
                                        return 0;
                                    }

                                    int changed = 0;

                                    for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument
                                            .getPlayers(context, "targets")) {
                                        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
                                        if (data.getUnlockedElements().contains(element)) continue;

                                        data.getUnlockedElements().add(element);
                                        if (data.getActiveElement().isEmpty()) data.setActiveElement(element);

                                        // NO CHANNELING IS GRANTED LIKE ANYTHING ELSE now,
                                        // and the special case that used to sit here is
                                        // gone. It overwrote the recipient's main element
                                        // so the path would register, which meant gifting
                                        // it to a fire channeler silently took their chi away
                                        // — the thing this command has no business doing.
                                        // Nothing needs overwriting any more: what makes
                                        // somebody a non-channeler is having no channeling art
                                        // at all, which the unlocked list above answers by
                                        // itself. See NoChanneling.
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

                                        player.setData(ModAttachments.CHANNELING_DATA, data);
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
     * /paths remove &lt;targets&gt; &lt;element&gt;, also reachable as /paths element remove.
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
                                        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

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
                                        player.setData(ModAttachments.CHANNELING_DATA, data);
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
        event.getDispatcher().register(Commands.literal("paths")
                // EVERY /paths command needs permission level 2, which is exactly
                // "cheats enabled in singleplayer, or op on a server" — vanilla ties
                // those two together, so one check covers both.
                //
                // On the ROOT rather than on each subcommand: Brigadier applies a
                // requires() to everything beneath it, so nothing added under /paths
                // later can be left ungated by being forgotten. It also hides the whole
                // command from the suggestion list for anyone who cannot use it, rather
                // than offering commands that will only refuse.
                .requires(source -> source.hasPermission(2))

                // ELEMENT COMMANDS — /paths add|remove <targets> <element>, and the same
                // two again under /paths element.
                //
                // Both spellings exist on purpose and neither is a copy: addElement()
                // and removeElement() each build a fresh node tree, so there is one
                // implementation behind all four entry points. The grouped form reads
                // better beside /paths paragon and is what anyone looking for "the command
                // that removes an element" would reach for; the ungrouped pair is what
                // already existed, and dropping it would break every note, macro and
                // habit built on it for no gain.
                .then(addElement())
                .then(removeElement())
                .then(Commands.literal("element")
                        .then(addElement())
                        .then(removeElement())
                )

                // WORLD EVENT COMMAND — /paths event <name>
                .then(startEvent())

                // TEMPLE COMMAND — /paths temple
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
                            var size = io.github.niilo411.elementalpaths.spirit.TempleStructure
                                    .sizeFor(player.serverLevel());
                            var corner = player.blockPosition().below()
                                    .offset(-size.getX() / 2, 0, -size.getZ() / 2);

                            var temple = io.github.niilo411.elementalpaths.spirit.TempleStructure.placeAt(
                                    player.serverLevel(), corner);

                            // A missing or misnamed .nbt is the only way this happens, and
                            // it is worth saying out loud rather than reporting success
                            // over an empty patch of ground.
                            if (temple == null) {
                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                        "No temple structure could be loaded. Check that the .nbt files are in "
                                                + "data/elemental_paths/structure/."));
                                return 0;
                            }

                            // Built in the Spirit Realm, it comes up already burning —
                            // nothing can channel there, so a dark frame would never open.
                            io.github.niilo411.elementalpaths.spirit.SpiritRealm.lightIfHere(
                                    player.serverLevel(), temple);

                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    io.github.niilo411.elementalpaths.spirit.SpiritRealm.isSpiritRealm(player.level())
                                            ? "Built a spirit temple. Its portal is already open."
                                            : "Built a spirit temple. Its portal is unlit — use "
                                            + io.github.niilo411.elementalpaths.spirit.SpiritPortals.REQUIRED_USES
                                            + " ability uses within "
                                            + (io.github.niilo411.elementalpaths.spirit.SpiritPortals.WINDOW_TICKS / 20)
                                            + "s, within "
                                            + io.github.niilo411.elementalpaths.spirit.SpiritPortals.RADIUS
                                            + " blocks of it, to open it."), true);
                            return temple == null ? 0 : 1;
                        })
                )
                // LEVEL COMMAND — /paths level <targets> <amount>
                .then(Commands.literal("level")
                        .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                                .then(Commands.argument("amount", integer(1))
                                        .executes(context -> {
                                            int amount = getInteger(context, "amount");
                                            int changed = 0;

                                            for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument
                                                    .getPlayers(context, "targets")) {
                                                ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

                                                // Bumps the level WITHOUT touching xp, so
                                                // the two can drift apart. That has always
                                                // been true of this command.
                                                data.setLevel(data.getLevel() + amount);
                                                player.setData(ModAttachments.CHANNELING_DATA, data);

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
                // PARAGON COMMANDS
                //
                // The permission check is on the /paths root and covers these too, so
                // there is no second requires() here — one would only be a copy that
                // could drift out of step with it.
                //
                // "cycle" is a literal and the player is an argument, so Brigadier
                // tries the literal first and there is no ambiguity between
                // "/paths paragon cycle ..." and "/paths paragon <player>" even for a
                // player unlucky enough to be called "cycle".
                .then(Commands.literal("paragon")
                        // /paths paragon cycle start|stop
                        .then(Commands.literal("cycle")
                                .then(Commands.literal("start")
                                        .executes(context -> {
                                            var server = context.getSource().getServer();
                                            io.github.niilo411.elementalpaths.paragon.Paragon.startCycle(server);

                                            var state = io.github.niilo411.elementalpaths.paragon.Paragon.state(server);
                                            if (state.hasParagon()) {
                                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                        "Paragon cycle started at " + state.getCycleElement() + "."), true);
                                            } else {
                                                // Not a failure. The search already skipped
                                                // every element in turn, so reaching here
                                                // means nobody online could be the Paragon at
                                                // all — the cycle is running and will take
                                                // the first qualifying player to turn up.
                                                // Said plainly so it doesn't read as the
                                                // command having done nothing.
                                                context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                        "Paragon cycle started, but nobody online has chosen an element. "
                                                                + "Waiting — it rests on " + state.getCycleElement() + "."), true);
                                            }
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("stop")
                                        .executes(context -> {
                                            io.github.niilo411.elementalpaths.paragon.Paragon.stopCycle(
                                                    context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                    "Paragon cycle stopped. Nobody is the Paragon."), true);
                                            return 1;
                                        })
                                )
                        )

                        // /paths paragon remove
                        .then(Commands.literal("remove")
                                .executes(context -> {
                                    var server = context.getSource().getServer();
                                    var state = io.github.niilo411.elementalpaths.paragon.Paragon.state(server);

                                    if (!state.hasParagon()) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                "There is no Paragon."));
                                        return 0;
                                    }

                                    io.github.niilo411.elementalpaths.paragon.Paragon.revokeCurrent(server);
                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "The Paragon has been removed."), true);

                                    // A running cycle keeps running: the Paragon was taken
                                    // away, not defeated, so the search resumes on the SAME
                                    // element rather than moving on. "cycle stop" is the
                                    // command for ending the cycle itself.
                                    io.github.niilo411.elementalpaths.paragon.Paragon.findParagon(server);
                                    return 1;
                                })
                        )

                        // /paths paragon <player>
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument
                                            .getPlayer(context, "player");

                                    // Refused for a non-channeler, and SAID so rather than
                                    // reporting a success that did not happen. Becoming
                                    // the Paragon hands over all four elements, which is
                                    // the exact opposite of what they chose.
                                    if (!io.github.niilo411.elementalpaths.paragon.Paragon.grant(
                                            context.getSource().getServer(), target)) {
                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(
                                                target.getGameProfile().getName()
                                                        + " has no channeling. The Paragon is always a channeler."));
                                        return 0;
                                    }

                                    context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            target.getGameProfile().getName() + " is now the Paragon."), true);
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

        if (living.hasEffect(io.github.niilo411.elementalpaths.ModEffects.STUNNED)) {
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

        // The Spirit Realm's low-gravity tide, extended to mobs — off by default
        // (PathsConfig's affectsMobs), and checked first because it is a single
        // volatile read that keeps this a no-op for every entity in the game
        // whenever the setting is left off. Players are handled separately, once
        // per player per tick, from ServerEvents' own player tick — not here, so
        // they are excluded rather than run through this path twice.
        if (io.github.niilo411.elementalpaths.PathsConfig.spiritTideAffectsMobs()
                && !(living instanceof net.minecraft.world.entity.player.Player)) {
            io.github.niilo411.elementalpaths.spirit.SpiritGravity.tick(living);
        }
    }

    @SubscribeEvent
    public static void onDismount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (!event.isDismounting()) return;

        if (io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.holdsSeat(event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }

        // Gravity Orbit holds its victims the same way, for the same reason: without
        // this, crouching was a way out of the whole fifteen seconds. GravityOrbits
        // drops an orbit from its list BEFORE releasing anyone, so the ability's own
        // release is never refused by this.
        // A DEAD victim is always let off, so a body is never left riding an empty ring.
        if (event.getEntityMounting().isAlive()
                && io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.holdsSeat(event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }
    }
    /**
     * Tells a player about someone else's Earth armor the moment they come into view.
     *
     * The per-tick broadcast only fires when the armor goes on or off, so without this
     * anyone who walked up to an already-armored channeler — or logged in near one, or
     * came back into render distance — would see them in ordinary clothes until the
     * effect ended.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;

        for (io.github.niilo411.elementalpaths.ChannelingArmorSuit suit
                : io.github.niilo411.elementalpaths.ChannelingArmorSuit.VALUES) {
            if (suit.isWornBy(target)) {
                PacketDistributor.sendToPlayer(watcher,
                        new io.github.niilo411.elementalpaths.network.ChannelingArmorPacket(
                                target.getId(), suit.ordinal(), true));
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A part-finished portal sequence is ten seconds of state and is not worth
            // keeping; the way home out of the Spirit Realm is transient by design and
            // falls back to the world spawn. See SpiritTravel.
            io.github.niilo411.elementalpaths.spirit.SpiritPortals.forget(player.getUUID());
            io.github.niilo411.elementalpaths.spirit.SpiritTravel.forget(player.getUUID());

            io.github.niilo411.elementalpaths.abilities.HeldBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.water.WaterSpheres.collapse(player);
            io.github.niilo411.elementalpaths.abilities.Rides.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.air.AirSpouts.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lightning.LightningBalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.IceBombs.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.FreezingBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.Frozens.forgetEntity(player);
            io.github.niilo411.elementalpaths.abilities.sound.BassWaves.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.metal.MetalShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodHolds.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodPuppets.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.FleshShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lava.LavaRains.forgetPlayer(player);

            io.github.niilo411.elementalpaths.abilities.gravity.GravitySlams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityPulls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityEncases.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityCrushes.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);
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
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.countHit(attacker, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        // Anything at all, not just players: a mob sealed in ice can still be killed
        // by something that bypasses invulnerability, and its shell has to come down
        // with it rather than standing there empty until its timer runs out.
        io.github.niilo411.elementalpaths.abilities.ice.Frozens.forgetEntity(event.getEntity());

        // A non-channeler earns XP by killing, since they cannot meditate and their two
        // abilities pay nothing. Granted to whoever landed the killing blow, scaled by
        // what they killed — see NoChanneling.xpForKill.
        //
        // Ordered cheapest first, like every handler here that fires for the whole world:
        // this runs for every death of every mob on the server, so the attacker is tested
        // before anything is read off them.
        if (event.getSource().getEntity() instanceof ServerPlayer killer
                && killer != event.getEntity()) {
            ChannelingData killerData = killer.getData(ModAttachments.CHANNELING_DATA);

            if (io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.is(killerData)) {
                io.github.niilo411.elementalpaths.abilities.AbilitySupport.grantXp(killerData,
                        io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.xpForKill(event.getEntity()));
                io.github.niilo411.elementalpaths.abilities.AbilitySupport.syncData(killer, killerData);
            }
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

            // A channeling armor suit is drawn from a client-side set keyed on entity
            // id, and a respawned player REUSES its id on both sides — so a death would
            // otherwise leave the art on a channeler who no longer has the effect. The
            // per-tick broadcast cannot catch it either: the mask it compares against is
            // transient and comes back empty, so it sees no change and says nothing.
            // Told explicitly here instead.
            data.clearArmorSuitsShown();
            for (io.github.niilo411.elementalpaths.ChannelingArmorSuit suit
                    : io.github.niilo411.elementalpaths.ChannelingArmorSuit.VALUES) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        player, new io.github.niilo411.elementalpaths.network.ChannelingArmorPacket(
                                player.getId(), suit.ordinal(), false));
            }
            io.github.niilo411.elementalpaths.abilities.HeldBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.water.WaterSpheres.collapse(player);
            io.github.niilo411.elementalpaths.abilities.Rides.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.air.AirSpouts.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lightning.LightningBalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.IceBombs.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.FreezingBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.Frozens.forgetEntity(player);
            io.github.niilo411.elementalpaths.abilities.sound.BassWaves.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.metal.MetalShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodHolds.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodPuppets.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.FleshShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lava.LavaRains.forgetPlayer(player);

            io.github.niilo411.elementalpaths.abilities.gravity.GravitySlams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityPulls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityEncases.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityCrushes.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);

            // One of the Paragon's three lives. Deliberately last: it can strip the
            // title and pass the cycle on, and the cleanup above should run for a
            // dying Paragon exactly as it does for anyone else.
            io.github.niilo411.elementalpaths.paragon.Paragon.onDeath(player);
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket(
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
                    new io.github.niilo411.elementalpaths.network.ChargeStatusPacket("", 0, 0, false));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.RootedPacket(false));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.SyncPassivesPacket(data.getEquippedPassives()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.SyncUpgradesPacket(data.getUnlockedUpgrades()));

            // Whether this is a dedicated server, which decides who the settings screen
            // lets edit — see SettingsAccess.
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.ServerKindPacket(
                            player.getServer() != null && player.getServer().isDedicatedServer()));

            // Safety net: Fire Rocket grants flight through the vanilla ability
            // flags, and those are saved to player NBT. If the player disconnected
            // mid-flight, onStop() never ran and they would return able to fly
            // forever with nothing in the world to take it back.
            if (!player.isCreative() && !player.isSpectator()
                    && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                io.github.niilo411.elementalpaths.abilities.fire.FireRocket.stopFlight(player);
            }

            // Fills in the HUD's lives counter, and takes the title off anyone who
            // comes back still flagged when the world says somebody else has it —
            // which is how the title is revoked from a player who was OFFLINE.
            io.github.niilo411.elementalpaths.paragon.Paragon.checkOnLogin(player);

            io.github.niilo411.elementalpaths.abilities.blood.Blood.sync(player, data);
        }
    }
    /**
     * Mining pays XP — Spirit Ore pays every channeler, and a non-channeler is ALSO paid a
     * trickle for any block at all.
     *
     * The SHARD Spirit Ore drops is a loot table and needs nothing here; this is only
     * the XP, which has nowhere else to come from — vanilla's own experience drops go
     * to the player's levels, and channeling XP is a different pot entirely.
     *
     * Spirit Ore's own bonus is granted whoever mines it, without asking whether they
     * have chosen an element yet. XP banked before a choice is simply theirs when they
     * make one, and refusing it would mean the same ore was worth less to a player who
     * happened to arrive earlier. A non-channeler breaking Spirit Ore gets BOTH: the ore's
     * own figure and the flat per-block one, since one is what the ore is worth and the
     * other is what mining itself is worth to a path with no other physical income.
     *
     * No longer ordered "block before player" the way this used to be: a non-channeler's
     * flat XP applies to every block, so there is nothing left to reject before reading
     * the player. Still cheapest-check-first within that — the player type, then
     * NoChanneling.is (a couple of list entries at most).
     */
    @SubscribeEvent
    public static void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
        int gained = 0;

        if (io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.is(data)) {
            gained += io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.XP_PER_BLOCK_MINED;
        }

        boolean spiritOre = event.getState().is(ElementalPaths.SPIRIT_ORE.get());
        if (spiritOre) {
            gained += io.github.niilo411.elementalpaths.spirit.island.SpiritOre.xpPerBlock();
        }

        if (gained <= 0) return;

        io.github.niilo411.elementalpaths.abilities.AbilitySupport.grantXp(data, gained);
        io.github.niilo411.elementalpaths.abilities.AbilitySupport.syncData(player, data);

        // On the action bar rather than in chat: a vein is several blocks and a line
        // each would bury whatever else the player was being told. Only shown for
        // Spirit Ore's own figure, matching what this already told a channeler — a
        // non-channeler's flat trickle from ordinary stone is not worth a line every
        // single block.
        if (spiritOre) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§b+" + gained + " channeling XP §7(" + data.getXp() + "/"
                            + io.github.niilo411.elementalpaths.abilities.AbilitySupport.xpPerLevel() + ")"), true);
        }
    }

    /**
     * Right clicking a spirit shrine's beacon.
     *
     * ORDERED CHEAPEST FIRST, and it has to be: this fires for every right click on every
     * block in the game, on both sides. The side, the hand, the block and the dimension are
     * all settled before {@link io.github.niilo411.elementalpaths.spirit.island.SpiritShrines#isShrine}
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
                .is(io.github.niilo411.elementalpaths.spirit.island.SpiritShrines.SHRINE_BLOCK)) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!io.github.niilo411.elementalpaths.spirit.island.SpiritShrines.isShrine(level, pos)) return;

        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);

        io.github.niilo411.elementalpaths.spirit.island.SpiritShrines.use(player, pos);
    }

    /**
     * Every toggle goes out at the threshold.
     *
     * FIRED BEFORE THE MOVE, which is the whole reason this is its own handler rather
     * than more lines in the clone handler below. That one runs AFTER the change, on a
     * fresh ChannelingData whose transient flags have already come back false — so by the
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
        io.github.niilo411.elementalpaths.events.WorldEventAnnouncer.forget();
    }

    @SubscribeEvent
    public static void onTravelToDimension(
            net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

        for (io.github.niilo411.elementalpaths.abilities.Ability ability
                : io.github.niilo411.elementalpaths.abilities.AbilityRegistry.all().values()) {
            if (ability.isActive(player, data)) {
                ability.deactivate(player, data);
            }
        }

        player.setData(ModAttachments.CHANNELING_DATA, data);
    }

    /**
     * Copies your data to your new body when respawning OR travelling to another dimension.
     *
     * LOWEST PRIORITY, deliberately. NeoForge copies attachments in a Clone listener of its
     * own ({@code AttachmentInternals.onPlayerClone}), which REPLACES the new body's
     * attachment outright — so if it happened to run after this one, everything copied by
     * hand here would be thrown away and only its codec round-trip would survive. At lowest
     * priority this is always the last word, whatever order the two are registered in.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        var oldData = event.getOriginal().getData(ModAttachments.CHANNELING_DATA);
        var newData = event.getEntity().getData(ModAttachments.CHANNELING_DATA);

        newData.setMainElement(oldData.getMainElement());
        newData.setActiveElement(oldData.getActiveElement());
        newData.setHasChosenElement(oldData.hasChosenElement());

        java.util.List<String> elements = new java.util.ArrayList<>(oldData.getUnlockedElements());
        newData.getUnlockedElements().clear();
        newData.getUnlockedElements().addAll(elements);

        newData.setXp(oldData.getXp());
        newData.setLevel(oldData.getLevel());
        newData.setCurrentChi(oldData.getCurrentChi());

        // Copied out into a NEW list before anything is cleared. Clearing the new body's
        // list and then adding the old one's back is only safe while the two are
        // different lists — if they were ever the same object, the clear would empty
        // both and nothing would come back. A copy taken first cannot be emptied by it.
        java.util.List<String> unlocked = new java.util.ArrayList<>(oldData.getUnlockedAbilities());
        newData.getUnlockedAbilities().clear();
        newData.getUnlockedAbilities().addAll(unlocked);

        // Key bindings stay bound through a death until the player unbinds them.
        newData.setAllEquippedAbilities(new java.util.ArrayList<>(oldData.getEquippedAbilities()));

        // Passives too. copyOnDeath already carries these through a death, but this
        // event also fires when changing dimension, where it does not — without this
        // walking into the Nether would silently unequip every passive.
        newData.setAllEquippedPassives(oldData.getEquippedPassives());

        // The Paragon too. copyOnDeath covers a death, but this event also fires on a
        // dimension change, where it does not — without this, walking into the Nether
        // would quietly cost a player the title along with the elements it granted.
        newData.setParagon(oldData.isParagon());
        newData.setParagonLives(oldData.getParagonLives());
        newData.setPreParagonElements(oldData.getPreParagonElements());

        // The blood track too, for the same reason: this event fires on a dimension
        // change where copyOnDeath does not, and walking into the Nether should not
        // cost a blood channeler their standing.
        newData.setBloodXp(oldData.getBloodXp());
        newData.setBloodLevel(oldData.getBloodLevel());

        // The spirit shrines too, and this one is load-bearing on both routes: a death
        // that reset the bonus would take back permanent max chi that was earned by
        // crossing the Spirit Realm, and a dimension change that forgot the LIST would let
        // every shrine be drawn from a second time.
        newData.setBonusMaxChi(oldData.getBonusMaxChi());
        newData.setAllUsedShrines(oldData.getUsedShrines());

        event.getEntity().setData(ModAttachments.CHANNELING_DATA, newData);
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Forces the UI to reappear on your screen after walking through a portal
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // A gale ride cannot follow its rider through a portal: the seat belongs to
            // the level they left, so the ride ends at the threshold.
            io.github.niilo411.elementalpaths.abilities.Rides.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.air.AirSpouts.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.earth.EarthTraps.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lightning.LightningBalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.IceBombs.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.FreezingBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.ice.Frozens.forgetEntity(player);
            io.github.niilo411.elementalpaths.abilities.sound.BassWaves.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.metal.MetalShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodHolds.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.BloodPuppets.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.blood.FleshShields.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.lava.LavaRains.forgetPlayer(player);

            io.github.niilo411.elementalpaths.abilities.gravity.GravitySlams.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityPulls.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityEncases.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravityCrushes.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.MeteorBlocks.forgetPlayer(player);
            io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.forgetPlayer(player);

            // The rocket cannot follow its rider through a portal either, and unlike a
            // gale ride it leaves something behind if it is not put out: this event builds
            // a FRESH ChannelingData and copies the saved fields across by hand, so the
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
                io.github.niilo411.elementalpaths.abilities.fire.FireRocket.stopFlight(player);
            }

            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket(
                    data.getMainElement() == null ? "" : data.getMainElement(),
                    data.getActiveElement() == null ? "" : data.getActiveElement(),
                    data.getUnlockedElements(),
                    data.hasChosenElement(),
                    data.getUnlockedAbilities(),
                    data.getEquippedAbilities()
            ));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.SyncParagonPacket(
                            data.isParagon(), data.getParagonLives()));

            io.github.niilo411.elementalpaths.abilities.blood.Blood.sync(player, data);
        }
    }

    /**
     * Air Jump: no fall damage while its window is open.
     *
     * Cancelled at LivingFallEvent rather than at the damage event, because
     * cancelling here also suppresses the landing sound and the puff of dust —
     * a channeler who steps out of a 20 block drop should not thud like a sack.
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
        // The Spirit Realm's low-gravity tide, checked FIRST and for whoever fell —
        // player or mob, since PathsConfig's affectsMobs can extend the tide to both.
        // Vanilla charges for fall DISTANCE rather than for impact speed, so something
        // that drifted gently down would otherwise be billed exactly as if it had
        // plummeted. See SpiritGravity#cancelsFallDamage.
        if (io.github.niilo411.elementalpaths.spirit.SpiritGravity.cancelsFallDamage(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

        // Two more ways to land for free, both player-only (ChannelingData has no
        // meaning for a mob) and both cancelling rather than reducing, for the same
        // reason as above — it takes the landing thud and the puff of dust with it.
        // The Gravity Channeling Scroll's confirmation window is the second — see
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
                && io.github.niilo411.elementalpaths.abilities.ice.Frozens.isFrozen(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);
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
                            io.github.niilo411.elementalpaths.abilities.fire.FireImmunity.KEY)) {
                event.setCanceled(true);
                return;
            }

            // Lava resistance: lava alone cannot touch them.
            //
            // Deliberately ONE damage type rather than the IS_FIRE tag Fire immunity
            // uses — the design's word is "Lava only", so a lava channeler wearing this
            // still burns in an ordinary fire and still cooks on magma. Our own lava
            // hurts through vanilla's own lava source (see Lava.scorch), so this one
            // check covers real lava and every lava ability at once.
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                    && data.hasPassiveEquipped(
                            io.github.niilo411.elementalpaths.abilities.lava.LavaResistance.KEY)) {
                event.setCanceled(true);
                return;
            }
        }

        // Flesh shield: the wall of bodies takes the blow instead, split between
        // whoever is still standing in it.
        //
        // Checked here rather than in the ability because this is where damage is
        // decided, and it RETURNS rather than falling through — a blow the shield ate
        // never reached the channeler, so none of the rules below it apply.
        if (event.getEntity() instanceof ServerPlayer shielded
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && io.github.niilo411.elementalpaths.abilities.blood.FleshShields.absorb(
                        shielded, event.getAmount(), event.getSource())) {
            event.setCanceled(true);
            return;
        }

        // The Combustion Channeling Scroll spares its own reader. Checked BEFORE the
        // resistance passive below, because this is a flat no rather than a reduction.
        if (event.getEntity() instanceof ServerPlayer reader
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                && reader.getData(ModAttachments.CHANNELING_DATA).getBlastImmuneTicks() > 0) {
            event.setCanceled(true);
            return;
        }

        // Combustion resistance: explosions do a quarter of their damage.
        //
        // Done here because vanilla has no explosion-resistance attribute to modify —
        // blast protection is an enchantment, not a number anything can be given. The
        // whole IS_EXPLOSION tag rather than a list of sources, so a channeler's own
        // charges, their misfires, TNT, creepers and beds are all covered at once.
        if (event.getEntity() instanceof ServerPlayer blasted
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            ChannelingData blastedData = blasted.getData(ModAttachments.CHANNELING_DATA);
            if (blastedData.hasPassiveEquipped(
                    io.github.niilo411.elementalpaths.abilities.combustion.CombustionResistance.KEY)) {
                event.setAmount(event.getAmount()
                        * io.github.niilo411.elementalpaths.abilities.combustion.CombustionResistance.DAMAGE_MULTIPLIER);
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
        if (io.github.niilo411.elementalpaths.abilities.combustion.Combustion.isCapping()
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            float allowed = io.github.niilo411.elementalpaths.abilities.combustion.Combustion.cap(
                    event.getEntity(), event.getAmount());

            if (allowed <= 0.0F) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(allowed);
        }

        // Compressed punches: a punch that actually LANDS hits for 10 rather than
        // whatever the channeler's fists are worth.
        //
        // Done here because this is where melee damage is decided — the wave that goes
        // out on the same click is a separate thing, thrown from the left-click packet.
        // Keyed on the source having a player ATTACKER and not being a projectile,
        // which is the mod's existing test for "somebody hit this by hand" (see Air
        // Aura). Set rather than added, so it does not stack with a weapon.
        if (event.getSource().getEntity() instanceof ServerPlayer puncher
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {

            ChannelingData puncherData = puncher.getData(ModAttachments.CHANNELING_DATA);

            if (puncherData.isPunchingCompressed()) {
                // Compressed punches wins, because it is an ability being actively
                // held up and paid for by the second, where Tough knuckles is a
                // permanent floor. They are not added together.
                event.setAmount(io.github.niilo411.elementalpaths.abilities.sound.Sound.damage(puncherData,
                        io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches.PUNCH_DAMAGE));

            } else if (puncherData.hasPassiveEquipped(
                    io.github.niilo411.elementalpaths.abilities.metal.ToughKnuckles.KEY)
                    && puncher.getMainHandItem().isEmpty()) {

                // Tough knuckles replaces what an EMPTY hand is worth, and only an
                // empty one: the passive is about punching, and letting it apply while
                // holding a weapon would make it a flat damage buff that happened to
                // be called knuckles. Raised to, never lowered from — a bare fist is
                // already worth more than this to somebody with a Strength potion.
                event.setAmount(Math.max(event.getAmount(),
                        io.github.niilo411.elementalpaths.abilities.metal.ToughKnuckles.PUNCH_DAMAGE));

            } else if (puncherData.hasPassiveEquipped(
                    io.github.niilo411.elementalpaths.abilities.nochanneling.SwordMastery.KEY)
                    && puncher.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem) {

                // Sword Mastery ADDS to what the sword and its enchantments already hit
                // for, the mirror of Tough knuckles' replace-an-empty-hand — a sword
                // already has a real damage figure of its own to build on, where a bare
                // fist does not.
                event.setAmount(event.getAmount()
                        + io.github.niilo411.elementalpaths.abilities.nochanneling.SwordMastery.bonusFor(puncherData));
            }
        }

        // Blue Fire doubles what the channeler's own FIRE abilities hit for, and nothing
        // else.
        //
        // IS_FIRE with a player behind it is a close match for exactly that: every fire
        // ability in the mod damages through damageSources().inFire(), while the fire
        // sources a player can cause without channeling — a Fire Aspect burn, a lit block,
        // spilled lava — all arrive with no attacker attached and so never qualify.
        //
        // Explosions used to be included as well, to catch Fireball. That was too broad:
        // it doubled any explosion the player caused, TNT included, which is not a fire
        // ability by any reading.
        net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer attackingPlayer
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {

            ChannelingData attackerData = attackingPlayer.getData(ModAttachments.CHANNELING_DATA);
            if (attackerData.hasPassiveEquipped(io.github.niilo411.elementalpaths.abilities.fire.BlueFire.KEY)) {
                event.setAmount(event.getAmount()
                        * io.github.niilo411.elementalpaths.abilities.fire.BlueFire.DAMAGE_MULTIPLIER);
            }
        }

        // Caught in a Gravity Orbit: whatever the victim manages to land while being
        // swung round the ring hits for HALF. Keyed on the CAUSING entity, so an arrow or
        // an ability loosed from the ring is halved as well as a punch. After every
        // multiplier above, so it halves the finished figure rather than a part of it.
        if (attacker instanceof net.minecraft.world.entity.LivingEntity orbiting
                && io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.isOrbiting(orbiting)) {
            event.setAmount(event.getAmount()
                    * io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbits.VICTIM_DAMAGE_MULTIPLIER);
        }

        // A world event's damage multiplier, applied to ANY damage source rather than to
        // fire's alone — a blood moon lifts water channeling, which hits through indirectMagic
        // and drown, and neither is a tag this handler could key off on its own.
        //
        // WHICH ELEMENT is read off the attacker, where the dispatcher records it for the
        // duration of an ability's effect. That is exact for anything that damages as it
        // casts, which is nearly everything; a PROJECTILE lands long after its cast has
        // finished, so it carries its own copy taken at launch instead. See
        // ChannelingProjectiles and ChannelingData.castingElement.
        if (event.getSource().getEntity() instanceof ServerPlayer caster) {
            String casting = caster.getData(ModAttachments.CHANNELING_DATA).getCastingElement();

            if (!casting.isEmpty()) {
                float eventScale = io.github.niilo411.elementalpaths.events.WorldEvents
                        .damageMultiplier(caster.level(), casting);

                if (eventScale != 1.0F) event.setAmount(event.getAmount() * eventScale);
            }
        }

        if (!event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        // From here down is damage from STANDING IN fire, not from being hit by an
        // ability that happens to burn. Fire block contact has no causing entity,
        // where ability damage carries the channeler. Without this check, hitting a mob
        // that is standing in channeling fire would have the ability's own damage
        // overwritten by the block's.
        if (event.getSource().getEntity() != null) return;

        // Blue fire burns at a flat rate rather than a multiple of ordinary fire:
        // 3 hearts a hit, and fire lands roughly once a second through the victim's
        // invulnerability frames. Checked off the block itself rather than off
        // ChannelingFire's tracking, so it holds for blue fire from any source —
        // including Firewall's, which is laid untracked at plain damage.
        net.minecraft.world.level.block.state.BlockState standingIn =
                level.getBlockState(event.getEntity().blockPosition());

        if (standingIn.getBlock() instanceof ChannelingFireBlock
                && standingIn.getValue(ChannelingFireBlock.BLUE)) {
            event.setAmount(io.github.niilo411.elementalpaths.abilities.fire.BlueFire.CONTACT_DAMAGE);
            return;
        }

        float multiplier = io.github.niilo411.elementalpaths.abilities.ChannelingFire.getMultiplier(
                level, event.getEntity().blockPosition());
        if (multiplier > 1.0F) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

            // --- UNIVERSAL COOLDOWN TICKER (Must be at the very top!) ---
            data.tickCooldowns();

            // The Spirit Realm's tide of low gravity. Asked every tick for every player
            // because it is what takes the modifier back OFF someone who has walked out
            // of the dimension, which is as much its job as putting it on — and it does
            // nothing at all unless the answer has actually changed.
            io.github.niilo411.elementalpaths.spirit.SpiritGravity.tick(player);

            // --- FIRE IMMUNITY PASSIVE ---
            // Damage is cancelled in the damage handler, but burning is separate from
            // being hurt by it: without this the player stands there wreathed in
            // flames taking nothing, which reads as a bug rather than as immunity.
            if (player.isOnFire() && data.hasPassiveEquipped(
                    io.github.niilo411.elementalpaths.abilities.fire.FireImmunity.KEY)) {
                player.clearFire();
            }

            // --- LAVA RESISTANCE PASSIVE ---
            // The other half of it. Damage is cancelled in the damage handler, but
            // burning is separate from being hurt by it — without this a channeler stands
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
                    io.github.niilo411.elementalpaths.abilities.lava.LavaResistance.KEY)) {
                player.clearFire();
            }

            // --- WATER BREATHING PASSIVE ---
            // Topped up rather than granted as a potion effect, so nothing can dispel
            // it and no timer is ever shown.
            if (player.getAirSupply() < player.getMaxAirSupply()
                    && data.hasPassiveEquipped(
                            io.github.niilo411.elementalpaths.abilities.water.WaterBreathing.KEY)) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            player.setData(ModAttachments.CHANNELING_DATA, data);

            // --- PASSIVES ARE SILENT IN THE SPIRIT REALM ---
            // Set BEFORE anything below reads a passive, because almost everything below
            // does. Channeling is already refused there; a passive is channeling that happens
            // to need no cast, so leaving them running would have meant fire immunity,
            // flight and doubled chi regen all working in the one place nothing else does.
            data.setPassivesSuppressed(
                    io.github.niilo411.elementalpaths.spirit.SpiritRealm.isSpiritRealm(player.level()));

            // --- CHI BLOCKED ---
            // Counted down here rather than in the tracker, because the tracker only
            // knows about marks: a block outlives the mark that caused it, and could
            // in principle be applied by something that never marked anybody at all.
            io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlocks.tickBlocked(player, data);

            // --- CHI REGEN ---
            // Regen is held off for a few seconds after any chi is spent, so a cheap
            // ability can't be sustained indefinitely by regen alone. The countdown
            // runs every tick; the refill itself stays on the 1-second cadence.
            //
            // TWO THINGS SKIP IT ENTIRELY. A non-channeler has no chi to refill — that is
            // what choosing no channeling means, and the HUD draws them no bar either. And
            // a channeler whose chi points have been struck is frozen where they stand,
            // which is the half of chi blocking that actually decides a fight: being
            // unable to cast for fifteen seconds is an inconvenience, coming out of it
            // with an empty pool is the punishment.
            //
            // Asked once and held rather than asked again later for the meditation
            // check below — same tick, same unlocked-elements list, so the answer
            // cannot have changed in between.
            // A MAXIMUM THAT FALLS TAKES THE POOL DOWN WITH IT; ONE THAT RISES DOES NOT.
            // The maximum can drop out from under a full pool — the base or per-level
            // figure lowered in the settings, a level taken away with /paths level — and
            // nothing else would ever bring chi back under it: regen only runs below the
            // cap, and the HUD would draw a bar overflowing its own frame. So it is held
            // at the new cap here, every tick, whatever lowered it. A RISE is deliberately
            // left alone and simply regenerates up, so levelling or a generous settings
            // change is headroom to earn rather than a free refill.
            int maxChi = data.getMaxChi();
            if (data.getCurrentChi() > maxChi) {
                data.setCurrentChi(Math.max(0, maxChi));
                player.setData(ModAttachments.CHANNELING_DATA, data);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));
            }

            boolean isNoChanneler = io.github.niilo411.elementalpaths.abilities.nochanneling.NoChanneling.is(data);
            if (isNoChanneler || data.isChiBlocked()) {
                // Nothing at all, deliberately — not even the delay countdown, which
                // exists only to pace a refill that is not going to happen.
                player.setData(ModAttachments.CHANNELING_DATA, data);
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
                            io.github.niilo411.elementalpaths.abilities.lightning.LightningStrength.KEY)) {
                        regenAmount *= io.github.niilo411.elementalpaths.abilities.lightning
                                .LightningStrength.CHI_REGEN_MULTIPLIER;
                    }

                    // Spirit armor adds a fraction of a percent per piece worn, so the
                    // arithmetic runs in THOUSANDTHS and keeps whatever it could not hand
                    // over. Without the carry a 46.5% piece on a base of 6 would round away
                    // and the pieces would step unevenly — see ChannelingData's note.
                    //
                    // With nothing worn the bonus is 0, so this is exactly the old
                    // `regenAmount` and the carry never leaves zero.
                    int thousandths = regenAmount * (io.github.niilo411.elementalpaths.SpiritArmor.BONUS_SCALE
                            + io.github.niilo411.elementalpaths.SpiritArmor.regenBonusTenths(player))
                            + data.getChiRegenCarry();

                    regenAmount = thousandths / io.github.niilo411.elementalpaths.SpiritArmor.BONUS_SCALE;
                    data.setChiRegenCarry(thousandths % io.github.niilo411.elementalpaths.SpiritArmor.BONUS_SCALE);

                    data.setCurrentChi(Math.min(data.getMaxChi(), data.getCurrentChi() + regenAmount));

                    // Save the data and sync it to the UI
                    player.setData(ModAttachments.CHANNELING_DATA, data);
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

                    serverLevel.sendParticles(io.github.niilo411.elementalpaths.abilities.ChannelingFire.flame(data), px, py, pz, 2, 0.1, 0.1, 0.1, 0.02);
                }
            }

            // --- ARMED TWO-PHASE ABILITY ---
            // Lets it draw what it is holding, and runs down the window for the ones
            // that have a time limit.
            if (!data.getActiveTwoPhaseAbility().isEmpty()) {
                AbilityHandler.tickArmedTwoPhase(player, data);
            }

            // --- MEDITATING LOGIC ---
            // A non-channeler cannot meditate. Meditation exists to fill a chi pool and to
            // earn XP from stillness, and they have neither — their XP comes from
            // killing instead (see NoChanneling). Stopped HERE rather than at the keybind
            // so it holds however the flag was set.
            if (data.isMeditating() && isNoChanneler) {
                data.setMeditating(false);
                data.setMeditateTickTimer(0);
                player.setData(ModAttachments.CHANNELING_DATA, data);
            }

            if (data.isMeditating()) {
                data.setMeditateTickTimer(data.getMeditateTickTimer() + 1);
                player.setData(ModAttachments.CHANNELING_DATA, data);

                player.setDeltaMovement(0, player.getDeltaMovement().y < 0 ? -0.08 : 0, 0);
                player.hurtMarked = true;

                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    if (data.getMeditateTickTimer() % 5 == 0) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    }
                }

                // XP every 1 second (20 ticks). The RATE is not fixed any more: the
                // Advanced meditating passive raises it with the channeler's level, and
                // that one method is the only place the figure is decided — with the
                // passive unequipped it simply answers the old flat 2.
                if (data.getMeditateTickTimer() % 20 == 0) {
                    io.github.niilo411.elementalpaths.abilities.AbilitySupport.grantXp(data,
                            io.github.niilo411.elementalpaths.abilities.air.AdvancedMeditating.meditationRate(data));

                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));
                }
            }

            // --- FIRE LEAP TICKS ---
            // Fire Leap ends itself on landing, so it isn't a channeled ability;
            // its per-tick trail logic lives on the ability class all the same.
            if (data.isFireLeaping()) {
                io.github.niilo411.elementalpaths.abilities.fire.FireLeap.tick(player, data);
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
                io.github.niilo411.elementalpaths.abilities.fire.FireRain.tick(player, data);
            }

            // --- AIR JUMP TICK ---
            // Runs the fall-protection window and ends it on landing. Same shape as
            // Fire Leap and Fire Rain: cast once, then a countdown on the data.
            if (data.getAirJumpTicks() > 0) {
                io.github.niilo411.elementalpaths.abilities.air.AirJump.tick(player, data);
            }

            // --- FLIGHT PASSIVE TICK ---
            // Runs unconditionally: taking flight away when the passive is unequipped
            // or the chi runs out is as much this call's job as granting it.
            io.github.niilo411.elementalpaths.abilities.air.Flight.tick(player, data);

            // --- COMBUSTION SCROLL BLAST IMMUNITY ---
            if (data.getBlastImmuneTicks() > 0) {
                data.setBlastImmuneTicks(data.getBlastImmuneTicks() - 1);
            }

            // --- GRAVITY SCROLL FALL IMMUNITY ---
            if (data.getGravityFallImmuneTicks() > 0) {
                data.setGravityFallImmuneTicks(data.getGravityFallImmuneTicks() - 1);
            }

            // --- CHANNELING LOCKOUT (Deafen) ---
            if (data.getChannelingLockedTicks() > 0) {
                data.setChannelingLockedTicks(data.getChannelingLockedTicks() - 1);
            }

            // --- SOUND TOGGLES, BILLED BY THE SECOND ---
            // Compressed punches and Sound wall are toggles rather than channels, so
            // the dispatcher's channel billing does not reach them — they are charged
            // here instead, on the same one-second beat, and switch themselves off the
            // moment the chi runs out.
            if (data.isPunchingCompressed()) {
                data.setCompressedPunchTicks(data.getCompressedPunchTicks() + 1);

                boolean outOfTime = data.getCompressedPunchTicks()
                        >= io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches.MAX_TICKS;

                boolean affordable = chargeSoundToggle(player, data,
                        "compressed punches", io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches.XP_PER_SECOND);

                // Both endings go through the ability's own stop(), so the thirty
                // second cooldown is stamped whichever one arrives first — running out
                // of chi must not be a cheaper way to end it than running out of time.
                if (outOfTime || !affordable) {
                    io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches.stop(player, data);
                }
            }

            // --- FIRE ROCKET (toggle) ---
            // Was a channel, so the dispatcher used to hold the flight open and drain the
            // chi. As a toggle both are this loop's job: the per-tick half keeps vanilla
            // from clearing the flight flag, and the per-second half bills it and puts the
            // rocket out when the pool runs dry.
            if (data.isFireRocketing()) {
                io.github.niilo411.elementalpaths.abilities.fire.FireRocket.tick(player, data);

                if (!chargeSoundToggle(player, data,
                        "fire rocket", io.github.niilo411.elementalpaths.abilities.fire.FireRocket.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.fire.FireRocket.XP_PER_SECOND)) {
                    io.github.niilo411.elementalpaths.abilities.fire.FireRocket.stop(player, data);
                }
            }

            if (io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.has(player)) {
                if (!chargeSoundToggle(player, data,
                        "combustion beam", io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeam.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeam.XP_PER_SECOND)) {
                    io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeams.stop(player);
                }
            }

            if (io.github.niilo411.elementalpaths.abilities.metal.MetalShields.has(player)) {
                if (!chargeSoundToggle(player, data,
                        "metal shield", io.github.niilo411.elementalpaths.abilities.metal.MetalShield.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.metal.MetalShield.XP_PER_SECOND)) {
                    io.github.niilo411.elementalpaths.abilities.metal.MetalShields.drop(player);
                }
            }

            if (io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.has(player)) {
                if (!chargeSoundToggle(player, data,
                        "sound wall", io.github.niilo411.elementalpaths.abilities.sound.SoundWall.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.sound.SoundWall.XP_PER_SECOND)) {
                    io.github.niilo411.elementalpaths.abilities.sound.SoundWalls.drop(player);
                }
            }

            // --- SPEED BOOST (toggle) ---
            if (io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.has(player)) {
                io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.tick(player);

                if (!chargeSoundToggle(player, data,
                        "speed boost", io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoost.CHI_PER_SECOND,
                        io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoost.XP_PER_SECOND)) {
                    io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoosts.stop(player);
                }
            }

            // --- BASS BOUNCE TICK ---
            // Cast and left running, like Fire Leap: the hop happens now and the slam
            // happens on landing.
            if (data.getBassBounceTicks() > 0) {
                io.github.niilo411.elementalpaths.abilities.sound.BassBounce.tick(player, data);
            }

            // --- LIGHTNING STRENGTH PASSIVE ---
            // Runs unconditionally, like Flight: taking the Speed back off when the
            // passive is unequipped is as much this call's job as granting it.
            io.github.niilo411.elementalpaths.abilities.lightning.LightningStrength.tick(player, data);

            // --- QUICK HANDS PASSIVE ---
            // Runs unconditionally too, for the same reason: taking Haste back off
            // when the passive is unequipped is as much this call's job as granting it.
            io.github.niilo411.elementalpaths.abilities.nochanneling.QuickHands.tick(player, data);

            // --- PARAGON LAST STAND ---
            // Resistance and Regeneration below three hearts, taken back off above
            // it. Runs unconditionally: taking the buffs away when the Paragon heals
            // up — or loses the title outright — is as much this call's job as
            // granting them.
            io.github.niilo411.elementalpaths.paragon.Paragon.tick(player, data);

            // --- CHANNELING ARMOR VISUAL ---
            // Broadcast only when it changes. Effects are synced to their owner alone,
            // so onlookers learn about a stone or steel suit from here or not at all.
            for (io.github.niilo411.elementalpaths.ChannelingArmorSuit suit
                    : io.github.niilo411.elementalpaths.ChannelingArmorSuit.VALUES) {
                boolean armored = suit.isWornBy(player);
                if (armored != data.isArmorSuitShown(suit)) {
                    data.setArmorSuitShown(suit, armored);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                            player, new io.github.niilo411.elementalpaths.network.ChannelingArmorPacket(
                                    player.getId(), suit.ordinal(), armored));
                }
            }
        }
    }

    /**
     * Charges a per-second sound toggle, on the same beat chi regen uses.
     *
     * Returns false when the channeler can no longer afford it, which is the caller's cue
     * to switch the toggle off. Spending goes through consumeChi so the regen delay is
     * re-armed each second, exactly as it is for a channel — a toggle that quietly
     * regenerated its own upkeep would be free.
     *
     * @return true if the toggle may keep running
     */
    private static boolean chargeSoundToggle(net.minecraft.server.level.ServerPlayer player,
                                             ChannelingData data, String key,
                                             int chiPerSecond, int xpPerSecond) {
        if (player.tickCount % 20 != 0) return true;

        // The constants passed in are only the defaults; the settings screen can retune
        // each toggle's upkeep, and asking here reaches one that is already running.
        chiPerSecond = io.github.niilo411.elementalpaths.abilities.AbilityTuning.upkeepChi(key, chiPerSecond);
        xpPerSecond = io.github.niilo411.elementalpaths.abilities.AbilityTuning.upkeepXp(key, xpPerSecond);

        if (data.getCurrentChi() < chiPerSecond) return false;

        data.consumeChi(chiPerSecond);
        io.github.niilo411.elementalpaths.abilities.AbilitySupport.grantXp(data, xpPerSecond);
        io.github.niilo411.elementalpaths.abilities.AbilitySupport.syncData(player, data);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            // Belt and braces alongside the death handler: a client that somehow still
            // believes in a suit is corrected the moment the channeler is back.
            ChannelingData armorData = player.getData(ModAttachments.CHANNELING_DATA);
            armorData.clearArmorSuitsShown();
            for (io.github.niilo411.elementalpaths.ChannelingArmorSuit suit
                    : io.github.niilo411.elementalpaths.ChannelingArmorSuit.VALUES) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new io.github.niilo411.elementalpaths.network.ChannelingArmorPacket(
                                player.getId(), suit.ordinal(), false));
            }

            // Same safety net as login: if the player died mid-rocket, onStop() never
            // ran. Vanilla usually rebuilds abilities from the gamemode on respawn,
            // but the cost of being wrong here is permanent creative flight.
            if (!player.isCreative() && !player.isSpectator()
                    && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                io.github.niilo411.elementalpaths.abilities.fire.FireRocket.stopFlight(player);
            }

            // Give the client 5 ticks to load the new body before sending the UI sync
            player.getServer().tell(new net.minecraft.server.TickTask(player.getServer().getTickCount() + 5, () -> {
                ChannelingData data = player.getData(ModAttachments.CHANNELING_DATA);

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket(
                        data.getMainElement() == null ? "" : data.getMainElement(),
                        data.getActiveElement() == null ? "" : data.getActiveElement(),
                        data.getUnlockedElements(),
                        data.hasChosenElement(),
                        data.getUnlockedAbilities(),
                        data.getEquippedAbilities()
                ));

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

                // The passive slots need their own packet: SyncChannelingDataPacket is
                // already at six fields, which is as many as StreamCodec.composite
                // takes. Without this the server still applies the player's passives
                // but the menu shows every slot empty after a death.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new io.github.niilo411.elementalpaths.network.SyncPassivesPacket(data.getEquippedPassives()));

                // The lives counter needs the same treatment as the passives: the
                // server still knows, but the client's copy is rebuilt on respawn,
                // and a death is exactly when that number has just changed.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new io.github.niilo411.elementalpaths.network.SyncParagonPacket(
                                data.isParagon(), data.getParagonLives()));

                io.github.niilo411.elementalpaths.abilities.blood.Blood.sync(player, data);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new io.github.niilo411.elementalpaths.network.SyncUpgradesPacket(data.getUnlockedUpgrades()));

                // Dying mid-channel or mid-charge ends them server-side, but these two
                // are client-side statics that survive it — leaving a stale charge bar
                // on screen, or worse, a player who respawns unable to move.
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new io.github.niilo411.elementalpaths.network.RootedPacket(false));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new io.github.niilo411.elementalpaths.network.ChargeStatusPacket("", 0, 0, false));
            }));
        }
    }
}
