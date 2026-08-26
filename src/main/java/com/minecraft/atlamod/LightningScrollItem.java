package com.minecraft.atlamod;

import com.minecraft.atlamod.network.SyncBendingDataPacket;
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
 * The Lightningbending Scroll. Bought from a village weaponsmith for 64 copper
 * ingots, and read by right clicking it.
 *
 * The scroll is a KEY, not a teacher: it only opens lightningbending to someone who
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

    /**
     * Fire's four paths, as the ability names the skill tree stores.
     *
     * Duplicated from UpgradeMenuScreen rather than shared with it, and that is a
     * deliberate trade: the menu is CLIENT-only and this check has to run on the
     * server. The alternative is hoisting the whole tree into common code, which is
     * a much larger change than this feature needs — but it does mean that renaming
     * a fire ability means changing it in both places.
     */
    private static final String[][] FIRE_PATHS = {
            { "Fire leap", "Fire whip", "Fireball", "Fire Breath" },
            { "Fire push", "Fire shield", "Firewall", "Fire ring" },
            { "Ignite", "Fire spikes", "Fire rocket", "Taller fire" },
            { "blue fire", "Fire blow", "Fire immunity", "Fire Rain" }
    };

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

        BendingData data = serverPlayer.getData(ModAttachments.BENDING_DATA);

        if (data.getUnlockedElements().stream().anyMatch("lightning"::equalsIgnoreCase)) {
            serverPlayer.sendSystemMessage(Component.literal("You already know lightningbending.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(held);
        }

        int completed = completedFirePaths(data);
        if (completed < FIRE_PATHS_REQUIRED) {
            serverPlayer.sendSystemMessage(Component.literal(
                    "The scroll means nothing to you yet. Complete " + FIRE_PATHS_REQUIRED
                            + " firebending paths first — you have finished " + completed + ".")
                    .withStyle(ChatFormatting.RED));
            // Deliberately NOT consumed: the reader has done nothing wrong, and a
            // scroll that burned itself on a failed reading would cost them 64 copper
            // for finding out a requirement.
            return InteractionResultHolder.fail(held);
        }

        data.getUnlockedElements().add("lightning");
        serverPlayer.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToPlayer(serverPlayer, new SyncBendingDataPacket(
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

            com.minecraft.atlamod.abilities.lightning.Lightning.visualStrike(serverLevel, at);
        }

        serverPlayer.sendSystemMessage(Component.literal(
                "Lightning answers you. Press [Y] to switch to it.")
                .withStyle(ChatFormatting.AQUA));

        // shrink, NOT consume(): ItemStack#consume skips the shrink entirely for
        // anyone with infinite materials, so in CREATIVE the scroll would stay in
        // hand after being read. The scroll is a one-use key and reading it is what
        // spends it, in every game mode — a creative player left holding a spent
        // scroll reads as the item being broken.
        held.shrink(1);
        return InteractionResultHolder.success(held);
    }

    /** How many of fire's four paths the player has every ability of. */
    private static int completedFirePaths(BendingData data) {
        List<String> unlocked = data.getUnlockedAbilities();

        int complete = 0;
        for (String[] path : FIRE_PATHS) {
            boolean whole = true;
            for (String ability : path) {
                if (!unlocked.contains(ability)) {
                    whole = false;
                    break;
                }
            }
            if (whole) complete++;
        }
        return complete;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right click to read.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Requires " + FIRE_PATHS_REQUIRED + " completed firebending paths.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
