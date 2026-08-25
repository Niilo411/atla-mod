package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Balanced / Earth. Takes the stone apart: a tap breaks the block you are looking at,
 * and holding on widens that out to as much as thirty.
 *
 * The only ability in the mod whose PRICE scales with the charge. Everything else that
 * charges pays one figure and grows what it does; this grows both together, so a tap
 * is nearly free and a full five seconds is an expensive hole. That cannot go through
 * the dispatcher, which knows a single number per ability, so the extra is taken in
 * execute — see there for how a bender who cannot afford the full charge gets the part
 * they can rather than nothing.
 *
 * Blocks drop as though mined, because a mining ability that destroyed what it broke
 * would be a demolition ability.
 *
 * It does not start able to take everything. Netherite is never available at any
 * price; obsidian and logs each wait on an upgrade, and the log one waits on the
 * obsidian one in turn, so the dig widens in a deliberate order rather than arriving
 * able to level a forest.
 */
public class Mine implements ChargedAbility {

    /** Five seconds to reach the widest dig. */
    private static final int MAX_CHARGE = 100;

    /** What a tap breaks, and what a full charge breaks. */
    private static final int MIN_BLOCKS = 1;
    private static final int MAX_BLOCKS = 30;

    /** Paid up front, for the one block a tap takes. */
    private static final int BASE_CHI = 10;
    private static final int BASE_XP = 1;

    /** Added for every whole second the key was held. */
    private static final int CHI_PER_SECOND = 10;
    private static final int XP_PER_SECOND = 1;

    /** How far the bender can reach to start a dig. */
    private static final double REACH = 20.0;

    /** How far around the aimed block the dig may spread. */
    private static final int SPREAD = 3;

    /** Lets the dig take obsidian. */
    public static final String OBSIDIAN = "mine_obsidian";
    private static final int OBSIDIAN_COST = 10;

    /** Lets the dig take logs — sold only once obsidian breaking is owned. */
    public static final String TIMBER = "mine_timber";
    private static final int TIMBER_COST = 20;

    @Override
    public java.util.List<com.minecraft.atlamod.abilities.AbilityUpgrade> getUpgrades() {
        return java.util.List.of(
                new com.minecraft.atlamod.abilities.AbilityUpgrade(
                        OBSIDIAN,
                        "Obsidian Breaker",
                        "The dig can take obsidian",
                        OBSIDIAN_COST),
                new com.minecraft.atlamod.abilities.AbilityUpgrade(
                        TIMBER,
                        "Timber",
                        "The dig can take logs of every kind",
                        TIMBER_COST,
                        OBSIDIAN));
    }

    @Override
    public String getName() {
        return "Mine";
    }

    /** The base only. The rest is charged in execute, once the charge is known. */
    @Override
    public int getChiCost() {
        return BASE_CHI;
    }

    @Override
    public int getXpReward() {
        return BASE_XP;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public int getChargeTicks() {
        return MAX_CHARGE;
    }

    /** A tap is a real cast — one block — so releasing early always fires. */
    @Override
    public boolean firesOnRelease() {
        return true;
    }

    /** No floor: the shortest possible press is the one-block dig. */
    @Override
    public int getMinimumChargeTicks() {
        return 0;
    }

    /** Refused for free when there is nothing in reach to break. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (target(player, data) != null) return true;

        player.displayClientMessage(
                Component.literal("§6Nothing in reach to mine!"), true);
        return false;
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** The stone starting to give, shown on whatever is being aimed at. */
    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos aim = target(player, data);
        if (aim == null) return;

        float power = Math.min(1.0F, ticksHeld / (float) MAX_CHARGE);

        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(aim)),
                aim.getX() + 0.5, aim.getY() + 0.5, aim.getZ() + 0.5,
                2 + (int) (8 * power), 0.4, 0.4, 0.4, 0.02);

        if (ticksHeld % 20 == 0) {
            level.playSound(null, aim.getX(), aim.getY(), aim.getZ(),
                    SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.9F, 0.5F + (0.6F * power));
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos aim = target(player, data);
        if (aim == null) return;

        // How far the charge got, recorded by the dispatcher just before the cast.
        int charged = Math.min(MAX_CHARGE, data.getLastChargeTicks());
        int seconds = charged / 20;

        // The base was already taken by the dispatcher; this is the rest. A bender who
        // cannot afford the whole charge gets as many seconds as their chi covers
        // rather than the cast failing outright — they held the key in good faith, and
        // silently doing nothing after five seconds would be the worse answer.
        int affordable = data.getCurrentChi() / CHI_PER_SECOND;
        seconds = Math.min(seconds, affordable);

        if (seconds > 0) {
            data.consumeChi(seconds * CHI_PER_SECOND);
            AbilitySupport.grantXp(data, seconds * XP_PER_SECOND);
        }

        float power = seconds / (float) (MAX_CHARGE / 20);
        int budget = MIN_BLOCKS + Math.round((MAX_BLOCKS - MIN_BLOCKS) * power);

        dig(level, player, aim, budget, data);
    }

    /**
     * Breaks up to {@code budget} blocks, working outward from the one aimed at.
     *
     * Nearest first, so a dig always starts where the bender pointed and grows into a
     * rough ball around it rather than taking an arbitrary corner of the box first.
     */
    private static void dig(ServerLevel level, ServerPlayer player, BlockPos aim, int budget, BendingData data) {
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(
                aim.offset(-SPREAD, -SPREAD, -SPREAD),
                aim.offset(SPREAD, SPREAD, SPREAD))) {

            if (!breakable(level, pos, data)) continue;
            candidates.add(pos.immutable());
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(aim)));

        int broken = 0;
        for (BlockPos pos : candidates) {
            if (broken >= budget) break;

            // Dropped rather than deleted: this is mining, and destroyBlock plays the
            // break sound and particles for free.
            level.destroyBlock(pos, true, player);
            broken++;
        }

        level.playSound(null, aim.getX(), aim.getY(), aim.getZ(),
                SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
    }

    /**
     * Whether this block may be taken, given what the bender has bought.
     *
     * Three things are held back. A netherite block is never available at any price —
     * the one flat "no" in the ability. Obsidian and logs each wait on an upgrade, and
     * logs wait on obsidian's upgrade in turn, so the dig widens in a deliberate order
     * rather than arriving able to level a forest.
     */
    private static boolean breakable(ServerLevel level, BlockPos pos, BendingData data) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;

        // Bedrock, portal frames and the like report a negative hardness. Mining is
        // not a reason to be able to take those.
        if (state.getDestroySpeed(level, pos) < 0.0F) return false;

        if (state.is(Blocks.NETHERITE_BLOCK)) return false;

        // Crying obsidian counts as obsidian: gating one and not the other would only
        // ever look like an oversight.
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
            return data.hasUpgrade(OBSIDIAN);
        }

        // The LOGS tag covers logs, stems, wood and hyphae, stripped or not.
        if (state.is(BlockTags.LOGS)) {
            return data.hasUpgrade(TIMBER);
        }

        return true;
    }

    /** The block the bender is looking at, or null if there is nothing in reach. */
    private static BlockPos target(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        return breakable(level, pos, data) ? pos : null;
    }
}
