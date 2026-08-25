package com.minecraft.atlamod.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Which players the client currently believes are wearing Earth armor.
 *
 * A client-side set of entity ids, fed by EarthArmorPacket and read by
 * EarthArmorLayer when it decides whether to draw the stone suit over someone. It
 * exists because effects are only synced to their own owner, so this is the only way
 * one player can know another is armored.
 */
public final class ClientEarthArmor {

    private static final Set<Integer> ARMORED = new HashSet<>();

    private ClientEarthArmor() {
    }

    public static void set(int entityId, boolean active) {
        if (active) {
            ARMORED.add(entityId);
        } else {
            ARMORED.remove(entityId);
        }
    }

    public static boolean has(int entityId) {
        return ARMORED.contains(entityId);
    }

    /**
     * Dropped on leaving a world, or the ids would be matched against whatever
     * entities happened to be given the same numbers in the next one.
     */
    public static void clear() {
        ARMORED.clear();
    }
}
