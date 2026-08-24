package com.minecraft.atlamod.abilities.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
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
 * Waves rolling away from waterbenders.
 *
 * Water Sphere in reverse, and it borrows the same load-bearing trick: blocks are
 * placed and cleared WITHOUT neighbour updates. Dropping a wall of water into the
 * world the ordinary way would have every block of it immediately try to flow, and
 * a wave that spread on its own would flood whatever it crossed and never leave.
 * Suppressing the update means the water sits exactly where it is put and goes when
 * it is taken away.
 *
 * The wave is a moving body rather than a growing one: a few slices deep, laid down
 * at the front and taken up at the back, so it travels rather than filling the world
 * behind it.
 */
public final class Tsunamis {

    /** Placed and cleared with no neighbour notification, so the wave never spreads. */
    private static final int QUIET = Block.UPDATE_CLIENTS;

    /** Blocks moved per step. */
    private static final int SPEED = 1;

    /**
     * Ticks between steps. A wave that advanced every tick crossed its twenty blocks
     * in a second, which read as a wall being teleported rather than as water moving.
     */
    private static final int ADVANCE_EVERY = 2;

    /**
     * How many slices thick the wall of water is.
     *
     * CAREFUL: this is tied to ADVANCE_EVERY by vanilla's own timing. Placing a water
     * block schedules it to spread five ticks later, and suppressing the neighbour
     * update does NOT suppress that — the block schedules its own tick. A slice is
     * only safe while it is taken up again before that tick comes round, so
     *
     *     BODY_DEPTH * ADVANCE_EVERY  must stay UNDER 5.
     *
     * Break the rule and the wave starts flooding: it spawns real flowing water that
     * nothing is tracking, so it is still there long after the wave has gone. That is
     * exactly what happened when the wave was slowed to a step every two ticks while
     * still four slices deep — eight ticks of life against a five tick fuse.
     */
    private static final int BODY_DEPTH = 2;

    /** Half the width, so the wave is nine blocks across. */
    private static final int HALF_WIDTH = 4;

    /** How tall it stands. */
    private static final int HEIGHT = 4;

    /** How far up and down each column looks for the ground to ride over. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    private static final List<Wave> ACTIVE = new ArrayList<>();

    private Tsunamis() {
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

        /** Each thing is hit once, however many slices wash over it. */
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

        level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS, 2.0F, 0.5F);
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
     * ploughing through it, and only air is ever replaced — a wave that broke blocks
     * would be a good deal more destructive than intended.
     */
    private static List<BlockPos> raiseSlice(Wave wave, int distance) {
        List<BlockPos> placed = new ArrayList<>();
        BlockState water = Blocks.WATER.defaultBlockState();

        for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
            Vec3 spot = wave.origin
                    .add(wave.forward.scale(distance))
                    .add(wave.across.scale(side));

            BlockPos ground = findFooting(wave.level, BlockPos.containing(spot));
            if (ground == null) continue;

            for (int h = 0; h < HEIGHT; h++) {
                BlockPos pos = ground.above(h);
                BlockState existing = wave.level.getBlockState(pos);

                if (!existing.isAir() && !existing.canBeReplaced()) break;
                if (wave.level.getFluidState(pos).is(FluidTags.WATER)) continue;

                wave.level.setBlock(pos, water, QUIET);
                placed.add(pos);
            }
        }

        return placed;
    }

    /** Takes a slice back out, leaving anything that is no longer our water alone. */
    private static void lowerSlice(Wave wave, List<BlockPos> slice) {
        for (BlockPos pos : slice) {
            if (!wave.level.getFluidState(pos).is(FluidTags.WATER)) continue;
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
                living.hurt(owner.damageSources().indirectMagic(owner, owner), wave.damage);
            }

            // Carried along with the wave rather than merely hurt by it.
            Vec3 push = wave.forward.scale(0.9);
            living.setDeltaMovement(push.x, 0.45, push.z);
            living.hurtMarked = true;
        }

        wave.level.sendParticles(ParticleTypes.SPLASH,
                front.x, front.y + 1.5, front.z, 60, HALF_WIDTH, HEIGHT * 0.5, 1.0, 0.15);
    }

    /** The first air space with solid ground under it, near the wave's own height. */
    private static BlockPos findFooting(ServerLevel level, BlockPos target) {
        for (int dy = UP_SCAN; dy >= -DOWN_SCAN; dy--) {
            BlockPos pos = target.above(dy);
            BlockState here = level.getBlockState(pos);

            boolean open = here.isAir() || here.canBeReplaced();
            if (open && level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Drops every wave in a level that is going away, taking its water with it.
     *
     * These live in a plain static list, so nothing else would ever clear them — and
     * water left behind by a half-finished wave would simply stay in the world.
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
