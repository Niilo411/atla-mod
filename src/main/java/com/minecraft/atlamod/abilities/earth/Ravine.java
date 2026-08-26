package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Earth. Tears the ground open in front of the bender — seven blocks out
 * and five deep.
 *
 * The one earth ability that takes the world apart and does not put it back. Everything
 * else in the element borrows: a wall stands and sinks, a spike rises and goes, a grab
 * lays its slices and takes them up again. A ravine is permanent, which is what the two
 * and a half minute cooldown is really paying for.
 *
 * Nothing is dropped. At over a hundred blocks a cast that would be a hundred items to
 * wade through, and this is not a mining ability — Mine is the one that gives you the
 * blocks. The earth here is collapsed, not harvested.
 */
public class Ravine implements Ability {

    /** How far ahead the tear reaches. */
    private static final int LENGTH = 10;

    /** How far down it goes, measured from each column's own surface. */
    private static final int DEPTH = 5;

    /** Half the width, so five columns across. */
    private static final int HALF_WIDTH = 2;

    /** How far each column hunts for its own surface, so the tear follows the ground. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    @Override
    public String getName() {
        return "Ravine";
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
        return 3000; // 150 seconds
    }

    /** Refused for free when there is no ground in front to tear. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 spot = player.position().add(facing(player).scale(2.0));
        if (EarthWorks.surfaceUnder(level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN) != null) {
            return true;
        }

        player.displayClientMessage(
                Component.literal("§6There is no ground here to tear open!"), true);
        return false;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 forward = facing(player);
        Vec3 across = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 origin = player.position();

        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 2.0F, 0.35F);

        // Starts a block out rather than underfoot: a bender should not drop into their
        // own ravine the instant they open it.
        for (int distance = 1; distance <= LENGTH; distance++) {
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                Vec3 spot = origin
                        .add(forward.scale(distance))
                        .add(across.scale(side));

                tearColumn(level, player, BlockPos.containing(spot));
            }
        }
    }

    /**
     * Opens one column, from its own surface downward.
     *
     * Each finds its own footing, so a ravine torn across a slope follows the slope
     * instead of floating over the low end and stopping short on the high one.
     */
    private static void tearColumn(ServerLevel level, ServerPlayer player, BlockPos target) {
        BlockPos surface = EarthWorks.surfaceUnder(level, target, UP_SCAN, DOWN_SCAN);
        if (surface == null) return;

        BlockState top = level.getBlockState(surface.below());

        for (int depth = 1; depth <= DEPTH; depth++) {
            BlockPos pos = surface.below(depth);
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;

            // Fluids are left where they are. Breaking them only drains whatever is
            // sitting nearby, and the ravine will fill on its own if it opens into
            // water — which is a far more interesting outcome than an empty trench.
            if (!state.getFluidState().isEmpty()) continue;

            // Bedrock and its friends stop the tear at that depth, as they should.
            if (state.getDestroySpeed(level, pos) < 0.0F) continue;

            // Not dropped: see the class note.
            level.destroyBlock(pos, false, player);
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top),
                surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5,
                14, 0.4, 0.3, 0.4, 0.12);
    }

    /**
     * Where the bender is facing, flattened onto the ground.
     *
     * The tear runs along the ground whether they are looking at the sky or their feet,
     * and looking straight up or down falls back to the way the body is turned rather
     * than leaving it with no direction at all.
     */
    private static Vec3 facing(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);

        if (flat.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }

        return flat.normalize();
    }
}
