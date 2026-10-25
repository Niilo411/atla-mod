package io.github.niilo411.elementalpaths.abilities.blood;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.abilities.Aiming;
import io.github.niilo411.elementalpaths.abilities.ChanneledAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Left / Blood. Held: the blood in whatever the channeler is looking at is slowed to a
 * crawl, and stays slowed for as long as the key is down.
 *
 * Re-aimed every tick rather than locked to one victim on the cast, so the channeler can
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
     * after the channeler lets go rather than lingering for its full duration.
     */
    private static final int SLOW_TICKS = 30;

    @Override
    public String getName() {
        return "Blood Slow";
    }

    @Override
    public int getChiCost(ChannelingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(ChannelingData data) {
        return 15;
    }

    /**
     * Zero, deliberately: the xp is paid into the BLOOD track instead, in onTick.
     *
     * The dispatcher's own xp trickle goes to the ordinary level, and blood channeling's
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
    public void onStart(ServerPlayer player, ChannelingData data) {
        Blood.squelch((ServerLevel) player.level(), player.position(), 0.7F, 0.8F);
    }

    @Override
    public void onTick(ServerPlayer player, ChannelingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canChannel(player, target)) return;

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
    public void onStop(ServerPlayer player, ChannelingData data) {
        // Nothing to put away: the slowness sees itself out a moment later.
    }
}
