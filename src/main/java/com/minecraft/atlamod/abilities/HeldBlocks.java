package com.minecraft.atlamod.abilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Blocks a bender has pulled loose and is carrying on their crosshair.
 *
 * Written to be element-agnostic on purpose: Water Manipulation is the first user,
 * but earthbending is expected to lean on this heavily, so nothing here knows or
 * cares what the block is.
 *
 * The block is taken out of the world when grabbed and put back when placed, so it
 * genuinely moves rather than being copied. If the carry is interrupted — death,
 * disconnect, an unloading level — the block is put back rather than lost, because
 * quietly destroying someone's block is a far worse failure than an awkward drop.
 */
public final class HeldBlocks {

    /** How far in front of the bender the block rides when nothing is in the way. */
    private static final double CARRY_DISTANCE = 4.0;

    /** How far the crosshair can reach when choosing where to put it. */
    private static final double AIM_REACH = 6.0;

    /** Kept a little clear of whatever it is resting against, so it is not inside it. */
    private static final double SURFACE_GAP = 0.6;

    private static final Map<UUID, Held> HELD = new HashMap<>();

    private HeldBlocks() {
    }

    private static final class Held {
        final ServerLevel level;
        final BlockState state;
        final BlockPos origin;
        Vec3 pos;
        FallingBlockEntity display;

        Held(ServerLevel level, BlockState state, BlockPos origin, Vec3 pos) {
            this.level = level;
            this.state = state;
            this.origin = origin;
            this.pos = pos;
        }
    }

    public static boolean isHolding(ServerPlayer player) {
        return HELD.containsKey(player.getUUID());
    }

    /**
     * Pulls a block loose and starts carrying it.
     *
     * @return false if there was nothing to take
     */
    public static boolean grab(ServerPlayer player, BlockPos from) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (isHolding(player)) return false;

        BlockState state = level.getBlockState(from);
        if (state.isAir()) return false;

        level.removeBlock(from, false);

        Vec3 start = Vec3.atCenterOf(from);
        Held held = new Held(level, state, from, start);

        // A real entity so the block is actually visible while carried. Fluids have no
        // model to render, so they get no entity and are shown with particles instead.
        if (state.getRenderShape() == RenderShape.MODEL) {
            FallingBlockEntity display = FallingBlockEntity.fall(level, from, state);
            display.setNoGravity(true);
            display.setDeltaMovement(Vec3.ZERO);
            display.setPos(start.x, start.y - 0.5, start.z);
            // Its own timer would eventually drop it as an item; kept at zero so the
            // carry lasts as long as the bender wants it to.
            display.time = 0;
            held.display = display;
        }

        HELD.put(player.getUUID(), held);
        return true;
    }

    /**
     * Moves the carried block onto wherever the bender is looking. Called every tick
     * while the ability is armed.
     */
    public static void follow(ServerPlayer player) {
        Held held = HELD.get(player.getUUID());
        if (held == null) return;

        // Changing dimension mid-carry would leave the block being steered around a
        // level the bender is no longer in. Put it back instead.
        if (player.level() != held.level) {
            release(player);
            return;
        }

        held.pos = aimPoint(player);

        if (held.display != null && held.display.isAlive()) {
            held.display.setNoGravity(true);
            held.display.setDeltaMovement(Vec3.ZERO);
            held.display.setPos(held.pos.x, held.pos.y - 0.5, held.pos.z);
            held.display.time = 0;
        } else {
            // No model to render: draw it instead.
            held.level.sendParticles(ParticleTypes.SPLASH,
                    held.pos.x, held.pos.y, held.pos.z, 10, 0.3, 0.3, 0.3, 0.02);
            held.level.sendParticles(ParticleTypes.FALLING_WATER,
                    held.pos.x, held.pos.y, held.pos.z, 5, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Sets the block down where it is being held.
     *
     * @return false if there is no room for it, in which case it stays carried
     */
    public static boolean place(ServerPlayer player) {
        Held held = HELD.get(player.getUUID());
        if (held == null) return false;

        BlockPos target = BlockPos.containing(held.pos);
        if (!canReplace(held, target)) return false;

        held.level.setBlockAndUpdate(target, held.state);
        finish(player, held);
        return true;
    }

    /**
     * Gives up the carry, putting the block back rather than destroying it: first
     * where it is being held, and failing that where it came from.
     */
    public static void release(ServerPlayer player) {
        Held held = HELD.get(player.getUUID());
        if (held == null) return;

        BlockPos target = BlockPos.containing(held.pos);
        if (canReplace(held, target)) {
            held.level.setBlockAndUpdate(target, held.state);
        } else if (canReplace(held, held.origin)) {
            held.level.setBlockAndUpdate(held.origin, held.state);
        }

        finish(player, held);
    }

    private static void finish(ServerPlayer player, Held held) {
        if (held.display != null) {
            held.display.discard();
        }
        HELD.remove(player.getUUID());
    }

    private static boolean canReplace(Held held, BlockPos pos) {
        BlockState existing = held.level.getBlockState(pos);
        return existing.isAir() || existing.canBeReplaced();
    }

    /**
     * Where the block should sit: on the surface the bender is looking at, or hanging
     * at arm's length when they are looking at nothing.
     */
    private static Vec3 aimPoint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(AIM_REACH));

        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            // Sat just off the face that was hit, so it lands beside the surface
            // rather than buried inside it.
            Vec3 face = Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(SURFACE_GAP);
            return hit.getLocation().add(face);
        }

        return eye.add(look.scale(CARRY_DISTANCE));
    }

    /** Drops every carry in a level that is going away, putting the blocks back first. */
    public static void forgetLevel(ServerLevel level) {
        Iterator<Map.Entry<UUID, Held>> entries = HELD.entrySet().iterator();
        while (entries.hasNext()) {
            Held held = entries.next().getValue();
            if (held.level != level) continue;

            if (canReplace(held, held.origin)) {
                held.level.setBlockAndUpdate(held.origin, held.state);
            }
            if (held.display != null) held.display.discard();
            entries.remove();
        }
    }

    /**
     * Ends a carry for a player who is no longer in a position to hold anything —
     * death or disconnect. Without this the block would simply cease to exist.
     */
    public static void forgetPlayer(ServerPlayer player) {
        Held held = HELD.get(player.getUUID());
        if (held == null) return;

        if (canReplace(held, held.origin)) {
            held.level.setBlockAndUpdate(held.origin, held.state);
        }
        if (held.display != null) held.display.discard();
        HELD.remove(player.getUUID());
    }

    /** Air, for callers that want to know what an empty grab looks like. */
    public static BlockState nothing() {
        return Blocks.AIR.defaultBlockState();
    }
}
