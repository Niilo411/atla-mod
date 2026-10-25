package io.github.niilo411.elementalpaths.abilities.blood;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.ModEffects;
import io.github.niilo411.elementalpaths.abilities.Aiming;
import io.github.niilo411.elementalpaths.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Left / Blood. One second of gathering, then the target is locked in place for five
 * seconds and bled the whole time.
 *
 * The element's opener. A target that cannot move at all is a target every other
 * blood channeling ability can be lined up on -- and unlike Freeze in ice channeling, this one
 * does NOT make them untouchable while it holds.
 */
public class BloodFreeze implements ChargedAbility {

    /** How far the hold reaches, in blocks. */
    private static final double REACH = 16.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /**
     * Five seconds, raised from the two it was specced at.
     *
     * The element's opener is meant to buy time to line something else up, and two
     * seconds behind a one second wind-up was barely longer than the cast itself. The
     * bleeding runs for the whole hold, so this raises its total damage from 4 to 10
     * as well.
     */
    private static final int HOLD_TICKS = 100;

    /** 2 hp a second while it holds. */
    private static final float DAMAGE_PER_SECOND = 2.0F;

    @Override
    public String getName() {
        return "Blood freeze";
    }

    @Override
    public int getChiCost(ChannelingData data) {
        return 100;
    }

    /** Zero: the xp is paid into the BLOOD track instead, in execute. */
    @Override
    public int getXpReward() {
        return 0;
    }

    /** What the cast pays into the BLOOD track. */
    private static final int BLOOD_XP = 10;

    @Override
    public int getCooldownTicks() {
        return 200; // 10 seconds
    }

    @Override
    public int getChargeTicks() {
        return 20; // 1 second
    }

    /**
     * Refuses the cast with nothing in front, or on somebody stronger.
     *
     * Both checked before chi is spent, so neither costs anything.
     */
    @Override
    public boolean canStart(ServerPlayer player, ChannelingData data) {
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        return target != null && Blood.canChannelOrTell(player, target);
    }

    @Override
    public void onChargeTick(ServerPlayer player, ChannelingData data, int ticksHeld) {
        Blood.mist((ServerLevel) player.level(),
                player.getEyePosition().add(player.getLookAngle().scale(1.0)), 3, 0.2);
    }

    @Override
    public void execute(ServerPlayer player, ChannelingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canChannel(player, target)) return;

        // Held by the same effect Lightning stun uses. Nothing in vanilla stops a
        // player walking, and there is no reason for blood channeling to grow its own copy.
        target.addEffect(new MobEffectInstance(
                ModEffects.STUNNED, HOLD_TICKS, 0, false, true, true));

        // The bleeding is tracked so it lands once a second for the whole hold rather
        // than all at once on the cast.
        BloodHolds.hold(level, player, target, HOLD_TICKS, DAMAGE_PER_SECOND);

        Blood.wrench(level, target, 30);
        Blood.squelch(level, target.position(), 1.0F, 0.7F);

        Blood.grantXp(player, data, BLOOD_XP);
    }
}
