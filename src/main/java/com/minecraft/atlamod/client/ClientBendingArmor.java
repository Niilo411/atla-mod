package com.minecraft.atlamod.client;

import com.minecraft.atlamod.BendingArmorSuit;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which bending armor suits the client currently believes each player is wearing.
 *
 * Fed by BendingArmorPacket and read by BendingArmorLayer when it decides what to draw
 * over someone. It exists because effects are only synced to their own owner, so this
 * is the only way one player can know another is armored.
 */
public final class ClientBendingArmor {

    private static final Map<Integer, Set<BendingArmorSuit>> WORN = new HashMap<>();

    private ClientBendingArmor() {
    }

    public static void set(int entityId, BendingArmorSuit suit, boolean active) {
        if (active) {
            WORN.computeIfAbsent(entityId, id -> EnumSet.noneOf(BendingArmorSuit.class)).add(suit);
        } else {
            Set<BendingArmorSuit> suits = WORN.get(entityId);
            if (suits == null) return;
            suits.remove(suit);
            // Dropped rather than left empty, so a player who takes their armor off
            // stops costing an entry for the rest of the session.
            if (suits.isEmpty()) WORN.remove(entityId);
        }
    }

    /**
     * The suit to actually draw for this player, or null for none.
     *
     * Only one is ever drawn: two sheets on the same model would z-fight. See
     * BendingArmorSuit.best for which wins.
     */
    public static BendingArmorSuit top(int entityId) {
        Set<BendingArmorSuit> suits = WORN.get(entityId);
        if (suits == null || suits.isEmpty()) return null;

        BendingArmorSuit top = null;
        for (BendingArmorSuit suit : suits) {
            top = BendingArmorSuit.best(top, suit);
        }
        return top;
    }

    /**
     * Dropped on leaving a world, or the ids would be matched against whatever
     * entities happened to be given the same numbers in the next one.
     */
    public static void clear() {
        WORN.clear();
    }
}
