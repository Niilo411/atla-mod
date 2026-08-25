package com.minecraft.atlamod.abilities;

import javax.annotation.Nullable;

/**
 * One purchasable improvement to a single ability.
 *
 * Abilities declare their own upgrades through {@link Ability#getUpgrades()}, so an
 * upgrade lives beside the code that reads it rather than in a table somewhere else
 * that has to be kept in step.
 *
 * @param key         stored on the player; must be unique across every ability
 * @param name        shown on the button
 * @param description one line explaining what buying it does
 * @param cost        levels to buy, spent the same way an ability unlock is
 * @param requires    key of an upgrade that must be owned first, or null if it stands
 *                    alone. Chains upgrades into an order — Mine's log cutting is only
 *                    for sale once its obsidian breaking is bought. Enforced by
 *                    BuyUpgradePacket, not just greyed out in the menu, because the
 *                    client is only ever asking.
 */
public record AbilityUpgrade(String key, String name, String description, int cost,
                             @Nullable String requires) {

    /** An upgrade with nothing standing in front of it, which is most of them. */
    public AbilityUpgrade(String key, String name, String description, int cost) {
        this(key, name, description, cost, null);
    }
}
