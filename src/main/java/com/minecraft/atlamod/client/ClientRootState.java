package com.minecraft.atlamod.client;

/**
 * Client-side mirror of "an ability is holding you still", fed by RootedPacket and
 * read by ClientEvents when it decides whether to pass movement input along.
 */
public final class ClientRootState {

    private static boolean rooted = false;

    private ClientRootState() {
    }

    public static boolean isRooted() {
        return rooted;
    }

    public static void set(boolean value) {
        rooted = value;
    }
}
