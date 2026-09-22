package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.spirit.SpiritBiomeSource;
import com.minecraft.atlamod.spirit.island.SpiritShrines;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Spirit shrines as a real STRUCTURE, which is what makes {@code /locate} work.
 *
 * This replaced placement from inside the island feature. The shrines went exactly where
 * they go now — the arithmetic in {@link SpiritShrines} is untouched — but a feature leaves
 * no record of what it placed, so nothing could ever be asked to find one. A structure is
 * registered and remembered per chunk, and is therefore locatable.
 *
 * IT DECIDES EVERYTHING ITSELF, and its structure set is spaced at one chunk so that it
 * gets the chance to. That is the opposite of how a vanilla structure works, where the
 * grid IS the rarity and the structure only vets the site it is offered. Here the rarity is
 * the per-island roll, and a coarse grid would place only the fraction of shrines whose
 * corner happened to land on it — so every chunk is offered, and almost every one of them
 * is answered with {@link Optional#empty}.
 *
 * That makes this method's cost the thing to watch, since it runs for every chunk in the
 * dimension. It is a few dozen hashes for the overwhelming majority of them: see
 * {@code SpiritShrines.originsNear}, which rejects distant islands on a couple of multiplies
 * before anything else is computed.
 *
 * NOTHING HERE READS A BLOCK, and it could not — a structure decides its position long
 * before the chunk it sits in exists. The Spirit World makes that easy rather than hard:
 * its ground is a pure function of the position, so the height a shrine plans around and
 * the height the island feature actually builds are the same number by construction.
 */
public class SpiritShrineStructure extends Structure {

    public static final MapCodec<SpiritShrineStructure> CODEC = simpleCodec(SpiritShrineStructure::new);

    public SpiritShrineStructure(StructureSettings settings) {
        super(settings);
    }

    /**
     * The shrine whose corner falls in this chunk, if there is one.
     *
     * THE SPIRIT WORLD ONLY, tested through the biome source — a GenerationContext carries
     * no dimension of its own, and a spirit biome source is only ever used by the one. The
     * biome tag would mostly cover this, but the islands are a fact about that dimension
     * and asking about them anywhere else would be meaningless rather than merely wasteful.
     */
    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(context.chunkGenerator().getBiomeSource() instanceof SpiritBiomeSource)) {
            return Optional.empty();
        }

        StructureTemplate template = SpiritShrines.templateFor(context.structureTemplateManager());
        if (template == null) return Optional.empty();

        Vec3i size = template.getSize();
        ChunkPos chunkPos = context.chunkPos();

        BlockPos origin = SpiritShrines.originIn(chunkPos, size);
        if (origin == null) return Optional.empty();

        BoundingBox bounds = SpiritShrines.boxAt(origin, size);

        return Optional.of(new GenerationStub(origin,
                builder -> builder.addPiece(new SpiritShrinePiece(origin, bounds))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SPIRIT_SHRINE.get();
    }
}
