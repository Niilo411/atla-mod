package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.HeldBlocks;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Water. Takes hold of a water source block and carries it on the
 * crosshair until it is set down with a left click.
 *
 * Two-phase, with the grab as the arming and the placement as the release, so the
 * dispatcher's existing machinery does the work. The carrying itself lives in
 * HeldBlocks, which knows nothing about water — earthbending is expected to use the
 * same system, and this ability is really its first test.
 */
public class WaterManipulation implements TwoPhaseAbility {

    /** How far away a source block can be and still be taken hold of. */
    private static final double REACH = 12.0;

    @Override
    public String getName() {
        return "Water Manipulation";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    /**
     * Deliberately NOT gated on the canteen, for the same reason Water heal and Water
     * stream are not: this needs a source block in sight, which is stronger than the
     * generic "within 15 blocks" rule.
     */
    @Override
    public boolean requiresWater() {
        return false;
    }

    /** Needs a source block in view, and nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (!data.getActiveTwoPhaseAbility().isEmpty()) return false;
        if (HeldBlocks.isHolding(player)) return false;

        if (findSource(player) == null) {
            player.displayClientMessage(
                    Component.literal("§bYou must be looking at a water source block!"), true);
            return false;
        }
        return true;
    }

    /** Take hold of it. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        BlockPos source = findSource(player);
        if (source == null) return;

        if (!HeldBlocks.grab(player, source)) return;

        player.level().playSound(null, source, SoundEvents.BUCKET_FILL,
                SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** Carried along on the crosshair for as long as it is held. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        HeldBlocks.follow(player);
    }

    /** Left click: set it down. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        if (HeldBlocks.place(player)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BUCKET_EMPTY,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }

        // Nowhere to put it. The dispatcher has already spent the click, so rather
        // than leave the water stranded with nothing carrying it, set it down
        // wherever it can go.
        player.displayClientMessage(
                Component.literal("§bNo room there — the water settles where it was."), true);
        HeldBlocks.release(player);
    }

    /** The water source block the player is looking at, or null. */
    private static BlockPos findSource(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return null;

        // Only a full source block: taking a flowing edge would leave the pool to
        // simply refill the hole a moment later.
        return level.getFluidState(pos).isSource() ? pos : null;
    }
}
