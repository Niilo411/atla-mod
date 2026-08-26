package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Sound. A TOGGLE: while it is up, every punch throws a wave of compressed
 * sound down the crosshair, and a punch that actually connects hits far harder.
 *
 * Two separate effects, deliberately, and they are what make it worth holding up
 * rather than being a flat damage buff:
 *  - the WAVE goes out on every left click whether it connects or not, so it is a
 *    ranged attack you can throw at nothing in particular;
 *  - the direct hit is raised to 10, which only applies when a punch really lands.
 *
 * Expensive to hold at 25 chi a second, capped at thirty seconds, and thirty seconds
 * of cooldown once it drops however it ended. It also needs 100 chi banked to switch
 * on -- a gate, not a cost, and nothing is deducted for meeting it.
 *
 * Thirty seconds at 25 a second is 750 chi to run it dry, which is more than a bender
 * below level 3 can even hold: the cap and the pool between them mean the full thirty
 * seconds is something to grow into rather than something available from the start.
 */
public class CompressedPunches implements Ability {

    /** Registry key, also what the toggle is tracked by. */
    public static final String KEY = "compressed punches";

    /** What a thrown wave hits for. */
    public static final float WAVE_DAMAGE = 6.0F;

    /** What a punch that actually connects hits for while this is up. */
    public static final float PUNCH_DAMAGE = 10.0F;

    /** Chi drained per second while it is up. */
    public static final int CHI_PER_SECOND = 25;

    /** Longest it may run before it switches itself off. */
    public static final int MAX_TICKS = 600; // 30 seconds

    /** How long before it can be raised again, counted from when it ENDS. */
    public static final int COOLDOWN_TICKS = 600; // 30 seconds

    /** Chi that must already be banked before it will switch on. */
    public static final int CHI_TO_START = 100;

    /** XP paid per second while it is up. */
    public static final int XP_PER_SECOND = 1;

    private static final BendingProjectiles.Spec WAVE = new BendingProjectiles.Spec(
            2.2, 30, WAVE_DAMAGE, 1.0, 0.35, BendingProjectiles.Style.AIR);

    @Override
    public String getName() {
        return "Compressed punches";
    }

    /**
     * Nothing up front: this is billed by the second from the player tick, like a
     * channel, even though it is a toggle rather than a held key.
     */
    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    /**
     * Thirty seconds, and it starts when the ability ENDS rather than when it begins.
     *
     * That is the CHANNEL rule rather than the toggle one, and it is deliberate: with
     * the cooldown running from the start, a bender could hold this for its full
     * thirty seconds and switch straight back on the moment it dropped. Stamped by
     * {@link #stop} instead, which every route out of the ability goes through.
     */
    @Override
    public int getCooldownTicks() {
        return COOLDOWN_TICKS;
    }

    /** Up already: the next press takes it down. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return data.isPunchingCompressed();
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        stop(player, data);
    }

    /**
     * Switches it off and starts the cooldown, however it ended.
     *
     * The single exit — pressed off, run out of time, run out of chi — so the thirty
     * seconds applies uniformly and none of the three can be used to dodge it. That
     * matters most for the deliberate toggle-off, which reaches this through
     * deactivate: the dispatcher's toggle path skips the cooldown stamp entirely, so
     * without doing it here that route would be free.
     */
    public static void stop(ServerPlayer player, BendingData data) {
        if (!data.isPunchingCompressed()) return;

        data.setPunchingCompressed(false);
        data.setCompressedPunchTicks(0);
        data.setCooldown(KEY, COOLDOWN_TICKS);

        Sound.play((ServerLevel) player.level(), player.position(),
                net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
    }

    /**
     * Refuses to switch on below 100 chi.
     *
     * A gate rather than a cost: nothing is deducted for meeting it, and once running
     * it keeps going below 100 until the chi actually runs out. Same shape as the two
     * shields' 200.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getCurrentChi() >= CHI_TO_START;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        data.setPunchingCompressed(true);
        data.setCompressedPunchTicks(0);
        Sound.play((ServerLevel) player.level(), player.position(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.7F, 1.4F);
    }

    /**
     * Throws one wave. Called from the LEFT CLICK path, not from the tick.
     *
     * Fired on every click regardless of whether anything is in the way, which is the
     * point — the wave is a ranged attack in its own right rather than a bonus on a
     * connecting punch. The bonus on a connecting punch is applied separately, in the
     * damage handler.
     */
    public static void punch(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.8));

        BendingProjectiles.launch(player, from, player.getLookAngle(),
                new BendingProjectiles.Spec(
                        WAVE.speed(), WAVE.lifetime(), Sound.damage(data, WAVE_DAMAGE),
                        WAVE.hitRadius(), WAVE.knockback(), WAVE.style()));

        Sound.wave(level, from, 12, 0.25);
        Sound.play(level, player.position(),
                net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 0.5F, 1.9F);
    }
}
