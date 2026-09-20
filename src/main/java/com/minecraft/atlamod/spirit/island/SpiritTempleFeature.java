package com.minecraft.atlamod.spirit.island;

import com.minecraft.atlamod.spirit.TempleStructure;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Scatters spirit temples through the world.
 *
 * A FEATURE RATHER THAN A STRUCTURE, deliberately, even though "as uncommon as ruined
 * portals" is describing a structure. A jigsaw structure would place its own blocks from
 * a template and would never call {@link TempleStructure#placeAt} — which is the one
 * thing the temple was built to keep. Every temple in the game comes out of that single
 * method, so the hand-made .nbt still only has to replace its insides. A structure would
 * have made this class a second, competing way to build a temple.
 *
 * What is given up is {@code /locate}, which a feature has no part in, and the even
 * spacing a structure set provides — see the placed feature's rarity note.
 *
 * WHERE it may stand is not decided here either. This asks
 * {@link TempleStructure#canStandAt}, because how much flat ground a temple needs is a
 * fact about its size, and its size is that class's business.
 */
public class SpiritTempleFeature extends Feature<NoneFeatureConfiguration> {

    public SpiritTempleFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();

        // The placement gave us the first empty block above the surface; the temple's
        // floor replaces the solid one underneath it.
        BlockPos floor = context.origin().below();

        if (!TempleStructure.canStandAt(level, floor)) return false;

        TempleStructure.placeAt(level, floor);
        return true;
    }
}
