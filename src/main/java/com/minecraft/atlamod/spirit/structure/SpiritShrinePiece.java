package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.spirit.island.SpiritShrines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The one piece a spirit shrine is made of.
 *
 * It builds nothing itself — it hands the job to {@link SpiritShrines#build}, which stays
 * the only thing in the mod that places a shrine block. Exactly the shape
 * {@link SpiritTemplePiece} has, and for the same reason: a jigsaw piece would be a second,
 * competing way to assemble the same structure.
 *
 * WRITTEN ONCE PER CHUNK THE SHRINE TOUCHES, and each time told to keep only that chunk's
 * share. Vanilla calls this for every chunk a piece's bounding box overlaps and hands it
 * that chunk's writable area; the shrine draws its whole self each time and the clip
 * discards the rest. The passes meet exactly because every one of them computes the same
 * positions from the same origin.
 *
 * The ORIGIN is saved rather than recovered from the bounding box. It could be derived —
 * it is the box's minimum corner — but only by repeating knowledge of how a shrine sits in
 * its own box, which is exactly what this package is supposed not to hold.
 */
public class SpiritShrinePiece extends StructurePiece {

    private static final String ORIGIN_X = "ShrineX";
    private static final String ORIGIN_Y = "ShrineY";
    private static final String ORIGIN_Z = "ShrineZ";

    private final BlockPos origin;

    public SpiritShrinePiece(BlockPos origin, BoundingBox bounds) {
        super(ModStructures.SPIRIT_SHRINE_PIECE.get(), 0, bounds);
        this.origin = origin;
    }

    public SpiritShrinePiece(CompoundTag tag) {
        super(ModStructures.SPIRIT_SHRINE_PIECE.get(), tag);
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
        SpiritShrines.build(level, origin, box);
    }
}
