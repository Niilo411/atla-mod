package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Soundbending's shared parts.
 *
 * The mod's THIRD sub-element, built to the same shape as lightning and ice: two
 * paths, not chosen at the start, unlocked with a scroll — see
 * {@link com.minecraft.atlamod.SoundScrollItem}.
 *
 * Most of this class is the Sound boosting passive, because that passive is unusual:
 * it does not change one ability, it changes FOUR things about two whole elements.
 * Putting all four in one place is what stops it becoming a rule nobody can find.
 */
public final class Sound {

    /** How much harder a boosted ability hits, and how much longer its effects last. */
    public static final float BOOST = 1.25F;

    /** How much of a boosted ability's cooldown and charge time is left. */
    public static final float BOOST_SHORTENED = 0.75F;

    private Sound() {
    }

    /** Whether the bender has Sound boosting in a passive slot. */
    public static boolean hasBoost(BendingData data) {
        return data.hasPassiveEquipped(SoundBoosting.KEY);
    }

    /**
     * An air or sound ability's damage, 25% higher with Sound boosting.
     *
     * Applied BY the abilities rather than in the incoming-damage handler, for the
     * same reason Lightning Strength's bonus is: air and sound damage through
     * indirectMagic, which every projectile in the mod also uses, so keying off it
     * centrally would quietly buff water and earth too.
     */
    public static float damage(BendingData data, float base) {
        return hasBoost(data) ? base * BOOST : base;
    }

    /** How long an air or sound ability's effect lasts, 25% longer with Sound boosting. */
    public static int duration(BendingData data, int ticks) {
        return hasBoost(data) ? Math.round(ticks * BOOST) : ticks;
    }

    /**
     * A cooldown or charge time, 25% SHORTER with Sound boosting.
     *
     * Unlike the two above, this one is applied by the DISPATCHER rather than by each
     * ability — see AbilityHandler. Cooldowns and charge times are read in a handful of
     * places the abilities never touch, and asking every air and sound ability to
     * remember to shorten its own would be a rule broken the first time one was added.
     */
    public static int shorten(BendingData data, int ticks) {
        return hasBoost(data) ? Math.max(1, Math.round(ticks * BOOST_SHORTENED)) : ticks;
    }

    // ----------------------------------------------------------------- look

    /** A ring of sound spreading outward, the element's common visual. */
    public static void ring(ServerLevel level, Vec3 centre, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2.0 / count) * i;
            Vec3 at = centre.add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius);

            level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** A burst of sound in place, for something striking or bursting. */
    public static void burst(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, count, spread, spread, spread, 0.4);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Softer: a body of compressed air travelling, rather than a note struck. */
    public static void wave(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
        level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z,
                Math.max(1, count / 4), spread, spread, spread, 0.1);
    }

    public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** The deep note the low abilities make. */
    public static void boom(ServerLevel level, Vec3 at, float volume) {
        play(level, at, SoundEvents.WARDEN_SONIC_BOOM, volume, 0.6F);
    }

    /** The high shriek the loud abilities make. */
    public static void shriek(ServerLevel level, Vec3 at, float volume) {
        play(level, at, SoundEvents.WARDEN_SONIC_CHARGE, volume, 1.8F);
    }
}
