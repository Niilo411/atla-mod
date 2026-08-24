package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.BendingFire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Defensive / Fire. Rings the player with fire at a distance, to break off a
 * fight rather than to win one. The fire it lays burns hotter than normal —
 * see BendingFire.
 */
public class FireRing implements Ability {

    /** Ring radius in blocks. */
    private static final double RADIUS = 4.0;

    /**
     * Samples taken around the circle. The circumference at radius 4 is about 25
     * blocks, so 40 samples oversamples it — neighbouring samples land on the same
     * block and are deduplicated, which is cheaper than leaving gaps in the ring.
     */
    private static final int SAMPLES = 40;

    /** How far up and down to look for ground, so the ring follows terrain. */
    private static final int UP_SCAN = 1;
    private static final int DOWN_SCAN = 3;

    /** How long this fire keeps its damage bonus, in ticks (30 seconds). */
    private static final int ENHANCED_LIFETIME = 600;

    /** How much harder this ring burns than ordinary fire. */
    private static final float DAMAGE_MULTIPLIER = 3.0F;

    @Override
    public String getName() {
        return "Fire Ring";
    }

    @Override
    public int getChiCost() {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 8;
    }

    @Override
    public int getCooldownTicks() {
        return 40; // 2 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        double cx = player.getX();
        double cz = player.getZ();
        BlockPos centre = player.blockPosition();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 0.7F);

        // Oversampled circle, deduplicated: several samples map to the same block.
        Set<BlockPos> ringColumns = new LinkedHashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            double angle = (Math.PI * 2.0 * i) / SAMPLES;
            int x = (int) Math.floor(cx + Math.cos(angle) * RADIUS);
            int z = (int) Math.floor(cz + Math.sin(angle) * RADIUS);
            ringColumns.add(new BlockPos(x, centre.getY(), z));
        }

        for (BlockPos column : ringColumns) {
            BendingFire.placeGrounded(level, data, column,
                    UP_SCAN, DOWN_SCAN, ENHANCED_LIFETIME, DAMAGE_MULTIPLIER);
        }
    }
}
