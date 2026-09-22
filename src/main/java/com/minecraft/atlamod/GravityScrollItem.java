package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.network.SyncBendingDataPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The Gravitybending Scroll. Bought from a village cartographer for 6 pistons, and
 * read by right clicking it.
 *
 * Air's second sub-element, and gated the way Combustion/Blood/Lava are rather than
 * the way Sound/Ice/Lightning/Metal are: it wants ALL FOUR airbending paths finished,
 * not two — gravity is the end of the air road rather than a branch off it.
 *
 * Anyone short keeps the scroll rather than burning it, since discovering a
 * requirement should not cost six pistons.
 *
 * The confirmation is the ability itself, briefly: five seconds of levitation and a
 * ten second window where a fall costs nothing, so the reader feels what the element
 * does to gravity before they have spent a single point on it.
 */
public class GravityScrollItem extends Item {

    /** All four, like Combustion/Blood/Lava, unlike the six two-path scrolls. */
    private static final int AIR_PATHS_REQUIRED = 4;

    /** How long the reader floats, in ticks. */
    private static final int LEVITATE_TICKS = 100;

    /** How long after that a fall costs nothing, in ticks. */
    private static final int FALL_IMMUNE_TICKS = 200;

    public GravityScrollItem(Properties properties) {
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

        if (data.getUnlockedElements().stream().anyMatch("gravity"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know gravitybending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = ElementPaths.completedPaths("air", data.getUnlockedAbilities());

        if (completed < AIR_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete ALL " + AIR_PATHS_REQUIRED
                            + " airbending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed. See the class note.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("gravity");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        serverPlayer.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION, LEVITATE_TICKS, 0, false, true, true));
        data.setGravityFallImmuneTicks(FALL_IMMUNE_TICKS);
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        if (level instanceof ServerLevel serverLevel) {
            confirm(serverLevel, serverPlayer);
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "The ground lets go of you. Press [Y] to switch to gravitybending.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        // shrink, NOT consume(): ItemStack#consume does nothing at all for anyone with
        // infinite materials, so in CREATIVE the scroll would survive being read. The
        // design says the scroll burns itself, and it should do that in every mode.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /** The sound and particle burst that confirms the reading, alongside the effects. */
    private static void confirm(ServerLevel level, ServerPlayer reader) {
        level.playSound(null, reader.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                reader.getX(), reader.getY() + 1.0, reader.getZ(),
                60, 0.6, 1.0, 0.6, 0.02);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires ALL " + AIR_PATHS_REQUIRED
                        + " airbending paths completed.")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("The ground goes quiet when it works.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
