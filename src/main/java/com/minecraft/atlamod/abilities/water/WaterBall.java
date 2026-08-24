package com.minecraft.atlamod.abilities.water;

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
 * Offensive / Water. Both shapes at once, exactly as Fireball is: hold the slot key
 * for two seconds to gather a body of water, then left click to throw it.
 *
 * ChargedAbility drives the gathering; TwoPhaseAbility is what the gathering
 * produces. The dispatcher arms the two-phase slot as part of the completed cast,
 * which is also why the cooldown waits for the throw rather than starting when the
 * water is ready.
 */
public class WaterBall implements ChargedAbility, TwoPhaseAbility {

    /** Blocks per tick. */
    private static final double SPEED = 1.3;

    /** Ticks before it falls apart unspent — roughly 40 blocks of reach. */
    private static final int LIFETIME = 30;

    /** 3 hearts. */
    private static final float DAMAGE = 6.0F;

    /** How close it has to pass to catch something. */
    private static final double HIT_RADIUS = 0.9;

    /** Enough of a shove to break someone off, well short of Water push. */
    private static final double KNOCKBACK = 0.55;

    @Override
    public String getName() {
        return "Water ball";
    }

    @Override
    public int getChiCost() {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 40; // 2 seconds, starting from the throw
    }

    @Override
    public int getChargeTicks() {
        return 40; // 2 seconds of gathering
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    /**
     * Refuses to gather a second body of water while one is still in hand. Without
     * this the player could charge again, pay another 50 chi, and simply re-arm the
     * slot they already had armed.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Water drawn in front of the player, tightening into a ball as it fills.
        Vec3 look = player.getLookAngle();
        double px = player.getX() + look.x * 1.8;
        double py = player.getEyeY() + look.y * 1.8;
        double pz = player.getZ() + look.z * 1.8;

        double spread = 0.6 - (0.42 * ticksHeld / (double) getChargeTicks());
        level.sendParticles(ParticleTypes.SPLASH, px, py, pz, 5, spread, spread, spread, 0.01);
        level.sendParticles(ParticleTypes.BUBBLE, px, py, pz, 2, spread, spread, spread, 0.0);
    }

    /**
     * The gathering finished. The dispatcher has already armed the two-phase slot, so
     * all that is left is telling the player the water is ready to throw.
     */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    /** The gathered water held ready, so others can see it coming and not just the HUD. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 look = player.getLookAngle();
        double px = player.getX() + look.x * 2.0;
        double py = player.getY() + 1.2 + look.y * 2.0;
        double pz = player.getZ() + look.z * 2.0;

        level.sendParticles(ParticleTypes.SPLASH, px, py, pz, 8, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.FALLING_WATER, px, py, pz, 3, 0.2, 0.25, 0.2, 0.0);
        level.sendParticles(ParticleTypes.BUBBLE, px, py, pz, 3, 0.2, 0.2, 0.2, 0.01);
    }

    /** Left click, with a gathered body of water in hand. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();

        // Launched a little ahead of the eyes so it does not clip the caster.
        Vec3 from = player.getEyePosition().add(look.scale(0.8));

        WaterProjectiles.launch(player, from, look, SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2F, 0.9F);
    }
}
