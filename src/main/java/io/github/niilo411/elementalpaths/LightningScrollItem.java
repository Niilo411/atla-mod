package io.github.niilo411.elementalpaths;

import io.github.niilo411.elementalpaths.network.SyncChannelingDataPacket;
import net.minecraft.ChatFormatting;
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
 * The Lightning Channeling Scroll. Bought from a village weaponsmith for 64 copper
 * ingots, and read by right clicking it.
 *
 * The scroll is a KEY, not a teacher: it only opens lightning channeling to someone who
 * has already gone far enough in fire, which is two completed fire paths. Anyone
 * else can hold it, buy it, and read it as often as they like, and it will refuse
 * and stay in their inventory — a scroll that vanished when it failed would punish
 * a player for not knowing a requirement the game never told them.
 *
 * A successful reading calls lightning down in front of the reader. That flash is
 * the confirmation the design asks for: it means it worked, and lightning can now be
 * chosen as an active element.
 */
public class LightningScrollItem extends Item {

    /** How many of fire's four paths must be finished before the scroll will open. */
    private static final int FIRE_PATHS_REQUIRED = 2;

    /** How far in front of the reader the confirming bolt lands, in blocks. */
    private static final double STRIKE_DISTANCE = 3.0;

    public LightningScrollItem(Properties properties) {
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

        ChannelingData data = serverPlayer.getData(ModAttachments.CHANNELING_DATA);

        if (data.getUnlockedElements().stream().anyMatch("lightning"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know lightning channeling.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = io.github.niilo411.elementalpaths.abilities.ElementPaths.completedPaths(
                "fire", data.getUnlockedAbilities());
        if (completed < FIRE_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete " + FIRE_PATHS_REQUIRED
                            + " fire channeling paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed: the reader has done nothing wrong, and a
            // scroll that burned itself on a failed reading would cost them 64 copper
            // for finding out a requirement.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("lightning");
        serverPlayer.setData(ModAttachments.CHANNELING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncChannelingDataPacket(
                data.getMainElement(),
                data.getActiveElement(),
                data.getUnlockedElements(),
                data.hasChosenElement(),
                data.getUnlockedAbilities(),
                data.getEquippedAbilities()));

        // The confirmation: a bolt in front of the reader. Visual-only, so reading a
        // scroll cannot set the village alight or hurt the person who bought it.
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = serverPlayer.getLookAngle();
            Vec3 at = serverPlayer.position().add(
                    new Vec3(look.x, 0.0, look.z).normalize().scale(STRIKE_DISTANCE));

            io.github.niilo411.elementalpaths.abilities.lightning.Lightning.visualStrike(serverLevel, at);
        }

        // Component.keybind, not a literal "[Y]" — resolves on each reader's own
        // client to whatever they actually have switch-element bound to.
        serverPlayer.sendSystemMessage(Component.literal("Lightning answers you. Press [")
                .append(Component.keybind("key.elemental_paths.switch_element"))
                .append(Component.literal("] to switch to it."))
                .withStyle(ChatFormatting.AQUA));

        // shrink, NOT consume(): ItemStack#consume skips the shrink entirely for
        // anyone with infinite materials, so in CREATIVE the scroll would stay in
        // hand after being read. The scroll is a one-use key and reading it is what
        // spends it, in every game mode — a creative player left holding a spent
        // scroll reads as the item being broken.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + FIRE_PATHS_REQUIRED + " completed fire channeling paths.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
