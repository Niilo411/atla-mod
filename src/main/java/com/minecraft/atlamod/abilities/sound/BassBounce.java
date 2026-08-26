package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Left / Sound. The bender leaps three blocks and slams back down, and the landing
 * throws everything within fifteen blocks ten blocks into the air.
 *
 * Cast and left running, like Fire Leap and Fire Rain: the jump happens now and the
 * SLAM happens whenever the bender lands, which is a countdown on BendingData ticked
 * by {@link #tick} rather than anything held. That delay is the ability — everyone
 * around gets the two-thirds of a second between the hop and the landing to move.
 */
public class BassBounce implements Ability {

    /** How high the opening hop carries the bender. */
    private static final double HOP_HEIGHT = 3.0;

    /** How hard they are driven back down, so the landing is quick rather than floaty. */
    private static final double SLAM_SPEED = -1.6;

    /** How far the slam reaches, in blocks, in every direction. */
    public static final double RADIUS = 15.0;

    /** How high everything caught is thrown. */
    private static final double BOUNCE_HEIGHT = 10.0;

    /** The bonus damage on top of whatever the fall costs them. */
    private static final float DAMAGE = 4.0F;

    /**
     * How long the ability waits for a landing before giving up.
     *
     * A backstop, not the usual path: if a landing is somehow never seen — the bender
     * logs out mid-hop, or lands somewhere the server never registers — the slam
     * expires by itself rather than waiting for the rest of the session.
     */
    public static final int AIR_TIME = 100;

    @Override
    public String getName() {
        return "Bass Bounce";
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
        return 300; // 15 seconds
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // Solved rather than guessed, the same way Air jump does it: drag means height
        // is not proportional to launch speed, so the speed that reaches three blocks
        // has to be worked out rather than picked.
        double launch = com.minecraft.atlamod.abilities.air.AirJump.speedForHeight(HOP_HEIGHT);

        player.setDeltaMovement(player.getDeltaMovement().x, launch, player.getDeltaMovement().z);
        player.hurtMarked = true;

        data.setBassBounceTicks(AIR_TIME);
        data.setBassBounceLeftGround(false);

        Sound.play(level, player.position(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.4F, 0.5F);
        Sound.wave(level, player.position(), 20, 0.5);
    }

    /**
     * Runs the hop and fires the slam on landing.
     *
     * The window closes on "has actually left the ground", not on a timer — the same
     * guard Air jump uses, and for the same reason: the server applies the launch but
     * the CLIENT moves the player, so for the first few ticks the server still sees
     * them standing where they were. A landing test without this would fire the slam
     * on the launch itself.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        int left = data.getBassBounceTicks() - 1;
        data.setBassBounceTicks(left);

        if (!player.onGround()) {
            data.setBassBounceLeftGround(true);

            // Driven down hard once they are past the top of the hop, so the slam
            // arrives with weight instead of drifting back to earth.
            if (player.getDeltaMovement().y < 0.1 && data.hasBassBounceLeftGround()) {
                player.setDeltaMovement(
                        player.getDeltaMovement().x, SLAM_SPEED, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
            return;
        }

        if (!data.hasBassBounceLeftGround()) {
            // Still on the ground — either the launch has not been applied yet, or it
            // was cast under a ceiling and never got off the floor. The AIR_TIME
            // countdown is what stops the second case waiting forever.
            if (left <= 0) data.setBassBounceTicks(0);
            return;
        }

        slam(player, data);
        data.setBassBounceTicks(0);
        data.setBassBounceLeftGround(false);
    }

    /** The landing itself. */
    private static void slam(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        double bounce = com.minecraft.atlamod.abilities.air.AirJump.speedForHeight(BOUNCE_HEIGHT);
        float damage = Sound.damage(data, DAMAGE);

        var area = new net.minecraft.world.phys.AABB(player.position(), player.position())
                .inflate(RADIUS);

        // getEntities(player, ...) skips the caster: they are the one doing the
        // slamming, and being thrown ten blocks by their own landing would be absurd.
        for (var caught : level.getEntities(player, area)) {
            if (!(caught instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            if (living.position().distanceToSqr(player.position()) > RADIUS * RADIUS) continue;

            living.hurt(player.damageSources().indirectMagic(player, player), damage);

            // AFTER the damage: hurt() applies its own knockback, so setting the
            // motion first would have the throw quietly overwritten by a smaller one.
            living.setDeltaMovement(living.getDeltaMovement().x, bounce, living.getDeltaMovement().z);
            living.hurtMarked = true;
        }

        // The bender does not pay for their own hop.
        player.fallDistance = 0.0F;

        for (int r = 2; r <= RADIUS; r += 3) {
            Sound.ring(level, player.position(), r, 10 + r);
        }
        Sound.boom(level, player.position(), 1.8F);
    }
}
