package com.minecraft.atlamod;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.minecraft.atlamod.network.SettingsFilePacket;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Who may change the mod's settings, and how a change reaches a server that is not on
 * this machine.
 *
 * THREE KINDS OF WORLD, THREE ANSWERS:
 * <ul>
 *   <li><b>Your own world</b> (singleplayer, LAN, or hosted through something like the
 *       Essential mod — all an integrated server on the host's machine): only the HOST.
 *       Guests are never allowed, however many permissions the world hands out, because
 *       "Open to LAN: Allow Cheats" makes every guest an operator and that is not the same
 *       thing as it being their world. The host edits the file directly from the screen;
 *       see {@code AtlaSettingsScreen.blockedReason} for the host's own cheats rule.</li>
 *   <li><b>A dedicated server</b>: operators only (permission level 2, the same test every
 *       {@code /bend} command makes). The file is on the server's disk, so the screen sends
 *       the new values up in an {@code UpdateSettingsPacket} and the server writes them.</li>
 * </ul>
 *
 * THE SERVER DECIDES, NOT THE MENU. The screen greys itself out for anyone who may not
 * edit, but a client is only ever asking — {@link #canEdit} is checked again on arrival,
 * and a packet from anybody else is dropped.
 *
 * AFTER ANY CHANGE, EVERYONE CONNECTED IS SENT THE NEW FILE. NeoForge only syncs a server
 * config while a player is joining, so without this the other players' menus would show
 * the old figures until they rejoined. It matters mostly for the menu: every cast, cost
 * and cooldown is decided on the server anyway.
 */
public final class SettingsAccess {

    private SettingsAccess() {
    }

    /** Whether this player may change the settings of the server they are on. */
    public static boolean canEdit(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        if (server.isDedicatedServer()) return player.hasPermissions(2);

        return server.isSingleplayerOwner(player.getGameProfile());
    }

    // ==========================================================================
    //  The values, as a tag, both ways
    // ==========================================================================

    /** Every setting's current value, keyed by its dotted TOML path. */
    public static CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag();

        forEachValue(AtlaConfig.SPEC.getValues(), "", (path, value) -> {
            Tag written = toTag(value.get());
            if (written != null) tag.put(path, written);
        });
        return tag;
    }

    /**
     * Applies a snapshot to the loaded config, skipping anything the spec would reject.
     *
     * VALIDATED AGAINST THE SPEC'S OWN RULES, the same ranges and element tests that
     * guard the TOML — a packet is no more trusted than a hand-edited file, and a value
     * out of range here could be an out-of-range spacing handed to world generation.
     * Paths it does not know are ignored, so a client on a slightly different build
     * cannot write keys that do not exist.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void apply(CompoundTag tag) {
        UnmodifiableConfig specs = AtlaConfig.SPEC.getSpec();

        forEachValue(AtlaConfig.SPEC.getValues(), "", (path, value) -> {
            if (!tag.contains(path)) return;

            Object wanted = fromTag(tag.get(path), value.get());
            if (wanted == null) return;

            Object spec = specs.get(path);
            if (spec instanceof ModConfigSpec.ValueSpec valueSpec && !valueSpec.test(wanted)) return;

            ((ModConfigSpec.ConfigValue) value).set(wanted);
        });
    }

    private static void forEachValue(UnmodifiableConfig config, String prefix,
                                     BiConsumer<String, ModConfigSpec.ConfigValue<?>> action) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();

            if (entry.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                action.accept(path, value);
            } else if (entry.getValue() instanceof UnmodifiableConfig child) {
                forEachValue(child, path, action);
            }
        }
    }

    /** The mod's settings are only ever booleans, whole numbers and lists of names. */
    private static Tag toTag(Object value) {
        if (value instanceof Boolean b) return ByteTag.valueOf(b);
        if (value instanceof Integer i) return IntTag.valueOf(i);

        if (value instanceof List<?> list) {
            ListTag out = new ListTag();
            for (Object element : list) out.add(StringTag.valueOf(String.valueOf(element)));
            return out;
        }
        return null;
    }

    /** A tag read back as the same kind of thing the value already holds, or null. */
    private static Object fromTag(Tag tag, Object current) {
        if (current instanceof Boolean && tag instanceof ByteTag b) return b.getAsByte() != 0;
        if (current instanceof Integer && tag instanceof IntTag i) return i.getAsInt();

        if (current instanceof List<?> && tag instanceof ListTag list) {
            List<String> out = new ArrayList<>();
            for (Tag element : list) out.add(element.getAsString());
            return out;
        }
        return null;
    }

    // ==========================================================================
    //  Telling everyone
    // ==========================================================================

    /**
     * Sends the settings file as it now is on disk to every connected player.
     *
     * The FILE rather than a snapshot, because that is exactly what NeoForge sends at login
     * and the client already knows how to take it in — it reloads the config and fires the
     * same Reloading event, which refreshes the cached values.
     */
    public static void broadcast(MinecraftServer server) {
        for (ModConfig config : ModConfigs.getConfigSet(ModConfig.Type.SERVER)) {
            if (config.getSpec() != AtlaConfig.SPEC) continue;

            try {
                byte[] contents = Files.readAllBytes(config.getFullPath());
                PacketDistributor.sendToAllPlayers(new SettingsFilePacket(config.getFileName(), contents));
            } catch (IOException | RuntimeException e) {
                Atlamod.LOGGER.warn("Could not send the updated settings to players", e);
            }
        }
    }
}
