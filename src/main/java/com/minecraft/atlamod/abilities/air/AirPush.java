package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Air. Air pull turned around: the same cone, the same five seconds of
 * Disorientation, but everything caught is thrown AWAY rather than dragged in — and
 * unlike the pull, this one hurts.
 *
 * The pair is deliberate. Air pull brings something to you and leaves it unable to
 * walk straight; Airpush does the opposite and takes two hearts on the way out. They
 * share their geometry exactly so that knowing one teaches you the other.
 */
public class AirPush implements Ability {

    /** How far in front of the bender the push reaches. Matched to Air pull. */
    private static final double RANGE = 12.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the
     * direction to a target. 0.5 is a 60-degree half angle, as every other push and
     * pull in the mod uses.
     */
    private static final double CONE_DOT = 0.5;

    /** Two hearts. */
    private static final float DAMAGE = 4.0F;

    /**
     * How hard the blast throws, at the bender's feet and at the far edge of the cone.
     *
     * The reverse of Air pull's scaling, and for the opposite reason: a pull has to
     * reach further to bring a distant target all the way in, where a gust of wind is
     * strongest where it leaves the hands and has spread itself thin by the time it
     * gets to the far end.
     */
    private static final double PUSH_NEAR = 1.05;
    private static final double PUSH_FAR = 0.45;

    /** A little lift, so targets are thrown clear instead of scraping along the floor. */
    private static final double PUSH_LIFT = 0.35;

    /** Five seconds of reversed controls, the same as Air pull. */
    private static final int DISORIENT_DURATION = 100;

    @Override
    public String getName() {
        return "Airpush";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 40; // 2 seconds, matching Air pull and the other pushes
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.4F, 0.8F);

        // A cone of air widening away from the bender — drawn outward, where the
        // pull's is drawn inward, so the two read as opposites at a glance.
        for (int i = 1; i <= RANGE; i++) {
            Vec3 pos = eye.add(look.scale(i));
            double spread = 0.25 * i;

            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
                    10, spread, spread, spread, 0.03);
            level.sendParticles(ParticleTypes.SMALL_GUST, pos.x, pos.y, pos.z,
                    2, spread * 0.6, spread * 0.6, spread * 0.6, 0.0);
        }

        // A box of +-RANGE around the eye. Any point within RANGE is inside it on
        // every axis, so nothing in the cone can fall outside the search — a tighter
        // box centred down the look vector misses targets at the edge of the arc.
        AABB searchBox = new AABB(eye, eye).inflate(RANGE);

        for (Entity target : level.getEntities(player, searchBox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().subtract(eye);
            double distance = toTarget.length();
            if (distance > RANGE) continue;

            // Skip anything behind or off to the side of where the bender is facing.
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;

            living.addEffect(new MobEffectInstance(
                    ModEffects.DISORIENTATION, com.minecraft.atlamod.abilities.sound.Sound.duration(data, DISORIENT_DURATION), 0, false, true, true));

            // indirectMagic rather than a fire or projectile source: it bypasses
            // armour, so two hearts is two hearts on a geared target, and it keeps
            // clear of the tags other abilities key off (Blue Fire's doubling, Air
            // Aura's projectile blocking).
            living.hurt(player.damageSources().indirectMagic(player, player), com.minecraft.atlamod.abilities.sound.Sound.damage(data, DAMAGE));

            shove(living, player, look, distance);

            level.sendParticles(ParticleTypes.POOF,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /**
     * Throws one target clear.
     *
     * Applied AFTER the damage, deliberately: hurt() applies its own knockback, and
     * setting the motion afterwards is what stops the ability's own shove being
     * quietly overwritten by a much smaller one.
     */
    private static void shove(LivingEntity living, ServerPlayer player, Vec3 look, double distance) {
        double falloff = Math.max(0.0, 1.0 - (distance / RANGE));
        double speed = PUSH_FAR + (PUSH_NEAR - PUSH_FAR) * falloff;

        // Away from the bender rather than along the look vector, so targets off to
        // one side are thrown outward rather than dragged sideways across the cone.
        Vec3 away = new Vec3(living.getX() - player.getX(), 0.0, living.getZ() - player.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(look.x, 0.0, look.z); // target standing inside the bender
        }
        away = away.normalize();

        living.setDeltaMovement(away.x * speed, PUSH_LIFT, away.z * speed);
        // Without this a pushed player's client ignores the server's velocity change.
        living.hurtMarked = true;
    }
}
