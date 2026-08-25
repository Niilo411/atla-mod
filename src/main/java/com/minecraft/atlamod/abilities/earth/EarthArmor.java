package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;

/**
 * Defensive / Earth. Draws the ground up over the bender as a suit of stone: ten
 * points of armor for two minutes.
 *
 * The ten points are ADDED to whatever is already being worn rather than replacing
 * it, which falls out of the effect carrying an ADD_VALUE modifier on the armor
 * attribute — a bender in full iron ends up in iron plus ten. The stone LOOK is drawn
 * over any real armor for the same reason: it is a layer on top, not a swap.
 *
 * Everything about the duration is vanilla's: this is a MobEffect, so the countdown,
 * the removal, the inventory timer and the cleanup on death are all handled without a
 * line of bookkeeping here. See ModEffects.EARTH_ARMOR and, for the look,
 * client/EarthArmorLayer.
 */
public class EarthArmor implements Ability {

    /** Two minutes. */
    private static final int DURATION = 2400;

    @Override
    public String getName() {
        return "Earth armor";
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
        return 3000; // 150 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.addEffect(new MobEffectInstance(
                ModEffects.EARTH_ARMOR, DURATION, 0, false, true, true));

        if (player.level() instanceof ServerLevel level) {
            // The ground coming up over them.
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    40, 0.5, 0.9, 0.5, 0.1);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
        }
    }
}
