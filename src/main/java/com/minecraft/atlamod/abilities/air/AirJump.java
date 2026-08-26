package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Defensive / Air. A column of air thrown down underfoot: the bender is launched
 * straight up, anywhere from 5 blocks to 20 depending on how long the key was held,
 * and lands from it without taking a scratch.
 *
 * Charged and fires on release, like Fire Blow, so the height is chosen in the
 * moment rather than fixed — the whole point of the ability is picking a height, and
 * a charge that only paid out when full would make 19 of the 20 blocks unusable.
 *
 * Fall protection is a countdown on BendingData rather than something applied to the
 * player, because it has to outlive the launch: see AIR_TIME below.
 */
public class AirJump implements ChargedAbility {

    /** 2 seconds of hold takes it from the floor of 5 blocks to the full 20. */
    private static final int MAX_CHARGE = 40;

    /**
     * A press this short is a brush of the key, not a cast, and costs nothing.
     * Deliberately tiny: unlike Fire Blow, the shortest REAL charge here is still a
     * 5-block jump, so anything the player actually meant should pay out.
     */
    private static final int MIN_CHARGE = 3;

    private static final double MIN_HEIGHT = 5.0;
    private static final double MAX_HEIGHT = 20.0;

    /**
     * How long fall protection lasts, in ticks. Generous — 20 seconds — because it
     * has to cover the whole descent, and a bender who clears a cliff edge on the way
     * up is still falling from THIS jump however long that takes. It ends the moment
     * they land; this is only the backstop for a landing that is somehow never seen.
     */
    private static final int AIR_TIME = 400;

    /**
     * How long to wait for the launch to actually get the player airborne before
     * giving up on it. The server applies the velocity but the CLIENT moves the
     * player, so "off the ground" arrives a few ticks later — this is only the bound
     * on a jump that never leaves the ground at all (cast under a low ceiling), so
     * that one can't sit on free fall protection for the full AIR_TIME.
     */
    private static final int LAUNCH_TIMEOUT = 40;

    // Vanilla player physics, used to work out what launch speed reaches a height.
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    @Override
    public String getName() {
        return "Air jump";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 0;
    }

    @Override
    public int getChargeTicks() {
        return MAX_CHARGE;
    }

    @Override
    public boolean firesOnRelease() {
        return true;
    }

    @Override
    public int getMinimumChargeTicks() {
        return MIN_CHARGE;
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = power(ticksHeld);

        // Air gathering under the bender's feet, thickening as the charge builds.
        int count = 4 + (int) (14 * power);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.1, player.getZ(),
                count, 0.4, 0.05, 0.4, 0.02);

        // The height being wound up, on the action bar. A jump is aimed at something
        // — a ledge, a roof — so the player needs to know what they are buying before
        // they let go, which the charge bar on its own can't tell them.
        if (ticksHeld % 2 == 0) {
            player.displayClientMessage(
                    Component.literal("§bAir Jump: §f" + Math.round(heightFor(power)) + " blocks"),
                    true);
        }

        if (ticksHeld % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS,
                    0.4F, 1.0F + 0.8F * power);
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // How far the charge actually got. Recorded by the dispatcher just before the
        // cast, because the live counter is cleared to stop a double fire.
        double height = heightFor(power(data.getLastChargeTicks()));
        double launch = launchSpeedFor(height);

        // Horizontal motion is left alone rather than zeroed, so a bender who jumps
        // while running carries that run into the air instead of stopping dead.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, launch, motion.z);
        player.hurtMarked = true;

        data.setAirJumpTicks(AIR_TIME);
        data.setAirJumpLeftGround(false);

        // Anything already banked — a chained jump cast while falling — is not this
        // jump's doing and shouldn't land on the player when they touch down.
        player.fallDistance = 0.0F;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 1.0F);

        // The blast of air that threw them up, spreading out along the ground.
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL,
                player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.1, player.getZ(),
                40, 0.6, 0.1, 0.6, 0.25);
    }

    /**
     * Called every tick from ServerEvents while the fall-protection window is open.
     * Air Jump ends itself on landing, so it isn't a channeled ability — but its
     * per-tick logic still belongs to this class rather than to the server tick loop.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        int remaining = data.getAirJumpTicks() - 1;
        data.setAirJumpTicks(remaining);

        // The real guarantee that this jump doesn't hurt. Fall damage is worked out
        // from the distance banked in Entity#fallDistance, so holding that at zero
        // for the whole flight means there is nothing to turn into damage no matter
        // which tick the landing is noticed on, or in which order the server happens
        // to run the landing and this tick. Cancelling the fall event alone left that
        // to timing; this doesn't.
        player.fallDistance = 0.0F;

        // The client is what actually moves the player, so this is how the server
        // learns the launch worked. Everything below waits for it.
        if (!player.onGround()) {
            data.setAirJumpLeftGround(true);
        }

        if (player.level() instanceof ServerLevel level) {
            // A wisp of air off the feet, so the ride is visibly being held up.
            level.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.1, player.getZ(),
                    2, 0.2, 0.05, 0.2, 0.01);

            if (data.hasAirJumpLeftGround() && (player.onGround() || player.isInWater())) {
                data.setAirJumpTicks(0);

                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.1, player.getZ(),
                        20, 0.5, 0.05, 0.5, 0.12);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BREEZE_LAND, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }

        // Never got off the ground — cast under a ceiling, most likely. Drop the
        // protection rather than leaving it running for the full AIR_TIME.
        if (!data.hasAirJumpLeftGround() && remaining < AIR_TIME - LAUNCH_TIMEOUT) {
            data.setAirJumpTicks(0);
        }

        player.setData(ModAttachments.BENDING_DATA, data);
    }

    /**
     * Charge progress, 0 to 1.
     *
     * Measured from MIN_CHARGE rather than from zero, so the shortest press that
     * fires at all is exactly the 5-block floor. Scaling from zero instead would
     * make the smallest jump the ability can actually produce a little OVER its
     * stated minimum, since everything below MIN_CHARGE never fires.
     */
    private static float power(int ticksHeld) {
        float progress = (ticksHeld - MIN_CHARGE) / (float) (MAX_CHARGE - MIN_CHARGE);
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    /** The height a given charge buys, in blocks. */
    private static double heightFor(float power) {
        return MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * power;
    }

    /**
     * The upward velocity that reaches {@code height} under vanilla player physics.
     *
     * Solved rather than tuned by hand. Height is NOT proportional to launch speed —
     * drag means doubling the speed more than doubles the climb — so lerping the
     * VELOCITY between two hand-picked values would make "halfway charged" land well
     * short of halfway up. Lerping the height and inverting it here is what makes the
     * number on the action bar true.
     */
    private static double launchSpeedFor(double height) {
        double low = 0.0;
        double high = 4.0;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) * 0.5;
            if (peakOf(mid) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5;
    }

    /** How high a launch of {@code speed} climbs, by running vanilla's own step. */
    private static double peakOf(double speed) {
        double v = speed;
        double climbed = 0.0;
        for (int i = 0; i < 400 && v > 0.0; i++) {
            climbed += v;
            v = (v - GRAVITY) * DRAG;
        }
        return climbed;
    }
}
