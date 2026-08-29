package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.water.Drownings;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Air. Reaches into something's lungs and takes the air out of them,
 * leaving it drowning where it stands.
 *
 * Drown's opposite number, and built on the same machinery: charged, firing on
 * release, and handing the victim to {@link Drownings} — which holds their air at
 * nothing and deals vanilla's one heart a second wherever they happen to be. The
 * suffocation is the same thing whether the air was replaced by water or simply
 * taken away, so there is one implementation of it rather than two.
 *
 * Where it differs is the trade. Drown takes five seconds of charge to reach its
 * ceiling; this reaches the same fifteen seconds in three, and costs less — but it
 * leaves the victim Disoriented on top, so an airbender's version is the faster and
 * nastier of the two.
 *
 * Like Drown, it ends early if the bender loses sight of the victim. The grip has to
 * be maintained, so breaking line of sight is the counter-play to both.
 */
public class Breathless implements ChargedAbility {

    /** Three seconds to reach full strength. */
    private static final int MAX_CHARGE = 60;

    /** A second is the shortest cast that counts; below it nothing is spent. */
    private static final int MIN_CHARGE = 20;

    /**
     * Every tick of charge buys five of suffocation — one second held for five
     * suffered, as asked. Kept as a straight multiplier rather than an interpolation
     * between two endpoints, because the rule itself is the simple one: a second in
     * is five seconds out, all the way up.
     */
    private static final int SUFFOCATION_PER_CHARGE_TICK = 5;

    /** Five seconds of reversed controls, matching Air pull and Airpush. */
    private static final int DISORIENT_DURATION = 100;

    /** How far away a victim can be picked out. */
    private static final double REACH = 20.0;

    /** How near the line of sight something has to be to count as the target. */
    private static final double AIM_TOLERANCE = 1.5;

    @Override
    public String getName() {
        return "breathless";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds, matching Drown
    }

    @Override
    public int getChargeTicks() {
        return MAX_CHARGE;
    }

    /** Letting go early still casts it, just for less. */
    @Override
    public boolean firesOnRelease() {
        return true;
    }

    @Override
    public int getMinimumChargeTicks() {
        return MIN_CHARGE;
    }

    /**
     * Needs something to smother, checked both when the charge starts and again when
     * it lands — the dispatcher runs this before spending anything, so a victim who
     * breaks line of sight during the wind-up costs the bender nothing.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (findVictim(player) != null) return true;

        player.displayClientMessage(Component.literal("§bNo target in sight!"), true);
        return false;
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = Math.min(1.0F, ticksHeld / (float) MAX_CHARGE);

        // Air being drawn back towards the bender's hand.
        Vec3 look = player.getLookAngle();
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX() + look.x, player.getEyeY(), player.getZ() + look.z,
                2 + (int) (6 * power), 0.3, 0.3, 0.3, 0.01);

        // And leaving the victim, so they can see it coming and run.
        LivingEntity victim = findVictim(player);
        if (victim != null && ticksHeld % 4 == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    victim.getX(), victim.getEyeY(), victim.getZ(),
                    3 + (int) (8 * power), 0.35, 0.35, 0.35, 0.02);
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    victim.getX(), victim.getEyeY(), victim.getZ(),
                    2, 0.25, 0.25, 0.25, 0.0);
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        LivingEntity victim = findVictim(player);
        if (victim == null) return;

        // How far the charge got, recorded by the dispatcher just before the cast.
        int charged = Math.min(MAX_CHARGE, data.getLastChargeTicks());
        int duration = charged * SUFFOCATION_PER_CHARGE_TICK;

        // Held only while the bender can still see them — see Drownings.
        Drownings.start(player, victim, duration);

        victim.addEffect(new MobEffectInstance(
                ModEffects.DISORIENTATION, DISORIENT_DURATION, 0, false, true, true));

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.GUST,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CLOUD,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 30, 0.4, 0.4, 0.4, 0.12);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 1.5F);
        }
    }

    /** The nearest living thing along the bender's line of sight. See Aiming. */
    private static LivingEntity findVictim(ServerPlayer player) {
        return Aiming.nearestAlongLook(player, REACH, AIM_TOLERANCE);
    }
}
