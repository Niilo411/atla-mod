package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Masterclass / Gravity. Drives whoever the bender is looking at straight down,
 * destroying the ground out from under them over five seconds, with a hit of bonus
 * damage every second on top of the descent itself.
 *
 * The crush itself is {@link GravityCrushes}' doing — a manager rather than
 * anything held on the caster, since the victim being crushed is very often not
 * the caster.
 */
public class GravityCrush implements Ability {

    /** INVENTED: reach for picking the target, the masterclass path's figure. */
    private static final double REACH = Gravity.MASTERCLASS_REACH;

    @Override
    public String getName() {
        return "Gravity Crush";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
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

        GravityCrushes.start(player, target);

        Gravity.gather(level, target.position(), 25, 0.6);
        Gravity.warp(level, target.position(), 1.1F);
    }
}
