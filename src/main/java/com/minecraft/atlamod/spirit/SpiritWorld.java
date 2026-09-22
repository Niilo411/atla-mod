package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The Spirit World dimension: its key, and the one temple that stands in it.
 *
 * The dimension itself is data — see data/atlamod/dimension/spirit_world.json and the
 * noise settings, biome and features beside it. All this class holds is the key to
 * reach it by and the handful of questions the rest of the mod asks about it.
 */
public final class SpiritWorld {

    /**
     * Matches data/atlamod/dimension/spirit_world.json.
     *
     * A ResourceKey is just a name — it resolves to nothing if the JSON is missing or
     * fails to load, which is why {@link #level} can return null and every caller has
     * to cope with that rather than assuming the dimension is there.
     */
    public static final ResourceKey<Level> SPIRIT_WORLD = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_world"));

    /**
     * Where the Spirit World's temple stands.
     *
     * Fixed, and deliberately so: it is the one fixed point in a dimension that is
     * otherwise scattered islands in a void, and a player who walks off the edge needs
     * somewhere findable to come back to. Y is well above the islands so the temple is
     * never buried inside one.
     */
    public static final BlockPos TEMPLE_ORIGIN = new BlockPos(0, 96, 0);

    private SpiritWorld() {
    }

    /** Whether this level IS the Spirit World. */
    public static boolean isSpiritWorld(Level level) {
        return level != null && level.dimension().equals(SPIRIT_WORLD);
    }

    /** The Spirit World itself, or null if the dimension failed to load. */
    public static ServerLevel level(MinecraftServer server) {
        return server == null ? null : server.getLevel(SPIRIT_WORLD);
    }

    /**
     * The Spirit World's temple, building it if it is not there.
     *
     * Checked by looking at the world rather than by remembering in a flag, which means
     * it repairs itself: a temple that was never built, or one whose floor somebody
     * mined out, is simply built again on the next arrival. A flag would have to be
     * persisted, and would be wrong the moment the two disagreed.
     */
    public static TempleStructure.Temple ensureTemple(ServerLevel level) {
        TempleStructure.Temple temple = TempleStructure.isPresentAt(level, TEMPLE_ORIGIN)
                ? TempleStructure.describeAt(level, TEMPLE_ORIGIN)
                : TempleStructure.placeAt(level, TEMPLE_ORIGIN);

        ensureLit(level, temple);
        return temple;
    }

    /**
     * How far the search for a nearby temple reaches, in CHUNKS.
     *
     * Temples sit forty chunks apart, so a hundred is a couple of rings of them and
     * effectively always finds one. It is the same figure vanilla's own /locate uses.
     */
    private static final int TEMPLE_SEARCH_CHUNKS = 100;

    /**
     * Where a player stepping through a portal at {@code from} should come out.
     *
     * THE NEAREST TEMPLE TO WHERE THEY LEFT, rather than the fixed one at the origin.
     * That fixed temple floats in open void — island generation deliberately keeps clear
     * of the world origin so the temple is never buried, which also means there is
     * nothing around it but a long fall. Arriving there was the bug: every portal in the
     * Overworld led to the same room hanging in nothing.
     *
     * Coordinates map one to one, since the dimension's coordinate scale is 1, so a
     * portal in the far north of the Overworld comes out in the far north of the Spirit
     * World and the two places stay roughly related.
     *
     * The central temple stays as the fallback for when no structure can be found at all
     * — a world generated with structures switched off, most likely. Something has to
     * happen, and a room in the void beats refusing to travel.
     */
    public static BlockPos arrivalNear(ServerLevel spirit, BlockPos from) {
        TempleStructure.Temple temple = nearestTemple(spirit, from);
        if (temple == null) temple = ensureTemple(spirit);

        // No temple at all means the .nbt is missing or misnamed, which is a data problem
        // rather than a place problem. Setting them down on the fixed origin at least puts
        // them somewhere findable instead of refusing to travel.
        if (temple == null) return TEMPLE_ORIGIN.above();

        ensureLit(spirit, temple);
        return temple.arrival();
    }

    /**
     * Where an ISLAND portal comes out: on the island nearest the coordinates it was
     * opened at, rather than inside the nearest temple.
     *
     * The other of the dimension's two arrivals, and the difference is the whole point of
     * the portal that uses it. A temple portal is navigation — it puts you at the fixed
     * landmark nearest where you left, with a way back standing in the same room. This
     * one puts you on open ground wherever you happened to be, with nothing there at all.
     * Only the Avatar can open one, and it lasts fifteen seconds.
     *
     * THE ISLAND IS FOUND BY ARITHMETIC, NOT BY LOOKING, which is what lets this answer
     * for ground that has never been generated: islands are a pure function of position
     * (see {@link com.minecraft.atlamod.spirit.island.SpiritIslands}), so the surface
     * height is knowable before the chunk exists and is exactly the height the chunk will
     * have when it does. The same property {@link #arrivalNear} relies on.
     *
     * Lands at the island's CENTRE rather than at the matching column, and that is
     * deliberate: the centre is the one place on an island guaranteed to have ground under
     * it, where the column straight below the portal may be a few blocks past the rim and
     * over the void. The island is the destination; which part of it is not worth a fall.
     */
    public static BlockPos islandArrivalNear(ServerLevel spirit, BlockPos from) {
        com.minecraft.atlamod.spirit.island.SpiritIslands.Island island =
                nearestIsland(from.getX(), from.getZ());

        // No island within reach at all is possible near the world origin, which island
        // generation deliberately keeps clear so the fixed temple is not buried. Falling
        // back to the temple arrival beats setting somebody down in open void.
        if (island == null) return arrivalNear(spirit, from);

        int surface = com.minecraft.atlamod.spirit.island.SpiritIslands
                .surfaceAt(island.centreX(), island.centreZ());
        if (surface == com.minecraft.atlamod.spirit.island.SpiritIslands.NO_GROUND) {
            return arrivalNear(spirit, from);
        }

        return new BlockPos(island.centreX(), surface + 1, island.centreZ());
    }

    /**
     * The island whose centre is nearest this column, or null if none is close.
     *
     * NINE CELLS, which is the same neighbourhood {@code coveringOrNull} walks and is
     * enough for the same reason: an island's centre is kept well inside its own cell, so
     * the nearest one to any column is always in that column's cell or one beside it.
     * Satellites are included, since they are real ground and often the closest thing.
     */
    private static com.minecraft.atlamod.spirit.island.SpiritIslands.Island nearestIsland(int x, int z) {
        int cell = com.minecraft.atlamod.spirit.island.SpiritIslands.cell();
        int cellX = Math.floorDiv(x, cell);
        int cellZ = Math.floorDiv(z, cell);

        com.minecraft.atlamod.spirit.island.SpiritIslands.Island best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (com.minecraft.atlamod.spirit.island.SpiritIslands.Island island
                        : com.minecraft.atlamod.spirit.island.SpiritIslands.allIn(cellX + dx, cellZ + dz)) {

                    double distance = island.distanceTo(x, z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = island;
                    }
                }
            }
        }
        return best;
    }

    /**
     * The nearest naturally generated temple, or null if there is none to be had.
     *
     * The position the locator gives back is a chunk's MIN corner, not the temple — the
     * offset to the middle is {@link com.minecraft.atlamod.spirit.structure.SpiritTempleStructure#originIn},
     * which is also what put the temple there, so the two cannot disagree.
     *
     * The height is computed rather than looked up, because this runs before the chunk
     * has been generated and {@link com.minecraft.atlamod.spirit.island.SpiritIslands} can
     * answer without generating anything. The chunk is then forced into existence and the
     * answer CHECKED against the world, which is what makes a wrong guess fall through to
     * the fallback instead of dropping somebody into empty air.
     */
    private static TempleStructure.Temple nearestTemple(ServerLevel spirit, BlockPos from) {
        BlockPos locate = spirit.findNearestMapStructure(
                com.minecraft.atlamod.spirit.structure.ModStructures.SPIRIT_TEMPLE_TAG,
                from, TEMPLE_SEARCH_CHUNKS, false);
        if (locate == null) return null;

        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(locate);
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();

        int floor = com.minecraft.atlamod.spirit.island.SpiritIslands.surfaceAt(x, z);
        if (floor == com.minecraft.atlamod.spirit.island.SpiritIslands.NO_GROUND) return null;

        BlockPos origin = com.minecraft.atlamod.spirit.structure.SpiritTempleStructure
                .originIn(chunkPos, floor);

        // Built, if it was not already. Everything below reads real blocks.
        spirit.getChunk(origin);

        return TempleStructure.isPresentAt(spirit, origin) ? TempleStructure.describeAt(spirit, origin) : null;
    }

    /**
     * A temple built in the Spirit World is born with its portal burning. Elsewhere it is
     * left dark.
     *
     * THE ONE PLACE THAT RULE LIVES, called by both things that build a temple — the
     * world-generation feature and the /bend temple command — so the two cannot drift
     * apart and start disagreeing about what a spirit temple looks like when it appears.
     *
     * The reason is not decoration. Opening a portal takes five ability casts, and
     * bending does not work in the Spirit World at all, so a dark frame there could never
     * be lit by anybody and the temple would be a room with a dead doorway.
     *
     * No closing tick is scheduled, which is how these stay open forever: an overworld
     * portal is given a tick a minute out, and a spirit one is simply never given one.
     *
     * Takes a WorldGenLevel because world generation is one of its two callers and that
     * is all a feature is handed; a ServerLevel is one, so the command fits too.
     */
    public static void lightIfHere(net.minecraft.world.level.WorldGenLevel level,
                                   TempleStructure.Temple temple) {
        lightIfHere(level, temple, null);
    }

    /**
     * The same, writing only inside {@code clip}.
     *
     * World generation builds a temple once per chunk it touches and clips each pass to
     * that chunk, so the portal has to be clipped the same way or a pass would reach into
     * a neighbour it is not allowed to write to.
     */
    public static void lightIfHere(net.minecraft.world.level.WorldGenLevel level,
                                   TempleStructure.Temple temple,
                                   net.minecraft.world.level.levelgen.structure.BoundingBox clip) {
        if (!isSpiritWorld(level.getLevel())) return;
        if (temple == null || temple.portal() == null) return;

        SpiritPortalFrame.light(level, temple.portal(), clip);
    }

    /**
     * Makes sure the Spirit World's own portal is burning.
     *
     * This is not decoration — it is the way out. A temple is built with its frame EMPTY,
     * which is right in the Overworld where lighting it is something a bender earns, and
     * completely wrong here: a player who arrived to find an unlit frame would be
     * standing in a sealed room in a dimension with no floor, and no way to bend their
     * way out of it either.
     *
     * Checked and repaired on every arrival rather than only at build time, so a portal
     * that somebody managed to clear out does not strand the next person through.
     * {@link SpiritPortals#activate} is what schedules the one minute closing tick, and
     * it deliberately does not do so here — this portal is meant to burn forever.
     */
    private static void ensureLit(ServerLevel level, TempleStructure.Temple temple) {
        if (temple == null || temple.portal() == null) return;

        SpiritPortalFrame.Frame frame = temple.portal();

        for (BlockPos pos : frame.interior()) {
            if (!level.getBlockState(pos).is(com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get())) {
                SpiritPortals.activate(level, frame);
                return;
            }
        }
    }
}
