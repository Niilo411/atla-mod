package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.air.AirJump;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Gravity. Throws whoever is in front of the bender eight blocks up and
 * hurls them away along the line from the bender to them, letting the landing do
 * the damage rather than dealing a number of its own — which is what the design's
 * "dealing fall damage" means read literally.
 *
 * The launch height is SOLVED, not guessed, the way Air jump and Bass Bounce already
 * do it: drag means height is not proportional to launch speed, so the speed that
 * reaches eight blocks has to be worked out rather than picked by eye.
 *
 * The horizontal throw is measured from the CASTER to the target rather than from
 * the caster's look angle, so it always reads as "away from you" even when the
 * camera has drifted off the target between the aim and the cast.
 */
public class GravityThrow implements Ability {

    /** How far away a target may be picked from. INVENTED, matching Gravity pull's. */
    private static final double REACH = Gravity.OFFENSIVE_REACH;

    private static final double LAUNCH_HEIGHT = 8.0;

    /**
     * Horizontal speed, in blocks per tick, that throws the target away from the
     * caster. Large deliberately — this is a hard shove clear of the bender, not the
     * gentle arc Gravity pull's opposite number would be.
     */
    private static final double HORIZONTAL_SPEED = 1.4;

    @Override
    public String getName() {
        return "Gravity Throw";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 60; // 3 seconds
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE);
        if (target == null) return;

        double launch = AirJump.speedForHeight(LAUNCH_HEIGHT);

        Vec3 away = target.position().subtract(player.position());
        Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
        if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
        if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(0.0, 0.0, 1.0);
        horizontal = horizontal.normalize().scale(HORIZONTAL_SPEED);

        target.setDeltaMovement(horizontal.x, launch, horizontal.z);
        target.hurtMarked = true;

        // The throw's own doing, not a fall the target had already banked — vanilla's
        // own fall-damage math is what actually hurts them, on the way down.
        target.fallDistance = 0.0F;

        Gravity.burst(level, target.position(), 15, 0.5);
        Gravity.snap(level, target.position(), 1.0F);
    }
}
