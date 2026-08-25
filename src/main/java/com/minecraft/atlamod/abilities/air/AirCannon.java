package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Air. Four seconds of packing air down into one shot, then a single
 * blast of it hard enough to take seven hearts off whatever it lands on.
 *
 * The opposite trade to Air splinters, which sits beside it on the same path: that
 * one is six quick cuts, this is one blow that ends most things outright. Four
 * seconds of standing still to build it, and nothing to show for it if you miss.
 *
 * Same shape as Fireball and Water ball — the charge builds it, the left click fires
 * it — so the aim is taken after the wind-up rather than during it.
 */
public class AirCannon implements ChargedAbility, TwoPhaseAbility {

    /** Four seconds to pack it down. */
    private static final int CHARGE_TICKS = 80;

    /** 7 hearts. */
    private static final float DAMAGE = 14.0F;

    /** Heavier and slower than the splinters, but still quick off the mark. */
    private static final double SPEED = 2.5;

    /** At this speed it carries about 60 blocks before it comes apart. */
    private static final int LIFETIME = 24;

    /** Wide: this is a blast, not a sliver, and it should not need pinpoint aim. */
    private static final double HIT_RADIUS = 1.2;

    /** It throws. A cannon that left the target standing where it was would be odd. */
    private static final double KNOCKBACK = 1.0;

    /** The shot, as the shared projectile system wants it. No lingering effect. */
    private static final BendingProjectiles.Spec SHOT = new BendingProjectiles.Spec(
            SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK, BendingProjectiles.Style.AIR);

    @Override
    public String getName() {
        return "Air cannon";
    }

    @Override
    public int getChiCost() {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 200; // 10 seconds, from the shot rather than from the charge
    }

    @Override
    public int getChargeTicks() {
        return CHARGE_TICKS;
    }

    /** Nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /**
     * Air dragged in from all around and crushed into a ball in front of the bender.
     *
     * The gather sweeps INWARD over the four seconds — wide and loose at the start,
     * tight and dense by the end — so the length of the wind-up is legible without
     * having to watch the charge bar.
     */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) CHARGE_TICKS;

        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(1.0));

        // Incoming air, closing on the muzzle as the charge builds.
        double gather = 2.5 - (2.0 * power);
        level.sendParticles(ParticleTypes.CLOUD,
                muzzle.x, muzzle.y, muzzle.z, 6, gather, gather, gather, 0.02);

        // The ball itself, tightening and thickening.
        int density = 2 + (int) (8 * power);
        double tight = 0.5 - (0.35 * power);
        level.sendParticles(ParticleTypes.CLOUD,
                muzzle.x, muzzle.y, muzzle.z, density, tight, tight, tight, 0.0);

        // A rising note each second, so four seconds of holding still has a shape.
        if (ticksHeld % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS,
                    0.6F, 0.6F + (0.9F * power));
        }
    }

    /** The charge only finishes the ball; the click is what fires it. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.2F, 0.7F);
    }

    /** The packed ball of air, held at the muzzle and waiting on the click. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(1.0));

        level.sendParticles(ParticleTypes.CLOUD,
                muzzle.x, muzzle.y, muzzle.z, 6, 0.18, 0.18, 0.18, 0.0);

        if (player.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SMALL_GUST,
                    muzzle.x, muzzle.y, muzzle.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(1.0));

        BendingProjectiles.launch(player, from, look, SHOT);

        if (player.level() instanceof ServerLevel level) {
            // Backwash at the muzzle — the air that didn't go with the shot.
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL,
                    from.x, from.y, from.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.5F, 0.6F);
    }
}
