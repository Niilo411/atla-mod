package com.minecraft.atlamod.abilities.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Waves of lava rolling away from lavabenders.
 *
 * {@link com.minecraft.atlamod.abilities.water.Tsunamis} done in molten rock, and it
 * borrows the shape wholesale: the wave is a moving BODY a few slices deep, laid down at
 * the front and taken up at the back, so it travels rather than filling in the world
 * behind it. A wall of lava thirty blocks long that simply stayed would not be an
 * ability, it would be a map edit.
 *
 * The one rule it does NOT have to obey is the water wave's flooding invariant. That
 * whole comment — BODY_DEPTH times ADVANCE_EVERY must stay under five, or the wave
 * spawns real flowing water nothing is tracking — exists because vanilla water schedules
 * itself to spread five ticks after it is placed, and suppressing the neighbour update
 * does not suppress that. Our lava is not vanilla lava and schedules itself nothing, so
 * the body can be as deep and as slow as it likes. That is exactly why the block exists.
 *
 * The wave keeps its own slices rather than handing them to {@link LavaWorks}, for the
 * same reason the water one does: it takes them up on its own schedule as it moves, not
 * on a timer each block was given when it was laid.
 */
public final class LavaTsunamis {

    /** Placed and cleared with no neighbour notification. Cheap, and nothing needs one. */
    private static final int QUIET = Block.UPDATE_CLIENTS;

    /** Blocks moved per step. */
    private static final int SPEED = 1;

    /** Ticks between steps. Thirty blocks in three seconds — a wave, not a teleport. */
    private static final int ADVANCE_EVERY = 2;

    /** How many slices thick the wall is. Free to be deeper than water's — see above. */
    private static final int BODY_DEPTH = 3;

    /** Half the width, so the wave is nine blocks across. */
    private static final int HALF_WIDTH = 4;

    /** How tall it stands. */
    private static final int HEIGHT = 4;

    /** How far up and down each column looks for the ground to ride over. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private LavaTsunamis() {
    }

    private static final class Wave {
        final ServerLevel level;
        final UUID ownerId;
        final Vec3 origin;
        final Vec3 forward;
        final Vec3 across;
        final int range;
        final float damage;

        int front = 0;
        int age = 0;
        final Deque<List<BlockPos>> slices = new ArrayDeque<>();

        /** Each thing takes the bonus once, however many slices wash over it. */
        final Set<UUID> struck = new HashSet<>();

        Wave(ServerLevel level, UUID ownerId, Vec3 origin, Vec3 forward, int range, float damage) {
            this.level = level;
            this.ownerId = ownerId;
            this.origin = origin;
            this.forward = forward;
            this.across = new Vec3(-forward.z, 0.0, forward.x);
            this.range = range;
            this.damage = damage;
        }
    }

    /** Sends a wave rolling away from the bender. */
    public static void launch(ServerPlayer owner, Vec3 origin, Vec3 forward, int range, float damage) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        ACTIVE.add(new Wave(level, owner.getUUID(), origin, forward.normalize(), range, damage));
        Lava.roar(level, origin, 3.0F, 0.5F);
    }

    /** Advances every wave in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Wave> waves = ACTIVE.iterator();
        while (waves.hasNext()) {
            if (!advance(waves.next())) {
                waves.remove();
            }
        }
    }

    /** @return false once the wave has run its distance and been drained */
    private static boolean advance(Wave wave) {
        // The wall only moves every so many ticks, but it goes on hitting things every
        // tick — something walking into a wave that happens to be between steps should
        // still be caught by it.
        wave.age++;
        if (wave.age % ADVANCE_EVERY != 0) {
            strike(wave);
            return true;
        }

        wave.front += SPEED;

        if (wave.front > wave.range) {
            drain(wave);
            return false;
        }

        wave.slices.addLast(raiseSlice(wave, wave.front));

        // Take up the back of the wave so it travels instead of filling in behind.
        while (wave.slices.size() > BODY_DEPTH) {
            lowerSlice(wave, wave.slices.removeFirst());
        }

        strike(wave);
        return true;
    }

    /**
     * Lays one slice of the wall.
     *
     * Each column finds its own footing so the wave rides over the ground rather than
     * ploughing through it, and only air is ever replaced — a wave of lava that broke
     * blocks would leave nothing at all standing behind it.
     */
    private static List<BlockPos> raiseSlice(Wave wave, int distance) {
        List<BlockPos> placed = new ArrayList<>();
        BlockState lava = Lava.block();

        for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
            Vec3 spot = wave.origin
                    .add(wave.forward.scale(distance))
                    .add(wave.across.scale(side));

            BlockPos ground = Lava.footing(
                    wave.level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN);
            if (ground == null) continue;

            for (int h = 0; h < HEIGHT; h++) {
                BlockPos pos = ground.above(h);
                BlockState existing = wave.level.getBlockState(pos);

                if (!existing.isAir() && !existing.canBeReplaced()) break;
                if (!existing.getFluidState().isEmpty()) break;

                wave.level.setBlock(pos, lava, QUIET);
                placed.add(pos);
            }
        }

        return placed;
    }

    /** Takes a slice back out, leaving anything that is no longer our lava alone. */
    private static void lowerSlice(Wave wave, List<BlockPos> slice) {
        BlockState lava = Lava.block();

        for (BlockPos pos : slice) {
            if (!wave.level.getBlockState(pos).equals(lava)) continue;
            wave.level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        }
    }

    /** Drops the whole wave at once, when it has run out of distance. */
    private static void drain(Wave wave) {
        for (List<BlockPos> slice : wave.slices) {
            lowerSlice(wave, slice);
        }
        wave.slices.clear();
    }

    /** Hits whatever the front of the wave has reached. */
    private static void strike(Wave wave) {
        Vec3 front = wave.origin.add(wave.forward.scale(wave.front));
        AABB reach = new AABB(front, front).inflate(HALF_WIDTH + 1.0, HEIGHT, HALF_WIDTH + 1.0);

        ServerPlayer owner = wave.level.getServer().getPlayerList().getPlayer(wave.ownerId);

        for (Entity target : wave.level.getEntities(owner, reach)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!wave.struck.add(living.getUUID())) continue;

            if (owner != null) {
                // The design's "bonus" damage: the lava itself does its own work through
                // the block, and this is what the wave adds on top for being hit by it.
                living.hurt(owner.damageSources().indirectMagic(owner, owner), wave.damage);
            }
            Lava.scorch(living);

            // Carried along with the wave rather than merely hurt by it.
            Vec3 push = wave.forward.scale(0.9);
            living.setDeltaMovement(push.x, 0.4, push.z);
            living.hurtMarked = true;
        }

        Lava.spatter(wave.level, front.add(0.0, 2.0, 0.0), 30, HALF_WIDTH * 0.6);
    }

    /**
     * Drops every wave in a level that is going away, taking its lava with it.
     *
     * These live in a plain static list, so nothing else would ever clear them — and
     * lava left behind by a half-finished wave would simply stay in the world, in an
     * unbreakable block nothing could remove.
     */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Wave> waves = ACTIVE.iterator();
        while (waves.hasNext()) {
            Wave wave = waves.next();
            if (wave.level != level) continue;

            drain(wave);
            waves.remove();
        }
    }
}
