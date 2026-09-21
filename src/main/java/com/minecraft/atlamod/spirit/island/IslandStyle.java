package com.minecraft.atlamod.spirit.island;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The twelve looks a spirit island can have, and the biome each one reports.
 *
 * Each carries a palette of three blocks, a scatter of things on top, and two numbers that
 * decide the terrain's shape: {@link #relief} is how many blocks the surface rises and
 * falls by, and {@link #reliefScale} is how far apart those changes happen. A big relief
 * over a short scale is jagged; a small relief over a long scale is nearly flat. Those two
 * are what make CRIMSON read as mountains and WASTELAND as a plain, off the same code.
 *
 * EVERY STYLE OWNS ITS BIOME KEY. That is deliberate: the biome decides fog, grass colour,
 * which sky is drawn overhead and which animals live there, and all of those have to agree
 * with the blocks underfoot. Keeping the key on the style means the palette and the biome
 * are declared in the same place and cannot be given different answers by two files.
 *
 * VARIANTS ARE GROUPED BY {@link IslandFamily}, which is chosen first — see that class for
 * why. A style is never picked straight out of this list.
 *
 * DECORATION MAY SPILL A BLOCK OR TWO into the neighbouring chunk, and that is allowed:
 * during feature generation the world is a region covering the chunk and its neighbours,
 * which is exactly how vanilla's own trees reach past their chunk. Nothing here reaches
 * further than a small canopy. Anything added later that reaches further than about two
 * blocks would be breaking the rule the island terrain follows strictly.
 */
public enum IslandStyle {

    // ------------------------------------------------------------------
    // OVERWORLD — ordinary land, four climates
    // ------------------------------------------------------------------

    /** Open grassland with the odd oak. */
    PLAINS(IslandFamily.OVERWORLD, "spirit_plains",
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, 5, 22.0),

    /** Sand over sandstone, cactus and dead bush. */
    DESERT(IslandFamily.OVERWORLD, "spirit_desert",
            Blocks.SAND, Blocks.SAND, Blocks.SANDSTONE, 6, 26.0),

    /** Snow over frozen ground, with spruce. */
    SNOWY(IslandFamily.OVERWORLD, "spirit_snowy",
            Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.STONE, 7, 20.0),

    /** Thick oak and birch woodland. */
    FOREST(IslandFamily.OVERWORLD, "spirit_forest",
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, 6, 18.0),

    // ------------------------------------------------------------------
    // NETHER — four of the Nether's own biomes
    // ------------------------------------------------------------------

    /** Plain netherrack, glowstone and soul sand. */
    NETHER_WASTES(IslandFamily.NETHER, "spirit_nether_wastes",
            Blocks.NETHERRACK, Blocks.NETHERRACK, Blocks.NETHERRACK, 7, 18.0),

    /** Red fungal forest: crimson stems and shroomlight. */
    CRIMSON_FOREST(IslandFamily.NETHER, "spirit_crimson_forest",
            Blocks.CRIMSON_NYLIUM, Blocks.NETHERRACK, Blocks.NETHERRACK, 6, 20.0),

    /** Blue fungal forest: warped stems and twisting vines. */
    WARPED_FOREST(IslandFamily.NETHER, "spirit_warped_forest",
            Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, Blocks.NETHERRACK, 6, 20.0),

    /** Soul sand and bone, over basalt. */
    SOUL_SAND_VALLEY(IslandFamily.NETHER, "spirit_soul_sand_valley",
            Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.BASALT, 8, 22.0),

    // ------------------------------------------------------------------
    // The four families with a single look each
    // ------------------------------------------------------------------

    /** End-mimic: end stone, with chorus flowers and the odd purpur outcrop. */
    END(IslandFamily.END, "spirit_end",
            Blocks.END_STONE, Blocks.END_STONE, Blocks.END_STONE, 5, 20.0),

    /**
     * Grassy mountains with crimson creeping across them.
     *
     * Ordinary overworld ground, not nylium — the crimson is an INFECTION spreading over a
     * living island rather than the island's own material, so grass has to be the thing it
     * is spreading over. Its relief is by far the largest in the set and its scale short,
     * which together are what "amplified mountains" means in practice.
     *
     * Quite different from CRIMSON_FOREST above, which is an honest nether biome.
     */
    CRIMSON(IslandFamily.CRIMSON, "spirit_crimson",
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, 48, 15.0),

    /**
     * Mangrove swamp with warped creeping across it.
     *
     * THE ONLY STYLE WITH WATER, and the only one that leaks. A pool on a floating island
     * runs off the rim and falls into the void, which is a deliberate accepted cost here
     * and the reason nothing else in the set goes near it.
     */
    WARPED(IslandFamily.WARPED, "spirit_warped",
            Blocks.MUD, Blocks.MUD, Blocks.STONE, 3, 26.0),

    /** Dead black flats. Nothing grows and nothing is placed. */
    WASTELAND(IslandFamily.WASTELAND, "spirit_wasteland",
            Blocks.BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE, 1, 30.0);

    private final IslandFamily family;
    private final ResourceKey<Biome> biome;
    private final Block top;
    private final Block filler;
    private final Block deep;
    private final int relief;
    private final double reliefScale;

    IslandStyle(IslandFamily family, String biomeName,
                Block top, Block filler, Block deep, int relief, double reliefScale) {
        this.family = family;
        this.biome = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, biomeName));
        this.top = top;
        this.filler = filler;
        this.deep = deep;
        this.relief = relief;
        this.reliefScale = reliefScale;
    }

    public IslandFamily family() {
        return family;
    }

    /** The biome this style reports. Must exist as worldgen/biome/&lt;name&gt;.json. */
    public ResourceKey<Biome> biome() {
        return biome;
    }

    public BlockState top() {
        return top.defaultBlockState();
    }

    public BlockState filler() {
        return filler.defaultBlockState();
    }

    public BlockState deep() {
        return deep.defaultBlockState();
    }

    public int relief() {
        return relief;
    }

    public double reliefScale() {
        return reliefScale;
    }

    /** Every style in a family, in declaration order. See {@link IslandFamily#variants}. */
    public static IslandStyle[] of(IslandFamily family) {
        List<IslandStyle> found = new ArrayList<>(4);

        for (IslandStyle style : values()) {
            if (style.family == family) found.add(style);
        }
        return found.toArray(new IslandStyle[0]);
    }

    /**
     * Which style a biome belongs to, or null for the open void between islands.
     *
     * The reverse of {@link #biome()}, for the two things that only ever have a biome to
     * go on: the sky, which has to know whether the player is over a nether island, and
     * the spawner, which has to know what lives there.
     */
    public static IslandStyle forBiome(Holder<Biome> holder) {
        for (IslandStyle style : values()) {
            if (holder.is(style.biome)) return style;
        }
        return null;
    }

    /**
     * Puts whatever this style scatters on one surface column.
     *
     * Called for every column of every island, so the common answer is "nothing" and the
     * chances below are all small.
     */
    public void decorate(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
                         int x, int surface, int z, Random random) {
        BlockPos above = new BlockPos(x, surface + 1, z);
        BlockPos at = new BlockPos(x, surface, z);

        switch (this) {
            case PLAINS -> {
                if (random.nextInt(140) == 0) {
                    tree(level, above, Blocks.OAK_LOG.defaultBlockState(),
                            Blocks.OAK_LEAVES.defaultBlockState(), 4 + random.nextInt(3), random);
                } else if (random.nextInt(7) == 0) {
                    set(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (random.nextInt(120) == 0) {
                    set(level, above, Blocks.POPPY.defaultBlockState());
                } else if (random.nextInt(140) == 0) {
                    set(level, above, Blocks.DANDELION.defaultBlockState());
                }
            }
            case DESERT -> {
                if (random.nextInt(220) == 0) {
                    // Cactus stands on sand and needs nothing beside it here — the column
                    // it grows in is cleared air by the time decoration runs.
                    int height = 1 + random.nextInt(3);
                    for (int i = 0; i < height; i++) {
                        set(level, above.above(i), Blocks.CACTUS.defaultBlockState());
                    }
                } else if (random.nextInt(60) == 0) {
                    set(level, above, Blocks.DEAD_BUSH.defaultBlockState());
                } else if (random.nextInt(90) == 0) {
                    set(level, at, Blocks.SANDSTONE.defaultBlockState());
                }
            }
            case SNOWY -> {
                if (random.nextInt(150) == 0) {
                    tree(level, above, Blocks.SPRUCE_LOG.defaultBlockState(),
                            Blocks.SPRUCE_LEAVES.defaultBlockState(), 5 + random.nextInt(4), random);
                } else if (random.nextInt(120) == 0) {
                    set(level, at, Blocks.PACKED_ICE.defaultBlockState());
                }
            }
            case FOREST -> {
                if (random.nextInt(45) == 0) {
                    boolean birch = random.nextBoolean();
                    tree(level, above,
                            (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).defaultBlockState(),
                            (birch ? Blocks.BIRCH_LEAVES : Blocks.OAK_LEAVES).defaultBlockState(),
                            5 + random.nextInt(3), random);
                } else if (random.nextInt(6) == 0) {
                    set(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
            case NETHER_WASTES -> {
                if (random.nextInt(200) == 0) {
                    set(level, at, Blocks.GLOWSTONE.defaultBlockState());
                } else if (random.nextInt(60) == 0) {
                    set(level, at, Blocks.SOUL_SAND.defaultBlockState());
                } else if (random.nextInt(90) == 0) {
                    set(level, at, Blocks.NETHER_WART_BLOCK.defaultBlockState());
                }
            }
            case CRIMSON_FOREST -> {
                if (random.nextInt(70) == 0) {
                    tree(level, above, Blocks.CRIMSON_STEM.defaultBlockState(),
                            Blocks.NETHER_WART_BLOCK.defaultBlockState(), 6 + random.nextInt(5), random);
                } else if (random.nextInt(6) == 0) {
                    set(level, above, Blocks.CRIMSON_ROOTS.defaultBlockState());
                } else if (random.nextInt(10) == 0) {
                    set(level, above, Blocks.CRIMSON_FUNGUS.defaultBlockState());
                } else if (random.nextInt(180) == 0) {
                    set(level, at, Blocks.SHROOMLIGHT.defaultBlockState());
                }
            }
            case WARPED_FOREST -> {
                if (random.nextInt(70) == 0) {
                    tree(level, above, Blocks.WARPED_STEM.defaultBlockState(),
                            Blocks.WARPED_WART_BLOCK.defaultBlockState(), 6 + random.nextInt(5), random);
                } else if (random.nextInt(6) == 0) {
                    set(level, above, Blocks.WARPED_ROOTS.defaultBlockState());
                } else if (random.nextInt(10) == 0) {
                    set(level, above, Blocks.WARPED_FUNGUS.defaultBlockState());
                } else if (random.nextInt(30) == 0) {
                    set(level, above, Blocks.TWISTING_VINES.defaultBlockState());
                }
            }
            case SOUL_SAND_VALLEY -> {
                if (random.nextInt(14) == 0) {
                    set(level, at, Blocks.SOUL_SOIL.defaultBlockState());
                } else if (random.nextInt(200) == 0) {
                    // A short bone spur, which is what makes the valley read as itself.
                    int height = 2 + random.nextInt(4);
                    for (int i = 0; i < height; i++) {
                        set(level, above.above(i), Blocks.BONE_BLOCK.defaultBlockState());
                    }
                } else if (random.nextInt(40) == 0) {
                    set(level, above, Blocks.NETHER_SPROUTS.defaultBlockState());
                }
            }
            case END -> {
                if (random.nextInt(260) == 0) {
                    set(level, above, Blocks.CHORUS_FLOWER.defaultBlockState());
                } else if (random.nextInt(200) == 0) {
                    set(level, at, Blocks.PURPUR_BLOCK.defaultBlockState());
                }
            }
            case CRIMSON -> {
                boolean infected = infectedAt(x, z, 0xC12501L);

                if (infected) {
                    // The infection eats the grass it stands on, which is what sells it
                    // as spreading rather than as decoration scattered on top.
                    set(level, at, Blocks.CRIMSON_NYLIUM.defaultBlockState());

                    if (random.nextInt(90) == 0) {
                        tree(level, above, Blocks.CRIMSON_STEM.defaultBlockState(),
                                Blocks.NETHER_WART_BLOCK.defaultBlockState(), 5 + random.nextInt(4), random);
                    } else if (random.nextInt(7) == 0) {
                        set(level, above, Blocks.CRIMSON_ROOTS.defaultBlockState());
                    } else if (random.nextInt(11) == 0) {
                        set(level, above, Blocks.CRIMSON_FUNGUS.defaultBlockState());
                    }
                } else if (random.nextInt(200) == 0) {
                    tree(level, above, Blocks.OAK_LOG.defaultBlockState(),
                            Blocks.OAK_LEAVES.defaultBlockState(), 4 + random.nextInt(3), random);
                } else if (random.nextInt(7) == 0) {
                    set(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                }
            }
            case WARPED -> {
                boolean infected = infectedAt(x, z, 0x3A2FEDL);

                // NOTHING IS PLACED ON A POND. Roots used to go here, meant to read as
                // standing in the shallows, and they did nothing of the sort: the water
                // fills the surface block and the one under it, while decoration goes a
                // block ABOVE the surface — so every one of them floated a block clear of
                // the waterline. The roots on dry ground below are unaffected.
                //
                // Dug DOWN into the ground rather than laid on top of it, so the water
                // sits level with the surrounding land instead of sheeting off across the
                // whole island. Not where the warp has taken hold: the infection reads
                // better as something drying the swamp out ahead of itself.
                if (!infected && pondAt(x, z)) {
                    set(level, at, Blocks.WATER.defaultBlockState());
                    set(level, new BlockPos(x, surface - 1, z), Blocks.WATER.defaultBlockState());
                    return;
                }

                if (infected) {
                    set(level, at, Blocks.WARPED_NYLIUM.defaultBlockState());

                    if (random.nextInt(8) == 0) {
                        set(level, above, Blocks.WARPED_ROOTS.defaultBlockState());
                    } else if (random.nextInt(12) == 0) {
                        set(level, above, Blocks.WARPED_FUNGUS.defaultBlockState());
                    } else if (random.nextInt(30) == 0) {
                        set(level, above, Blocks.TWISTING_VINES.defaultBlockState());
                    }
                } else if (random.nextInt(130) == 0) {
                    tree(level, above, Blocks.MANGROVE_LOG.defaultBlockState(),
                            Blocks.MANGROVE_LEAVES.defaultBlockState(), 5 + random.nextInt(3), random);
                } else if (random.nextInt(18) == 0) {
                    set(level, above, Blocks.MANGROVE_ROOTS.defaultBlockState());
                } else if (random.nextInt(9) == 0) {
                    set(level, above, Blocks.MOSS_CARPET.defaultBlockState());
                } else if (random.nextInt(12) == 0) {
                    set(level, at, Blocks.MOSS_BLOCK.defaultBlockState());
                } else if (random.nextInt(14) == 0) {
                    set(level, at, Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
                }
            }
            case WASTELAND -> {
                // Deliberately bare. The design asks for nothing else placed on it, and
                // the sculk is part of the ground rather than something standing on it.
                if (random.nextInt(24) == 0) {
                    set(level, at, Blocks.SCULK.defaultBlockState());
                }
            }
        }
    }

    /**
     * Whether an infection has reached this column.
     *
     * NOISE, not a dice roll, and the difference is the whole effect. A per-block chance
     * scatters single infected blocks evenly across the island, which reads as confetti; a
     * noise field above a threshold gives connected blotches with ragged edges, which
     * reads as something that grew outward from somewhere.
     *
     * Deliberately NOT seeded per island: the blotches are a property of the world's
     * position, so an infected patch keeps its shape across every chunk that draws part of
     * it, exactly as the island outline does.
     */
    private static boolean infectedAt(int x, int z, long salt) {
        return SpiritIslands.noise(salt, x, z, 26.0) > 0.72;
    }

    /**
     * Whether the swamp is flooded at this column.
     *
     * The threshold puts about an eighth of the island under water. That figure was
     * MEASURED rather than reckoned: this noise is bilinear interpolation between uniform
     * lattice values, so its distribution is bell-shaped around 0.5, not flat. 0.58 sounds
     * like it should leave a bit over forty percent standing and in fact left 36.9%.
     */
    private static boolean pondAt(int x, int z) {
        return SpiritIslands.noise(0xB0A7L, x, z, 34.0) > 0.71;
    }

    /**
     * A crude trunk with a blob on top.
     *
     * Not a vanilla tree feature, deliberately: those pick their own position, check their
     * own soil and can refuse, none of which is wanted here, and the design asks only for
     * a visual palette rather than correct vegetation. The canopy is kept to a radius of 2
     * so it cannot reach past the neighbouring chunk — see the class note.
     */
    private static void tree(WorldGenLevel level, BlockPos base, BlockState log, BlockState leaves,
                             int height, Random random) {
        for (int i = 0; i < height; i++) {
            set(level, base.above(i), log);
        }

        int top = height - 1;
        for (int dy = -1; dy <= 1; dy++) {
            int radius = dy == 1 ? 1 : 2;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < 1) continue;
                    // Corners dropped at full radius, which is what turns a square of
                    // leaves into something round enough to read as a canopy.
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && random.nextBoolean()) continue;

                    BlockPos at = base.above(top + dy).offset(dx, 0, dz);
                    if (level.getBlockState(at).isAir()) set(level, at, leaves);
                }
            }
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
