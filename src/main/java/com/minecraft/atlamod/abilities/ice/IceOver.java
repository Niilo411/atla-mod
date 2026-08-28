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
 * Left / Ice. The ground goes over for fifteen blocks in every direction: the whole
 * area turns to ice for thirty seconds, and everything standing on it is slowed.
 *
 * The ground itself is what freezes — every walkable block in range BECOMES ice and is
 * put back exactly as it was when the sheet melts. It used to lay a fresh layer on top
 * instead, which raised the floor a block wherever it went: a step to trip over at the
 * edge of the area, and a sealed doorway wherever it met one.
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
                BlockPos ground = groundNear(level, centre.offset(dx, 0, dz));
                if (ground == null) continue;

                IceWorks.freezeOver(level, ground, Blocks.PACKED_ICE.defaultBlockState(), ICE_TICKS);
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
     * The walkable ground block in this column — the block that BECOMES ice.
     *
     * The sheet used to be laid in the free space above the ground instead, which
     * raised the whole floor by a block: a step to trip over at the edge of the area,
     * a sealed doorway wherever it met one, and a bender standing a block higher than
     * they were a moment ago. Freezing the ground itself is what "the ground goes
     * over" actually means, and it leaves the world exactly as tall as it was.
     *
     * Searched down first and then up, so the sheet follows a slope and steps over a
     * low wall instead of stopping at the bender's own Y. Returns null where there is
     * nothing to freeze, which simply leaves a gap in the sheet — the same way a
     * raised wall is left short rather than broken when a column has no ground.
     */
    private static BlockPos groundNear(ServerLevel level, BlockPos column) {
        for (int dy = 0; dy >= -SURFACE_SCAN; dy--) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at.below();
        }
        for (int dy = 1; dy <= SURFACE_SCAN; dy++) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at.below();
        }
        return null;
    }

    /**
     * Free space with something solid underneath.
     *
     * Still asked about the SPACE rather than the ground, because that is what makes a
     * surface a surface: a block with something on top of it is buried, not walked on,
     * and freezing it would put a sheet of ice under somebody's floor. What is frozen
     * is the block below whatever this finds.
     */
    private static boolean freezable(ServerLevel level, BlockPos at) {
        var here = level.getBlockState(at);
        if (!here.isAir() && !here.canBeReplaced()) return false;
        if (!here.getFluidState().isEmpty()) return false;

        return level.getBlockState(at.below()).isSolid();
    }
}
