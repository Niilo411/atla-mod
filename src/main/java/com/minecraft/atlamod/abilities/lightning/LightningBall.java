package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerPlayer;

/**
 * Right / Lightning. A ball of live current set loose and then driven around on the
 * crosshair, shocking everything it drifts near for twenty seconds.
 *
 * A TOGGLE, like Tornado — press once to send it out, press again to put it out
 * early. That second press goes through {@link Ability#isActive}, which the
 * dispatcher checks BEFORE the cooldown gate and before spending anything: without
 * it, a twenty second ball behind a five second cooldown would be uncancellable for
 * its first quarter, and switching it off would cost another 100 chi.
 *
 * The ball itself lives in {@link LightningBalls}.
 */
public class LightningBall implements ChargedAbility {

    /**
     * The element's one-second wind-up, served when the ball is SENT OUT.
     *
     * Calling it back is instant: AbilityHandler.startCharge checks isActive before
     * anything else, so the toggle-off never waits on the charge.
     */
    @Override
    public int getChargeTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((net.minecraft.server.level.ServerLevel) player.level(),
                player, ticksHeld, getChargeTicks());
    }

    @Override
    public String getName() {
        return "Lightning ball";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    /**
     * Five seconds, starting when the ball is SENT OUT rather than when it goes off.
     *
     * That is the dispatcher's ordinary behaviour and the right one here: the
     * cooldown is on summoning another, and by the time a ball has run its twenty
     * seconds the cooldown is long gone.
     */
    @Override
    public int getCooldownTicks() {
        return 100;
    }

    /** One already out: the next press takes it down. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return LightningBalls.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        LightningBalls.cancel(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        LightningBalls.summon(player);
    }
}
