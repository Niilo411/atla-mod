package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.lava.Lava;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lava a lavabender puts in the world.
 *
 * A block of our own rather than vanilla lava, and the reason is the single most
 * important thing about the whole element: REAL LAVA FLOWS. Placing a lava source
 * spawns flowing lava into every space beside and below it, and none of that flow is
 * anything a tracker knows about — so a "temporary" wall of real lava would leave a
 * permanent lava field behind it, burn down whatever was nearby, and be impossible to
 * take back. Tsunami's flooding note documents the same trap for water, where the
 * timing could just about be worked around; lava's spread delay is thirty ticks, and
 * nothing in this element is short enough to beat it.
 *
 * So this does not flow, does not spread, and does not schedule itself a tick. It sits
 * exactly where it is put until {@link com.minecraft.atlamod.abilities.lava.LavaWorks}
 * takes it away. Everything else about it is deliberately lava:
 *
 *  - no collision, so things fall in rather than standing on it
 *  - light level 15
 *  - it burns and hurts whatever is inside it, on vanilla's own lava terms
 *
 * It is UNBREAKABLE for the same reason {@link BendingMetalBlock} is: every ability
 * that places it is borrowing it and takes it back, and a bender who could mine their
 * own lava would have an infinite supply of it.
 *
 * It wears vanilla's still-lava texture on a plain cube, so it needs no art of its own
 * — and because that texture ships animated, the block animates for free.
 */
public class BendingLavaBlock extends Block {

    public BendingLavaBlock(Properties properties) {
        super(properties);
    }

    /**
     * Called every tick for anything whose box overlaps this block.
     *
     * The burning lives in {@link Lava} rather than here so that the abilities which
     * hurt things directly — the tsunami rolling over somebody, a rain drop landing on
     * them — burn on exactly the same terms as standing in the stuff does.
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        Lava.scorch(entity);
    }
}
