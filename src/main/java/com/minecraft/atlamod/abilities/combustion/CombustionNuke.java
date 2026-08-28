package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Combustion. Ten seconds of gathering, and then four blasts laid in a line all
 * the way out to whatever the bender is looking at — each one worth seven sticks of
 * TNT.
 *
 * The most destructive thing in the mod by a wide margin, and the ten second wind-up
 * is the whole of what holds it back. Ten seconds is an eternity in a fight; this is
 * something aimed at a place rather than at a person.
 *
 * Letting go early MISFIRES, and after nine seconds of gathering that is a genuinely
 * frightening prospect — which is exactly the point.
 */
public class CombustionNuke implements ChargedAbility {

    /** How many blasts are laid along the line. */
    private static final int COUNT = 4;

    /**
     * "As powerful as 7 tnt".
     *
     * One stick is power 4.0, and an explosion's radius goes roughly as its power
     * rather than as the cube of it, so seven sticks' worth of destruction is nearer
     * three times the figure than seven times it. 12.0 is the reading taken here —
     * three times a single stick, and already enough to take a considerable bite out
     * of a hillside.
     */
    private static final float POWER = 12.0F;

    /** How far out the line of blasts can reach, in blocks. */
    private static final double REACH = 40.0;

    @Override
    public String getName() {
        return "Combustion nuke";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 1000;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    /**
     * Two seconds. The ten second wind-up and the 1000 chi are the real limits — at
     * that price it is uncastable below level 5, since getMaxChi is 500 + level*100.
     * A gate rather than a bug, and the same one Fire Rain and Tsunami have.
     */
    @Override
    public int getCooldownTicks() {
        return 2000; // 100 seconds
    }

    @Override
    public int getChargeTicks() {
        return 200; // 10 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        Combustion.gather(level, player, ticksHeld, getChargeTicks());

        // A warning everyone nearby can hear, rising as it fills. Ten seconds is long
        // enough that anything within range deserves the chance to leave.
        if (ticksHeld % 20 == 0) {
            float pitch = 0.6F + (ticksHeld / (float) getChargeTicks()) * 1.2F;
            Combustion.boom(level, player.position(), 0.7F, pitch);
        }
    }

    @Override
    public void onChargeCancel(ServerPlayer player, BendingData data) {
        Combustion.misfire(player);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 from = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // Where the line ends: whatever is being looked at, or the full reach.
        net.minecraft.world.phys.HitResult hit = level.clip(
                new net.minecraft.world.level.ClipContext(
                        from, from.add(look.scale(REACH)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));

        Vec3 target = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation()
                : from.add(look.scale(REACH));

        // Spread evenly BETWEEN the bender and the target rather than piled on it, so
        // the ability reads as a line of blasts walking out to where it was aimed —
        // which is what "all the way to the block you are looking at" describes.
        //
        // The loop starts at 1 rather than 0, so the nearest blast is a quarter of the
        // way out instead of underneath the bender's own feet. At this power, starting
        // at zero would simply be suicide.
        for (int i = 1; i <= COUNT; i++) {
            Vec3 at = from.add(target.subtract(from).scale(i / (double) COUNT));
            Combustion.detonate(level, player, at, POWER);
        }

        Combustion.boom(level, player.position(), 2.0F, 0.5F);
    }
}
