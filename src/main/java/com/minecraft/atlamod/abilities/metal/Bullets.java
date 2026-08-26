package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds of gathering, then twenty small slugs of iron go out
 * at once, very fast.
 *
 * Two hp each is almost nothing on its own; twenty of them landing is forty, and the
 * spread is tight enough that a close target takes most of the volley while a distant
 * one takes a scatter. That falloff IS the range limit rather than a hard cutoff —
 * the same shape Icicles uses, at four times the count.
 *
 * They PIERCE invulnerability frames, which is not optional at this count: vanilla
 * ignores a second hit of equal size within ten ticks, so without it nineteen of the
 * twenty would be silently discarded and the ability would deal 2.
 */
public class Bullets implements ChargedAbility {

    /** How many go out per cast. */
    private static final int COUNT = 20;

    /** 2 hp each, as specced. */
    private static final float DAMAGE = 2.0F;

    /** How far off the aim line they scatter. Tight — this is a volley, not a shotgun. */
    private static final double SPREAD = 0.07;

    /** Really fast, as specced: quicker than anything else the mod throws. */
    private static final BendingProjectiles.Spec SLUG = new BendingProjectiles.Spec(
            3.6, 30, DAMAGE, 0.5, 0.05, BendingProjectiles.Style.STONE, null, true);

    @Override
    public String getName() {
        return "Bullets";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 200;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // The particles the design describes, drawing in and hardening as they fill.
        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.9));

        Metal.spark(level, hand, 2 + (int) (progress * 8), 0.7 - (progress * 0.55));
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
        Vec3 look = player.getLookAngle();

        BendingProjectiles.Spec slug = new BendingProjectiles.Spec(
                SLUG.speed(), SLUG.lifetime(), Metal.damage(data, DAMAGE),
                SLUG.hitRadius(), SLUG.knockback(), SLUG.style(),
                SLUG.onHit(), SLUG.piercesInvulnerability(), SLUG.onImpact());

        for (int i = 0; i < COUNT; i++) {
            Vec3 direction = look.add(
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0,
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0,
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0);

            BendingProjectiles.launch(player, from, direction, slug);
        }

        Metal.spark(level, from, 25, 0.3);
        Metal.clang(level, player.position(), 1.0F, 1.8F);
    }
}
