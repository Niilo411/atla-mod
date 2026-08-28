package com.minecraft.atlamod.abilities.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Defensive / Earth. Earth wall narrowed to a single column: one block of ground
 * pushed up in front of the bender, as tall as they care to hold for.
 *
 * Same held rise, same seven-block ceiling, same half minute standing before it sinks
 * — all of it inherited from {@link RaisedEarth}. What it buys over the wall is that
 * it is cheap enough to throw up constantly: a fifth of the chi for a sixth of the
 * cover, and something to stand on rather than something to hide behind.
 */
public class EarthPillar extends RaisedEarth {

    /** How far in front of the bender the pillar goes up, matching Earth wall. */
    private static final double DISTANCE = 2.0;

    @Override
    public String getName() {
        return "Earth pillar";
    }

    @Override
    protected int chiCost() {
        return 10;
    }

    @Override
    protected int xpReward() {
        return 1;
    }

    /**
     * Half again as fast as a wall — thirteen ticks a block against twenty.
     *
     * A wall is cover, and taking its time coming up is part of what it is; a pillar
     * is a step, and a bender who wants to be four blocks higher wants to be there
     * now. Nothing else about the raise changes, so a full seven-block pillar still
     * costs the same held key, just less of it.
     */
    @Override
    protected int ticksPerLayer() {
        return 13;
    }

    /** One column, standing where the wall's middle would have been. */
    @Override
    protected List<BlockPos> surfaces(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 spot = player.position().add(facing(player).scale(DISTANCE));

        List<BlockPos> found = new ArrayList<>(1);
        BlockPos surface = EarthWorks.surfaceUnder(
                level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN);
        if (surface != null) found.add(surface);

        return found;
    }
}
