package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingFire;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Defensive / Fire. A held shell of flame that cancels all incoming damage for
 * as long as the key is down. No cooldown and no duration cap — chi is the only
 * thing that limits it.
 *
 * The invulnerability itself is applied in ServerEvents, which cancels incoming
 * damage whenever the player is channeling an ability that grants it.
 */
public class FireShield implements ChanneledAbility {

    /** How many flames make up each ring. */
    private static final int RING_POINTS = 10;

    /** Ring radius in blocks. */
    private static final double RADIUS = 1.0;

    /** Ticks for the rings to complete one full rotation. */
    private static final double SPIN_PERIOD = 40.0;

    @Override
    public String getName() {
        return "Fire Shield";
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

    /** 50 chi every 2 seconds. */
    @Override
    public int getChiPerSecond() {
        return 25;
    }

    /**
     * Needs a solid reserve before it will come up — 200 chi, none of which is
     * spent on starting. Below that the shield simply refuses.
     */
    @Override
    public int getMinimumChiToStart() {
        return 200;
    }

    @Override
    public int getXpPerSecond() {
        return 1;
    }

    @Override
    public boolean grantsInvulnerability() {
        return true;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        double cx = player.getX();
        double cy = player.getY();
        double cz = player.getZ();

        // Two rotating rings, at foot and head height, so the shield reads as a
        // shell around the player rather than a puddle at their feet.
        double spin = (player.tickCount % SPIN_PERIOD) / SPIN_PERIOD * Math.PI * 2.0;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / RING_POINTS);
            double px = cx + Math.cos(angle) * RADIUS;
            double pz = cz + Math.sin(angle) * RADIUS;

            level.sendParticles(BendingFire.flame(data), px, cy + 0.15, pz, 1, 0.0, 0.02, 0.0, 0.01);
            level.sendParticles(BendingFire.flame(data), px, cy + 1.15, pz, 1, 0.0, 0.02, 0.0, 0.01);
        }

        // Ambient flicker filling the space between the rings.
        level.sendParticles(BendingFire.flame(data), cx, cy + 1.0, cz, 4, 0.55, 0.7, 0.55, 0.01);
        level.sendParticles(ParticleTypes.LAVA, cx, cy + 0.8, cz, 1, 0.4, 0.5, 0.4, 0.0);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
