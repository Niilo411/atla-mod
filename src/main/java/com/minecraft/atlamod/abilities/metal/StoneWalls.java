package com.minecraft.atlamod.abilities.metal;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingProjectiles;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Metal. Three seconds of gathering produces three slabs of wall, two blocks
 * wide and three tall, thrown one per left click wherever the crosshair points.
 *
 * Both held shapes at once, like Fireball and Air splinters: the charge builds them,
 * and what the finished charge produces is the armed slot the clicks spend. Three
 * shots, so the slot stays armed until all three are gone and the cooldown waits for
 * the last.
 *
 * Six hearts each is the heaviest per-shot figure in the mod outside a masterclass,
 * and the one second cooldown means all three can be spent almost at once — which is
 * what the 150 chi and the three second wind-up are paying for.
 */
public class StoneWalls implements ChargedAbility, TwoPhaseAbility {

    /** How many walls a full charge produces. */
    private static final int WALLS = 3;

    /** 6 hearts, as specced. */
    private static final float DAMAGE = 12.0F;

    /**
     * A wall is wide, so it catches wide.
     *
     * The hit radius is the mod's measure of how big a shot is, and at two blocks
     * across this is the broadest thing anybody throws — a wall that had to be aimed
     * like a dart would not read as a wall.
     */
    private static final BendingProjectiles.Spec WALL = new BendingProjectiles.Spec(
            1.8, 50, DAMAGE, 1.6, 0.5, BendingProjectiles.Style.STONE);

    @Override
    public String getName() {
        return "Stone walls";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    /** One second, and it waits for the LAST of the three. */
    @Override
    public int getCooldownTicks() {
        return 20;
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    /** Three clicks before the slot is spent. */
    @Override
    public int getShots() {
        return WALLS;
    }

    /** Held until thrown: the walls wait as long as the bender likes. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().scale(2.0));

        Metal.spark(level, ahead, 3 + (int) (progress * 8), 1.4 - (progress * 0.9));
    }

    /** The walls waiting, drawn as three slabs turning around the bender. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 centre = player.getEyePosition().add(player.getLookAngle().scale(1.6));

        double phase = player.tickCount * 0.15;
        for (int i = 0; i < data.getTwoPhaseShots(); i++) {
            double a = phase + (i * Math.PI * 2.0 / WALLS);
            Vec3 at = centre.add(Math.cos(a) * 1.0, Math.sin(a * 1.3) * 0.3, Math.sin(a) * 1.0);

            Metal.spark(level, at, 2, 0.2);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(1.0));

        BendingProjectiles.launch(player, from, player.getLookAngle(),
                new BendingProjectiles.Spec(
                        WALL.speed(), WALL.lifetime(), Metal.damage(data, DAMAGE),
                        WALL.hitRadius(), WALL.knockback(), WALL.style()));

        Metal.spark(level, from, 25, 0.5);
        Metal.clang(level, player.position(), 1.1F, 1.0F);
    }

    /** Arming is the whole cast: the walls gather and wait on the clicks. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Metal.scrape((ServerLevel) player.level(), player.position(), 0.9F, 0.8F);
    }
}
