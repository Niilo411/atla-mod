package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Balanced / Fire. Rocket flight: creative-style free flying, but slower and
 * capped at 10 blocks above the ground, with flame venting from the player's feet.
 *
 * Flight is granted through the vanilla ability flags, which are written to player
 * NBT — so anything that turns them on has to be certain they get turned off again.
 * See stop(), and the safety net in ServerEvents on login for the case where the
 * player disconnects mid-flight and stop() never runs.
 */
public class FireRocket implements ChanneledAbility {

    /** How far above the ground the player may climb, in blocks. */
    private static final int MAX_HEIGHT = 10;

    /** How far down to look for ground before giving up (over the void, say). */
    private static final int GROUND_SCAN = 64;

    /** Vanilla creative flight is 0.05. */
    private static final float ROCKET_FLY_SPEED = 0.03F;
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    /** Ticks of fall protection after the rocket cuts out, so the trip down is safe. */
    public static final int LANDING_GRACE_TICKS = 100;

    @Override
    public String getName() {
        return "Fire Rocket";
    }

    /** Paid per second while channeling, not up front. */
    @Override
    public int getChiCost() {
        return 0;
    }

    /** Granted per second, not up front. */
    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond() {
        return 15;
    }

    @Override
    public int getXpPerSecond() {
        return 5;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().setFlyingSpeed(ROCKET_FLY_SPEED);
        player.onUpdateAbilities();

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        // No fall damage can build up while the rocket is lit.
        player.fallDistance = 0.0F;

        enforceCeiling(player);

        if (!(player.level() instanceof ServerLevel level)) return;

        // Exhaust venting downward from the feet.
        double fx = player.getX();
        double fy = player.getY() + 0.1;
        double fz = player.getZ();

        level.sendParticles(ParticleTypes.FLAME, fx, fy, fz, 6, 0.15, 0.05, 0.15, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, fx, fy, fz, 2, 0.1, 0.05, 0.1, 0.01);
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        stopFlight(player);

        // The player is usually airborne when the rocket cuts out, so protect the
        // trip down — otherwise the ability would routinely hurt the person using it.
        data.setFallImmunityTicks(LANDING_GRACE_TICKS);
        player.fallDistance = 0.0F;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * Takes flight away again, unless the player is in a mode that grants it anyway —
     * stripping mayfly from someone in creative would be a nasty surprise.
     */
    public static void stopFlight(ServerPlayer player) {
        player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);

        if (player.isCreative() || player.isSpectator()) {
            player.onUpdateAbilities();
            return;
        }

        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    /**
     * Holds the player under the height cap.
     *
     * Creative flight is driven by the client, so zeroing server-side velocity would
     * not stop the ascent — the position has to be corrected instead. The correction
     * only fires above the cap and puts the player exactly on it, so holding jump at
     * the ceiling reads as hovering against it.
     */
    private static void enforceCeiling(ServerPlayer player) {
        double ceiling = ceilingFor(player);
        if (player.getY() <= ceiling) return;

        player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        player.connection.teleport(player.getX(), ceiling, player.getZ(),
                player.getYRot(), player.getXRot());
    }

    /** Ground level under the player, plus the height allowance. */
    private static double ceilingFor(ServerPlayer player) {
        Level level = player.level();
        BlockPos from = player.blockPosition();

        for (int dy = 0; dy <= GROUND_SCAN; dy++) {
            BlockPos check = from.below(dy);
            if (level.getBlockState(check).isSolid()) {
                return check.getY() + 1 + MAX_HEIGHT;
            }
        }

        // Nothing underneath within range — over the void or very high up. Don't
        // yank the player anywhere, just let them be.
        return Double.MAX_VALUE;
    }
}
