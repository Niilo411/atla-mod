package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Gravity. Shoves whoever is in front of the bender ten blocks along
 * the bender's own look, hurting them harder if the shove ends against a wall rather
 * than open air — and tears up the ground in the corridor they are thrown through.
 *
 * The wall bonus is found with a RAYCAST from the target rather than by waiting to
 * see where they land: the push is a single instantaneous velocity change, not
 * something tracked tick by tick, so whether a wall is coming has to be known before
 * the velocity is even set.
 */
public class GravityPush implements Ability {

    /** INVENTED: reach for picking the target, the masterclass path's figure. */
    private static final double REACH = Gravity.MASTERCLASS_REACH;

    private static final double PUSH_DISTANCE = 10.0;
    private static final double PUSH_SPEED = 1.1;

    private static final float BASE_DAMAGE = 4.0F;
    private static final float WALL_BONUS = 6.0F;

    /** The corridor of destruction behind the target, matching the design's figures. */
    private static final int CORRIDOR_WIDTH = 5;
    private static final int CORRIDOR_LENGTH = 8;
    private static final int BROKEN_BLOCKS = 25;

    @Override
    public String getName() {
        return "Gravity Push";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
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
    public boolean canStart(ServerPlayer player, BendingData data) {
        return Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = Aiming.nearestAlongLook(player, REACH, Gravity.AIM_TOLERANCE);
        if (target == null) return;

        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-4) forward = new Vec3(0.0, 0.0, 1.0);
        forward = forward.normalize();

        Vec3 from = target.position();
        Vec3 to = from.add(forward.scale(PUSH_DISTANCE));

        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));

        boolean stoppedByWall = hit.getType() == HitResult.Type.BLOCK;
        float damage = stoppedByWall ? BASE_DAMAGE + WALL_BONUS : BASE_DAMAGE;

        target.hurt(player.damageSources().indirectMagic(player, player), damage);
        target.setDeltaMovement(forward.x * PUSH_SPEED, target.getDeltaMovement().y, forward.z * PUSH_SPEED);
        target.hurtMarked = true;

        shatterCorridor(level, from, forward);

        Gravity.burst(level, target.position(), 20, 0.5);
        Gravity.snap(level, target.position(), 1.2F);
    }

    /**
     * Twenty-five random blocks gone from a flattened corridor ahead of the target —
     * vanishing rather than dropping, the same call Ravine and Combustion Beam make
     * for area destruction like this.
     */
    private void shatterCorridor(ServerLevel level, Vec3 origin, Vec3 forward) {
        Vec3 across = new Vec3(-forward.z, 0.0, forward.x);

        for (int i = 0; i < BROKEN_BLOCKS; i++) {
            double along = level.random.nextInt(CORRIDOR_LENGTH);
            double side = level.random.nextInt(CORRIDOR_WIDTH) - (CORRIDOR_WIDTH / 2);

            Vec3 spot = origin.add(forward.scale(along)).add(across.scale(side));
            BlockPos pos = BlockPos.containing(spot);
            BlockState state = level.getBlockState(pos);

            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) continue;

            level.destroyBlock(pos, false);
        }
    }
}
