package com.minecraft.atlamod.abilities.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Every block of lava an ability has poured, and when it cools.
 *
 * Lavabending's counterpart to {@link com.minecraft.atlamod.abilities.ice.IceWorks}
 * and {@link com.minecraft.atlamod.abilities.earth.EarthWorks}, and it keeps the same
 * rule they both do: an ability may only ever fill AIR, and whatever it fills it takes
 * back afterwards. Between those two, no lava ability can destroy anything or leave
 * anything behind.
 *
 * That rule matters more here than anywhere else in the mod. Lava is the one material
 * a bender could use to erase a build outright, and an element that laid permanent lava
 * eight abilities' worth of casts at a time would be unplayable near anything anyone
 * cared about. The one deliberate exception is Lava throw, which the design calls
 * "permanent" in so many words — that ability does NOT come through here, and places
 * real vanilla lava instead.
 *
 * What it does NOT share with IceWorks is the material question, because there isn't
 * one: ice has to be PACKED ice or it melts into water and floods a build, where our
 * lava is a block of our own that does nothing on its own at all. See BendingLavaBlock
 * for why it could not be real lava.
 */
public final class LavaWorks {

    private static final List<Poured> POURED = new ArrayList<>();

    private LavaWorks() {
    }

    private static final class Poured {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState ours;
        int ticksLeft;

        Poured(ServerLevel level, BlockPos pos, BlockState ours, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ours = ours;
            this.ticksLeft = ticksLeft;
        }
    }

    /**
     * Fills one block with lava for a while, then takes it away again.
     *
     * @return false if the space was not free, in which case nothing was changed
     */
    public static boolean pour(ServerLevel level, BlockPos pos, int ticks) {
        BlockPos at = pos.immutable();

        // Only ever into air or something already flagged replaceable (tall grass,
        // snow layers). Anything else belongs to somebody.
        BlockState existing = level.getBlockState(at);
        if (!existing.isAir() && !existing.canBeReplaced()) return false;

        // Fluids are left alone. Pouring over water would restore a block of water
        // hanging in the air once the lava went, and a bender could drain a lake a
        // cast at a time — the same reason IceWorks refuses them.
        if (!existing.getFluidState().isEmpty()) return false;

        BlockState lava = Lava.block();
        level.setBlockAndUpdate(at, lava);
        POURED.add(new Poured(level, at, lava, ticks));
        return true;
    }

    /** Advances every block on a timer. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (POURED.isEmpty()) return;

        Iterator<Poured> blocks = POURED.iterator();
        while (blocks.hasNext()) {
            Poured block = blocks.next();
            if (block.ticksLeft-- > 0) continue;

            cool(block);
            blocks.remove();
        }
    }

    /**
     * Takes one block back, but ONLY if it is still the block we put there.
     *
     * Another ability may have replaced it in the meantime, and removing whatever
     * occupies the space now would be exactly the griefing the air-only rule exists to
     * prevent. Mining it is not one of the ways this can happen — our lava block is
     * unbreakable — but the check costs nothing and the invariant is worth keeping the
     * same across all three of the works classes.
     */
    private static void cool(Poured block) {
        if (!block.level.getBlockState(block.pos).equals(block.ours)) return;

        block.level.setBlockAndUpdate(block.pos, Blocks.AIR.defaultBlockState());

        // Cooling is drawn as smoke rather than as more lava: it is the one moment in
        // the element where something is going away rather than arriving.
        block.level.sendParticles(ParticleTypes.SMOKE,
                block.pos.getX() + 0.5, block.pos.getY() + 0.6, block.pos.getZ() + 0.5,
                3, 0.25, 0.1, 0.25, 0.01);
    }

    /** Cools everything an ability poured at a set of positions, ahead of its timer. */
    public static void coolNow(ServerLevel level, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            Iterator<Poured> blocks = POURED.iterator();
            while (blocks.hasNext()) {
                Poured block = blocks.next();
                if (block.level != level || !block.pos.equals(pos)) continue;

                cool(block);
                blocks.remove();
            }
        }
    }

    /**
     * Settles everything in a level that is going away, rather than dropping it.
     *
     * Cooling on the way out means a field of lava cannot be made permanent by the
     * simple trick of leaving the dimension while it is down — the same reason
     * EarthWorks and IceWorks settle their own timers on unload, and rather more
     * pressing here given what the block is.
     */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Poured> blocks = POURED.iterator();
        while (blocks.hasNext()) {
            Poured block = blocks.next();
            if (block.level != level) continue;

            cool(block);
            blocks.remove();
        }
    }

    /** Where a pour landed, for the abilities that want to draw it. */
    public static void splash(ServerLevel level, BlockPos pos) {
        Lava.spatter(level, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0), 4, 0.3);
    }
}
