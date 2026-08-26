package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.BendingFire;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Offensive / Fire. Launches the player forward on a trail of fire. */
public class FireLeap implements Ability {

    @Override
    public String getName() {
        return "Fire Leap";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        // Already mid-leap: don't let them re-cast (and don't charge them for it).
        return !data.isFireLeaping();
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        data.setFireLeaping(true);

        player.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE, 60, 0, false, false, false));

        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.9, 0.75, look.z * 1.9);
        player.hurtMarked = true;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Called every tick from ServerEvents while the player is mid-leap.
     * Fire Leap ends itself on landing, so it isn't a ChanneledAbility — but its
     * tick logic still belongs to this class rather than to the server tick loop.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(BendingFire.flame(data),
                    player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.25, 0.2, 0.25, 0.05);
            serverLevel.sendParticles(ParticleTypes.LAVA,
                    player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.1, 0.1, 0.1, 0.01);

            // While still rising, scorch the ground under the player.
            if (player.getDeltaMovement().y > 0) {
                BlockPos ground = player.blockPosition();
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos checkPos = ground.above(dy);
                    if (serverLevel.getBlockState(checkPos).isAir()
                            && serverLevel.getBlockState(checkPos.below()).isSolid()) {
                        serverLevel.setBlockAndUpdate(checkPos, Blocks.FIRE.defaultBlockState());
                        break;
                    }
                }
            }
        }

        if (player.onGround() || player.isInWater()) {
            data.setFireLeaping(false);
            player.setData(ModAttachments.BENDING_DATA, data);
        }
    }
}
