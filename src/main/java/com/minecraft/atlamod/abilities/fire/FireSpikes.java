package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingFire;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Balanced / Fire. Two seconds of wind-up, then fire erupts at random points all
 * around the player out to 15 blocks. Its fire burns at twice normal.
 */
public class FireSpikes implements ChargedAbility {

    /** Furthest a spike can land from the player, in blocks. */
    private static final double RADIUS = 15.0;

    /** How many spikes are attempted. Some land in air or water and simply don't take. */
    private static final int SPIKE_ATTEMPTS = 25;

    /** How far up and down to look for ground at each spot. */
    private static final int UP_SCAN = 2;
    private static final int DOWN_SCAN = 4;

    /** Fire Spikes burns at twice normal, like Ignite. */
    private static final float DAMAGE_MULTIPLIER = 2.0F;

    /** How long that bonus lasts, in ticks (30 seconds). */
    private static final int ENHANCED_LIFETIME = 600;

    @Override
    public String getName() {
        return "Fire Spikes";
    }

    @Override
    public int getChiCost() {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getChargeTicks() {
        return 40; // 2 seconds of hold
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Heat gathering at the player's feet, thickening as the charge fills.
        int count = 2 + (6 * ticksHeld / getChargeTicks());
        level.sendParticles(BendingFire.flame(data),
                player.getX(), player.getY() + 0.2, player.getZ(),
                count, 0.6, 0.1, 0.6, 0.02);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.6F);

        BlockPos centre = player.blockPosition();

        for (int i = 0; i < SPIKE_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;

            // sqrt keeps the scatter even across the area. Using the raw roll would
            // bunch most of the spikes near the player, since a disc has far more
            // room out at its edge than in its middle.
            double distance = Math.sqrt(random.nextDouble()) * RADIUS;

            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);

            BendingFire.placeGrounded(level, data, new BlockPos(x, centre.getY(), z),
                    UP_SCAN, DOWN_SCAN, ENHANCED_LIFETIME, DAMAGE_MULTIPLIER);
        }
    }
}
