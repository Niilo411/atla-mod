package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Defensive / Water. A wall of water shoved outward in a forward arc: everything
 * caught is thrown back and left floundering.
 *
 * Built on the same geometry as Fire Push — one search box filtered to a cone, and
 * the shove applied after any other handling so nothing overwrites it — but this
 * one does no damage at all. It is control: it moves things away and slows them
 * down, and that is the whole of it.
 */
public class WaterPush implements Ability {

    /** How far in front of the player the push reaches, in blocks. */
    private static final double RANGE = 8.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the
     * direction to a target. 0.5 is a 60-degree half angle — a forward arc rather
     * than a laser, so a shove doesn't require pixel-perfect aim.
     */
    private static final double CONE_DOT = 0.5;

    /**
     * Horizontal launch velocity, tuned so targets travel about 6 blocks. Minecraft
     * knockback is an impulse decaying under drag, not a distance, so this cannot be
     * set exactly — distance scales linearly with it.
     */
    private static final double PUSH_SPEED = 0.84;

    /** A little lift so targets slide back instead of being pinned by ground friction. */
    private static final double PUSH_LIFT = 0.2;

    /** One second of Slowness I on everything caught. */
    private static final int SLOW_DURATION = 20;
    private static final int SLOW_LEVEL = 0;

    @Override
    public String getName() {
        return "Water push";
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
        return 40; // 2 seconds, matching Fire Push
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.3F, 0.7F);

        // A fan of spray showing the arc that was pushed.
        for (int i = 1; i <= RANGE; i++) {
            Vec3 pos = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.SPLASH, pos.x, pos.y, pos.z,
                    14, 0.35 * i, 0.35 * i, 0.35 * i, 0.02);
            level.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z,
                    4, 0.25 * i, 0.25 * i, 0.25 * i, 0.01);
        }

        // A box of +-RANGE around the eye. Any point within RANGE is inside it on
        // every axis, so nothing in the cone can fall outside the search — a tighter
        // box centred down the look vector misses targets at the edge of the arc.
        AABB searchBox = new AABB(eye, eye).inflate(RANGE);

        for (Entity target : level.getEntities(player, searchBox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().subtract(eye);
            if (toTarget.lengthSqr() > RANGE * RANGE) continue;

            // Skip anything behind or off to the side of where the player is facing.
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;

            living.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_LEVEL, false, true, true));

            // Direction is away from the player rather than along the look vector, so
            // targets off to one side are shoved outward rather than sideways.
            Vec3 away = new Vec3(living.getX() - player.getX(), 0.0, living.getZ() - player.getZ());
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(look.x, 0.0, look.z); // target standing inside the player
            }
            away = away.normalize();

            living.setDeltaMovement(away.x * PUSH_SPEED, PUSH_LIFT, away.z * PUSH_SPEED);
            // Without this a pushed player's client ignores the server's velocity change.
            living.hurtMarked = true;
        }
    }
}
