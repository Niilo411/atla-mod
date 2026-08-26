package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerPlayer;

/**
 * Right / Sound. Fifteen seconds of dark-pitched waves rolling outward in every
 * direction, one every four seconds, stunning and wearing down whatever they wash
 * over.
 *
 * Cancellable mid-run by pressing the keybind again, which the design asks for
 * explicitly. That goes through isActive/deactivate — checked before the cooldown and
 * before anything is spent — rather than through the ordinary cast path, which would
 * charge another 150 chi for the privilege of switching it off.
 *
 * Named BassWavesAbility because {@link BassWaves} is the tracker that runs it. The
 * display name the tree and the registry key off is still "Bass waves".
 */
public class BassWavesAbility implements Ability {

    @Override
    public String getName() {
        return "Bass waves";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    /** No xp at all, as specced. */
    @Override
    public int getXpReward() {
        return 0;
    }

    /** No cooldown: the fifteen seconds it runs for is the whole limit. */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return BassWaves.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        BassWaves.cancel(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        BassWaves.start(player);
    }
}
