package com.minecraft.atlamod.abilities;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers which fire blocks were placed by an ability, so burning in bender's
 * fire can hurt more than burning in ordinary fire.
 *
 * Vanilla has nowhere to hang "this particular fire is special" — a fire block is
 * a fire block — and registering a whole custom block just to change a damage
 * number would drag in a blockstate, model, texture and its own spread rules.
 * Tracking positions instead keeps it to bookkeeping, and the damage bump is
 * applied by the LivingIncomingDamageEvent handler in ServerEvents.
 *
 * Entries expire on their own, so a fire that burns out (or is put out, or is
 * walked away from) stops counting without anything needing to notice.
 */
public final class BendingFire {

    /** Dimension + position. Bare coordinates would let Nether fire match Overworld fire. */
    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    /** Position -> game time at which the entry stops counting. */
    private static final Map<Key, Long> ENHANCED = new HashMap<>();

    /** Prune no more than once per this many ticks, so casts don't walk the whole map. */
    private static final long PRUNE_INTERVAL = 100L;

    private static long nextPrune = 0L;

    private BendingFire() {
    }

    /** Flags a fire block as ability-made for the next {@code lifetimeTicks} ticks. */
    public static void mark(ServerLevel level, BlockPos pos, int lifetimeTicks) {
        long now = level.getGameTime();
        maybePrune(now);
        ENHANCED.put(new Key(level.dimension(), pos.immutable()), now + lifetimeTicks);
    }

    /** Whether the fire at this position was placed by an ability and is still counted. */
    public static boolean isEnhanced(ServerLevel level, BlockPos pos) {
        Key key = new Key(level.dimension(), pos.immutable());
        Long expiry = ENHANCED.get(key);
        if (expiry == null) return false;

        if (level.getGameTime() > expiry) {
            ENHANCED.remove(key);
            return false;
        }
        return true;
    }

    private static void maybePrune(long now) {
        if (now < nextPrune) return;
        nextPrune = now + PRUNE_INTERVAL;

        Iterator<Map.Entry<Key, Long>> it = ENHANCED.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue()) it.remove();
        }
    }
}
