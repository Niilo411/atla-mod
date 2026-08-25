package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Air. A held funnel of roaring wind: everything in front of the bender
 * is shoved back for as long as the key is down, slowed, and worn away at a heart a
 * second.
 *
 * The damage is the least of it. Nothing caught in the tunnel can close the distance
 * while it is running, which is the whole point — it is an offensive ability that
 * wins by keeping things off you rather than by killing them quickly. Cheap to run
 * and with no cooldown, so the only limit is how long the chi lasts.
 */
public class WindTunnel implements ChanneledAbility {

    /** How far down the funnel reaches, in blocks. */
    private static final double RANGE = 12.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the
     * direction to a target. 0.6 is about 53 degrees — tighter than Wind's
     * screenful, because a tunnel is a directed column rather than a blast.
     */
    private static final double CONE_DOT = 0.6;

    /** One heart, once a second. */
    private static final float DAMAGE_PER_SECOND = 2.0F;

    /** Five seconds of Slowness I, topped back up as it runs down. */
    private static final int SLOW_DURATION = 100;
    private static final int SLOW_LEVEL = 0;

    /** Re-apply once the effect has a second's worth spent, not every tick. */
    private static final int SLOW_REFRESH_BELOW = 80;

    /**
     * How hard the wind blows, in blocks per tick, at the mouth of the funnel and at
     * the far end of it. Set rather than added: a force added every tick would
     * accelerate without limit, where a wind has a speed it pushes things at.
     */
    private static final double PUSH_NEAR = 0.65;
    private static final double PUSH_FAR = 0.15;

    /** A skim of lift, applied only on the ground, so things slide instead of grind. */
    private static final double GROUND_LIFT = 0.15;

    @Override
    public String getName() {
        return "Wind tunnel";
    }

    /** Paid per second while channeling, not up front. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Granted per second, not up front. */
    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 10;
    }

    @Override
    public double getXpPerSecond() {
        return 2;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        drawFunnel(level, eye, look);

        // The roar, kept to twice a second rather than every tick.
        if (data.getChannelTicks() % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.8F, 0.6F);
        }

        // Damage lands on a one-second beat rather than every tick, so "a heart a
        // second" is what it actually does. Leaning on vanilla's invulnerability
        // frames to space out per-tick hits would work by accident, not by design.
        boolean damageThisTick = data.getChannelTicks() % 20 == 0;

        // A box of +-RANGE around the eye. Any point within RANGE is inside it on
        // every axis, so nothing in the cone can fall outside the search — a tighter
        // box centred down the look vector misses targets at the edge of the arc.
        AABB searchBox = new AABB(eye, eye).inflate(RANGE);

        for (Entity target : level.getEntities(player, searchBox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().subtract(eye);
            double distance = toTarget.length();
            if (distance > RANGE) continue;
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;

            slow(living);
            shove(living, player, distance);

            if (damageThisTick) {
                living.hurt(player.damageSources().indirectMagic(player, player), DAMAGE_PER_SECOND);
            }
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    /**
     * Tops the Slowness back up rather than re-applying it every tick.
     *
     * Every addEffect sends an update packet to the client, so refreshing a whole
     * cone of targets twenty times a second would be pure noise on the wire — and for
     * effects driven by an internal counter it breaks them outright (see Water heal).
     */
    private static void slow(LivingEntity living) {
        MobEffectInstance current = living.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (current != null && current.getDuration() > SLOW_REFRESH_BELOW
                && current.getAmplifier() >= SLOW_LEVEL) {
            return;
        }

        living.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_LEVEL, false, true, true));
    }

    /** The wind itself: strongest at the mouth, trailing off down the funnel. */
    private static void shove(LivingEntity living, ServerPlayer player, double distance) {
        double falloff = Math.max(0.0, 1.0 - (distance / RANGE));
        double speed = PUSH_FAR + (PUSH_NEAR - PUSH_FAR) * falloff;

        // Away from the bender rather than along the look vector, so things off to
        // one side are blown outward instead of sideways across the funnel.
        Vec3 away = new Vec3(living.getX() - player.getX(), 0.0, living.getZ() - player.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            away = new Vec3(look.x, 0.0, look.z); // standing inside the bender
        }
        away = away.normalize();

        // Vertical motion is left alone, or setting it every tick would hold the
        // target hovering. On the ground it gets a skim of lift instead, so it slides
        // back rather than being ground into the floor.
        Vec3 motion = living.getDeltaMovement();
        double y = living.onGround() ? GROUND_LIFT : motion.y;

        living.setDeltaMovement(away.x * speed, y, away.z * speed);

        // Only players need telling: their client owns their movement and ignores
        // server-side velocity unless it is pushed to them. A mob is simulated on the
        // server, so marking it would send a motion packet every tick for nothing.
        if (living instanceof Player) {
            living.hurtMarked = true;
        }
    }

    /**
     * Rings of air widening away from the bender.
     *
     * Batched particle calls (count above zero) rather than individually directed
     * ones: a directed particle needs count = 0, which is one packet each, and this
     * runs every tick for as long as the key is held.
     */
    private static void drawFunnel(ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 across = new Vec3(-look.z, 0.0, look.x).normalize();
        Vec3 up = look.cross(across).normalize();

        for (double distance = 2.0; distance <= RANGE; distance += 2.0) {
            Vec3 centre = eye.add(look.scale(distance));

            // The funnel opens out as it goes.
            double radius = 0.4 + (distance * 0.22);

            for (int i = 0; i < 6; i++) {
                // Twisted a little further round at each distance, so the whole thing
                // reads as a spiral being drawn down the tunnel.
                double angle = (Math.PI * 2.0 * i / 6) + (distance * 0.6);
                Vec3 point = centre
                        .add(across.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));

                level.sendParticles(ParticleTypes.CLOUD,
                        point.x, point.y, point.z, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }

        level.sendParticles(ParticleTypes.SMALL_GUST,
                eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                2, 0.3, 0.3, 0.3, 0.0);
    }
}
