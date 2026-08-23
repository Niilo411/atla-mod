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

import java.util.List;

/** Offensive / Fire. Instant 15-block lash of flame along the player's look vector. */
public class FireWhip implements Ability {

    private static final int RANGE = 15;

    @Override
    public String getName() {
        return "Fire Whip";
    }

    @Override
    public int getChiCost() {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel serverLevel = (ServerLevel) player.level();
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5F, 1.5F);

        boolean hitSomething = false;
        for (int i = 1; i <= RANGE; i++) {
            Vec3 pos = start.add(look.scale(i));

            serverLevel.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 5, 0.2, 0.2, 0.2, 0.05);

            AABB hitbox = new AABB(pos.x - 0.5, pos.y - 0.5, pos.z - 0.5,
                    pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);

            for (Entity target : serverLevel.getEntities(player, hitbox)) {
                if (target instanceof LivingEntity livingTarget) {
                    livingTarget.hurt(player.damageSources().inFire(), 6.0F);
                    livingTarget.setRemainingFireTicks(100);
                    hitSomething = true;
                }
            }

            // Stop the whip at the first thing it connects with.
            if (hitSomething) break;
        }
    }
}
