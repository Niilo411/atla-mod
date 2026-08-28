package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import com.minecraft.atlamod.abilities.earth.EarthWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lava. The ground opens under somebody and what is underneath it is lava.
 *
 * The element's nastiest single cast, and the one that borrows most from earthbending:
 * the pit is dug with {@link EarthWorks#openFor}, which is Earth sink's own trick — take
 * the blocks out now, hand them back on a timer, and leave the world exactly as it was
 * afterwards except for whoever is inside it. The lava is then poured into the hole
 * through {@link LavaWorks}, so both halves are borrowed and both halves are given back.
 *
 * The two timers are NOT the same length, and that is load-bearing. EarthWorks will only
 * close a hole back up into EMPTY space — somebody may have built in it, and closing over
 * their work would be exactly the griefing the borrowing rule exists to prevent. So the
 * lava has to be gone BEFORE the ground comes back, or the ground would find our own lava
 * in the way, refuse to close, and leave a permanent pit. A second's grace between the two
 * is what buys that.
 *
 * Aimed at a body where there is one — the design says "under a player or mob" — and at
 * the ground the bender is looking at where there is not. It used to REFUSE the cast
 * with nobody in view, which made it unusable on an empty field: no way to test it, and
 * no way to lay it as ground work ahead of a fight.
 */
public class LavaSinkhole implements Ability {

    /** How far away a victim can be picked, in blocks. INVENTED — the design says none. */
    private static final double REACH = 20.0;

    /** How far off the aim line still counts, in blocks. The mod's usual figure. */
    private static final double TOLERANCE = 2.0;

    /** How far below the end of the look to hunt for ground when nobody is in view. */
    private static final int GROUND_SCAN = 20;

    /** Half the width of the hole, so it opens five blocks across. */
    private static final int RADIUS = 2;

    /**
     * How round it is. Slightly over the radius, so the corners of the square are left
     * out and the hole reads as a hole rather than as a pit somebody dug with a ruler.
     */
    private static final double ROUNDING = 2.4;

    /** How deep it goes. */
    private static final int DEPTH = 4;

    /** How long the lava sits there, in ticks. The design's fifteen seconds. */
    private static final int LAVA_TICKS = 300;

    /** When the ground comes back — deliberately AFTER the lava has gone. See above. */
    private static final int GROUND_TICKS = LAVA_TICKS + 20;

    @Override
    public String getName() {
        return "Lava sinkhole";
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
        return 800; // 40 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos feet = target(player);

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > ROUNDING) continue;

                for (int dy = 1; dy <= DEPTH; dy++) {
                    BlockPos pos = feet.offset(dx, -dy, dz);

                    // Taken first, filled second: the lava can only go into air, and
                    // the air is what taking the ground away produces.
                    EarthWorks.openFor(level, pos, GROUND_TICKS);
                    LavaWorks.pour(level, pos, LAVA_TICKS);
                }
            }
        }

        Lava.roar(level, Vec3.atCenterOf(feet), 2.0F, 0.6F);
        Lava.spatter(level, Vec3.atCenterOf(feet), 30, 1.5);
    }

    /**
     * Where the hole opens: under somebody if there is somebody, otherwise wherever
     * the bender is looking.
     *
     * The design says "under a player or mob", and requiring one was how that started
     * out — but it made the ability unusable on an empty field, which is exactly where
     * a bender wants to test it and exactly where a trap wants to be laid in advance.
     * A target is now the PREFERRED aim rather than a precondition, so the cast never
     * refuses and the ability can be used as ground work as well as as an attack.
     *
     * Falling back to the ground rather than to nothing is the same call Air spout
     * makes: something already paid for has to happen somewhere.
     */
    private static BlockPos target(ServerPlayer player) {
        LivingEntity victim = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (victim != null) return victim.blockPosition();

        return BlockPos.containing(
                Aiming.groundUnderLook(player, REACH, GROUND_SCAN));
    }
}
