package com.minecraft.atlamod.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tells everybody when the sky changes, and only when it changes.
 *
 * THE EVENTS THEMSELVES NEED NO STATE — they are a function of the clock, and that is
 * the whole design. But an ANNOUNCEMENT is not a state, it is a transition, and a
 * transition is the one thing a pure function of time cannot tell you: "is it happening"
 * is answerable at any moment, "did it just start" is not. So this remembers what was
 * true last tick and says something when the answer moves.
 *
 * REMEMBERED IN MEMORY RATHER THAN SAVED, deliberately. The worst a restart can do is
 * announce an event a second time, which is a line of chat; persisting a set of booleans
 * to avoid that would be more machinery than the problem is worth, and would then be a
 * second record of something the clock already knows.
 *
 * CHECKED ONCE A SECOND, not every tick. An event's edges are minutes apart and nobody
 * can tell a twentieth of a second either way, where asking twenty times a second would
 * be twenty times the work to reach the same answer.
 */
public final class WorldEventAnnouncer {

    private static final int CHECK_EVERY = 20;

    /** What was running when this was last looked at. */
    private static final Set<WorldEvents.Event> RUNNING = EnumSet.noneOf(WorldEvents.Event.class);

    private WorldEventAnnouncer() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (server.getTickCount() % CHECK_EVERY != 0) return;

        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        for (WorldEvents.Event event : WorldEvents.Event.values()) {
            boolean now = WorldEvents.isActive(overworld, event);
            boolean was = RUNNING.contains(event);

            if (now == was) continue;

            if (now) {
                RUNNING.add(event);
                announce(server, began(event));
            } else {
                RUNNING.remove(event);
                announce(server, ended(event));
            }
        }
    }

    /**
     * Cleared when a server stops, so a single-player session that opens a second world
     * does not start out believing the first one's sky is still overhead.
     */
    public static void forget() {
        RUNNING.clear();
    }

    private static void announce(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static Component began(WorldEvents.Event event) {
        return switch (event) {
            case BLOOD_MOON -> Component.literal("The moon runs red. Water answers more easily tonight.")
                    .withStyle(ChatFormatting.DARK_RED);
            case SOZINS_COMET -> Component.literal("Sozin's Comet is overhead. Firebending is at its height.")
                    .withStyle(ChatFormatting.GOLD);
            case BLACK_SUN -> Component.literal("The moon crosses the sun. Firebending is going out.")
                    .withStyle(ChatFormatting.DARK_GRAY);
        };
    }

    private static Component ended(WorldEvents.Event event) {
        return switch (event) {
            case BLOOD_MOON -> Component.literal("The blood moon sets.")
                    .withStyle(ChatFormatting.DARK_RED);
            case SOZINS_COMET -> Component.literal("The comet has passed.")
                    .withStyle(ChatFormatting.GOLD);
            case BLACK_SUN -> Component.literal("The sun returns. Fire comes back with it.")
                    .withStyle(ChatFormatting.YELLOW);
        };
    }
}
