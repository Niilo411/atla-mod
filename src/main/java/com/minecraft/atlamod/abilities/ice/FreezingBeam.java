package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Ice. Three seconds of gathering produces a ball of ice floating beside the
 * bender; a left click opens it into a beam that runs for twenty seconds, freezing
 * and wearing down everything it crosses.
 *
 * Both held shapes at once, like Fireball and Lightning bolt: the charge builds the
 * ball, and what the finished charge produces is the armed slot the click looses.
 *
 * ONE INTERPRETATION worth flagging: the design says the beam fires "when holding
 * left click". There is no held-left-click signal in the mod — the left click reaches
 * the server as a single event — so the click STARTS the beam and it runs its twenty
 * seconds on its own. That also reads better with the stated duration, which would
 * otherwise be a limit nobody could reach without holding a mouse button for twenty
 * seconds straight.
 */
public class FreezingBeam implements ChargedAbility, TwoPhaseAbility {

    @Override
    public String getName() {
        return "Freezing Beam";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 15;
    }

    @Override
    public int getCooldownTicks() {
        return 200; // 10 seconds, from the click that fires it
    }

    @Override
    public int getChargeTicks() {
        return 60; // 3 seconds
    }

    /**
     * Refuses to build a second ball while a beam is still running.
     *
     * Without this the cooldown could be waited out mid-beam and a bender could stack
     * beams on top of each other — the ten seconds is shorter than the twenty the beam
     * lasts, which is a gap only this closes.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return !FreezingBeams.has(player);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        ServerLevel level = (ServerLevel) player.level();

        double progress = ticksHeld / (double) getChargeTicks();
        Vec3 beside = player.getEyePosition().add(player.getLookAngle().scale(0.6));

        Ice.frost(level, beside, 2 + (int) (progress * 5), 0.7 - (progress * 0.5));
    }

    /** Held until fired: the ball waits beside the bender as long as they like. */
    @Override
    public int getArmedDurationTicks() {
        return 0;
    }

    /** The finished ball, turning and waiting. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize().scale(0.6);
        Vec3 ball = player.getEyePosition().add(side).subtract(0.0, 0.2, 0.0);

        double phase = player.tickCount * 0.3;
        for (int i = 0; i < 3; i++) {
            double a = phase + (i * Math.PI * 2.0 / 3.0);
            Vec3 at = ball.add(Math.cos(a) * 0.3, Math.sin(a * 1.5) * 0.15, Math.sin(a) * 0.3);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        FreezingBeams.start(player);
    }

    /** Arming is the whole cast: the ball forms and waits on the click. */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Ice.form((ServerLevel) player.level(), player.position(), 0.8F, 1.1F);
    }
}
