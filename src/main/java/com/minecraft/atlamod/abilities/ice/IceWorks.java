package com.minecraft.atlamod.abilities.ice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every block of ice an ability has put in the world, and when it comes back out.
 *
 * Icebending's counterpart to {@link com.minecraft.atlamod.abilities.earth.EarthWorks},
 * and it keeps the same rule, which matters as much here as it does for earth: an
 * ability may only ever fill AIR, and whatever it fills it takes back afterwards.
 * Between those two, no ice ability can destroy anything or leave anything behind —
 * without which Ice over would pave a permanent 15x15 rink every fifty seconds and
 * Ice sphere would be an infinite block supply.
 *
 * What it deliberately does NOT share with EarthWorks is the sliding. Earth is heavy
 * and rises out of the ground, so it moves as a real FallingBlockEntity; ice simply
 * forms where the cold is, so it is placed outright and the frost particles carry the
 * moment instead.
 *
 * One material rule worth knowing: everything structural uses PACKED ice, never plain
 * ice. Plain ice MELTS on a random tick near any light source and leaves WATER behind
 * — at which point this class sees a block that is not the one it placed, correctly
 * refuses to remove it, and the ability has silently flooded somebody's build. Packed
 * ice never melts, and is just as slippery. Snow is safe for the same reason in
 * reverse: it melts to AIR, which is where it was going anyway.
 */
public final class IceWorks {

    private static final List<Placed> PLACED = new ArrayList<>();

    private IceWorks() {
    }

    private static final class Placed {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState ours;

        /**
         * What was here before, or null if the space was empty.
         *
         * Null is the ordinary case and means "take the ice away and leave air", which
         * is all {@link #freeze} ever needs. Ice over is the one that fills this in:
         * it goes OVER the ground rather than on top of it, so it has something to
         * give back.
         */
        final BlockState was;

        int ticksLeft;

        Placed(ServerLevel level, BlockPos pos, BlockState ours, BlockState was, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ours = ours;
            this.was = was;
            this.ticksLeft = ticksLeft;
        }
    }

    /**
     * Freezes one block into place for a while, then takes it away again.
     *
     * @return false if the space was not free, in which case nothing was changed
     */
    public static boolean freeze(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockPos at = pos.immutable();

        // Only ever into air or something already flagged replaceable (tall grass,
        // snow layers). Anything else belongs to somebody.
        BlockState existing = level.getBlockState(at);
        if (!existing.isAir() && !existing.canBeReplaced()) return false;

        // Fluids are left alone. Freezing over water sounds right, but the block we
        // replaced would be restored as water hanging in the air once the ice went,
        // and a bender could drain a lake by icing it repeatedly.
        if (!existing.getFluidState().isEmpty()) return false;

        level.setBlockAndUpdate(at, state);
        PLACED.add(new Placed(level, at, state, null, ticks));
        return true;
    }

    /**
     * Freezes a block that is already THERE, and gives that exact block back later.
     *
     * The other half of this class, and the one that breaks its own air-only rule — on
     * purpose, and only for Ice over. Laying a sheet on TOP of the ground raises the
     * floor by a block, which is enough to trip anyone walking across it and enough to
     * seal a doorway; freezing the ground itself is what "the ground goes over" actually
     * means, and it leaves the world exactly as tall as it was.
     *
     * The rule that replaces the air-only one is that the original block is REMEMBERED
     * and restored, which is MetalWorks' shape rather than this class's usual one. The
     * refusals below are what keep that promise honest.
     *
     * @return false if this block was not the bender's to freeze
     */
    public static boolean freezeOver(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
        BlockPos at = pos.immutable();
        BlockState was = level.getBlockState(at);

        // Never over another ability's ice: the second one's timer would put the FIRST
        // one's ice back as "the original", and the ground would turn to ice a cast at
        // a time. The same trap MetalWorks documents.
        if (was.equals(state)) return false;

        // Bedrock and its friends are not the bender's to cover, and a block entity
        // would lose its contents the moment it was overwritten.
        if (was.getDestroySpeed(level, at) < 0.0F) return false;
        if (was.hasBlockEntity()) return false;

        // Fluids are skipped: restoring one as a block later would leave water hanging
        // in the air, and freezing over a lake would let a bender drain it.
        if (!was.getFluidState().isEmpty()) return false;

        level.setBlockAndUpdate(at, state);
        PLACED.add(new Placed(level, at, state, was, ticks));
        return true;
    }

    /** Advances every block on a timer. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (PLACED.isEmpty()) return;

        Iterator<Placed> blocks = PLACED.iterator();
        while (blocks.hasNext()) {
            Placed block = blocks.next();
            if (block.ticksLeft-- > 0) continue;

            melt(block);
            blocks.remove();
        }
    }

    /**
     * Takes one block back, but ONLY if it is still the block we put there.
     *
     * Somebody may have mined it, built over it, or had another ability replace it in
     * the meantime, and removing whatever occupies the space now would be exactly the
     * griefing the air-only rule exists to prevent.
     */
    private static void melt(Placed block) {
        if (!block.level.getBlockState(block.pos).equals(block.ours)) return;

        // Ice laid into empty space leaves empty space; ice laid OVER something gives
        // that exact something back. Which of the two this is was decided when it went
        // down, not now — see Placed.was.
        block.level.setBlockAndUpdate(block.pos, block.was != null
                ? block.was
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        Ice.frost(block.level, net.minecraft.world.phys.Vec3.atCenterOf(block.pos), 3, 0.25);
    }

    /** Melts everything an ability placed at a set of positions, ahead of its timer. */
    public static void meltNow(ServerLevel level, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            Iterator<Placed> blocks = PLACED.iterator();
            while (blocks.hasNext()) {
                Placed block = blocks.next();
                if (block.level != level || !block.pos.equals(pos)) continue;

                melt(block);
                blocks.remove();
            }
        }
    }

    /**
     * Settles everything in a level that is going away, rather than dropping it.
     *
     * Melting on the way out means a sheet of ice cannot be made permanent by the
     * simple trick of leaving the dimension while it is down — the same reason
     * EarthWorks settles its own timers on unload.
     */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Placed> blocks = PLACED.iterator();
        while (blocks.hasNext()) {
            Placed block = blocks.next();
            if (block.level != level) continue;

            melt(block);
            blocks.remove();
        }
    }
}
