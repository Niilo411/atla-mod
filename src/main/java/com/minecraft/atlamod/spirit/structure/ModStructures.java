package com.minecraft.atlamod.spirit.structure;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's world-generation structures.
 *
 * TWO registries, not one, and they are easy to confuse. A StructureType is how a
 * structure is READ OUT OF JSON — it is little more than a codec. A StructurePieceType is
 * how one of its pieces is READ BACK OUT OF A SAVE, which is a different job at a
 * different time. A structure with only the first registered loads from the datapack and
 * then fails the moment a world containing it is reloaded.
 *
 * What is actually placed, and where, is data: worldgen/structure/spirit_temple.json says
 * which biomes and which generation step, and worldgen/structure_set/spirit_temples.json
 * says how far apart. Both can be retuned without rebuilding the mod.
 */
public final class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Atlamod.MODID);

    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Atlamod.MODID);

    /** Must match the "type" in worldgen/structure/spirit_temple.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<SpiritTempleStructure>> SPIRIT_TEMPLE =
            STRUCTURE_TYPES.register("spirit_temple",
                    () -> (StructureType<SpiritTempleStructure>) () -> SpiritTempleStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SPIRIT_TEMPLE_PIECE =
            PIECE_TYPES.register("spirit_temple",
                    () -> (StructurePieceType.ContextlessType) SpiritTemplePiece::new);

    /** Must match the "type" in worldgen/structure/spirit_shrine.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<SpiritShrineStructure>> SPIRIT_SHRINE =
            STRUCTURE_TYPES.register("spirit_shrine",
                    () -> (StructureType<SpiritShrineStructure>) () -> SpiritShrineStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SPIRIT_SHRINE_PIECE =
            PIECE_TYPES.register("spirit_shrine",
                    () -> (StructurePieceType.ContextlessType) SpiritShrinePiece::new);

    /** The tag the portal system searches by when it looks for the nearest temple. */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure>
            SPIRIT_TEMPLE_TAG = net.minecraft.tags.TagKey.create(Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_temple"));

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECE_TYPES.register(modEventBus);
    }
}
