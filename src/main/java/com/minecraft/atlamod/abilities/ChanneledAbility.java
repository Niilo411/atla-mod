package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * An ability held down rather than tapped (Fire Breath, and later Water Stream,
 * Air Tornado...). The client sends AbilityHoldPacket on key-state CHANGE only.
 *
 * AbilityHandler owns the lifecycle: it records which channeled ability is active
 * on BendingData, drains getChiPerTick() every tick, trickles getXpPerSecond(),
 * and stops the channel automatically when the player runs out of chi. Implementors
 * only fill in the visible effect.
 */
public interface ChanneledAbility extends Ability {

    /** Chi drained every tick while channeling. Channeling stops when chi runs below this. */
    int getChiPerTick();

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
