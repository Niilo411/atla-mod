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

    /** Most chi a single tick of this channel can cost. The default start floor. */
    default int getMaxChiPerTick() {
        return (getChiPerSecond() + 19) / 20;
    }

    /**
     * Chi the player must already have before this channel will start.
     *
     * This is a gate, not a cost — nothing is deducted for meeting it, and once
     * running the channel keeps going below this figure until chi actually runs
     * out. Defaults to a single tick's worth, i.e. "enough to run at all".
     */
    default int getMinimumChiToStart() {
        return getMaxChiPerTick();
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

    /**
     * Whether incoming damage should be cancelled while this channel is running.
     *
     * Not total immunity: AbilityHandler#blocksDamage always lets fall damage and
     * anything tagged BYPASSES_INVULNERABILITY (the void, /kill) through. A shield
     * stops what is coming at the player, not the ground or the bottom of the world.
     *
     * Implemented by cancelling LivingIncomingDamageEvent rather than by setting
     * Entity#setInvulnerable, because that flag is written to the player's NBT —
     * logging out mid-channel would leave the player invincible for good. Reacting
     * to the event keeps invulnerability strictly tied to the channel being active.
     */
    default boolean grantsInvulnerability() {
        return false;
    }

    /**
     * Whether the player is held in place while this channel runs (both shields).
     *
     * Rooting is done on the server AND on the client. Zeroing motion server-side
     * alone would leave the client still trying to walk and being corrected every
     * tick, which rubber-bands; the client is told to stop taking movement input so
     * the two agree.
     */
    default boolean rootsPlayer() {
        return false;
    }

    /**
     * Checked every tick: return false and the channel stops itself.
     *
     * For conditions that can lapse while the key is still held — Water Heal only
     * works while standing in water, and should end when the bender walks out of it
     * rather than carrying on for free.
     */
    default boolean canContinue(ServerPlayer player, BendingData data) {
        return true;
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
