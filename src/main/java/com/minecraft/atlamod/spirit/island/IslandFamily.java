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
    OVERWORLD("Ordinary land", "Plains, desert, snowy and forest islands."),

    /** Nether-mimic: wastes, crimson forest, warped forest, soul sand valley. */
    NETHER("Nether", "Nether wastes, crimson forest, warped forest and soul sand valley."),

    /** End-mimic. One variant. */
    END("End", "End stone islands with chorus flowers."),

    /** Grassy mountains with crimson creeping over them. One variant. */
    CRIMSON("Crimson mountains", "Grassy mountains with a crimson infection spreading over them."),

    /** Mangrove swamp with warped creeping over it. One variant. */
    WARPED("Warped swamp", "Mangrove swamp with a warped infection spreading over it."),

    /** Dead black flats. One variant. */
    WASTELAND("Wasteland", "Dead black flats where nothing grows.");

    private final String displayName;
    private final String description;

    IslandFamily(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** What the settings screen calls this family. */
    public String displayName() {
        return displayName;
    }

    /** One line on what this family looks like, for the config file and the screen's tooltip. */
    public String description() {
        return description;
    }

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
