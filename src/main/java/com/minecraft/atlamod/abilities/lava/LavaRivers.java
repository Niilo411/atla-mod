package com.minecraft.atlamod.abilities.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Rivers of lava running away from lavabenders.
 *
 * The head of the river is the only thing tracked. Every block it lays is handed
 * straight to {@link LavaWorks} with its own timer, so the river drains from the near
 * end first, exactly as it was laid — which is what makes it read as something that
 * flowed past rather than as a strip that appeared and then vanished all at once.
 *
 * Deliberately NOT the moving-body shape {@link LavaTsunamis} uses. A wave is a wall of
 * material carried along, so it has to be taken up behind or it fills the world; a
 * river is the trail itself, and taking it up behind would leave nothing but a moving
 * dot.
 */
public final class LavaRivers {

    /** How far it runs before it peters out, in blocks. INVENTED — the design says none. */
    private static final int REACH = 20;

    /** Blocks moved per step. */
    private static final int SPEED = 1;

    /**
     * Ticks between steps. A river that advanced every tick crossed its twenty blocks
     * in a second, which reads as a strip being drawn rather than as lava running.
     */
    private static final int ADVANCE_EVERY = 2;

    /** Half the width, so the channel is three blocks across. */
    private static final int HALF_WIDTH = 1;

    /** How long each block of the river lasts. INVENTED — the design gives none. */
    private static final int LIFETIME = 300;

    /**
     * How far up and down a column looks for ground.
     *
     * Asymmetric on purpose: lava runs DOWNHILL happily and climbs nothing, so the
     * river will pour four blocks down a slope but will not step more than one block
     * up. A wall in the way therefore stops it, which is what a river meeting a wall
     * ought to do.
     */
    private static final int UP_SCAN = 1;
    private static final int DOWN_SCAN = 4;

    private static final List<River> ACTIVE = new ArrayList<>();

    private LavaRivers() {
    }

    private static final class River {
        final ServerLevel level;
        final Vec3 origin;
        final Vec3 forward;
        final Vec3 across;

        int head = 0;
        int age = 0;

        River(ServerLevel level, Vec3 origin, Vec3 forward) {
            this.level = level;
            this.origin = origin;
            this.forward = forward;
            this.across = new Vec3(-forward.z, 0.0, forward.x);
        }
    }

    /** Starts a river running away from a point. */
    public static void start(ServerLevel level, Vec3 origin, Vec3 forward) {
        ACTIVE.add(new River(level, origin, forward.normalize()));
        Lava.roar(level, origin, 1.5F, 0.8F);
    }

    /** Advances every river in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<River> rivers = ACTIVE.iterator();
        while (rivers.hasNext()) {
            if (!advance(rivers.next())) {
                rivers.remove();
            }
        }
    }

    /** @return false once the river has run out of distance or run into something */
    private static boolean advance(River river) {
        if (++river.age % ADVANCE_EVERY != 0) return true;

        river.head += SPEED;
        if (river.head > REACH) return false;

        Vec3 middle = river.origin.add(river.forward.scale(river.head));
        BlockPos centre = Lava.footing(
                river.level, BlockPos.containing(middle), UP_SCAN, DOWN_SCAN);

        // Nowhere for the middle of the channel to go: the river has met a wall, or run
        // off a drop taller than it can pour down. It stops here rather than skipping
        // the obstacle and carrying on beyond it.
        if (centre == null) return false;

        for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
            BlockPos column = BlockPos.containing(middle.add(river.across.scale(side)));

            // Each column searches from the MIDDLE's height rather than from its own
            // nominal one, so the channel follows the lie of the ground as one thing
            // instead of each side finding a different terrace to sit on.
            BlockPos ground = Lava.footing(river.level,
                    new BlockPos(column.getX(), centre.getY(), column.getZ()),
                    UP_SCAN, DOWN_SCAN);
            if (ground == null) continue;

            if (LavaWorks.pour(river.level, ground, LIFETIME)) {
                LavaWorks.splash(river.level, ground);
            }
        }

        return true;
    }

    /**
     * Drops every river in a level that is going away.
     *
     * The lava is LavaWorks' business and is settled by its own sweep; this only stops
     * more of it being laid into a level nothing is watching any more.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(river -> river.level == level);
    }
}
