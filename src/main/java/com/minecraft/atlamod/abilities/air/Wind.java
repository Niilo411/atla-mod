package com.minecraft.atlamod.abilities.air;

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
 * Defensive / Air. A single enormous gust thrown out across everything the bender
 * can see: 20 blocks of it, one heart of damage each and twenty seconds of Slowness.
 *
 * It is the widest thing in the air tree and hits players as well as mobs, so the
 * limits are the 30 second cooldown and a chi cost that keeps it from opening every
 * fight. The damage is barely a scratch — the twenty seconds of Slowness on
 * everything at once is the actual weapon.
 */
public class Wind implements Ability {

    /** How far the gust carries, in blocks. */
    private static final double RANGE = 20.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the
     * direction to a target — "on your screen", near enough. Minecraft's default
     * 70-degree vertical FOV works out around 106 degrees across on a widescreen
     * monitor, so the true half angle is about 53 degrees (dot 0.6). This is
     * deliberately wider at roughly 66: something at the very edge of the screen
     * should be caught rather than feeling like it was unfairly missed, and the
     * player's FOV setting is a client preference the server cannot see anyway.
     */
    private static final double CONE_DOT = 0.4;

    /** One heart. */
    private static final float DAMAGE = 2.0F;

    /** Twenty seconds of Slowness I. */
    private static final int SLOW_DURATION = 400;
    private static final int SLOW_LEVEL = 0;

    @Override
    public String getName() {
        return "Wind";
    }

    @Override
    public int getChiCost() {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.6F, 0.7F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.4F, 0.6F);

        drawGust(level, eye, look);

        // A box of +-RANGE around the eye. Any point within RANGE is inside it on
        // every axis, so nothing in the cone can fall outside the search — a tighter
        // box centred down the look vector misses targets at the edge of the arc.
        AABB searchBox = new AABB(eye, eye).inflate(RANGE);

        for (Entity target : level.getEntities(player, searchBox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!isOnScreen(player, living, eye, look)) continue;

            living.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_LEVEL, false, true, true));

            // indirectMagic rather than a fire or projectile source: it bypasses
            // armour, so the one heart is actually one heart on a geared target, and
            // it keeps clear of the tags other abilities key off (Blue Fire's
            // doubling, Air Aura's projectile blocking).
            living.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);

            level.sendParticles(ParticleTypes.POOF,
                    living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    10, 0.35, 0.45, 0.35, 0.03);
        }
    }

    /**
     * Whether the bender can actually see this target: inside the view cone, within
     * range, and not through a wall.
     *
     * The line of sight check is what makes "on your screen" mean what it says —
     * without it the gust would go through terrain and hit things in the cave below.
     */
    private static boolean isOnScreen(ServerPlayer player, LivingEntity target, Vec3 eye, Vec3 look) {
        Vec3 toTarget = target.getEyePosition().subtract(eye);
        if (toTarget.lengthSqr() > RANGE * RANGE) return false;
        if (toTarget.normalize().dot(look) < CONE_DOT) return false;

        return player.hasLineOfSight(target);
    }

    /**
     * The visible blast: a fan of cloud widening away from the bender.
     *
     * Drawn with batched particle calls (a count above zero) rather than individually
     * directed ones. A directed particle needs count = 0, which is one packet each —
     * filling a 20 block cone that way would be hundreds of packets for a single cast.
     */
    private static void drawGust(ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 across = new Vec3(-look.z, 0.0, look.x).normalize();

        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                eye.x + look.x * 2.0, eye.y + look.y * 2.0, eye.z + look.z * 2.0,
                1, 0.0, 0.0, 0.0, 0.0);

        for (double distance = 3.0; distance <= RANGE; distance += 3.0) {
            Vec3 centre = eye.add(look.scale(distance));

            // The cone widens with distance, so the spray does too.
            double spread = distance * 0.45;

            level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z,
                    24, spread, spread * 0.6, spread, 0.05);

            // A pair of wings out to either side, so the fan reads as wide rather
            // than as a single beam down the middle.
            for (int side = -1; side <= 1; side += 2) {
                Vec3 wing = centre.add(across.scale(side * spread * 0.7));
                level.sendParticles(ParticleTypes.CLOUD, wing.x, wing.y, wing.z,
                        10, spread * 0.35, spread * 0.4, spread * 0.35, 0.04);
            }
        }
    }
}
