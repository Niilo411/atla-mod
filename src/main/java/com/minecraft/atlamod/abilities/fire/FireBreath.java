package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Offensive / Fire. Held cone of flame in front of the player. */
public class FireBreath implements ChanneledAbility {

    private static final int RANGE = 6;

    @Override
    public String getName() {
        return "Fire Breath";
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
    public int getChiPerSecond() {
        return 25;
    }

    @Override
    public int getXpPerSecond() {
        return 2;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        for (int i = 1; i <= RANGE; i++) {
            Vec3 pos = start.add(look.scale(i));

            serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 4, 0.3, 0.3, 0.3, 0.03);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1, 0.2, 0.2, 0.2, 0.01);

            AABB hitbox = new AABB(pos.x - 0.75, pos.y - 0.75, pos.z - 0.75,
                    pos.x + 0.75, pos.y + 0.75, pos.z + 0.75);

            for (Entity target : serverLevel.getEntities(player, hitbox)) {
                if (target instanceof LivingEntity livingTarget) {
                    // Light per-tick damage since it's continuous.
                    livingTarget.hurt(player.damageSources().inFire(), 1.0F);
                    livingTarget.setRemainingFireTicks(60);
                }
            }
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to wind down.
    }
}
