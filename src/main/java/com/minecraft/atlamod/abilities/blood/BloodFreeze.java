package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Left / Blood. One second of gathering, then the target is locked in place for two
 * seconds and bled the whole time.
 *
 * The element's opener. Two seconds is short, but a target that cannot move at all is
 * a target every other bloodbending ability can be lined up on -- and unlike Freeze in
 * icebending, this one does NOT make them untouchable while it holds.
 */
public class BloodFreeze implements ChargedAbility {

    /** How far the hold reaches, in blocks. */
    private static final double REACH = 16.0;

    /** How far off the crosshair a target may be and still be caught. */
    private static final double TOLERANCE = 2.0;

    /** Two seconds, as specced. */
    private static final int HOLD_TICKS = 40;

    /** 2 hp a second while it holds. */
    private static final float DAMAGE_PER_SECOND = 2.0F;

    @Override
    public String getName() {
        return "Blood freeze";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

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
    public boolean canStart(ServerPlayer player, BendingData data) {
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        return target != null && Blood.canBendOrTell(player, target);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Blood.mist((ServerLevel) player.level(),
                player.getEyePosition().add(player.getLookAngle().scale(1.0)), 3, 0.2);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        LivingEntity target = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (target == null) return;
        if (!Blood.canBend(player, target)) return;

        // Held by the same effect Lightning stun uses. Nothing in vanilla stops a
        // player walking, and there is no reason for bloodbending to grow its own copy.
        target.addEffect(new MobEffectInstance(
                ModEffects.STUNNED, HOLD_TICKS, 0, false, true, true));

        // The bleeding is tracked so it lands once a second for the whole hold rather
        // than all at once on the cast.
        BloodHolds.hold(level, player, target, HOLD_TICKS, DAMAGE_PER_SECOND);

        Blood.wrench(level, target, 30);
        Blood.squelch(level, target.position(), 1.0F, 0.7F);

        Blood.grantXp(player, data, getXpReward());
    }
}
