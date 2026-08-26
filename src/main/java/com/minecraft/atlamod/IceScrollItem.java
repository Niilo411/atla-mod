package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ice.Ice;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Icebending Scroll. Bought from a village fisherman for a Heart of the Sea, and
 * read by right clicking it.
 *
 * Unlike the Lightningbending Scroll this has NO prerequisite: the design asks only
 * for the trade, so anyone who can find a fisherman and a buried treasure map can
 * learn it. If icebending should ever be gated behind waterbending the way lightning
 * is behind fire, this is the one place that would need to change.
 *
 * The confirmation is a five by five patch of snow laid around the reader, and the
 * scroll burning itself.
 */
public class IceScrollItem extends Item {

    /** Five by five means two blocks either side of the reader, plus the middle. */
    private static final int PATCH_HALF = 2;

    /** How long the confirming snow stays down. */
    private static final int PATCH_TICKS = 600; // 30 seconds

    /** How far up and down a surface is looked for under each column. */
    private static final int SURFACE_SCAN = 3;

    public IceScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("ice"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know icebending.")
                    .withStyle(ChatFormatting.GRAY));
            // Not consumed: nothing happened, so nothing should be spent.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("ice");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        if (level instanceof ServerLevel serverLevel) {
            freezeAround(serverLevel, serverPlayer.blockPosition());
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "The cold answers you. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.AQUA));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll burns itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /**
     * Lays the confirming patch of snow.
     *
     * Goes through IceWorks like every other ice ability, so it only ever fills air
     * and takes itself back afterwards — reading a scroll should not permanently
     * redecorate somebody's village square.
     */
    private static void freezeAround(ServerLevel level, BlockPos centre) {
        for (int dx = -PATCH_HALF; dx <= PATCH_HALF; dx++) {
            for (int dz = -PATCH_HALF; dz <= PATCH_HALF; dz++) {
                BlockPos surface = surfaceNear(level, centre.offset(dx, 0, dz));
                if (surface == null) continue;

                com.minecraft.atlamod.abilities.ice.IceWorks.freeze(
                        level, surface, Blocks.SNOW.defaultBlockState(), PATCH_TICKS);
            }
        }

        Ice.form(level, Vec3.atCenterOf(centre), 1.2F, 0.8F);
        Ice.frost(level, Vec3.atCenterOf(centre).add(0.0, 1.0, 0.0), 60, 1.5);
    }

    /** The first free space above solid ground near the given column. */
    private static BlockPos surfaceNear(ServerLevel level, BlockPos column) {
        for (int dy = 0; dy >= -SURFACE_SCAN; dy--) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at;
        }
        for (int dy = 1; dy <= SURFACE_SCAN; dy++) {
            BlockPos at = column.offset(0, dy, 0);
            if (freezable(level, at)) return at;
        }
        return null;
    }

    private static boolean freezable(ServerLevel level, BlockPos at) {
        var here = level.getBlockState(at);
        if (!here.isAir() && !here.canBeReplaced()) return false;
        if (!here.getFluidState().isEmpty()) return false;

        return level.getBlockState(at.below()).isSolid();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("The scroll burns itself once read.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
