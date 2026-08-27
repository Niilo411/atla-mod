package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.abilities.combustion.Combustion;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Combustionbending Scroll. Bought from a village armorer for 32 gunpowder, and
 * read by right clicking it.
 *
 * The fifth and steepest scroll: where the others ask for two completed paths of the
 * parent element, this one asks for ALL FOUR of firebending. Combustion is the end of
 * the fire road rather than a branch off it.
 *
 * Anyone short keeps the scroll rather than burning it, since discovering a
 * requirement should not cost 32 gunpowder.
 *
 * The confirmation is four sticks of primed TNT set down in a square around the
 * reader — which is a genuine hazard, and deliberately so. It is the element
 * introducing itself.
 */
public class CombustionScrollItem extends Item {

    /** All four, unlike every other scroll's two. */
    private static final int FIRE_PATHS_REQUIRED = 4;

    /** How far out to each corner the TNT is set, in blocks. */
    private static final int SQUARE = 2;

    /**
     * How long the reader is untouchable by explosions, in ticks.
     *
     * Comfortably longer than a TNT fuse (80), so all four go off inside the window
     * however the timing falls.
     */
    private static final int IMMUNE_TICKS = 140;

    public CombustionScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("combustion"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know combustionbending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("fire", data.getUnlockedAbilities());

        if (completed < FIRE_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete ALL " + FIRE_PATHS_REQUIRED
                            + " firebending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("combustion");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        if (level instanceof ServerLevel serverLevel) {
            // The reader is spared their own welcome. The four sticks are real and will
            // wreck the ground and anything else standing there, but blowing up the
            // person who just earned the element is a poor reward for finishing all
            // four fire paths.
            data.setBlastImmuneTicks(IMMUNE_TICKS);
            serverPlayer.setData(ModAttachments.BENDING_DATA, data);

            ring(serverLevel, serverPlayer);
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "Combustion answers you — MOVE. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.RED));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll burns itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /**
     * Sets four sticks of primed TNT down in a square around the reader.
     *
     * They are REAL primed TNT with a full vanilla fuse, not a decorative flash: the
     * design asks for them, and combustionbending is an element whose own abilities
     * blow their bender up for hesitating. Being given a few seconds to run is the
     * introduction.
     *
     * Placed at the corners rather than underfoot, so there is a way out for a reader
     * who moves — the point is the fright, not the death.
     */
    private static void ring(ServerLevel level, ServerPlayer reader) {
        for (int dx = -SQUARE; dx <= SQUARE; dx += SQUARE * 2) {
            for (int dz = -SQUARE; dz <= SQUARE; dz += SQUARE * 2) {
                PrimedTnt tnt = new PrimedTnt(level,
                        reader.getX() + dx + 0.5, reader.getY(), reader.getZ() + dz + 0.5, reader);
                level.addFreshEntity(tnt);
            }
        }

        // The bang the design asks for, immediately — the confirmation that it worked,
        // rather than the four that follow.
        Combustion.boom(level, reader.position(), 2.0F, 0.8F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires ALL " + FIRE_PATHS_REQUIRED
                        + " firebending paths completed.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Stand back when you read it.")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
