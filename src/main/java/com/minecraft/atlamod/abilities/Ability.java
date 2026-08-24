package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.BendingData;
import net.minecraft.server.level.ServerPlayer;

/**
 * One bending ability. Implementations hold ONLY their own effect logic —
 * chi cost, XP reward, cooldown bookkeeping and data syncing are all handled
 * for them by AbilityHandler before/after execute() runs.
 */
public interface Ability {

    /** Display name. Also the registry key (lowercased) and the cooldown key. */
    String getName();

    /** Chi consumed once, up front. Channeled abilities usually return 0 and drain per tick instead. */
    int getChiCost();

    /** XP granted once on a successful cast. */
    int getXpReward();

    /** Cooldown applied after a successful cast. 0 means no cooldown. */
    default int getCooldownTicks() {
        return 0;
    }

    /**
     * Extra per-ability precondition, checked BEFORE chi is spent so a blocked
     * cast never costs the player anything. Return false to silently abort.
     */
    default boolean canStart(ServerPlayer player, BendingData data) {
        return true;
    }

    /**
     * Whether this ability needs water to hand — true for waterbending.
     *
     * The dispatcher checks it before chi is spent: near open water it costs
     * nothing, away from water it draws a unit from the player's canteen, and with
     * neither the cast is refused for free. See WaterSupply.
     */
    default boolean requiresWater() {
        return false;
    }

    /**
     * Improvements that can be bought for this ability, shown by right clicking its
     * node in the skill tree. Empty for anything that has none.
     */
    default java.util.List<AbilityUpgrade> getUpgrades() {
        return java.util.List.of();
    }

    /** The actual effect. Chi has already been consumed and XP already granted by this point. */
    void execute(ServerPlayer player, BendingData data);

    /** Registry/cooldown key for this ability. */
    default String getKey() {
        return getName().toLowerCase();
    }
}
