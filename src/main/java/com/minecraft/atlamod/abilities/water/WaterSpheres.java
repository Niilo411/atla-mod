package com.minecraft.atlamod.abilities.water;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The pockets of air waterbenders are holding open inside bodies of water.
 *
 * Every block taken out is remembered so it can be put back — as the bender moves,
 * water closes in behind them and opens ahead, and when they stop the whole pocket
 * fills in. Anything less and a bender could quietly drain an ocean by walking
 * through it.
 *
 * Blocks are changed WITHOUT neighbour updates, which is the load-bearing detail.
 * Emptying a block in the middle of an ocean the ordinary way tells every
 * neighbouring water block to reconsider itself, and they immediately flow back into
 * the hole — the pocket would fight the sea for as long as it was held, churning the
 * whole boundary every tick. Suppressing the update leaves the surrounding water
 * believing nothing happened.
 */
public final class WaterSpheres {

    /**
     * Client-visible change with no neighbour notification, so the surrounding water
     * is never told to flow.
     */
    private static final int QUIET = Block.UPDATE_CLIENTS;

    /** Force a rescan this often regardless, in case water arrived some other way. */
    private static final int RESCAN_INTERVAL = 20;

    private static final Map<UUID, Sphere> SPHERES = new HashMap<>();

    private WaterSpheres() {
    }

    private static final class Sphere {
        final ServerLevel level;
        final Set<BlockPos> emptied = new HashSet<>();

        // Where the bender was when the pocket was last opened outward, so a bender
        // standing still does not pay to rescan a thousand blocks every tick.
        BlockPos lastScan = null;
        int ticksSinceScan = 0;

        Sphere(ServerLevel level) {
            this.level = level;
        }
    }

    /**
     * Opens the pocket around the bender and closes it behind them.
     *
     * @param radius how far the air reaches, and equally how far a block may drift
     *               from the bender before the water is allowed back
     */
    public static void update(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Sphere sphere = SPHERES.computeIfAbsent(player.getUUID(), id -> new Sphere(level));

        // Walking between dimensions with a pocket open would leave it in the old one.
        if (sphere.level != level) {
            collapse(player);
            sphere = SPHERES.computeIfAbsent(player.getUUID(), id -> new Sphere(level));
        }

        fillBehind(sphere, player, radius);

        // Opening the pocket outward is the expensive half — a thousand block lookups
        // — and it only has anything to do when the bender has moved. The periodic
        // rescan covers water arriving some other way: rain, another player, a chunk
        // loading in alongside.
        BlockPos here = player.blockPosition();
        sphere.ticksSinceScan++;

        if (here.equals(sphere.lastScan) && sphere.ticksSinceScan < RESCAN_INTERVAL) {
            return;
        }

        sphere.lastScan = here;
        sphere.ticksSinceScan = 0;
        emptyAround(sphere, player, level, radius);
    }

    /** Lets the water back into anything the bender has moved away from. */
    private static void fillBehind(Sphere sphere, ServerPlayer player, double radius) {
        double limitSqr = radius * radius;

        Iterator<BlockPos> held = sphere.emptied.iterator();
        while (held.hasNext()) {
            BlockPos pos = held.next();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= limitSqr) {
                continue;
            }

            restore(sphere.level, pos);
            held.remove();
        }
    }

    /** Pushes the water out of everything within reach. */
    private static void emptyAround(Sphere sphere, ServerPlayer player, ServerLevel level, double radius) {
        int reach = (int) Math.ceil(radius);
        BlockPos centre = player.blockPosition();
        double limitSqr = radius * radius;

        for (BlockPos pos : BlockPos.betweenClosed(
                centre.offset(-reach, -reach, -reach),
                centre.offset(reach, reach, reach))) {

            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > limitSqr) {
                continue;
            }
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;

            BlockPos immutable = pos.immutable();
            level.setBlock(immutable, Blocks.AIR.defaultBlockState(), QUIET);
            sphere.emptied.add(immutable);
        }
    }

    /** Fills the whole pocket back in and forgets it. */
    public static void collapse(ServerPlayer player) {
        Sphere sphere = SPHERES.remove(player.getUUID());
        if (sphere == null) return;

        for (BlockPos pos : sphere.emptied) {
            restore(sphere.level, pos);
        }
    }

    /**
     * Puts water back, but only where the pocket is still empty.
     *
     * A bender who builds inside their own air pocket should keep what they built,
     * rather than having it drowned the moment they walk away.
     */
    private static void restore(ServerLevel level, BlockPos pos) {
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir()) return;

        level.setBlock(pos, Blocks.WATER.defaultBlockState(), QUIET);
    }

    /** Fills in every pocket in a level that is going away. */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Map.Entry<UUID, Sphere>> entries = SPHERES.entrySet().iterator();
        while (entries.hasNext()) {
            Sphere sphere = entries.next().getValue();
            if (sphere.level != level) continue;

            for (BlockPos pos : sphere.emptied) {
                restore(sphere.level, pos);
            }
            entries.remove();
        }
    }
}
