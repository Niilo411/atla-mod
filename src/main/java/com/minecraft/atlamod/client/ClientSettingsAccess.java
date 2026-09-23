package com.minecraft.atlamod.client;

import com.minecraft.atlamod.AtlaConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.ConfigSync;

/**
 * The client's half of {@link com.minecraft.atlamod.SettingsAccess}: what kind of server
 * this is, and taking in the settings file when the server sends a new one.
 */
public final class ClientSettingsAccess {

    /**
     * Whether the server we are on is a dedicated one, as it told us on login.
     *
     * False until told, which is the safe way round: a client that has not heard is
     * treated as a guest in somebody's world, who may never edit.
     */
    private static volatile boolean dedicatedServer = false;

    private ClientSettingsAccess() {
    }

    public static void setDedicatedServer(boolean dedicated) {
        dedicatedServer = dedicated;
    }

    public static boolean isDedicatedServer() {
        return dedicatedServer;
    }

    /**
     * Takes in the settings file the server just sent.
     *
     * Skipped on the host of a world of their own: the host's client shares the one loaded
     * config with the server in the same game, and loading a synced copy over it would
     * detach it from the file on disk. NeoForge's own handler skips the same case.
     */
    public static void receive(String fileName, byte[] contents) {
        if (Minecraft.getInstance().isLocalServer()) return;

        ConfigSync.receiveSyncedConfig(contents, fileName);
        AtlaConfig.refresh();
    }
}
