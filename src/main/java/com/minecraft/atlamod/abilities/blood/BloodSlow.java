package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Left / Blood. Held: the blood in whatever the bender is looking at is slowed to a
 * crawl, and stays slowed for as long as the key is down.
 *
 * Re-aimed every tick rather than locked to one victim on the cast, so the bender can
 * sweep it across a group -- but only one at a time, which is what keeps it a duel
 * ability rather than a crowd one.
 */
public class BloodSlow implements ChanneledAbility {

    /** How far the hold reaches, in blocks. */
    private static final double REACH = 16.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /** Slowness IV: enough to make running away no longer an option. */
    private static final int SLOW_LEVEL = 3;

    /**
     * How long each application lasts.
     *
     * Short, and re-applied while the key is held, so the slowness falls off shortly
     * after the bender lets go rather than lingering for its full duration.
     */
    private static final int SLOW_TICKS = 30;

    @Override
    public String getName() {
        return "Blood Slow";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 15;
    }

    /**
     * Zero, deliberately: the xp is paid into the BLOOD track instead, in onTick.
     *
     * The dispatcher's own xp trickle goes to the ordinary level, and bloodbending's
     * whole point is that its abilities feed a separate one.
     */
    @Override
    public double getXpPerSecond() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 400; // 20 seconds, and it starts when the hold ENDS
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Blood.squelch((ServerLevel) player.level(), player.position(), 0.7F, 0.8F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canBend(player, target)) return;

        // Topped up rather than re-applied every tick: each addEffect is a packet, and
        // Slowness is not counter-driven so refreshing it is safe but noisy.
        var slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slowness == null || slowness.getAmplifier() < SLOW_LEVEL
                || slowness.getDuration() < SLOW_TICKS / 2) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_LEVEL, false, true, true));
        }

        Blood.wrench(level, target, 2);

        // One blood xp a second, on the channel's own beat.
        if (data.getChannelTicks() % 20 == 0) {
            Blood.grantXp(player, data, 1);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away: the slowness sees itself out a moment later.
    }
}
