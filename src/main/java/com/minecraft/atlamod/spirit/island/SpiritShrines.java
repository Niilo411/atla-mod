package com.minecraft.atlamod.spirit.island;

import com.minecraft.atlamod.Atlamod;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.network.SyncStatsPacket;
import com.minecraft.atlamod.spirit.SpiritWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Spirit shrines: where they are, how they are built, and what using one does.
 *
 * THE ONE PLACE THAT KNOWS WHAT A SHRINE IS, the same way {@link com.minecraft.atlamod.spirit.TempleStructure}
 * is for the temple — and it holds both halves deliberately, because the second half only
 * works if it agrees exactly with the first. A shrine is identified at right-click time by
 * RECOMPUTING where it must be rather than by remembering where one was put, so the rule
 * that places it and the rule that recognises it have to be the same rule.
 *
 * NOTHING IS STORED ABOUT A SHRINE ANYWHERE. Where the shrines are is a pure function of
 * the island they stand on, exactly as the island is a pure function of its cell — see
 * {@link SpiritIslands}' class note. That buys three things at once: a chunk can place its
 * share of a shrine without asking anything, {@link #isShrine} can answer "is this beacon a
 * shrine" from the position alone, and no save data is needed for any of it. The only thing
 * ever written down is which shrines a PLAYER has already drawn from, which lives on their
 * {@link BendingData}.
 *
 * A REAL STRUCTURE, NOT A FEATURE, and that is what makes {@code /locate} work — the same
 * swap the temple made, for the same reason. A feature can scatter something at a rarity
 * and that is all: the game keeps no record of where it went, so nothing can ever be asked
 * to find one. A structure is registered, remembered per chunk, and therefore locatable.
 *
 * ITS GRID IS DELIBERATELY EVERY CHUNK ({@code spacing: 1}), which is not how vanilla
 * structures are usually spaced and is the one thing to understand about the datapack side.
 * A structure set's grid normally IS the rarity; here the rarity is the island roll below,
 * and the grid's only job is to offer every chunk as a candidate so that the chunk holding
 * a shrine's corner is always asked. {@link SpiritShrineStructure} then answers for almost
 * all of them with "nothing here". Anything coarser would place only the fraction of
 * shrines whose corner happened to land on the grid.
 *
 * BUILT ONCE PER CHUNK IT TOUCHES, each pass clipped to that chunk — the temple's trick,
 * and it is not optional. Writing outside the chunk being generated forces the neighbour to
 * generate early, and that cascade can hang generation outright. Vanilla calls
 * {@link SpiritShrinePiece}'s postProcess for every chunk the bounding box overlaps and
 * hands it that chunk's writable area; {@link #build} draws the whole structure each time
 * and the clip discards the rest. Every pass computes the same positions from the same
 * origin, so the shares meet exactly.
 *
 * THE MARKER BECOMES THE BEACON. The hand-built .nbt carries one {@link #MARKER_BLOCK} at
 * the spot the interactable block belongs, read out of the TEMPLATE rather than searched
 * for in the world — a search would miss a marker that fell in a neighbouring chunk, and
 * the template always has it. Writing the beacon over it both clears the marker and places
 * the block, so the marker never survives.
 */
public final class SpiritShrines {

    /** The hand-built structure. */
    private static final ResourceLocation TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_shrine");

    /**
     * The single block marking where the interactable block goes.
     *
     * A redstone block for the same reason the temple's marker is stripped oak: it appears
     * exactly once in the structure and nowhere else, so finding it cannot be ambiguous. It
     * never survives placement — the beacon is written straight over it.
     */
    public static final Block MARKER_BLOCK = Blocks.REDSTONE_BLOCK;

    /**
     * What a shrine is interacted with through.
     *
     * A vanilla beacon, used PURELY as something to right click. None of its own mechanics
     * are wanted or implemented — no pyramid, no beam, no effect menu — and the interaction
     * handler cancels the event outright, so its screen never opens. A custom block would
     * have needed a blockstate, a model and a block-entity renderer to arrive at a beacon
     * that already exists.
     */
    public static final Block SHRINE_BLOCK = Blocks.BEACON;

    /**
     * How many islands in a hundred carry a shrine.
     *
     * Rolled per ISLAND, which includes satellites, so a cell with its main island and
     * three satellites offers four chances — about 0.23 shrines per cell, or one per 540
     * blocks of travel on average. The effective rate is a little lower than this figure,
     * because a site that cannot find flat enough ground on its island is abandoned; see
     * {@link #standingAt}.
     *
     * Rare enough to be a find rather than scenery, which it can afford to be now that
     * {@code /locate structure atlamod:spirit_shrine} will point at the nearest one.
     *
     * A SETTING, defaulting to the 6 this was fixed at. Everything above describes that
     * default; raising it scales the figures with it.
     */
    private static int chanceInHundred() {
        return com.minecraft.atlamod.AtlaConfig.shrineChance();
    }

    /** Salts for the shrine's draws, kept clear of every salt {@link SpiritIslands} uses. */
    private static final int SALT_CHANCE = 30;
    private static final int SALT_ANGLE = 31;
    private static final int SALT_DISTANCE = 32;

    /** How many places on an island are tried before it is left without a shrine. */
    private static final int ATTEMPTS = 6;

    /**
     * How far out from an island's centre a shrine may sit, as a fraction of its radius.
     *
     * Kept well inside the rim: a structure that straddles the edge would have half its
     * footprint over the void, and the ground test would simply refuse it — so allowing the
     * full radius would only waste attempts.
     */
    private static final double MAX_OUT = 0.6;

    /** How far the ground under a shrine may rise or fall across its footprint. */
    private static final int MAX_SLOPE = 2;

    /**
     * How many cells either way are searched for islands whose shrine reaches a chunk.
     *
     * TWO, not one, and the arithmetic matters. A shrine only exists where
     * {@link SpiritIslands#coveringOrNull} claims its own column for the island, and that
     * walks the 3x3 cells around the column — so the island may be registered a cell away
     * from its shrine. A chunk touching the shrine is within a few blocks of it, so at most
     * one more cell again. Search less and a chunk would fail to draw its share of a shrine
     * its neighbour drew, leaving it sliced in half.
     */
    private static final int CELL_REACH = 2;

    /** No ground in this column, or ground belonging to a different island. */
    private static final int NONE = Integer.MIN_VALUE;

    private SpiritShrines() {
    }

    // ------------------------------------------------------------------
    // PLACEMENT
    // ------------------------------------------------------------------

    /**
     * The shrine whose CORNER falls in this chunk, or null — the structure's whole decision.
     *
     * The corner rather than the middle, and any single deterministic block of the shrine
     * would do: the point is that exactly ONE chunk claims each shrine, so no two candidate
     * chunks can both start it. Vanilla then writes a reference into every chunk the
     * bounding box reaches and builds the rest of it from there.
     *
     * NOTHING HERE READS A BLOCK. A structure decides its position before the chunk it
     * sits in exists, so the ground is asked of {@link SpiritIslands} rather than looked at
     * — see {@link #standingAt}.
     */
    public static BlockPos originIn(ChunkPos chunk, Vec3i size) {
        for (BlockPos origin : originsNear(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), 8, size)) {
            if (SectionPos.blockToSectionCoord(origin.getX()) == chunk.x
                    && SectionPos.blockToSectionCoord(origin.getZ()) == chunk.z) {
                return origin;
            }
        }
        return null;
    }

    /**
     * Draws the shrine at {@code origin}, keeping only what falls inside {@code clip}.
     *
     * Called once per chunk the structure touches, from {@link SpiritShrinePiece}. Safe to
     * run repeatedly: every pass computes the same blocks and the clip keeps each to its
     * own share.
     */
    public static void build(WorldGenLevel level, BlockPos origin, BoundingBox clip) {
        StructureTemplate template = templateFor(level);
        if (template != null) build(level, template, origin, clip);
    }

    /** Lays the structure down, keeping only what falls inside {@code clip}. */
    private static void build(WorldGenLevel level, StructureTemplate template,
                              BlockPos origin, BoundingBox clip) {

        // MEASURED UNCLIPPED, PLACED CLIPPED — the trap the temple documents. filterBlocks
        // honours the bounding box in whatever settings it is handed, so the marker has to
        // be read through settings that carry no clip at all; markerOffset makes its own
        // for exactly that reason. Read it through THIS pass's clip instead and every pass
        // but the one that happens to own the marker would conclude the structure has none.
        BlockPos marker = markerOffset(template);

        StructurePlaceSettings placing = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setBoundingBox(clip);

        template.placeInWorld(level, origin, origin, placing,
                level.getRandom(), Block.UPDATE_CLIENTS);

        // Subject to the clip like every other write, so the pass that owns that block is
        // the one that places the beacon — and every chunk the shrine touches gets a pass,
        // so exactly one always does. Writing the beacon is also what clears the marker.
        if (marker != null) {
            BlockPos at = origin.offset(marker);
            if (clip.isInside(at)) {
                level.setBlock(at, SHRINE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);

                // The block entity has to be ASKED FOR during generation. A chunk being
                // generated is a ProtoChunk, whose setBlockState does not create one the
                // way a live chunk's does — so a beacon placed and left would sit there
                // without the block entity every beacon is supposed to have. Reading it
                // back is what builds it: WorldGenRegion.getBlockEntity makes one for any
                // state that wants one and registers it with the chunk.
                level.getBlockEntity(at);
            }
        }
    }

    // ------------------------------------------------------------------
    // WHERE THEY ARE — pure functions of the island, and of nothing else
    // ------------------------------------------------------------------

    /**
     * Every shrine origin whose structure could reach within {@code slack} blocks of here.
     *
     * TWO EARLY-OUTS, in the order that rejects most for least, because this runs for every
     * chunk generated in the dimension and the twenty-five cells around one hold a hundred
     * islands. The reach test is a couple of multiplies and throws out everything that is
     * simply too far away; the chance roll is one hash and throws out seven eighths of what
     * is left. Only the handful that survive both pay for the ground sampling.
     *
     * The reach is measured the way the boxes are, corner to corner rather than as the
     * crow flies, so it can never reject a shrine that genuinely overlaps.
     */
    private static List<BlockPos> originsNear(int centreX, int centreZ, int slack, Vec3i size) {
        int cellX = Math.floorDiv(centreX, SpiritIslands.cell());
        int cellZ = Math.floorDiv(centreZ, SpiritIslands.cell());

        // TWICE the structure, not once. A shrine's centre column sits within MAX_OUT of
        // the island's radius, but the block being asked about may be half a structure
        // further out again (the origin is the corner, not the centre) and then a whole
        // structure further than that (the beacon is somewhere inside it). Two covers both
        // with room to spare, and being generous here only costs a site computation.
        int span = 2 * Math.max(size.getX(), size.getZ()) + slack;

        List<BlockPos> found = null;

        for (int dx = -CELL_REACH; dx <= CELL_REACH; dx++) {
            for (int dz = -CELL_REACH; dz <= CELL_REACH; dz++) {
                for (SpiritIslands.Island island : SpiritIslands.allIn(cellX + dx, cellZ + dz)) {

                    double reach = island.radius() * MAX_OUT + span;
                    if (Math.abs(island.centreX() - centreX) > reach) continue;
                    if (Math.abs(island.centreZ() - centreZ) > reach) continue;

                    BlockPos origin = siteFor(island, size);
                    if (origin == null) continue;

                    if (found == null) found = new ArrayList<>(2);
                    found.add(origin);
                }
            }
        }

        // The overwhelmingly common answer, and the list is only built when there is
        // something to put in it.
        return found == null ? List.of() : found;
    }

    /**
     * Where this island's shrine stands, or null if it has none.
     *
     * The chance is rolled FIRST and rejects most islands for the cost of one hash, which
     * is what keeps this cheap enough to ask of a hundred islands per chunk.
     *
     * Positions are drawn by area rather than by radius — the square root — for the reason
     * Ice barrage and lava rain document: a ring at four fifths of the radius holds far
     * more ground than one near the middle, so a flat draw would bunch every shrine around
     * the island's centre.
     */
    private static BlockPos siteFor(SpiritIslands.Island island, Vec3i size) {
        if (SpiritIslands.pick(island.seed(), SALT_CHANCE, 100) >= chanceInHundred()) return null;

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            long seed = island.seed() ^ (attempt * 0x9E3779B97F4A7C15L);

            double angle = SpiritIslands.pick(seed, SALT_ANGLE, 3600) / 3600.0 * Math.PI * 2.0;
            double out = Math.sqrt(SpiritIslands.pick(seed, SALT_DISTANCE, 1000) / 1000.0) * MAX_OUT;

            int x = island.centreX() + (int) Math.round(Math.cos(angle) * out * island.radius());
            int z = island.centreZ() + (int) Math.round(Math.sin(angle) * out * island.radius());

            BlockPos origin = standingAt(island, x, z, size);
            if (origin != null) return origin;
        }

        // Every attempt landed on a slope or off the edge. The island simply has no shrine,
        // which is a far better answer than one hanging over the void.
        return null;
    }

    /**
     * Where a shrine centred on this column would stand, or null if it cannot stand here.
     *
     * NOTHING READS A BLOCK, and it could not: a chunk decides its share of a shrine long
     * before the neighbouring chunks exist. The ground is asked of {@link SpiritIslands}
     * instead — the same function the island feature uses when it actually lays the blocks,
     * so the height planned for and the height built are the same number by construction.
     *
     * The four corners must all belong to THIS island, which is what keeps a shrine off the
     * rim and off the seam where two overlapping islands meet.
     *
     * THE FLOOR IS THE LOWEST CORNER, not the middle. Sitting the structure at the middle
     * height would leave the low corner hanging in the air over a gentle slope; sitting it
     * at the lowest means the high corners are buried a block or two instead, which is what
     * a building on a hillside should look like.
     *
     * The structure's OWN bottom layer lands on that block, replacing the grass or sand
     * rather than sitting on top of it — the temple's convention, and it means the .nbt
     * wants its floor at y = 0 to come out flush with the ground.
     */
    private static BlockPos standingAt(SpiritIslands.Island island, int x, int z, Vec3i size) {
        int minX = x - size.getX() / 2;
        int minZ = z - size.getZ() / 2;

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int height = surfaceOursAt(island,
                        minX + dx * (size.getX() - 1), minZ + dz * (size.getZ() - 1));
                if (height == NONE) return null;

                lowest = Math.min(lowest, height);
                highest = Math.max(highest, height);
            }
        }

        if (highest - lowest > MAX_SLOPE) return null;

        return new BlockPos(minX, lowest, minZ);
    }

    /** The surface height of a column, but only if this island is the one that owns it. */
    private static int surfaceOursAt(SpiritIslands.Island island, int x, int z) {
        SpiritIslands.Island owner = SpiritIslands.coveringOrNull(x, z);
        if (owner == null || !owner.equals(island)) return NONE;

        return SpiritIslands.surfaceOf(island, x, z);
    }

    /** Everything a shrine placed from this corner occupies. */
    public static BoundingBox boxAt(BlockPos origin, Vec3i size) {
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1,
                origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1);
    }

    // ------------------------------------------------------------------
    // RECOGNISING ONE
    // ------------------------------------------------------------------

    /**
     * Whether this block is a shrine's beacon.
     *
     * WORKED OUT, NOT REMEMBERED: the shrines near this column are recomputed and the
     * position is compared against where their beacons must be. That is what makes the
     * whole feature need no save data — and it means a beacon a player carries in and
     * places themselves is not a shrine, because it is not where a shrine's beacon goes.
     *
     * Cheap in the order it asks: the dimension and the block are checked by the caller
     * before any of this runs, so an ordinary right click anywhere in the game never
     * reaches the island arithmetic at all.
     */
    public static boolean isShrine(ServerLevel level, BlockPos pos) {
        if (!SpiritWorld.isSpiritWorld(level)) return false;

        StructureTemplate template = templateFor(level.getServer().getStructureManager());
        if (template == null) return false;

        BlockPos marker = markerOffset(template);
        if (marker == null) return false;

        for (BlockPos origin : originsNear(pos.getX(), pos.getZ(), 0, template.getSize())) {
            if (origin.offset(marker).equals(pos)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // USING ONE
    // ------------------------------------------------------------------

    /**
     * Draws a shrine's power, once per player per shrine.
     *
     * The shrine is identified by its beacon's POSITION, which is safe as an identifier for
     * exactly the reason the placement is: shrines never move, and a given position holds
     * the same shrine for the life of the world. Nothing is written on the shrine itself —
     * a second player finds it as untouched as the first did.
     */
    public static void use(ServerPlayer player, BlockPos beacon) {
        BendingData data = player.getData(ModAttachments.BENDING_DATA);

        if (data.hasUsedShrine(beacon.asLong())) {
            player.sendSystemMessage(Component.literal(
                            "You have already drawn power from this shrine.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        data.recordShrine(beacon.asLong());
        player.setData(ModAttachments.BENDING_DATA, data);

        // The client keeps its own copy of the bonus: it is what the HUD's maximum reads
        // from, and what the chi bar takes its colour from. Without this the bar would
        // still be drawn against the old maximum until something else happened to sync.
        PacketDistributor.sendToPlayer(player, SyncStatsPacket.of(data));

        celebrate(player.serverLevel(), beacon);

        player.sendSystemMessage(Component.literal(
                        "The shrine's power settles into you. Max Chi is now " + data.getMaxChi()
                                + " (" + data.getShrinesUsed() + " shrine"
                                + (data.getShrinesUsed() == 1 ? "" : "s") + " drawn).")
                .withStyle(ChatFormatting.AQUA));
    }

    /** The sound and the burst that say it worked. */
    private static void celebrate(ServerLevel level, BlockPos beacon) {
        double x = beacon.getX() + 0.5;
        double y = beacon.getY() + 0.5;
        double z = beacon.getZ() + 0.5;

        level.playSound(null, beacon, SoundEvents.BEACON_POWER_SELECT,
                SoundSource.BLOCKS, 1.0F, 1.2F);

        // Batched rather than aimed, for the reason Fire Rain documents: a particle with a
        // direction needs count = 0 and costs one packet each, where a count buys a whole
        // cloud for one. A burst wants volume, not aim.
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 80, 0.6, 1.0, 0.6, 0.08);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 40, 0.5, 0.8, 0.5, 0.04);
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 1.0, z, 60, 0.8, 1.2, 0.8, 0.5);
    }

    // ------------------------------------------------------------------
    // THE TEMPLATE
    // ------------------------------------------------------------------

    private static StructureTemplate templateFor(WorldGenLevel level) {
        return templateFor(level.getLevel().getServer().getStructureManager());
    }

    public static StructureTemplate templateFor(StructureTemplateManager templates) {
        return templates.get(TEMPLATE).orElse(null);
    }

    /**
     * Where the marker sits INSIDE the template, or null if it has none.
     *
     * Read from the template at its own origin, so the answer is an offset rather than a
     * world position — which is what lets {@link #isShrine} work out a beacon's position
     * from a shrine's corner without placing anything.
     */
    private static BlockPos markerOffset(StructureTemplate template) {
        List<StructureTemplate.StructureBlockInfo> markers = template.filterBlocks(
                BlockPos.ZERO, new StructurePlaceSettings().setRotation(Rotation.NONE), MARKER_BLOCK);

        return markers.isEmpty() ? null : markers.get(0).pos();
    }
}
