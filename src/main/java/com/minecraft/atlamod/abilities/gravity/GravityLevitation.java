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
 * Defensive / Gravity. Strips whoever is in front of the bender of their footing for
 * ten seconds — vanilla Levitation, the same effect the Gravitybending Scroll grants
 * its reader as the unlock's own confirmation.
 *
 * Named {@code GravityLevitation} rather than {@code Levitation} purely to avoid
 * colliding with {@link net.minecraft.world.effect.MobEffects#LEVITATION} in import
 * statements; the display name — what the tree, the registry key and the equip
 * screen all use — is still plain "Levitation".
 */
public class GravityLevitation implements Ability {

    /** INVENTED: reach, matching the offensive path's figure since the design gives none. */
    private static final double REACH = Gravity.DEFENSIVE_REACH;

    private static final int DURATION = 200; // 10 seconds

    @Override
    public String getName() {
        return "Levitation";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 500; // 25 seconds
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
                MobEffects.LEVITATION, DURATION, 0, false, true, true));

        Gravity.gather(level, target.position(), 20, 0.5);
        Gravity.warp(level, target.position(), 0.6F);
    }
}
