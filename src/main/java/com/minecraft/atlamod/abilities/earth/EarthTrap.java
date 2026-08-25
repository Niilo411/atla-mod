package com.minecraft.atlamod.abilities.earth;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;

/**
 * Offensive / Earth. Stone closes over the feet of everything the bender can see, and
 * holds it there for ten seconds.
 *
 * Reaches everything in sight within twenty blocks rather than one chosen victim,
 * which is what its price is for — the same 150 chi and thirty second cooldown Wind
 * asks, over the same range and the same "what is actually on screen" test.
 *
 * The stone alone would not hold anyone: a slab is something you walk over, not
 * something you are stuck in. It is the visible half, and {@link EarthTraps} does the
 * actual holding — by making the victim a passenger, the same way Air Scooter and
 * Water Surf take movement out of a player's hands.
 */
public class EarthTrap implements Ability {

    /** How far the trap reaches. */
    private static final double RANGE = 20.0;

    /**
     * Cone width, as the minimum dot product between the look vector and the direction
     * to a target. Matched to Wind, which asks the same "is it on screen" question.
     */
    private static final double CONE_DOT = 0.4;

    /** Ten seconds of being stuck. */
    private static final int HOLD_TICKS = 200;

    /** How long the stone stays. A little past the hold, so it is not gone early. */
    private static final int SLAB_TICKS = HOLD_TICKS + 20;

    /** Quick — the point is that it closes before the victim can walk out of it. */
    private static final int SLIDE_TICKS = 3;

    @Override
    public String getName() {
        return "Earth trap";
    }

    @Override
    public int getChiCost() {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    /** Refused for free when there is nobody in sight to trap. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (!Aiming.allInSight(player, RANGE, CONE_DOT).isEmpty()) return true;

        player.displayClientMessage(
                Component.literal("§6Nobody in sight to trap!"), true);
        return false;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        List<LivingEntity> victims = Aiming.allInSight(player, RANGE, CONE_DOT);

        for (LivingEntity victim : victims) {
            closeAround(level, victim);
            EarthTraps.hold(victim, HOLD_TICKS);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.3F, 0.5F);
    }

    /**
     * Closes one slab over a victim's feet, in the block they are standing IN.
     *
     * One, not a ring: the point is that their feet are caught, and stone rising
     * through the space they occupy says that far better than a fence around them.
     * Nothing suffocates — a bottom slab is ankle height and Minecraft only smothers
     * something whose EYES are inside a block.
     */
    private static void closeAround(ServerLevel level, LivingEntity victim) {
        BlockPos feet = victim.blockPosition();
        BlockState stone = matchingSlab(level, feet.below());

        EarthWorks.raiseFor(level, feet, stone, SLAB_TICKS, SLIDE_TICKS);

        level.playSound(null, feet.getX(), feet.getY(), feet.getZ(),
                SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    /**
     * A slab that looks like the ground it came out of.
     *
     * Found by name — the slab for {@code x} is almost always {@code x_slab}, which
     * covers stone, cobblestone, every wood, sandstone, deepslate and the rest without
     * a table anybody has to maintain.
     *
     * Plenty of ground has no slab at all, dirt and grass being the obvious ones, and
     * those fall back to the GROUND BLOCK itself rather than to some stand-in stone —
     * looking like the block underneath matters more than being half height, and a
     * bender caught to the ankles in the earth they were standing on reads perfectly
     * well. EarthWorks.materialFor sanitises that, so sand cannot fall and nothing odd
     * gets duplicated.
     */
    private static BlockState matchingSlab(ServerLevel level, BlockPos ground) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(ground).getBlock());
        Block slab = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_slab"));

        if (slab instanceof SlabBlock) {
            return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }

        return EarthWorks.materialFor(level, ground);
    }
}
