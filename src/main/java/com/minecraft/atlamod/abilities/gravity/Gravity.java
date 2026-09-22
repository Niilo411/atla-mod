package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Gravitybending's shared parts.
 *
 * The mod's EIGHTH sub-element and air's second, unlocked the way Combustion, Blood
 * and Lava are rather than the way Sound, Ice, Lightning and Metal are — see
 * {@link com.minecraft.atlamod.GravityScrollItem}. It is also the first sub-element
 * with a MASTERCLASS and no balanced path beneath it; see the note on
 * UpgradeMenuScreen#checkTreeLogic.
 *
 * Ranges the design doc left unspecified are collected here as named constants
 * rather than repeated as magic numbers in each ability, each flagged INVENTED at
 * the point it is used.
 */
public final class Gravity {

    /**
     * How far the offensive path's single-target abilities reach when picking who to
     * affect, in blocks. INVENTED — the design gives Gravity pull an explicit 10, so
     * its siblings on the same path (Pin, Throw, Slam) use the same figure for
     * consistency rather than each inventing their own.
     */
    public static final double OFFENSIVE_REACH = 10.0;

    /**
     * INVENTED: how far Levitation reaches picking its target, matching the
     * offensive path's figure since the design gives it none of its own.
     */
    public static final double DEFENSIVE_REACH = 10.0;

    /**
     * INVENTED: how far the masterclass path's aimed abilities (Push, Encase, Crush)
     * reach — a little further than the offensive path's, matching the pattern of
     * later paths reaching further (Ignite's 20, Combustion nuke's wind-up target).
     */
    public static final double MASTERCLASS_REACH = 15.0;

    /** Tolerance for Aiming#nearestAlongLook — how far off the look line still counts. */
    public static final double AIM_TOLERANCE = 2.0;

    private Gravity() {
    }

    // ----------------------------------------------------------------- look

    /** A pull of matter inward, for anything that gathers or drags something in. */
    public static void gather(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z,
                count, spread, spread, spread, 0.04);
    }

    /** A burst outward, for anything that releases or slams. */
    public static void burst(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z,
                count, spread, spread, spread, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** A quiet hum of distorted space, for something standing or held rather than moving. */
    public static void hum(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z,
                count, spread, spread, spread, 0.01);
    }

    public static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** The low warp of something being pulled or crushed. */
    public static void warp(ServerLevel level, Vec3 at, float volume) {
        play(level, at, SoundEvents.WARDEN_ROAR, volume, 0.4F);
    }

    /** The sharper snap of something released or thrown. */
    public static void snap(ServerLevel level, Vec3 at, float volume) {
        play(level, at, SoundEvents.ENDER_EYE_LAUNCH, volume, 0.7F);
    }
}
