package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.abilities.lava.Lava;
import com.minecraft.atlamod.abilities.lava.LavaWorks;
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
 * The Lavabending Scroll. Bought from a village shepherd for 5 nether bricks, and read
 * by right clicking it.
 *
 * The seventh scroll, and the third of the steep ones: like the combustion and blood
 * scrolls it asks for ALL FOUR paths of its parent element rather than the two the first
 * four want. Lava is the end of the earth road rather than a branch off it — the same
 * relationship combustion has to fire and bloodbending has to water.
 *
 * Anyone short keeps the scroll rather than burning it.
 *
 * The confirmation is four blocks of lava set down around the reader. They are OUR lava
 * rather than the real thing, so they cool away on their own after a few seconds and
 * take nothing with them — the point is to show the new bender what they can now do, not
 * to burn their house down for them. That is the opposite call to the combustion
 * scroll's four sticks of live TNT, and deliberately so: a misfire is combustion's whole
 * character, where lavabending's is that the lava is always given back.
 */
public class LavaScrollItem extends Item {

    /** All four, like the combustion and blood scrolls and unlike the other four. */
    private static final int EARTH_PATHS_REQUIRED = 4;

    /** How far from the reader the four blocks are set down. */
    private static final int SPREAD = 2;

    /** How long they last before cooling, in ticks. */
    private static final int LIFETIME = 100;

    public LavaScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("lava"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know lavabending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("earth", data.getUnlockedAbilities());

        if (completed < EARTH_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete ALL " + EARTH_PATHS_REQUIRED
                            + " earthbending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("lava");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        if (level instanceof ServerLevel serverLevel) {
            pool(serverLevel, serverPlayer.blockPosition());
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "The stone runs molten. Press [Y] to switch to lavabending.")
                .withStyle(ChatFormatting.GOLD));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll burns itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /**
     * The confirming lava: four blocks, one to each side of the reader.
     *
     * Poured through LavaWorks like every other lava ability, so they obey the same two
     * rules the whole element does — they only ever fill air, and they are taken back.
     * The reader can stand in them and burn if they choose to; nothing else in the
     * neighbourhood is at risk.
     */
    private static void pool(ServerLevel level, BlockPos centre) {
        BlockPos[] corners = {
                centre.offset(SPREAD, 0, 0),
                centre.offset(-SPREAD, 0, 0),
                centre.offset(0, 0, SPREAD),
                centre.offset(0, 0, -SPREAD),
        };

        for (BlockPos corner : corners) {
            BlockPos ground = Lava.footing(level, corner, 1, 3);
            if (ground == null) continue;

            if (LavaWorks.pour(level, ground, LIFETIME)) {
                LavaWorks.splash(level, ground);
            }
        }

        Lava.roar(level, Vec3.atCenterOf(centre), 2.0F, 0.7F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires ALL " + EARTH_PATHS_REQUIRED
                        + " earthbending paths completed.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("The scroll burns itself once read.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
