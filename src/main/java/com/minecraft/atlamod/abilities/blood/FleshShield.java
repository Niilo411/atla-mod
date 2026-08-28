package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Right / Blood. Everything alive within ten blocks in front is dragged in, frozen,
 * and held as a wall between the bender and whatever comes next -- and whatever the
 * wall stops is dealt to the bodies making it.
 *
 * The only ability in the mod whose cost is XP rather than chi, which is what the
 * design asks for and what makes it feel like something spent rather than something
 * channelled. It pays 10 back, so the true price is 90.
 *
 * A bigger wall spreads a blow further, since absorbed damage is split evenly across
 * whoever is still standing in it. That is the only reason to gather more than one.
 *
 * The shield itself lives in {@link FleshShields}.
 */
public class FleshShield implements Ability {

    /** Registry key. */
    public static final String KEY = "flesh shield";

    /** What the cast pays into the BLOOD track. */
    private static final int BLOOD_XP = 10;

    @Override
    public String getName() {
        return "Flesh shield";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    /**
     * Zero, deliberately: the xp is paid into the BLOOD track instead, in execute.
     *
     * The dispatcher's reward goes to the ordinary level, and bloodbending's whole
     * point is that its abilities feed a separate one — the same reason every
     * bloodbending channel returns 0 from getXpPerSecond.
     */
    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getCooldownTicks() {
        return 400; // 20 seconds
    }

    /** Up already: the next press takes it down. */
    @Override
    public boolean isActive(ServerPlayer player, BendingData data) {
        return FleshShields.has(player);
    }

    @Override
    public void deactivate(ServerPlayer player, BendingData data) {
        FleshShields.drop(player);
    }

    /**
     * Refuses with nothing in front worth taking.
     *
     * Checked before chi is spent, so a shield of nobody costs nothing rather than 100
     * chi for an empty wall. The chi itself is the dispatcher's to gate.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (FleshShields.candidates(player).isEmpty()) {
            player.displayClientMessage(Component.literal(
                    "There is nobody to take.").withStyle(ChatFormatting.DARK_RED), true);
            return false;
        }

        return true;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        List<LivingEntity> bodies = FleshShields.candidates(player);
        if (bodies.isEmpty()) return;

        FleshShields.raise(player, bodies);
        Blood.grantXp(player, data, BLOOD_XP);
    }
}
