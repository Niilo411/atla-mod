package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * An ability that has to be held down to build up, and fires by itself the moment
 * it is fully charged (Fireball, Fire Spikes).
 *
 * Distinct from TwoPhaseAbility, which arms on a press and waits for a left click.
 * Here the wind-up is a timer: hold the slot key for getChargeTicks(), and it goes
 * off. Let go early and nothing happens — and nothing is spent.
 *
 * AbilityHandler owns the whole lifecycle. Chi is only checked when the charge
 * starts and is not actually spent until the cast lands, so an abandoned charge is
 * free. The payload is the ordinary Ability#execute, run through the same path as
 * an instant cast, so cooldown, chi, XP and syncing all behave identically.
 */
public interface ChargedAbility extends Ability {

    /** How long the key must be held before the ability fires, in ticks. */
    int getChargeTicks();

    /** Fired once when the player starts charging. */
    default void onChargeStart(ServerPlayer player, BendingData data) {
    }

    /**
     * Called every tick while charging, for wind-up effects.
     *
     * @param ticksHeld how long it has been charging, from 1 up to getChargeTicks()
     */
    default void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
    }

    /** Fired when the key is released before the charge completed. */
    default void onChargeCancel(ServerPlayer player, BendingData data) {
    }
}
