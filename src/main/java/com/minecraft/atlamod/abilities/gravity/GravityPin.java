package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Offensive / Gravity. Piles the weight of the world onto whoever is in front of
 * the bender, slowing them almost to a standstill for five seconds.
 *
 * "Slowness 50" is the design's own figure, taken literally — amplifier 49, deep
 * enough into vanilla's slowness formula that walking away simply is not an option.
 */
public class GravityPin implements Ability {

    /** INVENTED: reach, matching Gravity pull's on the same path. */
    private static final double REACH = Gravity.OFFENSIVE_REACH;

    /** Slowness LEVEL 50, taken literally from the design. */
    private static final int SLOWNESS_AMPLIFIER = 49;

    private static final int DURATION = 100; // 5 seconds

    @Override
    public String getName() {
        return "Gravity Pin";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 400; // 20 seconds
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

        target.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, DURATION, SLOWNESS_AMPLIFIER, false, true, true));

        Gravity.hum(level, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 30, 0.5);
        Gravity.warp(level, target.position(), 0.9F);
    }
}
