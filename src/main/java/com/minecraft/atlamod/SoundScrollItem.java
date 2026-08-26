package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.abilities.sound.Sound;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Soundbending Scroll. Bought from a village fletcher for 32 feathers, and read
 * by right clicking it.
 *
 * The third scroll, and identical in shape to the other two: a KEY rather than a
 * teacher, opening only for someone who has already finished two paths of the parent
 * element — AIRbending here, as sound comes out of air the way lightning comes out of
 * fire and ice out of water.
 *
 * Anyone short of that keeps the scroll. A scroll that burned itself on a failed
 * reading would cost 32 feathers to discover a requirement.
 *
 * The confirmation is a loud screech and the scroll burning itself.
 */
public class SoundScrollItem extends Item {

    /** How many of air's four paths must be finished before the scroll will open. */
    private static final int AIR_PATHS_REQUIRED = 2;

    public SoundScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("sound"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know soundbending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("air", data.getUnlockedAbilities());

        if (completed < AIR_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete " + AIR_PATHS_REQUIRED
                            + " airbending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("sound");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        // The confirmation the design asks for: a screech loud enough to be
        // unmistakable, played at the reader so everyone nearby hears it too.
        if (level instanceof ServerLevel serverLevel) {
            Sound.play(serverLevel, serverPlayer.position(),
                    SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.9F);
            Sound.play(serverLevel, serverPlayer.position(),
                    SoundEvents.ALLAY_HURT, 2.5F, 0.5F);
            Sound.burst(serverLevel, serverPlayer.getEyePosition(), 40, 0.8);
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "The air rings around you. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.AQUA));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll deletes itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + AIR_PATHS_REQUIRED
                        + " completed airbending paths.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("The scroll destroys itself once read.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
