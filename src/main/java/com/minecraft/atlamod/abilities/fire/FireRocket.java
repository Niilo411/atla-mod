package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Balanced / Fire. Rocket flight: hold the key and you fly, let go and you stop.
 * No height limit.
 *
 * Flight is entirely owned by the keybind. Vanilla would let a player with mayfly
 * toggle flight off by double-tapping space, and the client also drops flight the
 * moment you touch the ground — both are undone here, so the only thing that ends
 * the flight is releasing the key.
 *
 * The vanilla ability flags are written to player NBT, so anything that turns them
 * on has to be certain they get turned off again. See stopFlight(), and the safety
 * nets in ServerEvents on login and respawn for the case where the player
 * disconnects or dies mid-flight and onStop() never runs.
 */
public class FireRocket implements ChanneledAbility {

    /** Vanilla creative flight is 0.05. */
    private static final float ROCKET_FLY_SPEED = 0.03F;
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    /** Upward kick used to break contact with the ground, about a jump's worth. */
    private static final double LIFT_OFF = 0.42;

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

        // Launch. Without this the player is still standing on the ground, and the
        // client clears `flying` on anything that is touching down — so the flight
        // would switch itself off the instant it was granted.
        liftOff(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        // No fall damage can build up while the rocket is lit.
        player.fallDistance = 0.0F;

        keepFlying(player);

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

        // The player is nearly always airborne when the rocket cuts out, so protect
        // the trip down — otherwise the ability would routinely hurt whoever used it.
        data.setFallImmunityTicks(LANDING_GRACE_TICKS);
        player.fallDistance = 0.0F;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * Puts flight back on if anything turned it off, so the keybind is the only
     * thing that can end it.
     *
     * Two things fight us. A double-tap of space is vanilla's flight toggle for
     * anyone with mayfly, and the client also clears flight whenever the player is
     * on the ground. Re-asserting alone would beat the double-tap but would trade
     * packets with the client every tick on the ground, so touching down also earns
     * another kick upward.
     */
    private static void keepFlying(ServerPlayer player) {
        if (player.getAbilities().flying) return;

        player.getAbilities().flying = true;
        player.onUpdateAbilities();

        if (player.onGround()) {
            liftOff(player);
        }
    }

    /** Kick the player off the ground so the client stops clearing flight. */
    private static void liftOff(ServerPlayer player) {
        player.setDeltaMovement(
                player.getDeltaMovement().x, LIFT_OFF, player.getDeltaMovement().z);
        // Players ignore server-side velocity unless it is explicitly pushed to them.
        player.hurtMarked = true;
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
}
