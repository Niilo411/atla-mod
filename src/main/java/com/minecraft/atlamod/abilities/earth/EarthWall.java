package com.minecraft.atlamod.abilities.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Defensive / Earth. Pulls a wall of earth up out of the ground in front of the
 * bender — six blocks across their facing, and as tall as they care to hold for.
 *
 * Laid on Firewall's geometry: the same six-block line at the same two blocks of
 * standoff, so the two defensive walls sit in the same place relative to the caster
 * and a bender who knows one knows the other. Everything about HOW it rises, stands
 * and sinks belongs to {@link RaisedEarth}; this class only decides where.
 */
public class EarthWall extends RaisedEarth {

    /** Six blocks across, as asked. */
    private static final int LENGTH = 6;

    /** How far in front of the bender the wall goes up, matching Firewall. */
    private static final double DISTANCE = 2.0;

    @Override
    public String getName() {
        return "Earth wall";
    }

    @Override
    protected int chiCost() {
        return 50;
    }

    @Override
    protected int xpReward() {
        return 5;
    }

    @Override
    protected List<BlockPos> surfaces(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 flat = facing(player);

        // Perpendicular in the horizontal plane, so the wall spans left to right
        // across the bender's facing rather than stretching away from them.
        Vec3 across = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 origin = player.position().add(flat.scale(DISTANCE));

        List<BlockPos> found = new ArrayList<>(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            // Centre the run on the bender: offsets go -2.5 .. 2.5 for a length of 6.
            double offset = i - (LENGTH - 1) / 2.0;
            Vec3 spot = origin.add(across.scale(offset));

            BlockPos surface = EarthWorks.surfaceUnder(
                    level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN);
            if (surface != null) found.add(surface);
        }

        return found;
    }
}
