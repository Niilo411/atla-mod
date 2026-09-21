package com.minecraft.atlamod.spirit.island;

/**
 * The six kinds of island, before it is decided which FLAVOUR of that kind.
 *
 * This layer exists so that adding variants does not quietly change how common a kind of
 * island is. An island picks its family first, one in six, and only then picks a variant
 * within it — so splitting the nether islands into four does not make nether islands four
 * times as likely, it makes each nether island one of four looks.
 *
 * Choosing straight from {@link IslandStyle} would do exactly that: with twelve styles in
 * one list, the four overworld variants alone would be a third of every island in the
 * dimension and the end would drop from a sixth to a twelfth.
 */
public enum IslandFamily {

    /** Ordinary land: plains, desert, snowy, forest. */
    OVERWORLD,

    /** Nether-mimic: wastes, crimson forest, warped forest, soul sand valley. */
    NETHER,

    /** End-mimic. One variant. */
    END,

    /** Grassy mountains with crimson creeping over them. One variant. */
    CRIMSON,

    /** Mangrove swamp with warped creeping over it. One variant. */
    WARPED,

    /** Dead black flats. One variant. */
    WASTELAND;

    /**
     * Every style belonging to this family, in a fixed order.
     *
     * Order matters: a variant is chosen by index from a hash, so reordering this would
     * silently change which island is where in every existing world.
     */
    public IslandStyle[] variants() {
        return IslandStyle.of(this);
    }
}
