package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.earth.EarthTraps;
import com.minecraft.atlamod.abilities.earth.EarthWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Masterclass / Gravity. Five seconds of gathering, then sixteen blocks are pulled
 * out of the ground within twenty-five blocks of the bender and driven into the
 * body of whoever they are looking at — locked in place the way Earth Trap locks
 * its victims, unable to move for eight seconds while the stone buries them.
 *
 * The blocks are spawned OVERLAPPING the victim rather than around them, which is
 * what "encasing" actually needs to hurt: vanilla suffocates anything whose eyes
 * are inside a block that blocks motion, the same trick Freeze relies on, and here
 * that suffocation is wanted rather than guarded against — it stacks with the
 * ability's own explicit 1 hp/sec (see {@link GravityEncases}) instead of being
 * cancelled the way Freeze cancels it for itself. The footprint is two columns
 * wide, one of which is always the victim's own block, four tall so it spans
 * comfortably past both their feet and their head — sixteen blocks in total,
 * matching the design's own count. Each column stands on its own timer via {@link
 * EarthWorks#raiseFor}, which is what gives every block back on its own once the
 * eight seconds are up without this class having to remember any of them.
 */
public class GravityEncase implements ChargedAbility {

    /** INVENTED: reach for picking the target. */
    private static final double REACH = 20.0;

    /** Half the width of the 25-block area the cage's material is drawn from. */
    private static final int MATERIAL_AREA = 2; // a 5x5 footprint, 25 columns

    private static final int TRAP_TICKS = 160; // 8 seconds

    @Override
    public String getName() {
        return "Encase";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 200;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 400; // 20 seconds
    }

    @Override
    public int getChargeTicks() {
        return 100; // 5 seconds
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE);
        if (target == null) return;

        EarthTraps.hold(target, TRAP_TICKS);
        GravityEncases.start(player, target, TRAP_TICKS);

        // Straddles the victim's own block column on both axes, so whichever corner
        // lands on them, one of the four ALWAYS does.
        BlockPos centre = target.blockPosition();
        int x0 = centre.getX() - 1;
        int x1 = centre.getX();
        int z0 = centre.getZ() - 1;
        int z1 = centre.getZ();
        int y0 = centre.getY() - 1;

        int[][] corners = { {x0, z0}, {x0, z1}, {x1, z0}, {x1, z1} };

        BlockPos casterFeet = player.blockPosition();

        for (int[] corner : corners) {
            for (int h = 0; h < 4; h++) {
                BlockPos pos = new BlockPos(corner[0], y0 + h, corner[1]);

                // Drawn from somewhere in the 25-block area around the caster, as the
                // design asks — a different sample each block, so the cage isn't one
                // material copied sixteen times.
                int dx = level.random.nextInt(MATERIAL_AREA * 2 + 1) - MATERIAL_AREA;
                int dz = level.random.nextInt(MATERIAL_AREA * 2 + 1) - MATERIAL_AREA;
                BlockState material = EarthWorks.materialUnder(level, casterFeet.offset(dx, 0, dz));

                EarthWorks.raiseFor(level, pos, material, TRAP_TICKS - EarthWorks.SLIDE_TICKS,
                        EarthWorks.SLIDE_TICKS);
            }
        }

        Gravity.gather(level, target.position(), 30, 1.5);
        Gravity.warp(level, target.position(), 1.0F);
    }
}
