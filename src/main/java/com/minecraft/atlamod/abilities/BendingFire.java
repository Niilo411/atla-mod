package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingFireBlock;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.fire.BlueFire;
import com.minecraft.atlamod.abilities.fire.TallerFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers which fire blocks were placed by an ability, so bender's fire can
 * hurt more than ordinary fire — and by a different amount per ability.
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

    /** When the entry stops counting, and how hard its fire burns meanwhile. */
    private record Entry(long expiry, float multiplier) {}

    private static final Map<Key, Entry> ENHANCED = new HashMap<>();

    /** Prune no more than once per this many ticks, so casts don't walk the whole map. */
    private static final long PRUNE_INTERVAL = 100L;

    private static long nextPrune = 0L;

    private BendingFire() {
    }

    /**
     * Flags a fire block as ability-made for {@code lifetimeTicks}, burning at
     * {@code multiplier} times normal fire damage.
     */
    public static void mark(ServerLevel level, BlockPos pos, int lifetimeTicks, float multiplier) {
        long now = level.getGameTime();
        maybePrune(now);
        ENHANCED.put(new Key(level.dimension(), pos.immutable()),
                new Entry(now + lifetimeTicks, multiplier));
    }

    /**
     * How hard the fire at this position burns. Returns 1.0 for ordinary fire, or
     * for ability fire whose entry has expired.
     */
    public static float getMultiplier(ServerLevel level, BlockPos pos) {
        Key key = new Key(level.dimension(), pos.immutable());
        Entry entry = ENHANCED.get(key);
        if (entry == null) return 1.0F;

        if (level.getGameTime() > entry.expiry()) {
            ENHANCED.remove(key);
            return 1.0F;
        }
        return entry.multiplier();
    }

    private static void maybePrune(long now) {
        if (now < nextPrune) return;
        nextPrune = now + PRUNE_INTERVAL;

        Iterator<Map.Entry<Key, Entry>> it = ENHANCED.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue().expiry()) it.remove();
        }
    }

    /**
     * Lays one fire block at the first air space with solid ground beneath, scanning
     * a little up and down so it follows terrain. Only ever replaces air, so an
     * ability that calls this can never destroy anything.
     *
     * Shared by Firewall, Fire Ring and Fire Spikes — they had a copy each, and
     * Taller Fire needs all of them to change together.
     *
     * @param multiplier how hard the fire burns; 1.0 means ordinary fire, untracked
     * @return true if fire was placed
     */
    public static boolean placeGrounded(ServerLevel level, BendingData data, BlockPos target,
                                        int upScan, int downScan, int lifetimeTicks, float multiplier) {
        for (int dy = upScan; dy >= -downScan; dy--) {
            BlockPos pos = target.above(dy);

            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;

            level.setBlockAndUpdate(pos, baseFireState(data));
            if (multiplier > 1.0F) {
                mark(level, pos, lifetimeTicks, multiplier);
            }

            level.sendParticles(flame(data),
                    pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    6, 0.2, 0.2, 0.2, 0.01);

            placeTallHalf(level, data, pos, lifetimeTicks, multiplier);
            return true;
        }
        return false;
    }

    /**
     * The second block of Taller Fire, when the passive is equipped.
     *
     * It has to be BendingFireBlock rather than vanilla fire: FireBlock#canSurvive
     * wants a face-sturdy block below or a flammable neighbour, and a fire block is
     * neither, so a stacked vanilla fire would delete itself almost immediately.
     */
    private static void placeTallHalf(ServerLevel level, BendingData data, BlockPos base,
                                      int lifetimeTicks, float multiplier) {
        if (data == null || !data.hasPassiveEquipped(TallerFire.KEY)) return;

        BlockPos above = base.above();
        if (!level.getBlockState(above).isAir()) return;

        level.setBlockAndUpdate(above, BendingFireBlock.stateFor(isBlue(data), true));
        if (multiplier > 1.0F) {
            mark(level, above, lifetimeTicks, multiplier);
        }
    }

    /**
     * The flame particle to use for this player — blue while Blue Fire is equipped.
     * Abilities call this instead of naming ParticleTypes.FLAME directly, so the
     * passive recolours all of them from one place.
     */
    public static SimpleParticleType flame(BendingData data) {
        return isBlue(data) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
    }

    /** Whether this player's fire burns blue. */
    public static boolean isBlue(BendingData data) {
        return data != null && data.hasPassiveEquipped(BlueFire.KEY);
    }

    /**
     * The block to lay for the base of a fire.
     *
     * Ordinary fire stays vanilla, so it keeps spreading the way the abilities
     * were built around. Blue fire has to be our own block, because vanilla soul
     * fire only survives on soul sand.
     */
    private static BlockState baseFireState(BendingData data) {
        return isBlue(data)
                ? BendingFireBlock.stateFor(true, false)
                : Blocks.FIRE.defaultBlockState();
    }
}
