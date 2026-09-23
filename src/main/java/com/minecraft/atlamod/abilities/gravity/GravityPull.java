package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Gravity. Hauls whoever is in front of the bender in to arm's reach,
 * damaging them on the way.
 *
 * The cheapest ability on the path, and still among the quickest to come back at four
 * seconds — which is the point: this is the opener that puts a
 * distant target where the rest of the path can reach them, not a finisher of its
 * own. The design's own words for that are "allowing you to start a combo"; there is
 * no combo system behind it, just a fast, cheap pull meant to be followed by
 * something else.
 */
public class GravityPull implements Ability {

    /** How far away a target may be picked from. The design's own figure. */
    private static final double REACH = 10.0;

    private static final float DAMAGE = 4.0F;

    @Override
    public String getName() {
        return "Gravity pull";
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
        return 80; // 4 seconds
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

        target.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);

        // AFTER the hit, never before: hurt() applies a knockback of its own, which would
        // otherwise knock the target back out of the first tick of the pull. The pull
        // itself is steered every tick until the target is set down right in front of the
        // bender — see GravityPulls for why one shove could not do that.
        GravityPulls.start(player, target);

        Gravity.gather(level, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 20, 0.4);
        Gravity.warp(level, target.position(), 0.7F);
    }
}
