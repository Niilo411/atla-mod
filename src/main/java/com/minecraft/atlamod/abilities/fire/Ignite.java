package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.BendingFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Balanced / Fire. Lights whatever the player is looking at, at range.
 *
 * Aimed at a furnace, blast furnace or smoker it fuels the thing instead —
 * bending the heat directly rather than dropping fire next to it.
 */
public class Ignite implements Ability {

    /** How far the player can light something, in blocks. */
    private static final double REACH = 20.0;

    /** Ignite's fire burns at twice normal. */
    private static final float DAMAGE_MULTIPLIER = 2.0F;

    /** How long that bonus lasts, in ticks (30 seconds). */
    private static final int ENHANCED_LIFETIME = 600;

    /** How long a furnace runs off one cast, in ticks (15 seconds). */
    private static final int SMELT_TICKS = 300;

    @Override
    public String getName() {
        return "Ignite";
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
     * Nothing in view means nothing to light, so the cast is refused before any
     * chi is spent rather than burning 50 on a look at the sky.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return trace(player) != null;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockHitResult hit = trace(player);
        if (hit == null) return;

        BlockPos targetPos = hit.getBlockPos();

        // Anything that smelts gets stoked instead of set alight.
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) {
            stokeFurnace(level, data, targetPos, furnace);
            return;
        }

        // Otherwise drop fire against the face that was hit.
        BlockPos firePos = targetPos.relative(hit.getDirection());
        BlockState fire = Blocks.FIRE.defaultBlockState();

        if (level.getBlockState(firePos).isAir() && fire.canSurvive(level, firePos)) {
            level.setBlockAndUpdate(firePos, BendingFire.isBlue(data)
                    ? com.minecraft.atlamod.BendingFireBlock.stateFor(true, false)
                    : fire);
            BendingFire.mark(level, firePos, ENHANCED_LIFETIME, DAMAGE_MULTIPLIER);

            level.playSound(null, firePos, SoundEvents.FLINTANDSTEEL_USE,
                    SoundSource.BLOCKS, 1.0F, 1.2F);
            level.sendParticles(BendingFire.flame(data),
                    firePos.getX() + 0.5, firePos.getY() + 0.4, firePos.getZ() + 0.5,
                    8, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /**
     * Sets the furnace burning for SMELT_TICKS as though it had been fuelled.
     *
     * litTime is what actually drives smelting; litDuration only scales the flame
     * icon in the GUI, so both are set or the icon starts part-burned. The block's
     * own ticker syncs the LIT blockstate, but it is set here too so the furnace
     * lights up on the same tick as the cast instead of one later.
     */
    private static void stokeFurnace(ServerLevel level, BendingData data, BlockPos pos, AbstractFurnaceBlockEntity furnace) {
        furnace.litTime = SMELT_TICKS;
        furnace.litDuration = SMELT_TICKS;
        furnace.setChanged();

        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
        }

        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.5F);
        level.sendParticles(BendingFire.flame(data),
                pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                10, 0.25, 0.1, 0.25, 0.01);
    }

    /** Ray from the player's eyes to whatever solid block they are looking at. */
    private static BlockHitResult trace(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
}
