package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every block of bent metal in the world, and what was there before it.
 *
 * The third of the mod's block-lending classes, after EarthWorks and IceWorks, and it
 * differs from both in one way that matters: it does NOT only fill air. Metal is laid
 * OVER whatever is there — the scroll's floor covers the ground the reader is standing
 * on — so this remembers the block it replaced and puts that exact block back, rather
 * than leaving a hole.
 *
 * That makes it EarthWorks.openFor's shape rather than IceWorks': what goes back is
 * what was there, so the world afterwards is exactly as it was.
 */
public final class MetalWorks {

    private static final List<Laid> LAID = new ArrayList<>();

    private MetalWorks() {
    }

    private static final class Laid {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState ours;
        final BlockState was;
        int ticksLeft;

        Laid(ServerLevel level, BlockPos pos, BlockState ours, BlockState was, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ours = ours;
            this.was = was;
            this.ticksLeft = ticksLeft;
        }
    }

    /** The block metalbending places: unbreakable, and always taken back. */
    public static BlockState metal() {
        return Atlamod.BENDING_METAL.get().defaultBlockState();
    }

    /**
     * Lays one block of metal over whatever is there, for a while.
     *
     * @return false if the space could not be taken
     */
    public static boolean lay(ServerLevel level, BlockPos pos, int ticks) {
        BlockPos at = pos.immutable();
        BlockState was = level.getBlockState(at);

        // Never over another ability's metal: the second one's timer would put the
        // FIRST one's block back as "the original", and the ground would slowly turn
        // to iron a cast at a time.
        if (was.is(Atlamod.BENDING_METAL.get())) return false;

        // Bedrock and its friends are not the bender's to cover, and a block entity
        // would lose its contents the moment it was overwritten.
        if (was.getDestroySpeed(level, at) < 0.0F) return false;
        if (was.hasBlockEntity()) return false;

        // Fluids are skipped: restoring one as a block later would leave water or lava
        // hanging in the air, and covering a lake would let a bender drain it.
        if (!was.getFluidState().isEmpty()) return false;

        level.setBlockAndUpdate(at, metal());
        LAID.add(new Laid(level, at, metal(), was, ticks));
        return true;
    }

    /** Advances every block on a timer. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (LAID.isEmpty()) return;

        Iterator<Laid> blocks = LAID.iterator();
        while (blocks.hasNext()) {
            Laid block = blocks.next();
            if (block.ticksLeft-- > 0) continue;

            restore(block);
            blocks.remove();
        }
    }

    /**
     * Puts the original block back, but ONLY if our metal is still standing there.
     *
     * Somebody may have had another ability replace it in the meantime, and putting
     * the old block over whatever occupies the space now would be exactly the griefing
     * these classes exist to prevent. The metal itself cannot be mined — it is
     * unbreakable — so in practice this only fires when another ability got there.
     */
    private static void restore(Laid block) {
        if (!block.level.getBlockState(block.pos).is(Atlamod.BENDING_METAL.get())) return;

        block.level.setBlockAndUpdate(block.pos, block.was);
        Metal.spark(block.level, net.minecraft.world.phys.Vec3.atCenterOf(block.pos), 3, 0.25);
    }

    /** Takes a set of laid blocks back ahead of their timer. */
    public static void restoreNow(ServerLevel level, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            Iterator<Laid> blocks = LAID.iterator();
            while (blocks.hasNext()) {
                Laid block = blocks.next();
                if (block.level != level || !block.pos.equals(pos)) continue;

                restore(block);
                blocks.remove();
            }
        }
    }

    /**
     * Settles everything in a level that is going away.
     *
     * Restored rather than dropped, so a floor of unbreakable metal cannot be made
     * permanent by leaving the dimension while it is down — which would matter far
     * more here than for ice or earth, since nothing in the game could remove it.
     */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Laid> blocks = LAID.iterator();
        while (blocks.hasNext()) {
            Laid block = blocks.next();
            if (block.level != level) continue;

            restore(block);
            blocks.remove();
        }
    }
}
