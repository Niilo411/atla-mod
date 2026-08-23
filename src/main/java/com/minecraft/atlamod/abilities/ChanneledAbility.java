package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * An ability held down rather than tapped (Fire Breath, and later Water Stream,
 * Air Tornado...). The client sends AbilityHoldPacket on key-state CHANGE only.
 *
 * AbilityHandler owns the lifecycle: it records which channeled ability is active
 * on BendingData, drains getChiPerSecond() spread across the ticks, trickles getXpPerSecond(),
 * and stops the channel automatically when the player runs out of chi. Implementors
 * only fill in the visible effect.
 */
public interface ChanneledAbility extends Ability {

    /**
     * Chi drained per SECOND while channeling. The dispatcher spreads this across
     * the 20 ticks in a second, so rates that aren't a multiple of 20 (like 25/sec
     * = 1.25/tick) still drain smoothly and add up exactly over each second.
     * Channeling stops when the player can't afford the next tick.
     */
    int getChiPerSecond();

    /** Most chi a single tick of this channel can cost. Used as the "can you start?" floor. */
    default int getMaxChiPerTick() {
        return (getChiPerSecond() + 19) / 20;
    }

    /**
     * Hard cap on how long this channel may run, in ticks (20 ticks = 1 second).
     * 0 means unlimited — it runs until the key is released or chi runs out.
     *
     * Hitting the cap stops the channel exactly like releasing the key does,
     * cooldown included, so a player can't dodge the cooldown by holding on.
     */
    default int getMaxDurationTicks() {
        return 0;
    }

    /** XP trickled once per second while channeling. 0 for none. */
    default int getXpPerSecond() {
        return 0;
    }

    void onStart(ServerPlayer player, BendingData data);

    void onTick(ServerPlayer player, BendingData data);

    void onStop(ServerPlayer player, BendingData data);

    /** Channeled abilities are driven by onStart/onTick/onStop, not by a one-shot cast. */
    @Override
    default void execute(ServerPlayer player, BendingData data) {
        // intentionally empty
    }
}
