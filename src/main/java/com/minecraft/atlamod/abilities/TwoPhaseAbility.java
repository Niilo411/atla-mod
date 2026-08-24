package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * A charge-then-fire ability: pressing the slot key arms it, the next left click
 * releases it (Fireball, and later Water Sphere, Air Cannon...).
 *
 * AbilityHandler arms the ability automatically on cast and clears the armed slot
 * on release, so implementors never touch getActiveTwoPhaseAbility() themselves.
 *
 * Cooldowns for this shape start on RELEASE, not on cast — otherwise the timer
 * would run down while the player is still holding the charge.
 */
public interface TwoPhaseAbility extends Ability {

    /** Fired when the player left-clicks while this ability is armed. */
    void onRelease(ServerPlayer player, BendingData data);

    /**
     * Drawn every tick while this ability sits armed, waiting on the left click.
     *
     * Owned by the ability rather than by the tick loop, because what is being held
     * differs per ability — a ball of fire and a body of water do not look alike, and
     * a shared implementation can only ever be right for one of them.
     */
    default void onArmedTick(ServerPlayer player, BendingData data) {
    }

    /**
     * How long the armed state lasts before the ability is lost, in ticks.
     * 0 means it waits indefinitely, which is what Fireball and Water ball do.
     *
     * Chi is spent when the ability is ARMED, not when it is released, so letting the
     * window lapse costs the player the cast. That is the point of having one.
     */
    default int getArmedDurationTicks() {
        return 0;
    }

    /** Fired when the window ran out with the ability still unspent. */
    default void onArmedExpire(ServerPlayer player, BendingData data) {
    }
}
