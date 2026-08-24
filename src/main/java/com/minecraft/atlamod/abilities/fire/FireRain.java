package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.BendingFire;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Masterclass / Fire. The sky opens and burns everything below it for fifteen
 * seconds, out to fifty blocks.
 *
 * Cast once and then left running, so it is neither a channel nor a charge: it
 * follows Fire Leap in keeping a transient countdown on BendingData and doing its
 * per-tick work from a static tick() the server loop calls.
 *
 * It does NOT spare the bender. Standing in your own downpour hurts exactly as
 * much as being caught in someone else's — Fire immunity is the answer to that.
 */
public class FireRain implements Ability {

    /** 15 seconds of rain. */
    public static final int DURATION = 300;

    /** How far the downpour reaches, measured horizontally. */
    private static final double RADIUS = 50.0;

    /** How far above and below the caster the rain still catches things. */
    private static final double VERTICAL_REACH = 40.0;

    /** 1 heart a second to everything underneath. */
    private static final float DAMAGE_PER_SECOND = 2.0F;

    /** How high above the caster the flames appear before falling. */
    private static final double SKY_HEIGHT = 28.0;

    /** Directed falling flames per tick. Each one is its own packet, so keep it lean. */
    private static final int FALLING_PER_TICK = 8;

    @Override
    public String getName() {
        return "Fire Rain";
    }

    @Override
    public int getChiCost() {
        return 1000;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    /** No stacking a second downpour on top of one already falling. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getFireRainTicks() <= 0;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        data.setFireRainTicks(DURATION);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.6F);
    }

    /**
     * Called every tick from ServerEvents while the rain is falling. Ends itself
     * when the countdown runs out.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        int left = data.getFireRainTicks() - 1;
        data.setFireRainTicks(left);

        if (!(player.level() instanceof ServerLevel level)) return;

        drawRain(level, data, player);

        // Damage lands once a second rather than every tick, so "1 heart a second"
        // is what it actually deals instead of what it averages.
        if (left % 20 == 0) {
            scorchEverything(player, level);
        }

        if (left <= 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.WEATHER, 2.0F, 0.7F);
        }
    }

    /** The visible downpour: a haze of embers overhead, plus flames actually falling. */
    private static void drawRain(ServerLevel level, BendingData data, ServerPlayer player) {
        RandomSource random = level.getRandom();

        double cx = player.getX();
        double cy = player.getY() + SKY_HEIGHT;
        double cz = player.getZ();

        // One cheap batched call for the general glow up there. Batched particles get
        // random velocities rather than a chosen one, so this layer hangs and fades
        // instead of falling — it is the sky being alight, not the rain itself.
        level.sendParticles(BendingFire.flame(data), cx, cy, cz,
                40, RADIUS * 0.5, 4.0, RADIUS * 0.5, 0.0);

        // The falling part. A directed velocity needs count 0, which costs one packet
        // each, so only a handful go out per tick and the downpour builds up over the
        // fifteen seconds rather than arriving all at once.
        for (int i = 0; i < FALLING_PER_TICK; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * RADIUS;

            double x = cx + Math.cos(angle) * distance;
            double z = cz + Math.sin(angle) * distance;
            double y = cy - random.nextDouble() * 6.0;

            level.sendParticles(BendingFire.flame(data), x, y, z, 0, 0.0, -1.0, 0.0, 0.6);
        }

        // A little smoke drifting under the front, for weight.
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy - 6.0, cz,
                10, RADIUS * 0.4, 2.0, RADIUS * 0.4, 0.01);
    }

    /** Burns everything caught under the rain, the caster included. */
    private static void scorchEverything(ServerPlayer player, ServerLevel level) {
        AABB area = new AABB(
                player.getX() - RADIUS, player.getY() - VERTICAL_REACH, player.getZ() - RADIUS,
                player.getX() + RADIUS, player.getY() + VERTICAL_REACH, player.getZ() + RADIUS);

        for (Entity target : level.getEntities(player, area)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!withinRadius(player, living)) continue;

            living.hurt(player.damageSources().inFire(), DAMAGE_PER_SECOND);
            living.setRemainingFireTicks(60);
        }

        // getEntities always excludes the caster, so the bender is burned separately.
        // Being caught in your own downpour is the point of the ability having a cost.
        player.hurt(player.damageSources().inFire(), DAMAGE_PER_SECOND);
    }

    /** Cylindrical, not spherical: the rain falls from above, so height barely matters. */
    private static boolean withinRadius(ServerPlayer player, LivingEntity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        return (dx * dx + dz * dz) <= (RADIUS * RADIUS);
    }
}
