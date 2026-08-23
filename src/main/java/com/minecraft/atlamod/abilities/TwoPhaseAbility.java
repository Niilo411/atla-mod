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
}
