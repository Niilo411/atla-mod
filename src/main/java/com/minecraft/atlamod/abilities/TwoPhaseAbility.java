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
}
