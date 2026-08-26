package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Sound. Five seconds of compressing the air, then twenty-five blocks forward
 * in one leap -- and nothing to pay for the landing.
 *
 * A LAUNCH rather than a teleport, which is what separates it from Lightning Jump:
 * the bender really travels the distance, so a wall in the way stops them and the
 * arc can be aimed over things. The fall protection is what makes that survivable.
 *
 * It borrows Air jump's protection wholesale by reusing the same countdown on
 * BendingData -- the fall damage window, the "has actually left the ground" guard and
 * the LivingFallEvent cancel are all already there and all already correct.
 */
public class SoundLeap implements ChargedAbility {

    /** How far forward the leap carries, in blocks. */
    private static final double DISTANCE = 25.0;

    /** How much of the throw is upward, as a share of the forward push. */
    private static final double LIFT = 0.45;

    @Override
    public String getName() {
        return "Sound Leap";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 50;
    }

    @Override
    public int getXpReward() {
        return 5;
    }

    /** None at all, as specced. The five second wind-up is the whole limit. */
    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public int getChargeTicks() {
        return 100; // 5 seconds
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        // Compressing: the air draws in tighter around the bender as it fills.
        double progress = ticksHeld / (double) getChargeTicks();
        Sound.wave(level, player.position().add(0.0, 1.0, 0.0),
                3 + (int) (progress * 8), 1.6 - (progress * 1.2));
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        // Solved rather than guessed. Minecraft drag means distance is not
        // proportional to launch speed, so the speed that carries twenty-five blocks
        // has to be worked out -- the same argument Air jump makes about height.
        double speed = speedForDistance(DISTANCE);

        player.setDeltaMovement(heading.x * speed, speed * LIFT, heading.z * speed);
        player.hurtMarked = true;

        // Air jump's fall protection, reused whole: the countdown, the left-ground
        // guard and the LivingFallEvent cancel are all already there and correct.
        data.setAirJumpTicks(com.minecraft.atlamod.abilities.air.AirJump.AIR_TIME);
        data.setAirJumpLeftGround(false);

        Sound.burst(level, player.position(), 30, 0.6);
        Sound.boom(level, player.position(), 1.5F);
    }

    /**
     * The launch speed that carries roughly {@code distance} blocks.
     *
     * Binary searched against a run of vanilla's own horizontal step, exactly as Air
     * jump solves for height. Horizontal drag is 0.91 a tick airborne, and the flight
     * lasts as long as the upward part keeps the bender off the ground -- so the two
     * are solved together rather than separately.
     */
    private static double speedForDistance(double distance) {
        double low = 0.0;
        double high = 5.0;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) * 0.5;
            if (reachOf(mid) < distance) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5;
    }

    /** How far a launch of {@code speed} travels before it comes back to its own height. */
    private static double reachOf(double speed) {
        double horizontal = speed;
        double vertical = speed * LIFT;
        double travelled = 0.0;
        double height = 0.0;

        for (int i = 0; i < 400; i++) {
            travelled += horizontal;
            height += vertical;

            horizontal *= 0.91;
            vertical = (vertical - 0.08) * 0.98;

            if (height <= 0.0 && i > 0) break;
        }
        return travelled;
    }
}
