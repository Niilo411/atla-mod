package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Air. Three small tornadoes wound up over three seconds, then set down
 * wherever the bender is looking — one per click — where each stands for a full
 * minute throwing anything that walks into it ten blocks up.
 *
 * The same charge-then-click shape as Air splinters, and the same three-shot slot,
 * but what the clicks produce is not a projectile: it is a piece of the battlefield
 * that stays put — three of them, each standing for a minute.
 *
 * The spouts themselves live in {@link AirSpouts}; this class only decides where.
 */
public class AirSpout implements ChargedAbility, TwoPhaseAbility {

    /** Three seconds to wind all three up. */
    private static final int CHARGE_TICKS = 60;

    /** Set down one at a time. */
    private static final int SHOTS = 3;

    /** How far away one can be placed. */
    private static final double REACH = 20.0;

    /** How far below the aim point to look for ground when aiming at open sky. */
    private static final int GROUND_SCAN = 20;

    @Override
    public String getName() {
        return "Air spout";
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
        return 300; // 15 seconds, starting from the last of the three
    }

    @Override
    public int getChargeTicks() {
        return CHARGE_TICKS;
    }

    /** Three clicks, not one. */
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
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.9F, 0.7F);
    }

    /** Three knots of air gathering and tightening around the bender. */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) CHARGE_TICKS;
        double radius = 2.2 - (1.3 * power);
        double spin = (player.tickCount % 12) / 12.0 * Math.PI * 2.0;

        for (int i = 0; i < SHOTS; i++) {
            double angle = spin + (Math.PI * 2.0 * i / SHOTS);
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + 0.6 + power,
                    player.getZ() + Math.sin(angle) * radius,
                    2, 0.08, 0.2, 0.08, 0.01);
        }
    }

    /** The charge only finishes winding them up; the clicks set them down. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    /**
     * The spouts still in hand, turning around the bender.
     *
     * Drawn from the live count rather than from SHOTS, so setting one down visibly
     * leaves two — the ring is the counter.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        int remaining = data.getTwoPhaseShots();
        if (remaining <= 0) return;

        double spin = (player.tickCount % 24) / 24.0 * Math.PI * 2.0;
        for (int i = 0; i < remaining; i++) {
            double angle = spin + (Math.PI * 2.0 * i / remaining);
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + Math.cos(angle) * 0.9,
                    player.getY() + 1.4,
                    player.getZ() + Math.sin(angle) * 0.9,
                    2, 0.08, 0.15, 0.08, 0.0);
        }
    }

    /** One click, one spout, standing where the bender is looking. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        AirSpouts.place(level, aimPoint(player, level));
    }

    /** Where the spout's foot goes. See Aiming. */
    private static Vec3 aimPoint(ServerPlayer player, ServerLevel level) {
        return com.minecraft.atlamod.abilities.Aiming.groundUnderLook(player, REACH, GROUND_SCAN);
    }
}
