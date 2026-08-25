package com.minecraft.atlamod.abilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Picking out the one thing a bender is aiming at.
 *
 * Element-agnostic, like HeldBlocks and BendingProjectiles — Drown and Breathless
 * both single out a victim exactly this way, and anything else that wants one target
 * rather than a cone should use it too rather than growing a third copy.
 */
public final class Aiming {

    private Aiming() {
    }

    /**
     * The living thing nearest the bender's line of sight, or null if there is none.
     *
     * Picked by distance from the aim LINE rather than by a raycast, so a cast does
     * not have to be pixel-perfect on something that is moving. Of everything within
     * {@code tolerance} of the line, the NEAREST along it wins — a bender should hit
     * what is in front of them, not something further off that happens to be better
     * aligned.
     *
     * @param reach     how far down the line to look, in blocks
     * @param tolerance how far off the line something may be and still count
     */
    public static LivingEntity nearestAlongLook(ServerPlayer player, double reach, double tolerance) {
        if (!(player.level() instanceof ServerLevel level)) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        AABB search = new AABB(eye, eye).inflate(reach);
        LivingEntity best = null;
        double bestAlong = Double.MAX_VALUE;

        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0).subtract(eye);

            double along = toTarget.dot(look);
            if (along <= 0.0 || along > reach) continue;

            double offLine = toTarget.subtract(look.scale(along)).length();
            if (offLine > tolerance) continue;

            if (along < bestAlong) {
                bestAlong = along;
                best = living;
            }
        }

        return best;
    }

    /**
     * Everything living the bender can actually SEE — in front of them, within reach,
     * and not through a wall.
     *
     * The caster is never included. The cone is deliberately a little wider than the
     * real view frustum (Minecraft's default 70-degree vertical FOV works out around
     * 106 across on a widescreen, so a true half angle near 0.6), because something at
     * the very edge of the screen should be caught rather than feel unfairly missed —
     * and the player's FOV slider is a client preference the server cannot see anyway.
     *
     * The line-of-sight test is what makes "in sight" mean what it says: without it an
     * ability would reach through terrain and catch things in the cave below.
     */
    public static List<LivingEntity> allInSight(ServerPlayer player, double reach, double coneDot) {
        List<LivingEntity> found = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel level)) return found;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // A box of +-reach around the eye. Any point within reach is inside it on every
        // axis, so nothing in the cone can fall outside the search — a tighter box
        // centred down the look vector misses targets at the edge of the arc.
        AABB search = new AABB(eye, eye).inflate(reach);

        for (Entity candidate : level.getEntities(player, search)) {
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.getEyePosition().subtract(eye);
            if (toTarget.lengthSqr() > reach * reach) continue;
            if (toTarget.normalize().dot(look) < coneDot) continue;
            if (!player.hasLineOfSight(living)) continue;

            found.add(living);
        }

        return found;
    }

    /**
     * The point on the ground the bender is looking at.
     *
     * Aimed at a wall or floor it is the face they hit; aimed at open sky it drops to
     * whatever ground lies under the end of the look, and only if there is none within
     * {@code groundScan} does it give back the end of the ray itself. Falling back
     * rather than returning nothing matters for anything already committed by the time
     * it asks — Air spout has spent its click before it gets here.
     */
    public static Vec3 groundUnderLook(ServerPlayer player, double reach, int groundScan) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            // On top of the face that was hit, so something aimed at the floor stands
            // on it rather than half sunk into it.
            return Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
        }

        BlockPos from = BlockPos.containing(end);
        for (int i = 0; i <= groundScan; i++) {
            BlockPos below = from.below(i);
            if (level.getBlockState(below).blocksMotion()) {
                return Vec3.atBottomCenterOf(below.above());
            }
        }

        return end;
    }
}
