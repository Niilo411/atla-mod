package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Icebending's shared parts.
 *
 * The mod's SECOND sub-element, built to the same shape as lightning: two paths
 * rather than four, not chosen at the start, and unlocked with a scroll — see
 * {@link com.minecraft.atlamod.IceScrollItem}.
 *
 * Everything here is what more than one ice ability needs: the chill that several of
 * them apply, and the look and sound the whole element shares.
 */
public final class Ice {

    /**
     * How long the chill is held on, in ticks, each time it is applied.
     *
     * Vanilla decays a frozen entity's counter every tick once it is out of powder
     * snow, so this is topped up rather than set once — an ability that chills has to
     * keep chilling for the effect to persist.
     */
    private static final int CHILL_TICKS = 200;

    private Ice() {
    }

    /**
     * Puts the freeze on something: vanilla's own frost, plus Slowness.
     *
     * Vanilla's freezing counter is used rather than a custom effect because it is
     * already exactly this — the blue vignette, the shivering, and the visible frost
     * on the model, all synced and drawn for free. What it does NOT reliably do is
     * slow the victim down outside powder snow, so the Slowness is applied alongside
     * rather than relied upon.
     *
     * Note the frost is set ABOVE the threshold at which vanilla considers something
     * fully frozen, so it reads as frozen immediately instead of frosting up slowly.
     */
    public static void chill(LivingEntity target, int slownessTicks, int slownessLevel) {
        target.setTicksFrozen(Math.max(target.getTicksFrozen(),
                target.getTicksRequiredToFreeze() + CHILL_TICKS));

        // Topped up rather than re-applied every tick: each addEffect is a packet, and
        // an ability that chills a whole area twenty times a second is pure noise on
        // the wire. Slowness is not counter-driven, so topping it up is safe — unlike
        // Regeneration, which breaks outright if refreshed. See Water heal.
        MobEffectInstance slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slowness == null || slowness.getAmplifier() < slownessLevel
                || slowness.getDuration() < slownessTicks / 2) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    slownessTicks, slownessLevel, false, true, true));
        }
    }

    /** Clears the frost from something the moment an ability stops holding it on. */
    public static void thaw(Entity target) {
        target.setTicksFrozen(0);
    }

    /** A puff of frost, the common visual for the whole element. */
    public static void frost(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z,
                count, spread, spread, spread, 0.02);
    }

    /** Heavier frost, for something breaking or bursting. */
    public static void shatter(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z,
                count, spread, spread, spread, 0.12);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z,
                count / 2, spread, spread, spread, 0.15);
    }

    /** The crack of ice forming or breaking. */
    public static void crack(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, volume, pitch);
    }

    /** The groan of a sheet of ice going down. */
    public static void form(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * An ice ability's damage.
     *
     * Ice has no equivalent of Lightning Strength, so this is a pass-through for now.
     * It exists so every ice ability already routes its damage through one place: if a
     * bonus is ever added, it goes here and nothing else has to change.
     */
    public static float damage(BendingData data, float base) {
        return base;
    }
}
