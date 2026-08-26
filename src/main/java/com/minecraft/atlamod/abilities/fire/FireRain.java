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
 * Masterclass / Fire. The sky opens and burns everything below it for thirty
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

    /** 30 seconds of rain. */
    public static final int DURATION = 600;

    /** How far the downpour reaches, measured horizontally. */
    private static final double RADIUS = 50.0;

    /** How far above and below the caster the rain still catches things. */
    private static final double VERTICAL_REACH = 40.0;

    /** Half a heart a second to everything underneath. */
    private static final float DAMAGE_PER_SECOND = 1.0F;

    /** How high above the caster the flames appear before falling. */
    private static final double SKY_HEIGHT = 28.0;

    /**
     * Batched layers filling the air column, cheapest way to get real density: one
     * packet buys a whole layer, where a directed particle costs a packet each.
     */
    private static final int LAYERS = 5;
    private static final int PER_LAYER = 70;

    /** How far down the layers reach from SKY_HEIGHT. */
    private static final double LAYER_DROP = 26.0;

    /** Directed falling flames per tick. One packet each, so this is the expensive half. */
    private static final int FALLING_PER_TICK = 22;

    @Override
    public String getName() {
        return "Fire Rain";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 1000;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 1200; // 60 seconds
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

        // Damage lands once a second rather than every tick, so "half a heart a second"
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

        // Stacked layers of batched flame filling the air column. Batched particles
        // take random velocities rather than a chosen one, so they hang and flicker
        // instead of falling — but one packet buys seventy of them, which is the only
        // affordable way to make a fifty block downpour actually look like one.
        for (int layer = 0; layer < LAYERS; layer++) {
            double layerY = cy - (LAYER_DROP * layer / (double) (LAYERS - 1));

            // Tighter near the ground, so density builds where the player is looking
            // rather than being thrown away out at the rim.
            double spread = RADIUS * (0.5 - 0.15 * (layer / (double) (LAYERS - 1)));

            level.sendParticles(BendingFire.flame(data), cx, layerY, cz,
                    PER_LAYER, spread, 3.0, spread, 0.02);
        }

        // The falling part. A chosen velocity needs count 0, which costs a packet
        // each, so these are rationed — they are the streaks that read as rain, on
        // top of the volume the layers provide.
        for (int i = 0; i < FALLING_PER_TICK; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;

            // Deliberately NOT sqrt here. An even spread over the area puts most of
            // the flames out at the rim where they are far away and barely visible;
            // the raw roll biases them inward, towards the person watching.
            double distance = random.nextDouble() * RADIUS;

            double x = cx + Math.cos(angle) * distance;
            double z = cz + Math.sin(angle) * distance;
            double y = cy - random.nextDouble() * 10.0;

            level.sendParticles(BendingFire.flame(data), x, y, z, 0, 0.0, -1.0, 0.0, 0.7);
        }

        // Smoke drifting under the front, for weight.
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy - 10.0, cz,
                20, RADIUS * 0.35, 3.0, RADIUS * 0.35, 0.01);
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
