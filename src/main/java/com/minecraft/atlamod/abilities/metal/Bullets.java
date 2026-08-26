package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.AbilityHandler;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds of gathering summons twenty small slugs of iron, then
 * they are fired ONE PER LEFT CLICK.
 *
 * Air splinters' shape at four times the count: the charge summons, and the armed slot
 * spends them a click at a time. That makes it a magazine rather than a burst — the
 * opposite of Icicles beside it in icebending, which holds one click that spends five
 * shards.
 *
 * Twenty shots is a long time to be committed to one ability, so pressing the key
 * again PUTS THEM DOWN. That goes through isActive/deactivate, which the dispatcher
 * checks at the very top of startCharge, before the cooldown gate and before anything
 * is spent — nobody should pay chi to stop doing something.
 *
 * Two hp a slug is almost nothing alone; twenty of them landing is forty, and the two
 * second cooldown means a fresh magazine is never far away. The 200 chi is the real
 * limit.
 */
public class Bullets implements ChargedAbility, TwoPhaseAbility {

    /** How many a full charge summons. */
    private static final int COUNT = 20;

    /** 2 hp each, as specced. */
    private static final float DAMAGE = 2.0F;

    /**
     * Really fast, as specced: quicker than anything else the mod throws.
     *
     * They PIERCE invulnerability frames, which matters at this rate of fire — vanilla
     * ignores a second hit of equal size within ten ticks, so without it a bender
     * clicking quickly would have most of the magazine silently discarded.
     */
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

    /** Two seconds, and it waits for the LAST of the twenty. */
    @Override
    public int getCooldownTicks() {
        return 40;
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    /** Twenty clicks before the magazine is spent. */
    @Override
    public int getShots() {
        return COUNT;
    }

    /** Held until fired: the slugs wait as long as the bender likes. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /**
     * Already summoned: the next press puts them down rather than summoning again.
     *
     * Checked before the cooldown and before any chi is spent, so cancelling is always
     * possible and always free. The chi already paid for the magazine is NOT refunded —
     * that was spent summoning them, and it happened.
     */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return getKey().equals(data.getActiveTwoPhaseAbility());
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        AbilityHandler.clearArmedTwoPhase(player, data);

        // The cooldown is stamped on the way out, exactly as it would be on the last
        // shot. Without it, cancelling and re-summoning would be a way round the two
        // seconds rather than a way out of the ability.
        data.setCooldown(getKey(), getCooldownTicks());

        Metal.scrape((ServerLevel) player.level(), player.position(), 0.6F, 1.5F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // The particles the design describes, drawing in and hardening as they fill.
        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.9));

        Metal.spark(level, hand, 2 + (int) (progress * 8), 0.7 - (progress * 0.55));
    }

    /**
     * The magazine, turning around the bender's hand.
     *
     * Drawn from how many are LEFT rather than from the full twenty, so the ring
     * visibly thins out as they are spent and a bender can see what they have without
     * reading the meter.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 hand = player.getEyePosition()
                .add(player.getLookAngle().scale(0.9))
                .subtract(0.0, 0.25, 0.0);

        int left = data.getTwoPhaseShots();
        double phase = player.tickCount * 0.2;

        // Every third one, or twenty particles a tick around one hand is a solid ball.
        for (int i = 0; i < left; i += 3) {
            double a = phase + (i * Math.PI * 2.0 / Math.max(1, left));
            Vec3 at = hand.add(Math.cos(a) * 0.45, Math.sin(a * 2.0) * 0.15, Math.sin(a) * 0.45);

            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** One slug per click, straight down the crosshair. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));

        BendingProjectiles.launch(player, from, player.getLookAngle(),
                new BendingProjectiles.Spec(
                        SLUG.speed(), SLUG.lifetime(), Metal.damage(data, DAMAGE),
                        SLUG.hitRadius(), SLUG.knockback(), SLUG.style(),
                        SLUG.onHit(), SLUG.piercesInvulnerability(), SLUG.onImpact()));

        Metal.spark(level, from, 6, 0.15);
        Metal.clang(level, player.position(), 0.5F, 1.9F);
    }

    /** Arming is the whole cast: the slugs are summoned and wait on the clicks. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Metal.scrape((ServerLevel) player.level(), player.position(), 0.9F, 1.2F);
    }
}
