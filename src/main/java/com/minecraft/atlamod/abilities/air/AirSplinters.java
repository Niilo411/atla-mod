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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Air. Six slivers of compressed air gathered over two seconds, then
 * loosed one at a time at whatever the bender is looking at.
 *
 * Both held shapes at once, the way Fireball is: the charge is phase one and what it
 * produces is the armed slot, which the left click then fires. Where Fireball is
 * spent by its throw, this holds six shots, so the slot stays armed until they are
 * all gone and the cooldown waits for the last of them.
 *
 * They fly faster than anything else the mod throws and hit for less. The second of
 * Slowness on each one is what makes six of them add up: a target caught by the first
 * has a harder time getting out of the way of the rest.
 */
public class AirSplinters implements ChargedAbility, TwoPhaseAbility {

    /** Two seconds to gather them. */
    private static final int CHARGE_TICKS = 40;

    /** Loosed one at a time. */
    private static final int SHOTS = 6;

    /** One second of Slowness I on whatever it catches. */
    private static final int SLOW_DURATION = 20;
    private static final int SLOW_LEVEL = 0;

    /** 1.5 hearts each. */
    private static final float DAMAGE = 3.0F;

    /** Faster than the water bullets — these are slivers, not masses. */
    private static final double SPEED = 3.2;

    /** Short-lived, but at this speed it still carries about 45 blocks. */
    private static final int LIFETIME = 14;

    /** Tight, to suit something this small and this fast. */
    private static final double HIT_RADIUS = 0.7;

    /** Barely a shove: a splinter cuts, it does not throw. */
    private static final double KNOCKBACK = 0.2;

    /**
     * One splinter, as the shared projectile system wants it.
     *
     * The effect is a supplier rather than an instance because a MobEffectInstance
     * carries its own countdown once applied — one shared between six hits would be
     * six references to the same ticking object.
     */
    private static final BendingProjectiles.Spec SHOT = new BendingProjectiles.Spec(
            SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK,
            BendingProjectiles.Style.AIR,
            () -> new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION, SLOW_LEVEL, false, true, true));

    @Override
    public String getName() {
        return "Air splinters";
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
        return 200; // 10 seconds, starting from the last of the six
    }

    @Override
    public int getChargeTicks() {
        return CHARGE_TICKS;
    }

    /** Six clicks, not one. */
    @Override
    public int getShots() {
        return SHOTS;
    }

    /** Nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    /** Air being drawn in and packed down, tightening as the charge builds. */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) CHARGE_TICKS;

        // The gather closes IN rather than spreading out, so it reads as compression.
        double radius = 2.0 - (1.4 * power);
        double spin = (player.tickCount % 10) / 10.0 * Math.PI * 2.0;

        for (int i = 0; i < SHOTS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / SHOTS);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.CLOUD,
                    px, player.getY() + 1.2, pz, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** The charge only completes the gather; the splinters are thrown by the clicks. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /**
     * The splinters still in hand, circling the bender.
     *
     * Drawn from the live count rather than from SHOTS, so loosing one visibly leaves
     * five — the ring is the ammunition counter.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        int remaining = data.getTwoPhaseShots();
        if (remaining <= 0) return;

        double spin = (player.tickCount % 16) / 16.0 * Math.PI * 2.0;
        double radius = 0.9;

        for (int i = 0; i < remaining; i++) {
            double angle = spin + (Math.PI * 2.0 * i / remaining);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + 1.3;

            level.sendParticles(ParticleTypes.CLOUD, px, py, pz, 3, 0.08, 0.08, 0.08, 0.0);
        }
    }

    /** One click, one splinter. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.8));

        BendingProjectiles.launch(player, from, look, SHOT);

        // Pitch climbs as they run down, so the last splinter is audibly the last.
        float pitch = 1.2F + (0.15F * (SHOTS - data.getTwoPhaseShots()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.9F, pitch);
    }
}
