package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.air.AirJump;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Offensive / Gravity. Lifts whoever the bender is looking at, and everything else
 * within eight blocks of them, eight blocks into the sky — then slams the whole
 * group back down together.
 *
 * The slam itself is {@link GravitySlams}' doing, run from a manager rather than a
 * countdown on BendingData: a target other than the caster has no BendingData of its
 * own to hang a timer on, and the blast usually catches more than one victim at once.
 */
public class GravitySlam implements Ability {

    /** How far away the primary target may be picked from. The design's own figure. */
    private static final double REACH = 10.0;

    /** How far the blast reaches around the primary target. The design's own figure. */
    private static final double RADIUS = 8.0;

    private static final double LIFT_HEIGHT = 8.0;

    @Override
    public String getName() {
        return "Gravity Slam";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 100; // 5 seconds
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity primary = Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE);
        if (primary == null) return;

        double launch = AirJump.speedForHeight(LIFT_HEIGHT);
        AABB blast = new AABB(primary.position(), primary.position()).inflate(RADIUS);

        for (Entity caught : level.getEntities(player, blast)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(primary.position()) > RADIUS * RADIUS) continue;

            GravitySlams.lift(living, player, launch);
            Gravity.gather(level, living.position(), 15, 0.4);
        }

        Gravity.warp(level, primary.position(), 1.0F);
    }
}
