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
 * Defensive / Air. Water push and Fire push in reverse: a forward cone that drags
 * everything in it TOWARDS the bender, and leaves it disoriented — controls
 * reversed — for five seconds afterwards.
 *
 * Pulling something in is a strange thing for a defensive ability to do, so the
 * Disorientation is the point rather than a garnish: whatever arrives cannot walk
 * straight for long enough that the bender chooses what happens next. There is no
 * damage at all, like Water push.
 *
 * Geometry is the same as those two — one search box filtered down to a cone —
 * because a pull that demanded pixel-perfect aim would be miserable to land on
 * something that is already running at you.
 */
public class AirPull implements Ability {

    /** How far in front of the bender the pull reaches, in blocks. */
    private static final double RANGE = 12.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the
     * direction to a target. 0.5 is a 60-degree half angle, matching the two pushes.
     */
    private static final double CONE_DOT = 0.5;

    /**
     * Pull speed scales with distance, unlike the pushes' flat impulse. Knockback in
     * Minecraft is an impulse decaying under drag, so a single flat value that brings
     * a target 3 blocks away in nicely would leave one at 12 blocks barely stirring.
     * Speed is BASE + PER_BLOCK * distance, capped so nothing arrives at a lethal clip.
     */
    private static final double PULL_BASE = 0.35;
    private static final double PULL_PER_BLOCK = 0.07;
    private static final double PULL_MAX = 1.1;

    /** A little lift, so targets are dragged over the ground rather than into it. */
    private static final double PULL_LIFT = 0.25;

    /** Five seconds of reversed controls, as asked. */
    private static final int DISORIENT_DURATION = 100;

    /**
     * How close a target has to be before the pull stops applying. Without this, the
     * bender yanks anything already standing on top of them straight through
     * themselves and out the other side.
     */
    private static final double MIN_PULL_DISTANCE = 1.5;

    @Override
    public String getName() {
        return "Air pull";
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
        return 40; // 2 seconds, matching the other two push/pull abilities
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.8F);

        // Gusts drawn from far to near, so the arc reads as air rushing back INWARDS
        // rather than being blown out. The spread narrows as it approaches the bender
        // for the same reason: it funnels.
        for (int i = (int) RANGE; i >= 1; i--) {
            Vec3 pos = eye.add(look.scale(i));
            double spread = 0.25 * i;
            level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z,
                    10, spread, spread, spread, 0.01);
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
                    ModEffects.DISORIENTATION, DISORIENT_DURATION, 0, false, true, true));

            level.sendParticles(ParticleTypes.POOF,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    8, 0.3, 0.4, 0.3, 0.02);

            if (distance < MIN_PULL_DISTANCE) continue; // already here; just disorient it

            // Toward the bender's feet rather than their eyes, so tall targets aren't
            // dragged downwards into the floor.
            Vec3 toward = player.position().subtract(living.position());
            double speed = Math.min(PULL_BASE + PULL_PER_BLOCK * distance, PULL_MAX);
            toward = new Vec3(toward.x, 0.0, toward.z).normalize();

            living.setDeltaMovement(toward.x * speed, PULL_LIFT, toward.z * speed);
            // Without this a pulled player's client ignores the server's velocity change.
            living.hurtMarked = true;
        }
    }
}
