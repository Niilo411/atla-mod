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
        int ticksLeft;

        Placed(ServerLevel level, BlockPos pos, BlockState ours, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ours = ours;
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
        PLACED.add(new Placed(level, at, state, ticks));
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

        block.level.setBlockAndUpdate(block.pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
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
