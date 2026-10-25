package io.github.niilo411.elementalpaths.abilities.earth;

import io.github.niilo411.elementalpaths.ChannelingData;
import io.github.niilo411.elementalpaths.abilities.ChannelingProjectiles;
import io.github.niilo411.elementalpaths.abilities.HeldBlocks;
import io.github.niilo411.elementalpaths.abilities.TwoPhaseAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Earth. Tears a block out of the ground, holds it on the crosshair, and
 * throws it at whatever the channeler is looking at.
 *
 * Everything about it is a REAL BLOCK from start to finish, with no particles
 * anywhere: {@link HeldBlocks} genuinely removes the block from the world (so the hole
 * it leaves is visible), shows it with a FallingBlockEntity that follows the crosshair
 * (so the block itself is visible in hand), and the throw hands that same entity
 * straight to the projectile — it is never discarded and respawned, so what flies is
 * visibly the block that was picked up.
 *
 * It also always ends up somewhere. The block is out of the world while carried and
 * while flying, so every route — thrown and landed, thrown and timed out, died,
 * disconnected — puts it back. A throw that deleted its own block would make this a
 * quiet way to dig holes.
 */
public class EarthBlock implements TwoPhaseAbility {

    /** 2.5 hearts. */
    private static final float DAMAGE = 5.0F;

    /** Heavy: slower than a splinter, and it hits like a thrown rock. */
    private static final double SPEED = 1.6;

    /** At this speed it carries about 35 blocks before it drops out of the air. */
    private static final int LIFETIME = 22;

    /** Generous — it is a whole block, and should catch what it is aimed at. */
    private static final double HIT_RADIUS = 0.9;

    /** A solid shove, matching the weight of the thing. */
    private static final double KNOCKBACK = 0.6;

    /** How far away a block can be pulled from. */
    private static final double REACH = 12.0;

    private static final ChannelingProjectiles.Spec SHOT = new ChannelingProjectiles.Spec(
            SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK, ChannelingProjectiles.Style.BLOCK);

    @Override
    public String getName() {
        return "Earth block";
    }

    @Override
    public int getChiCost(ChannelingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    @Override
    public int getCooldownTicks() {
        return 20; // 1 second, from the throw
    }

    /** Needs a block in reach, and nothing already in hand. */
    @Override
    public boolean canStart(ServerPlayer player, ChannelingData data) {
        if (!data.getActiveTwoPhaseAbility().isEmpty()) return false;
        if (HeldBlocks.isHolding(player)) return false;

        if (target(player) != null) return true;

        player.displayClientMessage(
                Component.literal("§6No block in reach to pull!"), true);
        return false;
    }

    /** Pulls the block loose. It is now genuinely gone from the world. */
    @Override
    public void execute(ServerPlayer player, ChannelingData data) {
        BlockPos from = target(player);
        if (from == null) return;

        BlockState state = player.level().getBlockState(from);
        if (!HeldBlocks.grab(player, from)) return;

        player.level().playSound(null, from.getX(), from.getY(), from.getZ(),
                state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    /** Keeps the block on the crosshair while it is held. */
    @Override
    public void onArmedTick(ServerPlayer player, ChannelingData data) {
        HeldBlocks.follow(player);
    }

    /**
     * Throws it.
     *
     * The carry is ended with take() rather than release(), which hands the block over
     * WITHOUT putting it down — and hands the live display entity with it, so the
     * block visibly continues rather than blinking out and back.
     */
    @Override
    public void onRelease(ServerPlayer player, ChannelingData data) {
        HeldBlocks.Taken taken = HeldBlocks.take(player);
        if (taken == null) return;

        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(1.0));

        ChannelingProjectiles.launchCarried(player, from, look, SHOT, taken.state(), taken.display());

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /**
     * Puts the block back if the channeler never throws it — the armed slot going away
     * without this would take the block with it.
     */
    @Override
    public void onArmedExpire(ServerPlayer player, ChannelingData data) {
        HeldBlocks.release(player);
    }

    /** The block the channeler is looking at, or null if there is nothing in reach. */
    private static BlockPos target(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // Only ordinary diggable ground, and nothing with an inventory or a mind of
        // its own — a channeler should not be able to throw somebody's chest at them.
        if (state.isAir()) return null;
        if (state.hasBlockEntity()) return null;
        if (state.getDestroySpeed(level, pos) < 0.0F) return null; // bedrock and friends
        if (!state.isCollisionShapeFullBlock(level, pos)) return null;

        return pos;
    }
}
