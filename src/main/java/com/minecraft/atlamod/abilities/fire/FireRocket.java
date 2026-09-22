package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.BendingFire;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Balanced / Fire. Rocket flight: press once to light it, press again to put it out.
 * No height limit.
 *
 * A TOGGLE RATHER THAN A HELD CHANNEL, which it used to be. The difference is not just
 * the input: flight is the one ability in the mod you want to stay in for minutes at a
 * time, and holding a key for the length of a journey is a poor way to ask for that.
 * Being a toggle also puts it in the company it belongs to — Air scooter, Tornado and
 * Water Surf are all travel, and all switch rather than hold.
 *
 * THAT MOVED THE BILLING. A channel is drained by the dispatcher, which spreads the
 * per-second cost across its twenty ticks; a toggle is nobody's business but the player
 * tick's, so the fifteen a second is taken by {@code ServerEvents} on the same one-second
 * beat Sound wall, Metal shield and Combustion Beam are charged on, and the rocket puts
 * itself out when the chi runs out. Spending goes through {@code consumeChi}, so the regen
 * delay is re-armed each second exactly as it was while this was a channel — a toggle that
 * regenerated its own upkeep would be free.
 *
 * Flight is entirely owned by the keybind either way. Vanilla would let a player with
 * mayfly toggle flight off by double-tapping space, and the client also drops flight the
 * moment you touch the ground — both are undone here, so nothing but the key or an empty
 * pool ends it.
 *
 * The vanilla ability flags are written to player NBT, so anything that turns them on has
 * to be certain they get turned off again. See stopFlight(), and the safety nets in
 * ServerEvents on login and respawn for the case where the player disconnects or dies
 * mid-flight and the toggle is never switched off.
 */
public class FireRocket implements Ability {

    /** Registry key, used by anything that needs to know the rocket owns flight. */
    public static final String KEY = "fire rocket";

    /** Charged once a second from the player tick, the way every other toggle is. */
    public static final int CHI_PER_SECOND = 15;
    public static final int XP_PER_SECOND = 5;

    /** Vanilla creative flight is 0.05. */
    private static final float ROCKET_FLY_SPEED = 0.03F;
    private static final float DEFAULT_FLY_SPEED = 0.05F;

    /** Upward kick used to break contact with the ground, about a jump's worth. */
    private static final double LIFT_OFF = 0.42;

    @Override
    public String getName() {
        return "Fire Rocket";
    }

    /** Paid per second by the player tick, not up front. */
    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    /** Granted per second, not up front. */
    @Override
    public int getXpReward() {
        return 0;
    }

    /**
     * Refuses to light with nothing to burn.
     *
     * One second's worth, which is the smallest amount that actually buys anything — the
     * player tick charges on the second, so a bender who cannot afford one would be
     * launched and dropped again before they had gone anywhere.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (data.getCurrentChi() >= CHI_PER_SECOND) return true;

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                "§cNot enough Chi! (Requires " + CHI_PER_SECOND + ")"), true);
        return false;
    }

    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return data.isFireRocketing();
    }

    /**
     * Pressed again: put it out.
     *
     * The dispatcher reaches this BEFORE the cooldown gate and before anything is spent,
     * which is the whole reason the toggle hook exists — switching something off must
     * never be refused or charged for.
     */
    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        stop(player, data);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        data.setFireRocketing(true);
        player.setData(ModAttachments.BENDING_DATA, data);

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

    /**
     * The single way out, whichever end arrives first.
     *
     * Both the deliberate switch-off and running dry come through here, so the flight
     * flags cannot be left open by one route and closed by the other. Public because the
     * player tick calls it when the chi runs out.
     */
    public static void stop(ServerPlayer player, BendingData data) {
        if (!data.isFireRocketing()) return;

        data.setFireRocketing(false);
        player.setData(ModAttachments.BENDING_DATA, data);

        stopFlight(player);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /**
     * Per-tick upkeep: hold the flight open and vent the exhaust.
     *
     * Called from the player tick now rather than from the dispatcher's channel tick,
     * which is the only thing about the rocket's behaviour the toggle actually changed.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        keepFlying(player);

        if (!(player.level() instanceof ServerLevel level)) return;

        // Exhaust venting downward from the feet.
        double fx = player.getX();
        double fy = player.getY() + 0.1;
        double fz = player.getZ();

        level.sendParticles(BendingFire.flame(data), fx, fy, fz, 6, 0.15, 0.05, 0.15, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, fx, fy, fz, 2, 0.1, 0.05, 0.1, 0.01);
    }

    /**
     * Puts flight back on if anything turned it off, so only the keybind ends it.
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
