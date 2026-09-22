package com.minecraft.atlamod.spirit.island;

/**
 * Where spirit ore sits inside the islands, and how its veins are shaped.
 *
 * A PURE FUNCTION OF THE POSITION, like everything else in this package, and here that is
 * not tidiness — it is the only way veins can work at all. The obvious implementation is
 * "roll a seed block, then grow a cluster outwards from it", but a cluster grown that way
 * crosses chunk borders, and {@link SpiritIslandFeature} may only write into the chunk it
 * is generating. A vein spilled into the neighbour would be overwritten the moment that
 * neighbour laid its own columns, leaving veins sliced in half along chunk lines.
 *
 * So nothing is ever grown OUTWARD. Every block asks {@link #at} whether IT is ore, and
 * the answer depends only on its own coordinates — so two chunks either side of a vein
 * reach the identical conclusion and the vein meets itself exactly.
 *
 * THE VEIN LIVES IN A CELL. Space is cut into {@value #CELL}-block cubes; a cell either
 * has one vein or none, and the vein is grown INSIDE that cell from the cell's own seed.
 * That is what keeps the cost to one hash for the great majority of blocks — a block only
 * pays for the vein expansion if its own cell turned out to have a vein at all.
 *
 * The cost of the cell is that a vein cannot cross a cell boundary, so the largest one is
 * bounded by the cube it sits in. At two to four blocks in a sixty-four block cell that is
 * invisible; it would start to show if veins ever got much bigger.
 *
 * TWO RARITIES, ONE VEIN SYSTEM. The design asks for underground ore about as common as
 * iron and surface ore about as rare as diamond, which is a ratio of roughly sixteen to
 * one. Rather than run two vein systems, a vein is rolled once at the underground rate and
 * then asked a SECOND, much rarer question: may it break the surface? A vein that may not
 * simply has its topmost blocks left as ordinary ground, so it is still there to be dug
 * out — it just cannot be spotted from above.
 */
public final class SpiritOre {

    /** The cube a single vein is grown inside. 4x4x4, so 64 blocks per cell. */
    private static final int CELL = 4;

    /** How many blocks a vein is made of. */
    private static final int VEIN_MIN = 2;
    private static final int VEIN_MAX = 4;

    /**
     * How many cells in a thousand carry a vein.
     *
     * DOWN FROM 170, which put spirit ore at iron's density — about 0.8% of island rock.
     * Underground now sits at the rate the SURFACE used to have, roughly 0.05%, so the ore
     * is a find everywhere rather than something tripped over while tunnelling.
     *
     * A cell is 64 blocks and a vein averages 3, so the share of island rock that is ore is
     * {@code 11/1000 * 3/64}. That still leaves a few hundred blocks buried in a full-sized
     * island, which is plenty for the 24 shards a full armor set costs.
     */
    private static final int VEIN_IN_THOUSAND = 11;

    /**
     * How many of those veins in a thousand are allowed to reach the surface.
     *
     * THIS IS A CONDITIONAL, asked only of veins that already passed
     * {@link #VEIN_IN_THOUSAND}, so the surface rate is the product of the two and falls
     * whenever that one does. It is left at 62 deliberately: dropping the vein roll to 11
     * already took the surface down with it, by the same factor of fifteen and a half, and
     * a second cut on top would have put visible ore below one block per island.
     *
     * MEASURED against this class over 1.4 million columns rather than derived, because
     * what fraction of veins touch the top layer at all is not worth working out on paper —
     * and because an early attempt tuned from a few hundred columns was out by half.
     *
     * Underground now lands at 0.0495% of island rock and the surface at 0.0032% of island
     * TOP blocks, holding the same 15.5 : 1 ratio the two have always had.
     *
     * In blocks a player would count: about ONE surface block per full-sized island, so
     * most islands show none at all and spotting one is a genuine event. The veins that
     * reach the top and are refused simply stop a block short and stay buried, so nothing
     * is lost; it just cannot be seen from above.
     */
    private static final int SURFACE_IN_THOUSAND = 62;

    /**
     * How much bending XP one block is worth.
     *
     * Deliberately a mid-tier ability's reward — Wind, Earth trap and breathless all pay
     * 15 — so a vein is worth about as much as landing three good casts. At 200 XP to a
     * level that is roughly thirteen blocks per level, which is a real reason to go
     * looking without being a way to skip the tree.
     */
    public static final int XP_PER_BLOCK = 15;

    /** Kept clear of every salt {@link SpiritIslands} and {@link SpiritShrines} use. */
    private static final int SALT_VEIN = 50;
    private static final int SALT_SURFACE = 51;
    private static final int SALT_SIZE = 52;
    private static final int SALT_SEED_X = 53;
    private static final int SALT_SEED_Y = 54;
    private static final int SALT_SEED_Z = 55;
    private static final int SALT_GROW = 60;

    /** Fixed, like the island grid's, and for the same reason — see {@link SpiritIslands}. */
    private static final long ORE_SEED = 0x0E0D1A57L;

    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    private SpiritOre() {
    }

    /**
     * Whether this block of island is spirit ore.
     *
     * @param surface the Y of the column's top block, which is the only thing that
     *                distinguishes a vein somebody can SEE from one they have to dig for
     */
    public static boolean at(int x, int y, int z, int surface) {
        int cellX = Math.floorDiv(x, CELL);
        int cellY = Math.floorDiv(y, CELL);
        int cellZ = Math.floorDiv(z, CELL);

        long seed = cellSeed(cellX, cellY, cellZ);

        // The early-out that makes this affordable: five blocks in six stop here, having
        // paid for exactly one hash.
        if (pick(seed, SALT_VEIN, 1000) >= VEIN_IN_THOUSAND) return false;

        if (!inVein(seed, Math.floorMod(x, CELL), Math.floorMod(y, CELL), Math.floorMod(z, CELL))) {
            return false;
        }

        // Breaking the surface is the rare half of the ability. A vein that may not simply
        // stops short, so the ore is still down there to be found by digging.
        if (y >= surface) return pick(seed, SALT_SURFACE, 1000) < SURFACE_IN_THOUSAND;

        return true;
    }

    /**
     * Whether a cell-local position is one of this cell's vein blocks.
     *
     * The vein is grown here rather than stored: a seed block, then blocks hung off the
     * ones already placed, each step choosing a source and a direction from the cell's own
     * seed. Every caller runs the identical walk and gets the identical vein, which is the
     * whole point — see the class note.
     *
     * Bounded by {@link #VEIN_MAX}, so the arrays are tiny and the loop is short.
     */
    private static boolean inVein(long seed, int lx, int ly, int lz) {
        int size = VEIN_MIN + pick(seed, SALT_SIZE, VEIN_MAX - VEIN_MIN + 1);
        int query = pack(lx, ly, lz);

        int[] vein = new int[VEIN_MAX];
        vein[0] = pack(pick(seed, SALT_SEED_X, CELL),
                pick(seed, SALT_SEED_Y, CELL),
                pick(seed, SALT_SEED_Z, CELL));
        if (vein[0] == query) return true;

        int count = 1;
        while (count < size) {
            boolean grew = false;

            // Several attempts, because a direction may point out of the cell or at a
            // block already taken. A 64-block cell always has room for four, so this is
            // about finding a free neighbour rather than about whether one exists.
            for (int attempt = 0; attempt < 16 && !grew; attempt++) {
                int salt = SALT_GROW + count * 32 + attempt * 2;

                int from = vein[pick(seed, salt, count)];
                int dir = pick(seed, salt + 1, 6);

                int nx = unpack(from, 8) + DX[dir];
                int ny = unpack(from, 4) + DY[dir];
                int nz = unpack(from, 0) + DZ[dir];

                if (nx < 0 || nx >= CELL || ny < 0 || ny >= CELL || nz < 0 || nz >= CELL) continue;

                int next = pack(nx, ny, nz);
                if (contains(vein, count, next)) continue;

                vein[count++] = next;
                grew = true;
            }

            // Cannot happen in a cell this size, but a walk that cannot grow must not spin.
            if (!grew) break;
        }

        return contains(vein, count, query);
    }

    private static boolean contains(int[] vein, int count, int value) {
        for (int i = 0; i < count; i++) {
            if (vein[i] == value) return true;
        }
        return false;
    }

    private static int pack(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    private static int unpack(int packed, int shift) {
        return (packed >> shift) & 0xF;
    }

    /** A stable seed for a cell. The multipliers are the ones vanilla uses for the job. */
    private static long cellSeed(int x, int y, int z) {
        return ORE_SEED ^ (x * 341873128712L) ^ (y * 132897987541L) ^ (z * 6364136223846793005L);
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
}
