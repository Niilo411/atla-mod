package com.minecraft.atlamod.abilities.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Earthbending's shared block handling: what earth is made of, where the ground is,
 * and how a block slides into or out of place instead of simply appearing.
 *
 * The rule every earth ability follows lives here — an ability may only ever fill
 * AIR, and whatever it fills it takes back afterwards. Between those two, no earth
 * ability can destroy anything or leave anything behind, which matters far more for
 * earth than for the other elements: a wall that simply stayed would be an infinite
 * block supply and would litter the world with every cast.
 *
 * The sliding is done with FallingBlockEntity, the same trick HeldBlocks uses — a
 * real entity so the block is genuinely visible in motion, with gravity off and its
 * own timer pinned so it never drops itself as an item. The real block is only set
 * when the slide finishes, so a block is never in two places at once.
 */
public final class EarthWorks {

    /** How long a block takes to slide one block's distance, in ticks. */
    public static final int SLIDE_TICKS = 8;

    private static final List<Mote> MOVING = new ArrayList<>();

    private EarthWorks() {
    }

    /** One block mid-slide. */
    private static final class Mote {
        final ServerLevel level;
        final FallingBlockEntity entity;
        final double step;
        int ticksLeft;

        /** Where to set the block when the slide finishes, or null to just vanish. */
        @Nullable
        final BlockPos landAt;
        final BlockState state;

        Mote(ServerLevel level, FallingBlockEntity entity, double step, int ticksLeft,
             @Nullable BlockPos landAt, BlockState state) {
            this.level = level;
            this.entity = entity;
            this.step = step;
            this.ticksLeft = ticksLeft;
            this.landAt = landAt;
            this.state = state;
        }
    }

    /**
     * Slides a block of earth up out of the ground into {@code target}.
     *
     * The block is not set until the slide lands, so for those few ticks the space is
     * still air and nothing is standing on half a block.
     *
     * @return false if the space was already occupied
     */
    public static boolean riseInto(ServerLevel level, BlockPos target, BlockState state) {
        return riseInto(level, target, state, SLIDE_TICKS);
    }

    /**
     * Slides a block up over a chosen number of ticks. Earth spike uses a short one:
     * a spike that eased into place the way a wall does would be trivially stepped
     * away from.
     */
    public static boolean riseInto(ServerLevel level, BlockPos target, BlockState state, int slideTicks) {
        if (!level.getBlockState(target).isAir()) return false;

        BlockPos landing = target.immutable();

        // Anything without a model to render — and there should not be any here — is
        // simply set, since an invisible slide would just look like a delay.
        if (state.getRenderShape() != RenderShape.MODEL) {
            level.setBlockAndUpdate(landing, state);
            return true;
        }

        FallingBlockEntity mover = FallingBlockEntity.fall(level, landing, state);
        park(mover);
        mover.setPos(landing.getX() + 0.5, landing.getY() - 1.0, landing.getZ() + 0.5);

        MOVING.add(new Mote(level, mover, 1.0 / slideTicks, slideTicks, landing, state));
        return true;
    }

    /**
     * Slides a block back down into the ground and away.
     *
     * Only touches it if it is still the block that was put there — someone may have
     * mined it, built over it, or had another ability replace it, and taking away
     * whatever occupies the space now would be exactly the griefing the air-only rule
     * exists to prevent.
     */
    public static void sinkFrom(ServerLevel level, BlockPos pos, BlockState placed) {
        if (!level.getBlockState(pos).equals(placed)) return;

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);

        if (placed.getRenderShape() != RenderShape.MODEL) {
            level.removeBlock(pos, false);
            return;
        }

        // fall() takes the block out of the world and hands back the entity standing
        // where it was, which is exactly the swap wanted here.
        FallingBlockEntity mover = FallingBlockEntity.fall(level, pos, placed);
        park(mover);

        MOVING.add(new Mote(level, mover, -1.0 / SLIDE_TICKS, SLIDE_TICKS, null, placed));
    }

    /** Stops an entity being a falling block in every sense except how it looks. */
    private static void park(FallingBlockEntity mover) {
        mover.setNoGravity(true);
        mover.setDeltaMovement(Vec3.ZERO);
        // Its own timer would drop it as an item or try to place it; held at zero so
        // only this class decides where it ends up.
        mover.time = 0;
        // EntityType.FALLING_BLOCK is registered with updateInterval(20) — its position
        // is only broadcast once a second. Without this a slide is never actually seen:
        // the block simply appears at the end of it. ServerEntity clears the flag after
        // each send, so it has to be set every tick.
        mover.hasImpulse = true;
    }

    /**
     * Raises a block and takes it back down again on its own after {@code standTicks}.
     *
     * For earth that is placed and forgotten rather than held — Earth spike, and
     * whatever else wants a shape that stands for a moment and goes. Earth wall and
     * pillar do NOT use this: their standing is one part of a longer life that
     * EarthWalls has to own anyway.
     *
     * @return false if the space was already occupied
     */
    public static boolean raiseFor(ServerLevel level, BlockPos target, BlockState state,
                                   int standTicks, int slideTicks) {
        if (!riseInto(level, target, state, slideTicks)) return false;

        // Counted from the moment it starts moving, so a whole spike sinks together
        // rather than each block going at its own slightly different time.
        STANDING.add(new Standing(level, target.immutable(), state, false, standTicks + slideTicks));
        return true;
    }

    /**
     * Takes a block out of the world and puts it back after {@code openTicks}.
     *
     * The inverse of raiseFor, and Earth sink's whole trick: the ground opens, whatever
     * was standing on it falls in, and then the ground comes back over the top. What
     * closes is the block that was there, so the world is left exactly as it was —
     * except for whoever is now inside it.
     *
     * @return false if there was nothing there to take
     */
    public static boolean openFor(ServerLevel level, BlockPos pos, int openTicks) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;

        // Fluids would only pour into the hole and then be "restored" as a block of
        // water hanging in the air. Left alone.
        if (!state.getFluidState().isEmpty()) return false;

        // Bedrock and its friends are not the bender's to move.
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;

        level.destroyBlock(pos, false);
        STANDING.add(new Standing(level, pos.immutable(), state, true, openTicks));
        return true;
    }

    /**
     * One block on a timer, in whichever direction.
     *
     * {@code restore} is what tells the two apart: earth that was RAISED sinks away
     * when its time is up, and earth that was TAKEN comes back. Earth sink is the only
     * user of the second, and it is the whole ability — a pit that closes over whoever
     * fell into it.
     */
    private static final class Standing {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState state;
        final boolean restore;
        int ticksLeft;

        Standing(ServerLevel level, BlockPos pos, BlockState state, boolean restore, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.restore = restore;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final List<Standing> STANDING = new ArrayList<>();

    /** Advances every sliding block. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        tickStanding();

        if (MOVING.isEmpty()) return;

        Iterator<Mote> motes = MOVING.iterator();
        while (motes.hasNext()) {
            Mote mote = motes.next();

            if (!mote.entity.isAlive()) {
                motes.remove();
                continue;
            }

            park(mote.entity);
            mote.entity.setPos(
                    mote.entity.getX(), mote.entity.getY() + mote.step, mote.entity.getZ());

            if (--mote.ticksLeft > 0) continue;

            mote.entity.discard();
            if (mote.landAt != null && mote.level.getBlockState(mote.landAt).isAir()) {
                mote.level.setBlockAndUpdate(mote.landAt, mote.state);
            }
            motes.remove();
        }
    }

    /** Settles anything whose time is up, in whichever direction it was waiting. */
    private static void tickStanding() {
        if (STANDING.isEmpty()) return;

        Iterator<Standing> standing = STANDING.iterator();
        while (standing.hasNext()) {
            Standing block = standing.next();
            if (block.ticksLeft-- > 0) continue;

            if (block.restore) {
                // Only into empty space. Somebody may have built here while the hole
                // was open, and closing over their work would be the griefing this
                // class exists to avoid — an entity standing there is a different
                // matter, and is the entire point of Earth sink.
                if (block.level.getBlockState(block.pos).isAir()) {
                    block.level.setBlockAndUpdate(block.pos, block.state);
                }
            } else {
                sinkFrom(block.level, block.pos, block.state);
            }

            standing.remove();
        }
    }

    /**
     * What a column of earth should be made of, given the ground it is drawn from.
     *
     * Mirrors the surface so a wall out of a hillside looks like the hillside, rather
     * than every ability everywhere producing the same brown blocks. Two exceptions:
     * anything that FALLS is swapped for dirt, since a wall of sand would collapse the
     * moment it went up, and anything that is not plain diggable ground falls back to
     * dirt as well rather than duplicating whatever a player happened to be standing on.
     */
    public static BlockState materialFor(ServerLevel level, BlockPos ground) {
        BlockState state = level.getBlockState(ground);

        if (state.getBlock() instanceof FallingBlock) return Blocks.DIRT.defaultBlockState();
        if (!state.isCollisionShapeFullBlock(level, ground)) return Blocks.DIRT.defaultBlockState();

        boolean diggable = state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE);

        return diggable ? state : Blocks.DIRT.defaultBlockState();
    }

    /** The block a raised column should be made of, for a surface found above ground. */
    public static BlockState materialUnder(ServerLevel level, BlockPos surface) {
        return materialFor(level, surface.below());
    }

    /** The first free space standing on solid ground near {@code from}, or null. */
    @Nullable
    public static BlockPos surfaceUnder(ServerLevel level, BlockPos from, int upScan, int downScan) {
        for (int dy = upScan; dy >= -downScan; dy--) {
            BlockPos pos = from.above(dy);

            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.below()).isSolid()) continue;

            return pos;
        }
        return null;
    }

    /**
     * Drops every sliding block in a level that is going away, landing each one first
     * so nothing is lost in transit.
     */
    public static void forgetLevel(ServerLevel level) {
        MOVING.removeIf(mote -> {
            if (mote.level != level) return false;

            if (mote.entity.isAlive()) mote.entity.discard();
            if (mote.landAt != null && level.getBlockState(mote.landAt).isAir()) {
                level.setBlockAndUpdate(mote.landAt, mote.state);
            }
            return true;
        });

        // Anything mid-timer is settled now rather than left behind in a level nothing
        // is watching any more — raised earth taken away, opened ground closed up. An
        // unloading level must not be the one way to keep a hole for good.
        STANDING.removeIf(block -> {
            if (block.level != level) return false;

            if (block.restore) {
                if (level.getBlockState(block.pos).isAir()) {
                    level.setBlockAndUpdate(block.pos, block.state);
                }
            } else if (level.getBlockState(block.pos).equals(block.state)) {
                level.removeBlock(block.pos, false);
            }
            return true;
        });
    }
}
