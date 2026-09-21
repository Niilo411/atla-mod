package com.minecraft.atlamod.spirit.island;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * The six looks a spirit island can have.
 *
 * Each is a palette of three blocks plus a scatter of things on top, and two numbers
 * that decide the terrain's shape: {@link #relief} is how many blocks the surface rises
 * and falls by, and {@link #reliefScale} is how far apart those changes happen. A big
 * relief over a short scale is jagged; a small relief over a long scale is nearly flat.
 * Those two are what make CRIMSON read as mountains and WASTELAND as a plain, off the
 * same code.
 *
 * DECORATION MAY SPILL A BLOCK OR TWO into the neighbouring chunk, and that is allowed:
 * during feature generation the world is a region covering the chunk and its neighbours,
 * which is exactly how vanilla's own trees reach past their chunk. Nothing here reaches
 * further than a small canopy, so the rule the island terrain follows strictly — never
 * write outside your own chunk — is not really bent. Anything added later that reaches
 * further than about two blocks would be.
 */
public enum IslandStyle {

    /** Ordinary overworld: grass, dirt, stone, and a scattering of oaks. */
    OVERWORLD(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, 5, 22.0),

    /** Nether-mimic: netherrack over basalt, with glowstone and soul sand. */
    NETHER(Blocks.NETHERRACK, Blocks.NETHERRACK, Blocks.BASALT, 7, 18.0),

    /** End-mimic: end stone, with chorus flowers and the odd purpur outcrop. */
    END(Blocks.END_STONE, Blocks.END_STONE, Blocks.END_STONE, 5, 20.0),

    /**
     * Grassy mountains with crimson creeping across them.
     *
     * Ordinary overworld ground, not nylium — the crimson is an INFECTION spreading over
     * a living island rather than the island's own material, so grass has to be the thing
     * it is spreading over. The patches come from noise rather than per-block chance, and
     * that is what makes it read as infection at all: scattered single blocks look like
     * confetti, where blotches look like something growing outward.
     *
     * Its relief is by far the largest in the set and its scale short, which together are
     * what "amplified mountains" means in practice.
     */
    CRIMSON(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, 48, 15.0),

    /**
     * Mangrove swamp with warped creeping across it.
     *
     * The same infection idea as CRIMSON, over mud instead of grass, and flat instead of
     * mountainous.
     *
     * THE ONLY STYLE WITH WATER, and the only one that leaks. A pool on a floating island
     * runs off the rim and falls into the void, which is a deliberate accepted cost here
     * and the reason nothing else in the set goes near it. See the pond handling in
     * {@link #decorate}.
     */
    WARPED(Blocks.MUD, Blocks.MUD, Blocks.STONE, 3, 26.0),

    /** Dead black flats. Nothing grows and nothing is placed. */
    WASTELAND(Blocks.BLACKSTONE, Blocks.BASALT, Blocks.BLACKSTONE, 1, 30.0);

    private final Block top;
    private final Block filler;
    private final Block deep;
    private final int relief;
    private final double reliefScale;

    IslandStyle(Block top, Block filler, Block deep, int relief, double reliefScale) {
        this.top = top;
        this.filler = filler;
        this.deep = deep;
        this.relief = relief;
        this.reliefScale = reliefScale;
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

    /**
     * Puts whatever this style scatters on one surface column.
     *
     * Called for every column of every island, so the common answer is "nothing" and the
     * chances below are all small.
     */
    public void decorate(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
                         int x, int surface, int z, Random random) {
        BlockPos above = new BlockPos(x, surface + 1, z);

        switch (this) {
            case OVERWORLD -> {
                if (random.nextInt(120) == 0) {
                    tree(level, above, Blocks.OAK_LOG.defaultBlockState(),
                            Blocks.OAK_LEAVES.defaultBlockState(), 4 + random.nextInt(3), random);
                } else if (random.nextInt(8) == 0) {
                    set(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                } else if (random.nextInt(140) == 0) {
                    set(level, above, Blocks.POPPY.defaultBlockState());
                }
            }
            case NETHER -> {
                if (random.nextInt(200) == 0) {
                    set(level, new BlockPos(x, surface, z), Blocks.GLOWSTONE.defaultBlockState());
                } else if (random.nextInt(60) == 0) {
                    set(level, new BlockPos(x, surface, z), Blocks.SOUL_SAND.defaultBlockState());
                } else if (random.nextInt(90) == 0) {
                    set(level, new BlockPos(x, surface, z), Blocks.NETHER_WART_BLOCK.defaultBlockState());
                }
            }
            case END -> {
                if (random.nextInt(260) == 0) {
                    set(level, above, Blocks.CHORUS_FLOWER.defaultBlockState());
                } else if (random.nextInt(200) == 0) {
                    set(level, new BlockPos(x, surface, z), Blocks.PURPUR_BLOCK.defaultBlockState());
                }
            }
            case CRIMSON -> {
                boolean infected = infectedAt(x, z, 0xC12501L);

                if (infected) {
                    // The infection eats the grass it stands on, which is what sells it
                    // as spreading rather than as decoration scattered on top.
                    set(level, new BlockPos(x, surface, z), Blocks.CRIMSON_NYLIUM.defaultBlockState());

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

                // Standing water, which a mangrove swamp needs and which every other
                // style here deliberately avoids. It WILL run off the island's rim and
                // fall as waterfalls into the void — that is accepted rather than
                // overlooked, and is why no other style does this.
                //
                // Dug DOWN into the ground rather than laid on top of it. The top block
                // becomes water, so its surface sits level with the surrounding land
                // instead of a block proud of it — laid on top, every pool would sheet
                // straight off across the whole island instead of sitting in place.
                //
                // Not where the warp has taken hold: the infection reads better as
                // something drying the swamp out ahead of itself.
                // NOTHING IS PLACED ON A POND. Roots used to go here, meant to read as
                // standing in the shallows, and they did nothing of the sort: the water
                // fills the surface block and the one under it, while decoration goes a
                // block ABOVE the surface — so every one of them floated a block clear of
                // the waterline. The roots on dry ground below are unaffected.
                if (!infected && pondAt(x, z)) {
                    set(level, new BlockPos(x, surface, z), Blocks.WATER.defaultBlockState());
                    set(level, new BlockPos(x, surface - 1, z), Blocks.WATER.defaultBlockState());
                    return;
                }

                if (infected) {
                    set(level, new BlockPos(x, surface, z), Blocks.WARPED_NYLIUM.defaultBlockState());

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
                    set(level, new BlockPos(x, surface, z), Blocks.MOSS_BLOCK.defaultBlockState());
                } else if (random.nextInt(14) == 0) {
                    set(level, new BlockPos(x, surface, z),
                            Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState());
                }
            }
            case WASTELAND -> {
                // Deliberately bare. The design asks for nothing else placed on it, and
                // the sculk is part of the ground rather than something standing on it.
                if (random.nextInt(24) == 0) {
                    set(level, new BlockPos(x, surface, z), Blocks.SCULK.defaultBlockState());
                }
            }
        }
    }

    /**
     * Whether the infection has reached this column.
     *
     * NOISE, not a dice roll, and the difference is the whole effect. A per-block chance
     * scatters single infected blocks evenly across the island, which reads as confetti;
     * a noise field above a threshold gives connected blotches with ragged edges, which
     * reads as something that grew outward from somewhere. The threshold is high so most
     * of the island is still clean — it is meant to look like early infection, not like a
     * nether biome with grass in it.
     *
     * Deliberately NOT seeded per island: the blotches are a property of the world's
     * position, so an infected patch keeps its shape across every chunk that draws part
     * of it, exactly as the island outline does.
     */
    private static boolean infectedAt(int x, int z, long salt) {
        return SpiritIslands.noise(salt, x, z, 26.0) > 0.72;
    }

    /**
     * Whether the swamp is flooded at this column.
     *
     * The same noise trick the infection uses, and for the same reason: pools want to be
     * connected shapes with ragged banks, not single scattered water blocks. A longer
     * scale than the infection's, so the pools are broader and fewer — a swamp reads as
     * having a few large channels rather than a rash of puddles.
     *
     * The threshold puts about an eighth of the island under water — half what 0.58 gave.
     *
     * That figure was MEASURED rather than reckoned, and it is worth saying why: this
     * noise is bilinear interpolation between uniform lattice values, so its distribution
     * is bell-shaped around 0.5, not flat. 0.58 sounds like it should leave a bit over
     * forty percent standing and in fact left 36.9%; halving that needed 0.71, where
     * guessing from the shape of the numbers would have said something nearer 0.79.
     */
    private static boolean pondAt(int x, int z) {
        return SpiritIslands.noise(0xB0A7L, x, z, 34.0) > 0.71;
    }

    /**
     * A crude trunk with a blob on top.
     *
     * Not a vanilla tree feature, deliberately: those pick their own position, check
     * their own soil and can refuse, none of which is wanted here, and the design asks
     * only for a visual palette rather than correct vegetation. The canopy is kept to a
     * radius of 2 so it cannot reach past the neighbouring chunk — see the class note.
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
