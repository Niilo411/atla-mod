package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Right / Ice. Five seconds of gathering, and then a shell of real ice closes around
 * the bender — five blocks across, and solid.
 *
 * Waterbending's Water Sphere holds the sea BACK; this one is the opposite idea, and
 * the opposite use: it is cover. The bender is sealed inside something that has to be
 * broken through, which is why it needs five seconds to raise and cannot be thrown up
 * the moment trouble arrives.
 *
 * The shell is hollow, and deliberately so — the two blocks the bender occupies are
 * left clear along with everything else inside, so raising it does not entomb them.
 */
public class IceSphere implements ChargedAbility {

    /** Diameter of 5 means a radius of 2 blocks from the centre. */
    private static final double RADIUS = 2.5;

    /**
     * How thick the shell is drawn, as a band around the radius.
     *
     * A sphere plotted as "distance == radius" on a block grid comes out full of
     * holes, because almost no block centre lands exactly on it. Filling a band
     * instead gives a closed shell, which is the whole point of the ability.
     */
    private static final double THICKNESS = 0.6;

    /** How long the shell stands before melting. */
    private static final int STAND_TICKS = 400; // 20 seconds

    @Override
    public String getName() {
        return "Ice sphere";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
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
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();

        // Closing in as it fills, so the shell visibly gathers from the air around
        // the bender rather than appearing all at once at the end.
        Ice.frost(level, player.position().add(0.0, 1.0, 0.0),
                6, RADIUS * (1.6 - progress));
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Centred on the bender's middle rather than their feet, so the shell sits
        // around them instead of half sunk into the floor.
        BlockPos centre = BlockPos.containing(player.position().add(0.0, 1.0, 0.0));

        int reach = (int) Math.ceil(RADIUS + THICKNESS);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    // The band, not the ball: everything inside is left clear.
                    if (distance < RADIUS - THICKNESS || distance > RADIUS + THICKNESS) continue;

                    IceWorks.freeze(level, centre.offset(dx, dy, dz),
                            Blocks.PACKED_ICE.defaultBlockState(), STAND_TICKS);
                }
            }
        }

        Ice.form(level, player.position(), 1.2F, 0.9F);
        Ice.frost(level, player.position().add(0.0, 1.0, 0.0), 60, 2.0);
    }
}
