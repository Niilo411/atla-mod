package com.minecraft.atlamod.abilities;

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
 */
public record AbilityUpgrade(String key, String name, String description, int cost) {
}
