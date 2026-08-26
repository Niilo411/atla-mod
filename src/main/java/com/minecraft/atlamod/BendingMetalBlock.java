package com.minecraft.atlamod;

import net.minecraft.world.level.block.Block;

/**
 * The metal a metalbender puts in the world.
 *
 * A block of our own rather than vanilla iron, for one reason: it has to be
 * UNBREAKABLE. Every metal ability that places blocks is borrowing them for a few
 * seconds and takes them back afterwards, and a bender who could mine their own
 * shield would have an infinite iron supply -- which is the same argument earthbending
 * makes for never leaving raised ground behind.
 *
 * Negative destroy speed and an enormous blast resistance is how vanilla makes
 * bedrock unbreakable, and it is what this borrows: no tool touches it, and no
 * explosion moves it. MetalWorks is the only thing that can take one away.
 *
 * It wears vanilla's iron block texture, so it needs no art of its own.
 */
public class BendingMetalBlock extends Block {

    public BendingMetalBlock(Properties properties) {
        super(properties);
    }
}
