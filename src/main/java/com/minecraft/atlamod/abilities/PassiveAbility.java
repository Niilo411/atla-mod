package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * An ability that is never cast. Equipping it into a passive slot IS the
 * activation, and whatever it affects asks whether it's equipped.
 *
 * Passives cost no chi and grant no XP — there is no moment of use to attach
 * either to. AbilityHandler refuses to cast them, so a passive that somehow ends
 * up in a keybind slot does nothing rather than silently burning resources.
 */
public interface PassiveAbility extends Ability {

    /** One line for the passive tab, explaining what equipping it does. */
    String getDescription();

    @Override
    default int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    default int getXpReward() {
        return 0;
    }

    @Override
    default void execute(ServerPlayer player, BendingData data) {
        // Nothing to do: a passive works by being equipped, not by being used.
    }
}
