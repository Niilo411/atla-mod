package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
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

/**
 * Defensive / Water. Four masses of water wheeling around the bender, turning aside
 * everything that comes in. Holds them in place while it runs.
 *
 * The same shape and cost as Fire Shield — 25 chi/sec, 1 xp/sec, no cooldown, no
 * duration cap, 200 chi to start — and it gets its invulnerability from the same
 * one-method override, which is what that contract was built for.
 *
 * The four "blocks" are dense clusters of water particles rather than real water
 * blocks. Actual water placed and moved every tick would flood everything around
 * the player: source blocks flow, and they would keep flowing after the shield
 * moved on. The slowing effect is applied directly to whoever gets close, so the
 * gameplay half does not depend on how the water is drawn.
 */
public class WaterShield implements ChanneledAbility {

    /** How many masses of water orbit the bender. */
    private static final int ORBS = 4;

    /** How far out they ride. */
    private static final double ORBIT_RADIUS = 1.4;

    /** Ticks for a full revolution. */
    private static final double SPIN_PERIOD = 30.0;

    /** Particles making up each mass. Enough to read as water, not a sparkle. */
    private static final int ORB_DENSITY = 6;

    /** How close something has to get before the water shoves at it. */
    private static final double SLOW_RANGE = 2.2;

    /** Slowness II, refreshed while they stay in it. */
    private static final int SLOW_DURATION = 40;
    private static final int SLOW_LEVEL = 1;

    @Override
    public String getName() {
        return "Water shield";
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

    /** 50 chi every 2 seconds, the same as Fire Shield. */
    @Override
    public int getChiPerSecond(BendingData data) {
        return 25;
    }

    @Override
    public double getXpPerSecond() {
        return 1;
    }

    /**
     * Needs a solid reserve before it will come up — 200 chi, none of which is
     * spent on starting.
     */
    @Override
    public int getMinimumChiToStart(BendingData data) {
        return 200;
    }

    @Override
    public boolean grantsInvulnerability() {
        return true;
    }

    @Override
    public boolean rootsPlayer(BendingData data) {
        return true;
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        drawShield(level, player);
        slowIntruders(player, level);
    }

    /** Four wheeling masses of water, plus a scatter of spray between them. */
    private static void drawShield(ServerLevel level, ServerPlayer player) {
        double cx = player.getX();
        double cy = player.getY();
        double cz = player.getZ();

        double spin = (player.tickCount % SPIN_PERIOD) / SPIN_PERIOD * Math.PI * 2.0;

        for (int i = 0; i < ORBS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / ORBS);
            double ox = cx + Math.cos(angle) * ORBIT_RADIUS;
            double oz = cz + Math.sin(angle) * ORBIT_RADIUS;

            // Each mass is a tight clump at two heights, so it reads as a body of
            // water riding around the player rather than as a trail of droplets.
            level.sendParticles(ParticleTypes.SPLASH, ox, cy + 0.5, oz,
                    ORB_DENSITY, 0.18, 0.18, 0.18, 0.01);
            level.sendParticles(ParticleTypes.FALLING_WATER, ox, cy + 1.2, oz,
                    ORB_DENSITY, 0.18, 0.25, 0.18, 0.0);
        }

        // Spray filling the ring between them.
        level.sendParticles(ParticleTypes.BUBBLE, cx, cy + 0.9, cz,
                6, ORBIT_RADIUS * 0.8, 0.5, ORBIT_RADIUS * 0.8, 0.01);
    }

    /** Anything that walks into the water gets pushed about for its trouble. */
    private static void slowIntruders(ServerPlayer player, ServerLevel level) {
        AABB reach = player.getBoundingBox().inflate(SLOW_RANGE);

        for (Entity target : level.getEntities(player, reach)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            living.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_LEVEL, false, true, true));
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.7F, 1.2F);
    }
}
