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

        if (tryLight(player)) {
            // Cleared so the next portal needs its own five, rather than the sixth
            // ability lighting a second frame for free.
            used.clear();
        }
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

        BlockPos sound = frame.bottomLeft();
        level.playSound(null, sound, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** Drops a player's part-finished sequence and scan throttle when they leave. */
    public static void forget(UUID player) {
        RECENT.remove(player);
        LAST_SCAN.remove(player);
    }
}
