package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Metalbending's shared parts.
 *
 * The mod's FOURTH sub-element, and the second to come out of earth's side of the
 * tree. Same shape as the other three: two paths, not chosen at the start, unlocked
 * with a scroll -- see {@link com.minecraft.atlamod.MetalScrollItem}.
 */
public final class Metal {

    private Metal() {
    }

    /**
     * A metal ability's damage.
     *
     * Metal has no equivalent of Lightning Strength or Sound boosting, so this is a
     * pass-through. It exists so every metal ability already routes its damage through
     * one place: if a bonus is ever added it goes here and nothing else changes.
     */
    public static float damage(BendingData data, float base) {
        return base;
    }

    /** Sparks off struck metal, the element's common visual. */
    public static void spark(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, count, spread, spread, spread, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                Math.max(1, count / 3), spread, spread, spread, 0.05);
    }

    /** The ring of a heavy plate landing. */
    public static void clang(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, volume, pitch);
    }

    /** The scrape of metal being drawn or bent. */
    public static void scrape(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, volume, pitch);
    }
}
