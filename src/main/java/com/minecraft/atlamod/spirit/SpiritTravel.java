package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Going through a spirit portal, in either direction.
 *
 * Kept apart from {@link SpiritPortalBlock} so the block only has to notice that
 * something walked into it, and apart from {@link SpiritPortals} so that lighting a
 * portal and travelling through one never become the same piece of code. The block
 * calls in here; nothing calls back out.
 */
public final class SpiritTravel {

    /**
     * Where each player was standing when they last stepped into the Spirit World.
     *
     * TRANSIENT, and that is a known limitation rather than an oversight: a player who
     * logs out in the Spirit World and comes back loses their way home and is returned
     * to the world spawn instead. Making it survive would mean a persisted field and a
     * packet, which is more plumbing than a first pass at this feature needs — but it
     * is the obvious next thing to add if the fallback turns out to grate.
     */
    private static final Map<UUID, GlobalPos> ENTRY = new HashMap<>();

    private SpiritTravel() {
    }

    /** Someone walked into a lit portal. Which way they go depends on where they are. */
    public static void through(Entity entity, BlockPos portalPos) {
        MinecraftServer server = entity.getServer();
        if (server == null) return;

        if (SpiritWorld.isSpiritWorld(entity.level())) {
            comeHome(entity, server);
        } else {
            enter(entity, server, portalPos);
        }
    }

    /**
     * Into the Spirit World, arriving inside the NEAREST temple to where they left.
     *
     * Nearest rather than fixed, and that difference was a real bug: every portal used to
     * lead to the one temple at the world origin, which hangs in open void because island
     * generation keeps clear of the origin on purpose. Whichever portal you stepped into,
     * you came out in the same room with nothing but a drop outside its door.
     *
     * Where exactly is {@link SpiritWorld#arrivalNear}'s business. All this has to do is
     * remember which portal was stepped into, so the way back leads to it.
     */
    private static void enter(Entity entity, MinecraftServer server, BlockPos portalPos) {
        ServerLevel spirit = SpiritWorld.level(server);
        if (spirit == null) {
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal(
                        "§cThe Spirit World is not loaded on this server."), true);
            }
            return;
        }

        ENTRY.put(entity.getUUID(), GlobalPos.of(entity.level().dimension(), portalPos));

        send(entity, spirit, SpiritWorld.arrivalNear(spirit, portalPos));
    }

    /**
     * Back out again, to the portal that was stepped into.
     *
     * Falls back to the overworld spawn when that is not known — see {@link #ENTRY}.
     * Something has to happen: a player who walks into the only portal home and is told
     * "no" has no other way out of the dimension.
     */
    private static void comeHome(Entity entity, MinecraftServer server) {
        GlobalPos entry = ENTRY.remove(entity.getUUID());

        ServerLevel destination = entry == null ? null : server.getLevel(entry.dimension());
        if (destination == null) destination = server.overworld();

        BlockPos at = entry == null ? destination.getSharedSpawnPos() : entry.pos();
        send(entity, destination, at);
    }

    /**
     * The move itself.
     *
     * The portal cooldown is set on the entity that comes BACK from changeDimension, not
     * on the one that went in: for a player those are the same object, but for anything
     * else the old entity is discarded and a copy is made in the new level, and arming
     * the one that is about to be thrown away would leave the arrival standing in a
     * portal with nothing stopping it going straight back.
     */
    private static void send(Entity entity, ServerLevel destination, BlockPos at) {
        if (!entity.canChangeDimensions(entity.level(), destination)) return;

        Vec3 pos = at.getBottomCenter();
        Entity arrived = entity.changeDimension(new DimensionTransition(
                destination, pos, Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND));

        if (arrived != null) arrived.setPortalCooldown();
    }

    /** Forgets a player's way home, so a stale entry cannot outlive their session. */
    public static void forget(UUID player) {
        ENTRY.remove(player);
    }
}
