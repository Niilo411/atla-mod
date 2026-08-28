package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Ice. Five sharp shards gathered on the crosshair and loosed ALL AT ONCE on
 * a left click.
 *
 * The distinction from Air splinters and Earth Splinters matters, and it is the whole
 * character of the ability: those hold six shots and spend one per click, where this
 * holds one click that spends five shards. A burst, not a magazine — which is why
 * {@link #getShots()} is left at 1 and the loop lives inside {@link #onRelease}.
 *
 * Two hearts a shard is not much on its own; all five landing is ten, and the spread
 * is tight enough that a target at close range takes most of them.
 */
public class Icicles implements TwoPhaseAbility {

    /** How many shards go out per click. */
    private static final int COUNT = 5;

    /** 2 hp each, as specced — one heart a shard. */
    private static final float DAMAGE = 2.0F;

    /**
     * How far off the crosshair the shards scatter.
     *
     * Small on purpose: a burst that sprayed would make the ability a coin flip at
     * any range, where a tight cone means all five land on a close target and only
     * some of them on a distant one. That falloff is the range limit, rather than a
     * hard cutoff.
     */
    private static final double SPREAD = 0.09;

    private static final BendingProjectiles.Spec SHARD = new BendingProjectiles.Spec(
            2.4, 40, DAMAGE, 0.6, 0.15, BendingProjectiles.Style.ICE);

    @Override
    public String getName() {
        return "icicles";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    /** A second between volleys, so five shards at a time is a burst and not a stream. */
    @Override
    public int getCooldownTicks() {
        return 20; // 1 second
    }

    /** Held until thrown, like Fireball and Water ball. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /**
     * Nothing else already held — including another handful of these.
     *
     * Without it, pressing the key with shards already gathered simply gathered them
     * again: another 100 chi spent, the armed slot reset, and nothing at all to show
     * for it, since the first handful was never thrown. The armed state waits
     * indefinitely, so there is no case where re-summoning is the thing the bender
     * wanted. Every other two-phase ability in the mod already guards this way; this
     * one was simply missing it.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    /** The gathered shards, turning slowly around the bender's hand. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 hand = player.getEyePosition()
                .add(player.getLookAngle().scale(0.9))
                .subtract(0.0, 0.25, 0.0);

        double phase = player.tickCount * 0.25;
        for (int i = 0; i < COUNT; i++) {
            double a = phase + (i * Math.PI * 2.0 / COUNT);
            Vec3 at = hand.add(Math.cos(a) * 0.4, Math.sin(a * 2.0) * 0.12, Math.sin(a) * 0.4);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
        Vec3 look = player.getLookAngle();

        BendingProjectiles.Spec shard = new BendingProjectiles.Spec(
                SHARD.speed(), SHARD.lifetime(), Ice.damage(data, DAMAGE),
                SHARD.hitRadius(), SHARD.knockback(), SHARD.style());

        for (int i = 0; i < COUNT; i++) {
            // Scattered around the aim line rather than fanned in a flat row, so the
            // burst reads as a handful thrown rather than a volley fired.
            Vec3 direction = look.add(
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0,
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0,
                    (level.random.nextDouble() - 0.5) * SPREAD * 2.0);

            BendingProjectiles.launch(player, from, direction, shard);
        }

        Ice.shatter(level, from, 15, 0.25);
        Ice.crack(level, player.position(), 0.8F, 1.6F);
    }

    /** Arming is the whole cast: the shards gather and wait on the click. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Ice.form((ServerLevel) player.level(), player.position(), 0.7F, 1.4F);
    }
}
