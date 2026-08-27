package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.abilities.blood.Blood;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * The Bloodbending Scroll. Bought from a village cleric for 5 rabbit feet, and read by
 * right clicking it.
 *
 * The sixth scroll, and one of the two steepest: like combustion it asks for ALL FOUR
 * paths of its parent element rather than the two the others want. Bloodbending is the
 * end of the water road rather than a branch off it.
 *
 * Anyone short keeps the scroll rather than burning it.
 *
 * The confirmation is blood rain across a five by five patch around the reader — no
 * damage, no blocks changed, just a very clear statement of what has been learned.
 */
public class BloodScrollItem extends Item {

    /** All four, like the combustion scroll and unlike the other four. */
    private static final int WATER_PATHS_REQUIRED = 4;

    /** Five by five means two blocks either side of the reader, plus the middle. */
    private static final int PATCH_HALF = 2;

    /** How high above the patch the rain starts. */
    private static final double RAIN_HEIGHT = 4.0;

    public BloodScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("blood"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know bloodbending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("water", data.getUnlockedAbilities());

        if (completed < WATER_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete ALL " + WATER_PATHS_REQUIRED
                            + " waterbending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("blood");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        if (level instanceof ServerLevel serverLevel) {
            rain(serverLevel, serverPlayer.blockPosition());
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "Blood answers you. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.DARK_RED));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll burns itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /**
     * The confirming blood rain: a five by five patch of falling red, and nothing else.
     *
     * Purely particles. Unlike the combustion scroll's TNT this changes nothing and
     * hurts nobody — the element is unpleasant enough without the unlock being a
     * hazard, and what makes bloodbending frightening is what it does to other people
     * rather than what it does on arrival.
     */
    private static void rain(ServerLevel level, BlockPos centre) {
        for (int dx = -PATCH_HALF; dx <= PATCH_HALF; dx++) {
            for (int dz = -PATCH_HALF; dz <= PATCH_HALF; dz++) {
                Vec3 column = Vec3.atBottomCenterOf(centre.offset(dx, 0, dz));

                // Batched into one call per column rather than one per drop: a
                // directed velocity needs count 0, which is one particle per packet,
                // and this is twenty-five columns at once.
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                        column.x, column.y + RAIN_HEIGHT * 0.5, column.z,
                        12, 0.35, RAIN_HEIGHT * 0.5, 0.35, 0.02);

                level.sendParticles(ParticleTypes.FALLING_LAVA,
                        column.x, column.y + RAIN_HEIGHT, column.z,
                        3, 0.3, 0.2, 0.3, 0.0);
            }
        }

        Blood.squelch(level, Vec3.atCenterOf(centre), 1.6F, 0.4F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires ALL " + WATER_PATHS_REQUIRED
                        + " waterbending paths completed.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("The scroll burns itself once read.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
