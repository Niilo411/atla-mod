package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lighting a spirit portal by bending in front of it.
 *
 * FIVE ABILITY USES within TEN SECONDS, standing within {@value #RADIUS} blocks of an
 * unlit frame. They do NOT have to be different abilities — the same one cast five times
 * opens the portal exactly as five different ones would. Only the count and the window
 * matter.
 *
 * All of this is TRANSIENT. A player's recent casts are not worth persisting — the
 * window is ten seconds, and anyone who logs out mid-sequence can simply do it again.
 * The portals themselves are a different matter and are not tracked here at all: a lit
 * portal is lit because its blocks are in the world, and its one-minute life is a
 * scheduled block tick that vanilla saves with the chunk. See {@link SpiritPortalBlock}.
 */
public final class SpiritPortals {

    /** How many ability uses are needed, of any abilities at all. */
    public static final int REQUIRED_USES = 5;

    /** How long the window is: ten seconds. */
    public static final int WINDOW_TICKS = 20 * 10;

    /**
     * How far from the frame the bending has to happen, measured horizontally.
     *
     * Has to cover the whole temple and a little outside it. Ten did not, and that was a
     * real bug rather than a tight number: a temple is thirteen deep with its portal four
     * blocks back from the middle, so from the doorway to the portal is exactly ten and
     * from outside the door it is more. Anyone who walked into a temple they had found
     * and bent where they stood was out of range of the thing they were standing in front
     * of.
     *
     * It went unnoticed because /bend temple builds the room AROUND the player, which
     * leaves them four blocks from the portal and always in range — so the command always
     * worked and only naturally generated temples failed.
     */
    public static final int RADIUS = 18;

    /**
     * How far up and down the search looks, which is much less.
     *
     * A portal's hole sits between the floor and three blocks above it, so a player
     * standing in front of one is always within a few blocks vertically. Keeping this
     * small is what stops the wider horizontal reach turning the search into a 50,000
     * block cube.
     */
    public static final int VERTICAL_RADIUS = 6;

    /**
     * The game ticks each player last cast on, oldest first.
     *
     * A queue of timestamps rather than a map keyed on the ability, which is the whole
     * of the "any five casts" rule: nothing here can tell one ability from another, so
     * repeating one counts every time. Ordered, so expiring the stale ones is taking
     * them off the front rather than walking the lot.
     */
    private static final Map<UUID, Deque<Long>> RECENT = new HashMap<>();

    /**
     * When each player last had the world searched for a frame.
     *
     * Needed because the search is a cube of block reads and the trigger is not rare:
     * five casts in ten seconds is an ordinary fight, not something only ever
     * done in front of a temple. Without this, every cast after the fifth would scan
     * nine thousand positions again — for the whole ten seconds, for every bender in
     * the fight.
     */
    private static final Map<UUID, Long> LAST_SCAN = new HashMap<>();

    /** The least time between two searches for the same player. */
    private static final int SCAN_EVERY = 20;

    private SpiritPortals() {
    }

    /**
     * Records one ability actually being used, and lights a portal if that was the
     * fifth.
     *
     * Called from the dispatcher at the points where an ability has passed every gate
     * and its chi has been spent — a refused cast is not a use, or a player standing in
     * front of a frame on cooldown would light it by pressing keys that did nothing.
     */
    public static void recordUse(ServerPlayer player, String abilityKey) {
        if (abilityKey == null || abilityKey.isEmpty()) return;
        // Bending is impossible in the Spirit World anyway, and its portal never closes,
        // so there is nothing there to light.
        if (SpiritWorld.isSpiritWorld(player.level())) return;

        long now = player.level().getGameTime();
        Deque<Long> used = RECENT.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        used.addLast(now);

        forgetStale(used, now);

        if (used.size() < REQUIRED_USES) return;

        // Throttled, not skipped: the sequence stays complete, so the next cast a second
        // later still opens the portal. Only the searching is rationed.
        Long last = LAST_SCAN.get(player.getUUID());
        if (last != null && now - last < SCAN_EVERY) return;
        LAST_SCAN.put(player.getUUID(), now);

        // A FRAME FIRST, ALWAYS. The two triggers overlap — an Avatar could perfectly well
        // meditate inside a temple — and this ordering means the behaviour that already
        // existed is never taken away by the new one. Lighting the frame you are standing
        // in front of is also plainly the better outcome: that portal lasts a minute and
        // has a temple with a way back at the far end, where the torn one lasts fifteen
        // seconds and lands you on bare ground.
        if (tryLight(player) || tryTear(player)) {
            // Cleared so the next portal needs its own five, rather than the sixth
            // ability opening a second one for free.
            used.clear();
        }
    }

    // ==========================================================================
    //  The Avatar's own portal
    // ==========================================================================

    /** How long a torn portal stands before it closes: fifteen seconds. */
    public static final int TORN_TICKS = 20 * 15;

    /** How wide and how tall the opening is. Three by three, like an overworld temple's. */
    private static final int TORN_WIDTH = 3;
    private static final int TORN_HEIGHT = 3;

    /** How far in front of the Avatar it opens. Tried in order, nearest first. */
    private static final int[] TORN_DISTANCES = { 2, 3 };

    /**
     * The Avatar tearing a way through while meditating.
     *
     * NOT A FRAME AND NOT A TEMPLE. Every other portal in the mod is a hole in something
     * somebody built; this is the Avatar's own bridge to the spirit world, opened by
     * sitting still and bending anyway — which is why it asks for both at once. Meditating
     * roots you and casting is the one thing the rooting does not stop, so doing five casts
     * without moving is a deliberate act rather than something a fight produces by
     * accident.
     *
     * @return whether one was opened, so the caller knows to spend the sequence
     */
    private static boolean tryTear(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        com.minecraft.atlamod.BendingData data =
                player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);

        // Cheapest first, and both are one boolean read. Only the Avatar can do this at
        // all, and only while actually sitting in meditation.
        if (!data.isAvatar()) return false;
        if (!data.isMeditating()) return false;

        for (int distance : TORN_DISTANCES) {
            if (tearAt(level, player, distance)) return true;
        }

        // TOLD, not silent. Five casts is a real effort to spend on nothing, and "there
        // is a wall in the way" is something the player can act on by turning round.
        player.displayClientMessage(Component.literal(
                "§bThere is no room in front of you to open a way."), true);
        return false;
    }

    /**
     * Lays the opening at one distance, if it fits.
     *
     * ONLY EVER FILLS AIR, and all of it or none — the same rule every block-placing
     * ability in this mod follows, and the reason the whole footprint is checked before a
     * single block is written. A portal with a corner missing because somebody's fence
     * post was there is not a portal you can walk through, and carving out the fence post
     * to make room would be the griefing the air-only rule exists to prevent.
     */
    private static boolean tearAt(ServerLevel level, ServerPlayer player, int distance) {
        net.minecraft.core.Direction facing = player.getDirection();

        // The plane stands ACROSS the way they are looking, so they walk into its face
        // rather than along its edge. A portal's axis is the one its width runs along,
        // which is the horizontal axis the facing is not.
        net.minecraft.core.Direction.Axis axis =
                facing.getAxis() == net.minecraft.core.Direction.Axis.X
                        ? net.minecraft.core.Direction.Axis.Z
                        : net.minecraft.core.Direction.Axis.X;

        BlockPos base = player.blockPosition().relative(facing, distance);

        java.util.List<BlockPos> opening = new java.util.ArrayList<>(TORN_WIDTH * TORN_HEIGHT);

        for (int w = 0; w < TORN_WIDTH; w++) {
            for (int h = 0; h < TORN_HEIGHT; h++) {
                // Centred on the player's line, so the middle column is straight ahead.
                int across = w - (TORN_WIDTH / 2);

                BlockPos at = axis == net.minecraft.core.Direction.Axis.X
                        ? base.offset(across, h, 0)
                        : base.offset(0, h, across);

                if (!level.getBlockState(at).isAir()) return false;
                opening.add(at);
            }
        }

        net.minecraft.world.level.block.state.BlockState portal =
                com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get().defaultBlockState()
                        .setValue(SpiritPortalBlock.AXIS, axis)
                        .setValue(SpiritPortalBlock.ISLAND, true);

        for (BlockPos at : opening) {
            level.setBlock(at, portal, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);

            // The same scheduled tick an ordinary portal gets, just a much shorter one.
            // Saved with the chunk, so the fifteen seconds survives a save, a restart, or
            // the chunk unloading with nobody near it — none of which a countdown held in
            // memory would. Every block gets one; whichever fires first tears down the
            // rest. See SpiritPortalBlock.
            level.scheduleTick(at, com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get(), TORN_TICKS);
        }

        opening(level, opening.get(0));
        player.displayClientMessage(Component.literal(
                "§bYou tear a way open. It will not hold long."), true);

        return true;
    }

    /**
     * The far end, standing on the island somebody is about to arrive on.
     *
     * WITHOUT THIS THERE IS NO WAY BACK. Every other portal in the mod leads to a temple,
     * and a temple's own portal never closes, so the return trip takes care of itself. An
     * island has nothing on it — so unless the far end is built too, the Avatar's portal
     * is one way and whoever used it is walking until they find a temple.
     *
     * ITS FIFTEEN SECONDS START WHEN IT IS BUILT, not when the near end was torn, and that
     * is an INTERPRETATION rather than something the design settles. Both ends closing on
     * the same clock would be more literally "the portal lasts fifteen seconds", and would
     * also strand anyone who stepped through on the fourteenth. Giving this end its own
     * fifteen makes the trip a round one: cross, look, and come back, or stay and walk.
     *
     * The arrival is INSIDE it, exactly as a temple arrival is — that is what the portal
     * cooldown on the arriving entity is for, and the same pattern already proven there.
     */
    public static void tearFarSide(ServerLevel spirit, BlockPos arrival) {
        net.minecraft.core.Direction.Axis axis = net.minecraft.core.Direction.Axis.X;

        net.minecraft.world.level.block.state.BlockState portal =
                com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get().defaultBlockState()
                        .setValue(SpiritPortalBlock.AXIS, axis)
                        .setValue(SpiritPortalBlock.ISLAND, true);

        java.util.List<BlockPos> opening = new java.util.ArrayList<>(TORN_WIDTH * TORN_HEIGHT);

        for (int w = 0; w < TORN_WIDTH; w++) {
            for (int h = 0; h < TORN_HEIGHT; h++) {
                BlockPos at = arrival.offset(w - (TORN_WIDTH / 2), h, 0);

                // Anything already there is left alone and simply not filled. An island's
                // surface carries trees and boulders, and a portal missing a corner is
                // still a portal you can step into — where clearing the tree to square it
                // off would be the one thing every block-placing rule in this mod forbids.
                if (!spirit.getBlockState(at).isAir()) continue;
                opening.add(at);
            }
        }

        // Nothing could be placed at all, which would take a very unlucky arrival. The
        // traveller still gets there; they just have to walk home the long way.
        if (opening.isEmpty()) return;

        for (BlockPos at : opening) {
            spirit.setBlock(at, portal, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);

            // THE SPIRIT WORLD SIDE IS NORMALLY GIVEN NO TICK AT ALL — see activate, where
            // that one `if` is the only difference between the two sides and is what makes
            // a temple portal a permanent way home. This one is scheduled deliberately:
            // it is a tear rather than a temple, and a permanent hole on a random island
            // is exactly what the Avatar's portal is not meant to leave behind.
            spirit.scheduleTick(at, com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get(), TORN_TICKS);
        }

        opening(spirit, opening.get(0));
    }

    /**
     * Drops casts that have fallen out of the ten second window.
     *
     * Only the front needs looking at: the queue is in the order the casts happened, so
     * the moment one is young enough to keep, everything behind it is too.
     */
    private static void forgetStale(Deque<Long> used, long now) {
        while (!used.isEmpty() && now - used.peekFirst() > WINDOW_TICKS) {
            used.removeFirst();
        }
    }

    /**
     * Looks for an unlit frame in range and lights it.
     *
     * @return whether one was found, so the caller knows whether the sequence was spent
     */
    private static boolean tryLight(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        Optional<SpiritPortalFrame.Frame> found =
                SpiritPortalFrame.findInactiveNear(level, player.blockPosition(), RADIUS, VERTICAL_RADIUS);
        if (found.isEmpty()) return false;

        activate(level, found.get());
        player.displayClientMessage(Component.literal("§bThe spirit portal opens."), true);
        return true;
    }

    /**
     * Fills a frame and starts its clock.
     *
     * The clock is the whole difference between the two sides. An overworld portal is
     * given a scheduled tick a minute out and closes itself; the Spirit World's is given
     * none at all, so it stays open forever and there is always a way back. That is one
     * `if`, and it is deliberately the only place the two sides differ.
     */
    public static void activate(Level level, SpiritPortalFrame.Frame frame) {
        SpiritPortalFrame.light(level, frame);

        if (!SpiritWorld.isSpiritWorld(level)) {
            for (BlockPos pos : frame.interior()) {
                level.scheduleTick(pos, com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get(),
                        SpiritPortalBlock.OPEN_TICKS);
            }
        }

        opening(level, frame.bottomLeft());
    }

    /**
     * The sound of a spirit portal opening.
     *
     * THREE SOUNDS AT ONCE RATHER THAN ONE, each chosen to sit in a different part of the
     * range so they read as a single composite rather than a muddle: a deep swell
     * underneath, the old portal-fill thunk in the middle, and a bright chime over the
     * top. One vanilla sound on its own always sounds like that vanilla sound; three
     * layered and re-pitched do not sound like any of them.
     *
     * Nothing is shipped for this. They are sounds the player's own copy of the game
     * already has, the same trade the portal's blue makes with its texture.
     *
     * THE SWELL IS DELIBERATELY LOUDER THAN FULL. Volume above 1 does not clip in
     * Minecraft — it widens the radius the sound carries to — so the low layer is audible
     * well across an island while the detail layers stay local to the temple. A portal
     * opening should be something other people notice.
     */
    private static void opening(Level level, BlockPos at) {
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6F, 0.5F);
        level.playSound(null, at, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.9F, 0.7F);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.8F);
    }

    /** Drops a player's part-finished sequence and scan throttle when they leave. */
    public static void forget(UUID player) {
        RECENT.remove(player);
        LAST_SCAN.remove(player);
    }
}
