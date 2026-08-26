package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.abilities.metal.Metal;
import com.minecraft.atlamod.abilities.metal.MetalWorks;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Metalbending Scroll. Bought from a village mason for 4 iron blocks, and read by
 * right clicking it.
 *
 * The fourth scroll, and identical in shape to the other three: a KEY rather than a
 * teacher, opening only for someone who has already finished two paths of the parent
 * element — EARTHbending here, as metal is refined out of earth.
 *
 * Anyone short keeps the scroll rather than burning it, since discovering a
 * requirement should not cost four iron blocks.
 *
 * The confirmation is a two by two floor of unbreakable metal under the reader, which
 * gives the ground back exactly as it was after fifteen seconds.
 */
public class MetalScrollItem extends Item {

    /** How many of earth's four paths must be finished before the scroll will open. */
    private static final int EARTH_PATHS_REQUIRED = 2;

    /** How long the confirming floor stays down. */
    private static final int FLOOR_TICKS = 300; // 15 seconds

    public MetalScrollItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // The client is told nothing and does nothing: every check and every effect
        // here is server-side, and the sync packet puts the client right afterwards.
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(held);
        }

        BendingData data = serverPlayer.getData(ModAttachments.BENDING_DATA);

        if (data.getUnlockedElements().stream().anyMatch("metal"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know metalbending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("earth", data.getUnlockedAbilities());

        if (completed < EARTH_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete " + EARTH_PATHS_REQUIRED
                            + " earthbending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("metal");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        if (level instanceof ServerLevel serverLevel) {
            layFloor(serverLevel, serverPlayer.blockPosition());
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "The iron answers you. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.AQUA));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll destroys itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /**
     * The confirming floor: two by two, under the reader's feet.
     *
     * Laid through MetalWorks, so the blocks are unbreakable while they stand and the
     * ORIGINAL ground comes back when they go — reading a scroll should not leave four
     * permanent iron blocks in somebody's village, and it certainly should not leave
     * four holes.
     *
     * The square is placed from the reader's own block rather than centred on them, so
     * they are standing on it rather than beside it.
     */
    private static void layFloor(ServerLevel level, BlockPos feet) {
        BlockPos under = feet.below();

        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                MetalWorks.lay(level, under.offset(dx, 0, dz), FLOOR_TICKS);
            }
        }

        Metal.clang(level, Vec3.atCenterOf(under), 1.4F, 0.9F);
        Metal.spark(level, Vec3.atCenterOf(feet), 30, 0.8);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + EARTH_PATHS_REQUIRED
                        + " completed earthbending paths.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("The scroll destroys itself once read.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
