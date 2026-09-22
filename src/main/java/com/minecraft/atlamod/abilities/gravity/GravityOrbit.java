package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Defensive / Gravity. Ten seconds of gathering, then everything within six blocks
 * is pulled into orbit around the bender for fifteen seconds — locked as a
 * passenger the way Earth Trap locks its victims, so they cannot walk or fight back,
 * while the bender is free to hit anything on the ring at will.
 *
 * A plain charged ability rather than a two-phase one: the design gives it a wind-up
 * and nothing to aim afterwards, so it simply fires once the hold completes, the way
 * Fireball's siblings that DON'T also arm a throw behave.
 */
public class GravityOrbit implements ChargedAbility {

    private static final double CAPTURE_RADIUS = 6.0;

    /** INVENTED: how far out the ring itself sits — closer than the capture radius,
     *  so the bender can reach anything on it without stepping forward. */
    private static final double ORBIT_RADIUS = 3.0;

    private static final int ORBIT_TICKS = 300; // 15 seconds

    @Override
    public String getName() {
        return "Gravity Orbit";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 2000; // 100 seconds
    }

    @Override
    public int getChargeTicks() {
        return 200; // 10 seconds
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        double progress = ticksHeld / (double) getChargeTicks();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (4 + 10 * progress), CAPTURE_RADIUS * (1.0 - progress), 1.0,
                CAPTURE_RADIUS * (1.0 - progress), 0.02);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        AABB search = new AABB(player.position(), player.position()).inflate(CAPTURE_RADIUS);

        double angle = 0.0;
        int caught = 0;
        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(player.position()) > CAPTURE_RADIUS * CAPTURE_RADIUS) continue;

            caught++;
        }

        double step = caught > 0 ? (Math.PI * 2.0) / caught : 0.0;

        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(player.position()) > CAPTURE_RADIUS * CAPTURE_RADIUS) continue;

            GravityOrbits.capture(player, living, ORBIT_RADIUS, angle, ORBIT_TICKS);
            angle += step;
        }

        Gravity.burst(level, player.position().add(0.0, 1.0, 0.0), 30, ORBIT_RADIUS);
        Gravity.warp(level, player.position(), 1.2F);
    }
}
