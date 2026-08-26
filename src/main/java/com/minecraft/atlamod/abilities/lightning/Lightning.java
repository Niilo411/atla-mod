package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;

/**
 * Lightningbending's shared parts.
 *
 * Lightning is the first SUB-element: it is not one of the four, it has only two
 * paths instead of four, and it is not chosen at the start — it is unlocked with the
 * scroll (see {@link com.minecraft.atlamod.LightningScrollItem}) by a firebender who
 * has already finished two fire paths.
 *
 * Everything here is the stuff more than one lightning ability needs: the damage
 * bonus the Lightning Strength passive grants, and the two ways of putting a bolt in
 * the world.
 */
public final class Lightning {

    /**
     * What Lightning Strength multiplies lightning damage by — "Does 50% more damage".
     *
     * Applied by the abilities through {@link #damage}, NOT by the incoming-damage
     * handler the way Blue Fire's bonus is. Blue Fire could be done there because
     * every fire ability shares the IS_FIRE tag, which is a usable signature for "a
     * fire ability did this". Lightning abilities damage through indirectMagic, which
     * Wind and every projectile in the mod also use — keying off it there would
     * quietly buff half the other elements too.
     */
    public static final float STRENGTH_MULTIPLIER = 1.5F;

    /**
     * The wind-up every lightningbending ability has to serve, in ticks.
     *
     * One second, and it is a MINIMUM rather than a fixed figure: Lightning bolt
     * already takes five to build and keeps them. Nothing in the element goes off the
     * instant the key is pressed — current has to be gathered first.
     *
     * The one exception is Lightning Strength, and it is not really an exception:
     * a passive is never cast at all, so there is no moment of use to put a wind-up
     * in front of.
     */
    public static final int MINIMUM_CHARGE_TICKS = 20;

    private Lightning() {
    }

    /**
     * The gather: current drawn into the hands and tightening as the wind-up fills.
     *
     * Shared by every lightning ability that winds up, so the whole element reads the
     * same way while charging and a player can tell at a glance that something is
     * being built rather than that the key did nothing.
     *
     * @param ticksHeld  how far the wind-up has got
     * @param totalTicks how long it runs for in full
     */
    public static void gather(ServerLevel level, net.minecraft.world.entity.Entity at,
                              int ticksHeld, int totalTicks) {
        double progress = Math.min(1.0, ticksHeld / (double) Math.max(1, totalTicks));

        Vec3 hands = at.getEyePosition()
                .add(at.getLookAngle().scale(0.8))
                .subtract(0.0, 0.3, 0.0);

        // Tightens as it fills: a wide scatter at the start pulling into a tight knot
        // by the end, so the wind-up is visibly going somewhere.
        spark(level, hands, 2 + (int) (progress * 6), 0.6 - (progress * 0.45));
    }

    /** Whether the bender has Lightning Strength in a passive slot. */
    public static boolean hasStrength(BendingData data) {
        return data.hasPassiveEquipped(LightningStrength.KEY);
    }

    /** A lightning ability's damage, with the Lightning Strength bonus if it is equipped. */
    public static float damage(BendingData data, float base) {
        return hasStrength(data) ? base * STRENGTH_MULTIPLIER : base;
    }

    /**
     * A bolt that is only for show: the flash and the thunder, but no damage, no
     * fire, and no turning pigs into zoglins.
     *
     * This is what almost every lightning ability wants. They deal their OWN damage,
     * in the amount their description promises, and a real bolt landing on top of
     * that would add five points nobody asked for — and would set the world alight
     * every time a bender used a movement ability.
     */
    public static void visualStrike(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;

        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /**
     * A real bolt, with everything vanilla lightning does — damage, fire, and the
     * mob conversions.
     *
     * Only Lightning bolt's "Storm Caller" upgrade uses this, where calling down
     * actual lightning IS the upgrade. Note it can start fires: that is vanilla
     * lightning behaving normally, and it is what was bought.
     */
    public static void realStrike(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;

        bolt.moveTo(at.x, at.y, at.z);
        level.addFreshEntity(bolt);
    }

    /** A crackle of sparks, the common visual for everything in the element. */
    public static void spark(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                count, spread, spread, spread, 0.08);
    }

    /** The snap a lightning ability makes. Quieter and sharper than real thunder. */
    public static void crack(ServerLevel level, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, volume, pitch);
    }
}
