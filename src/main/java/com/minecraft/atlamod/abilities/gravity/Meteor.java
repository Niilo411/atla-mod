package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Masterclass / Gravity. Fifteen seconds of gathering assembles a four-by-four-by-
 * four block of mixed stone above the bender's head; a left click sends it down the
 * crosshair to explode on whatever it hits.
 *
 * Both held shapes at once, the way Fireball and Lava throw are: the charge is what
 * BUILDS the meteor, and what it produces is the armed two-phase slot the left click
 * then throws. Sized down from the design's literal "16x16x16" — four thousand
 * ninety-six real blocks is far beyond anything else the mod assembles, where a
 * four-block cube is sixty-four and still reads as a real, solid mass overhead.
 */
public class Meteor implements ChargedAbility, TwoPhaseAbility {

    @Override
    public String getName() {
        return "Meteor";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 250;
    }

    @Override
    public int getXpReward() {
        return 25;
    }

    @Override
    public int getCooldownTicks() {
        return 600; // 30 seconds
    }

    @Override
    public int getChargeTicks() {
        return 300; // 15 seconds
    }

    /** Nothing else already held, the same guard Lava throw uses. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Gravity.gather((net.minecraft.server.level.ServerLevel) player.level(),
                player.position().add(0.0, 10.0, 0.0),
                3, 2.0 - (1.5 * (ticksHeld / (double) getChargeTicks())));
    }

    /** The charge only assembles the meteor; the click is what throws it. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        MeteorBlocks.summon(player);

        player.displayClientMessage(Component.literal(
                "§5Meteor gathered — left click to throw"), true);
    }

    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        // MeteorBlocks.tickAll (run from ServerEvents each server tick) is what keeps
        // the formation hovering and eventually flying — the same split IceBombs
        // uses, since the manager has to keep moving a THROWN meteor even after this
        // ability's own armed slot has been cleared.
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        MeteorBlocks.throwIt(player);
    }
}
