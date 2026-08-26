package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Left / Ice. The ground goes over for fifteen blocks in every direction: a sheet of
 * real ice laid across the whole area for thirty seconds, and everything standing on
 * it slowed.
 *
 * The caster is deliberately spared the slowness — the design asks for it explicitly,
 * and it is what makes the ability an advantage rather than a mutual inconvenience.
 * The ice itself is laid under them like everywhere else, so they still skate on it.
 */
public class IceOver implements Ability {

    /** 15 by 15 means seven blocks either side of the bender, plus the middle. */
    private static final int HALF = 7;

    /** How long the sheet stays down. */
    private static final int ICE_TICKS = 600; // 30 seconds

    /** How far up and down a surface is looked for, so the sheet follows terrain. */
    private static final int SURFACE_SCAN = 4;

    private static final int SLOWNESS_LEVEL = 1; // Slowness II

    @Override
    public String getName() {
        return "Ice over";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 200;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 1000; // 50 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos centre = player.blockPosition();

        // --- The sheet ---
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                BlockPos surface = surfaceNear(level, centre.offset(dx, 0, dz));
                if (surface == null) continue;

                IceWorks.freeze(level, surface, Blocks.PACKED_ICE.defaultBlockState(), ICE_TICKS);
            }
        }

        // --- The chill ---
        // Everything in the box, and getEntities(player, ...) excludes the caster for
        // free — which is exactly what "this ability does not freeze the user" asks
        // for. The same call was a bug for Earth spike and is right here.
        AABB area = new AABB(centre).inflate(HALF, SURFACE_SCAN + 2, HALF);

        for (Entity caught : level.getEntities(player, area)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            Ice.chill(living, ICE_TICKS, SLOWNESS_LEVEL);
        }

        Ice.form(level, player.position(), 1.4F, 0.7F);
        Ice.frost(level, player.position().add(0.0, 1.0, 0.0), 80, 4.0);
    }

    /**
     * The first free space above solid ground near the given column.
     *
     * Searched down first and then up, so the sheet follows a slope and steps over a
     * low wall instead of stopping at the bender's own Y. Returns null where there is
     * nothing to freeze onto, which simply leaves a gap in the sheet — the same way a
     * raised wall is left short rather than broken when a column has no ground.
     */
    private static BlockPos surfaceNear(ServerLevel level, BlockPos column) {
        for (int dy = 0; dy >= -SURFACE_SCAN; dy--) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at;
        }
        for (int dy = 1; dy <= SURFACE_SCAN; dy++) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at;
        }
        return null;
    }

    /** Free space with something solid underneath — somewhere ice can actually form. */
    private static boolean freezable(ServerLevel level, BlockPos at) {
        var here = level.getBlockState(at);
        if (!here.isAir() && !here.canBeReplaced()) return false;
        if (!here.getFluidState().isEmpty()) return false;

        return level.getBlockState(at.below()).isSolid();
    }
}
