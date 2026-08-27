package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Lava. A wall of lava thrown up across the ground in front of the bender,
 * standing twenty seconds and then cooling away to nothing.
 *
 * Firewall's geometry exactly — six blocks long, two blocks out, laid PERPENDICULAR to
 * the way the bender is looking rather than away from them, because it is a barrier to
 * put between yourself and something. What it is made of is the whole difference: fire
 * can be walked through at a cost, where three blocks of lava with no floor under them
 * is a hole full of molten rock.
 *
 * Every block goes through {@link LavaWorks}, so it only ever fills air and every one
 * of them comes back out at the end.
 */
public class LavaWall implements Ability {

    /** Wall length in blocks, matching Firewall. */
    private static final int LENGTH = 6;

    /** How tall it stands. INVENTED — the design gives the wall no height. */
    private static final int HEIGHT = 3;

    /** How far in front of the bender it is laid, in blocks. */
    private static final double DISTANCE = 2.0;

    /** How long it stands, in ticks. The design's twenty seconds. */
    private static final int LIFETIME = 400;

    /** How far up and down each column looks for ground, so it follows a slope. */
    private static final int UP_SCAN = 1;
    private static final int DOWN_SCAN = 3;

    @Override
    public String getName() {
        return "Lava wall";
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
        return 300; // 15 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = Lava.flatLook(player);

        // Perpendicular in the horizontal plane, so the wall spans across the bender's
        // facing rather than stretching away from them.
        Vec3 across = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 origin = player.position().add(forward.scale(DISTANCE));

        for (int i = 0; i < LENGTH; i++) {
            // Centred on the bender: offsets run -2.5 .. 2.5 for a length of six.
            double offset = i - (LENGTH - 1) / 2.0;
            Vec3 spot = origin.add(across.scale(offset));

            BlockPos ground = Lava.footing(level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN);
            if (ground == null) continue;

            for (int h = 0; h < HEIGHT; h++) {
                // A column that runs into something stops there rather than skipping
                // past it, so the wall never appears on the far side of a ceiling.
                if (!LavaWorks.pour(level, ground.above(h), LIFETIME)) break;
            }

            LavaWorks.splash(level, ground);
        }

        Lava.roar(level, player.position(), 1.4F, 0.9F);
    }
}
