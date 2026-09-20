package com.minecraft.atlamod.spirit.island;


/**
 * Where the Spirit World's islands are, how big they are and what they are made of.
 *
 * THE SINGLE SOURCE OF TRUTH, and it has to be: two completely separate parts of the
 * game ask these questions and must never disagree. {@link SpiritIslandFeature} asks so
 * it can place blocks, and {@link com.minecraft.atlamod.spirit.SpiritBiomeSource} asks so
 * it can say which biome a position is in. If those two drifted apart you would get a
 * nether-looking island reporting an end biome — wrong fog, wrong sky, wrong everything —
 * and the mismatch would be invisible in the code because the two run at different times
 * in different parts of world generation.
 *
 * Everything here is a pure function of a position. Nothing is stored, nothing is
 * remembered between calls, and no chunk needs to have been generated for a question
 * about it to be answerable.
 *
 * THE SEED IS A CONSTANT, not the world seed, and that is a deliberate trade. A
 * BiomeSource is never handed the world seed — it is constructed from its codec and asked
 * about positions, and there is no point at which the seed reaches it. Using a constant is
 * what lets the biome source and the feature agree at all. The cost is that every world's
 * Spirit World has the same island layout; the alternative was the two disagreeing, which
 * is far worse.
 */
public final class SpiritIslands {

    /**
     * How far apart islands sit: one per {@value} block cell.
     *
     * Down from the original 320, so there are about half again as many islands in the
     * same space. It could not go as low as the islands grew: they are now up to 250
     * across, and packing those into a 230 cell would have every one of them merged into
     * its neighbours and no void left between them.
     */
    public static final int CELL = 260;

    /** The narrowest and widest an island may be, measured across. */
    public static final int MIN_DIAMETER = 130;
    public static final int MAX_DIAMETER = 250;

    /** The band islands float in. Well clear of the temple at y=96. */
    public static final int MIN_Y = 40;
    public static final int MAX_Y = 140;

    /** How deep an island's underside hangs below its surface at the thickest point. */
    public static final int MAX_THICKNESS = 34;

    /**
     * How far an island's centre is kept from its cell's edge.
     *
     * Small enough to leave real jitter — without it the grid would be visible as a grid —
     * and large enough that two neighbouring islands are usually clear of one another.
     * USUALLY, not always: at this density two large islands facing each other across a
     * cell boundary can touch. That is allowed and is safe, because {@link #coveringOrNull}
     * gives every column to exactly one island, so they butt up against each other rather
     * than interleaving.
     */
    private static final int MARGIN = 85;

    /** See the class note: deliberately fixed rather than the world seed. */
    private static final long GRID_SEED = 0x5B1217A7L;

    private SpiritIslands() {
    }

    /** One island, entirely derived from its cell. */
    public record Island(int centreX, int centreZ, int centreY, int radius, IslandStyle style, long seed) {

        /**
         * How far the rim is from the centre in this direction.
         *
         * Noise on the world POSITION rather than on the angle, which matters: an
         * angle-based wobble pinches to nothing at the centre and gives a flower shape,
         * where this gives an irregular coastline at every scale. The rim lands between
         * 0.85 and 1.15 times the nominal radius.
         */
        public double edgeAt(int x, int z) {
            double wobble = noise(seed ^ 0x5EEDL, x, z, 24.0);
            return radius * (0.85 + 0.30 * wobble);
        }

        /**
         * Whether this column is inside the island proper.
         *
         * The two shortcuts are exact, not approximations: {@link #edgeAt} always lands
         * between 0.85 and 1.15 times the radius, so anything outside the larger circle
         * is certainly out and anything inside the smaller one is certainly in. Only the
         * band between them needs the noise computed.
         *
         * Worth the care because of how often this runs. The biome source asks about
         * every quart of every chunk, and each question walks nine cells and four islands
         * in each — so the noise would otherwise be evaluated some fifty thousand times
         * per chunk, almost always to reject something far away.
         */
        public boolean covers(int x, int z) {
            double distance = distanceTo(x, z);

            if (distance > radius * 1.15) return false;
            if (distance <= radius * 0.85) return true;
            return distance <= edgeAt(x, z);
        }

        public double distanceTo(int x, int z) {
            double dx = x - centreX;
            double dz = z - centreZ;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * The island belonging to one grid cell. Always the same answer for the same cell.
     *
     * Derived by HASHING rather than from a {@link Random}, and that is a performance
     * decision rather than a style one. The biome source asks this about every quart of
     * every chunk — roughly fifteen hundred times per chunk, each checking nine cells —
     * so a {@code new Random()} here would be some fourteen thousand allocations per
     * chunk generated. The hash is pure arithmetic and allocates nothing.
     */
    /**
     * How many smaller islands orbit each main one.
     *
     * They exist to break up the void: a lone island with a couple of hundred blocks of
     * nothing on every side is a long way to fall and a long way to look at. Stepping
     * stones around it make the dimension feel like somewhere you can cross.
     */
    public static final int SATELLITES = 3;

    /** How far out a satellite sits, as a multiple of its parent's radius. */
    private static final double ORBIT_MIN = 1.25;
    private static final double ORBIT_RANGE = 0.45;

    /** How big a satellite is, as a fraction of its parent's radius. */
    private static final double SATELLITE_MIN = 0.22;
    private static final double SATELLITE_RANGE = 0.18;

    /** The island belonging to one grid cell. Always the same answer for the same cell. */
    public static Island in(int cellX, int cellZ) {
        return island(cellX, cellZ, 0);
    }

    /**
     * One of a cell's islands: index 0 is the main one, 1 and up are its satellites.
     *
     * A satellite inherits its parent's STYLE deliberately — it is meant to read as a
     * piece that broke off the island beside it, not as an unrelated island that happens
     * to be nearby. Its height is only nudged from its parent's, for the same reason.
     *
     * Derived by HASHING rather than from a {@link java.util.Random}, and that is a
     * performance decision rather than a style one. The biome source asks this about
     * every quart of every chunk — roughly fifteen hundred times per chunk, each checking
     * nine cells and four islands in each — so a fresh Random here would be tens of
     * thousands of allocations per chunk generated. The hash is pure arithmetic.
     */
    public static Island island(int cellX, int cellZ, int index) {
        long seed = mix(GRID_SEED, cellX, cellZ);

        int centreX = cellX * CELL + MARGIN + pick(seed, 1, CELL - MARGIN * 2);
        int centreZ = cellZ * CELL + MARGIN + pick(seed, 2, CELL - MARGIN * 2);

        int diameter = MIN_DIAMETER + pick(seed, 3, MAX_DIAMETER - MIN_DIAMETER + 1);
        int centreY = MIN_Y + pick(seed, 4, MAX_Y - MIN_Y + 1);

        IslandStyle style = IslandStyle.values()[pick(seed, 5, IslandStyle.values().length)];
        int radius = diameter / 2;

        if (index == 0) return new Island(centreX, centreZ, centreY, radius, style, seed);

        long satelliteSeed = seed ^ (index * 0x9E3779B97F4A7C15L);

        double angle = pick(satelliteSeed, 6, 3600) / 3600.0 * Math.PI * 2.0;
        double orbit = radius * (ORBIT_MIN + pick(satelliteSeed, 7, 100) / 100.0 * ORBIT_RANGE);

        int satelliteRadius = Math.max(12,
                (int) (radius * (SATELLITE_MIN + pick(satelliteSeed, 8, 100) / 100.0 * SATELLITE_RANGE)));

        return new Island(
                centreX + (int) Math.round(Math.cos(angle) * orbit),
                centreZ + (int) Math.round(Math.sin(angle) * orbit),
                centreY - 12 + pick(satelliteSeed, 9, 25),
                satelliteRadius, style, satelliteSeed);
    }

    /** One value in 0..bound-1, stable for a given seed and salt. */
    private static int pick(long seed, int salt, int bound) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);

        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);

        return (int) Math.floorMod(h, (long) bound);
    }

    /**
     * The island whose ground covers this column, or null for open void.
     *
     * THE ONE RULE both the blocks and the biomes obey. Where two islands overlap this
     * hands the column to exactly one of them — the first in cell order — so the biome a
     * position reports and the blocks actually standing there can never disagree.
     */
    public static Island coveringOrNull(int x, int z) {
        if (nearTemple(x, z)) return null;

        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int index = 0; index <= SATELLITES; index++) {
                    Island island = island(cellX + dx, cellZ + dz, index);
                    if (island.covers(x, z)) return island;
                }
            }
        }
        return null;
    }

    /**
     * How much open void is kept clear around the temple at the world origin.
     *
     * Necessary rather than optional. Islands are up to 250 across and their centres can
     * sit as close as 85 blocks to the origin, which means the cell at the origin can
     * reach the origin itself — so without this the Spirit World's one fixed landmark
     * would generate buried inside an island, with its doorway walled up and its portal
     * in the rock.
     */
    private static final int TEMPLE_CLEARANCE = 44;

    /** Whether this column is inside the clear space kept around the temple. */
    private static boolean nearTemple(int x, int z) {
        return x * x + z * z <= TEMPLE_CLEARANCE * TEMPLE_CLEARANCE;
    }

    /** Returned by {@link #surfaceAt} where there is no island at all. */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    /**
     * The Y of the island surface at this column, or {@link #NO_GROUND} for open void.
     *
     * Needed because the Spirit World's TERRAIN IS A FEATURE, not noise. Asking the chunk
     * generator how high the ground is there gets the honest answer "there is none" — the
     * noise produces nothing and the islands are stamped in afterwards. So anything that
     * has to know where the ground is before the chunk is built, which is every structure
     * deciding whether it can stand somewhere, has to ask this instead.
     *
     * {@link SpiritIslandFeature} asks the same method when it actually lays the blocks,
     * so the height a structure plans around and the height it finds are the same number
     * by construction.
     */
    public static int surfaceAt(int x, int z) {
        Island island = coveringOrNull(x, z);
        if (island == null) return NO_GROUND;

        return surfaceOf(island, x, z);
    }

    /** The surface height of a known island at a column inside it. */
    public static int surfaceOf(Island island, int x, int z) {
        double inward = 1.0 - (island.distanceTo(x, z) / island.edgeAt(x, z));
        IslandStyle style = island.style();

        // Sqrt so the middle is a broad plateau rather than a sharp peak, then the style's
        // own relief on top of it — which is the whole difference between crimson's
        // mountains and the wasteland's flats.
        return island.centreY()
                + (int) Math.round(style.relief() * noise(island.seed(), x, z, style.reliefScale()))
                + (int) Math.round(4 * Math.sqrt(Math.max(0.0, inward)));
    }

    /**
     * Smooth 2D value noise in 0..1, with no setup and no allocation.
     *
     * Vanilla's PerlinNoise would do this better, but it has to be built from a
     * RandomSource and held somewhere, and everything here is static and shared by every
     * world at once — so there is nowhere correct to keep one. Seeded per island at the
     * call site instead, which is what makes islands differ from one another rather than
     * all wearing the same noise field.
     */
    static double noise(long seed, double x, double z, double scale) {
        double sx = x / scale;
        double sz = z / scale;

        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);

        double fx = smooth(sx - x0);
        double fz = smooth(sz - z0);

        double n00 = lattice(seed, x0, z0);
        double n10 = lattice(seed, x0 + 1, z0);
        double n01 = lattice(seed, x0, z0 + 1);
        double n11 = lattice(seed, x0 + 1, z0 + 1);

        double top = n00 + fx * (n10 - n00);
        double bottom = n01 + fx * (n11 - n01);

        return top + fz * (bottom - top);
    }

    /** Smoothstep, so the interpolation has no visible creases on the lattice lines. */
    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** One lattice point's value in 0..1, from a hash rather than a stored table. */
    private static double lattice(long seed, int x, int z) {
        long h = mix(seed, x, z);

        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);

        return ((h >>> 11) & 0x1FFFFF) / (double) 0x1FFFFF;
    }

    /**
     * A stable seed for a pair of coordinates.
     *
     * Deliberately not a sum or Objects.hash: both collide badly on a grid, and
     * neighbouring cells sharing a seed would mean neighbouring islands being identical.
     * These are the multipliers vanilla uses for the same job.
     */
    static long mix(long base, int a, int b) {
        return base ^ (a * 341873128712L) ^ (b * 132897987541L);
    }
}
