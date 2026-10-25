package io.github.niilo411.elementalpaths.client;

import io.github.niilo411.elementalpaths.ChannelingArmorSuit;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which channeling armor suits the client currently believes each player is wearing.
 *
 * Fed by ChannelingArmorPacket and read by ChannelingArmorLayer when it decides what to draw
 * over someone. It exists because effects are only synced to their own owner, so this
 * is the only way one player can know another is armored.
 */
public final class ClientChannelingArmor {

    private static final Map<Integer, Set<ChannelingArmorSuit>> WORN = new HashMap<>();

    private ClientChannelingArmor() {
    }

    public static void set(int entityId, ChannelingArmorSuit suit, boolean active) {
        if (active) {
            WORN.computeIfAbsent(entityId, id -> EnumSet.noneOf(ChannelingArmorSuit.class)).add(suit);
        } else {
            Set<ChannelingArmorSuit> suits = WORN.get(entityId);
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
     * ChannelingArmorSuit.best for which wins.
     */
    public static ChannelingArmorSuit top(int entityId) {
        Set<ChannelingArmorSuit> suits = WORN.get(entityId);
        if (suits == null || suits.isEmpty()) return null;

        ChannelingArmorSuit top = null;
        for (ChannelingArmorSuit suit : suits) {
            top = ChannelingArmorSuit.best(top, suit);
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
