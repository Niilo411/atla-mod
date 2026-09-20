package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.spirit.SpiritWorld;
import com.minecraft.atlamod.spirit.TempleStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.StructureManager;

/**
 * The one piece a spirit temple is made of.
 *
 * It builds nothing itself — it hands the job to {@link TempleStructure#placeAt}, which
 * is the only thing in the mod that places a temple block. That is the whole reason this
 * is a hand-written structure rather than a jigsaw one: a jigsaw would assemble the
 * temple from a template and would be a second, competing way to build the same room.
 *
 * WRITTEN ONCE PER CHUNK THE TEMPLE TOUCHES, and each time told to keep only that
 * chunk's share. Vanilla calls postProcess for every chunk a piece's bounding box
 * overlaps and hands it the box to stay inside; the temple draws its whole self each time
 * and the clip discards the rest. The passes meet exactly because every one of them
 * computes the same positions from the same origin.
 *
 * The ORIGIN is saved rather than recovered from the bounding box. It could be derived —
 * the box is symmetrical about it — but only by repeating the layout arithmetic out here,
 * which is exactly the knowledge this package is supposed not to hold.
 */
public class SpiritTemplePiece extends StructurePiece {

    private static final String ORIGIN_X = "TempleX";
    private static final String ORIGIN_Y = "TempleY";
    private static final String ORIGIN_Z = "TempleZ";

    private final BlockPos origin;

    public SpiritTemplePiece(BlockPos origin) {
        super(ModStructures.SPIRIT_TEMPLE_PIECE.get(), 0, TempleStructure.boundingBoxAt(origin));
        this.origin = origin;
    }

    public SpiritTemplePiece(CompoundTag tag) {
        super(ModStructures.SPIRIT_TEMPLE_PIECE.get(), tag);
        this.origin = new BlockPos(tag.getInt(ORIGIN_X), tag.getInt(ORIGIN_Y), tag.getInt(ORIGIN_Z));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt(ORIGIN_X, origin.getX());
        tag.putInt(ORIGIN_Y, origin.getY());
        tag.putInt(ORIGIN_Z, origin.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        TempleStructure.Temple temple = TempleStructure.placeAt(level, origin, box);

        // Lights the portal if this is the Spirit World and leaves it dark if not. Safe to
        // run on every chunk pass: lighting is just setting the same blocks again, and the
        // clip keeps each pass to its own share like everything else.
        SpiritWorld.lightIfHere(level, temple, box);
    }
}
