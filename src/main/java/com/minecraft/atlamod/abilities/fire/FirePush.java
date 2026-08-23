package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Defensive / Fire. A short forward shove: damages everything in a cone in front
 * of the player and knocks it roughly two blocks back.
 */
public class FirePush implements Ability {

    /** How far in front of the player the push reaches, in blocks. */
    private static final double RANGE = 4.0;

    /**
     * Cone width, as the minimum dot product between the player's look vector and
     * the direction to a target. 0.5 is a 60-degree half angle — a forward arc
     * rather than a laser, so a shove doesn't require pixel-perfect aim.
     */
    private static final double CONE_DOT = 0.5;

    /** 1.5 hearts. Minecraft health is half-hearts, so 3.0F. */
    private static final float DAMAGE = 3.0F;

    /**
     * Horizontal launch velocity, tuned so targets travel about 2 blocks.
     *
     * Minecraft knockback is an impulse, not a distance: the entity is given a
     * velocity and then decays under drag (0.91/tick airborne), so distance can't
     * be set exactly. With PUSH_LIFT below the target is airborne ~11 ticks, and
     * the resulting travel is roughly v0 * 7, hence ~0.28 for two blocks.
     * Raise this to push further.
     */
    private static final double PUSH_SPEED = 0.28;

    /** A little lift so targets slide back instead of being pinned by ground friction. */
    private static final double PUSH_LIFT = 0.2;

    @Override
    public String getName() {
        return "Fire Push";
    }

    @Override
    public int getChiCost() {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 40; // 2 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);

        // Flame fan showing the arc that was pushed.
        for (int i = 1; i <= RANGE; i++) {
            Vec3 pos = eye.add(look.scale(i));
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                    12, 0.35 * i, 0.35 * i, 0.35 * i, 0.02);
        }

        // One box covering the whole reach, then filtered to the forward cone.
        // Sweeping step-by-step like Fire Whip would hit the same entity at several
        // steps and push it repeatedly.
        Vec3 center = eye.add(look.scale(RANGE / 2.0));
        AABB searchBox = new AABB(center, center).inflate(RANGE / 2.0 + 1.0);

        for (Entity target : level.getEntities(player, searchBox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().subtract(eye);
            if (toTarget.lengthSqr() > RANGE * RANGE) continue;

            // Skip anything behind or off to the side of where the player is facing.
            Vec3 direction = toTarget.normalize();
            if (direction.dot(look) < CONE_DOT) continue;

            living.hurt(player.damageSources().inFire(), DAMAGE);

            // Push AFTER the damage, so hurt()'s own knockback handling doesn't
            // overwrite it. Direction is away from the player rather than along the
            // look vector, so targets off to one side are shoved outward sensibly.
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
