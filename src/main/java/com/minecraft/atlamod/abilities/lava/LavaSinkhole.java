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
 * Aimed at a body, not at a patch of floor — the design says "under a player or mob", and
 * {@link #canStart} refuses the cast when there is nobody in view, so a miss costs nothing.
 */
public class LavaSinkhole implements Ability {

    /** How far away a victim can be picked, in blocks. INVENTED — the design says none. */
    private static final double REACH = 20.0;

    /** How far off the aim line still counts, in blocks. The mod's usual figure. */
    private static final double TOLERANCE = 2.0;

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

    /** Nobody in view means nothing to open the ground under, so the cast is free. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (Aiming.nearestAlongLook(player, REACH, TOLERANCE) != null) return true;

        player.displayClientMessage(Component.literal(
                "§7Nobody there to drop."), true);
        return false;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Asked again rather than remembered from canStart: a target that stepped out
        // of view between the two is simply not there any more, and the alternative is
        // opening a hole under wherever they used to be.
        LivingEntity victim = Aiming.nearestAlongLook(player, REACH, TOLERANCE);
        if (victim == null) return;

        BlockPos feet = victim.blockPosition();

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
}
