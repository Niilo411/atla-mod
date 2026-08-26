package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Water. Tears a stream out of a body of water and holds it, ready to be
 * thrown at whatever the bender turns to face.
 *
 * Two-phase, but unlike Fireball and Water ball the armed state does not wait: there
 * are three seconds to find a target before the water falls apart. The chi is spent
 * when the stream is drawn, so losing the window loses the cast — which is what makes
 * the window mean anything.
 *
 * The water is not actually removed from the pool. Draining source blocks would let a
 * bender empty a pond a stream at a time, and with infinite sources it would only
 * refill anyway, so the pull is drawn rather than performed.
 */
public class WaterStream implements TwoPhaseAbility {

    /** How far away a body of water can be and still be drawn from. */
    private static final double REACH = 20.0;

    /** Three seconds to place the shot. */
    private static final int ARMED_WINDOW = 60;

    /** 4 hearts. */
    private static final float DAMAGE = 8.0F;

    /** Faster than Water ball, and wider — it is a lance of water, not a lobbed mass. */
    private static final double SPEED = 2.0;
    private static final int LIFETIME = 25;
    private static final double HIT_RADIUS = 1.1;
    private static final double KNOCKBACK = 0.4;

    /** One shot of this ability, as the shared projectile system wants it. */
    private static final com.minecraft.atlamod.abilities.BendingProjectiles.Spec SHOT =
            new com.minecraft.atlamod.abilities.BendingProjectiles.Spec(
                    SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK,
                    com.minecraft.atlamod.abilities.BendingProjectiles.Style.WATER);

    @Override
    public String getName() {
        return "Water stream";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 8;
    }

    @Override
    public int getArmedDurationTicks() {
        return ARMED_WINDOW;
    }

    /**
     * Deliberately NOT gated on the canteen, for the same reason Water heal is not:
     * this ability already demands a body of water in sight, which is a stronger
     * requirement than the generic "within 15 blocks" rule. Charging a canteen unit
     * for water the bender is looking straight at would be nonsense.
     */
    @Override
    public boolean requiresWater() {
        return false;
    }

    /** Needs open water in view, and nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (!data.getActiveTwoPhaseAbility().isEmpty()) return false;

        if (findWater(player) == null) {
            player.displayClientMessage(
                    Component.literal("§bYou must be looking at water!"), true);
            return false;
        }
        return true;
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        BlockPos source = findWater(player);
        if (source == null) return;

        level.playSound(null, source, SoundEvents.PLAYER_SPLASH_HIGH_SPEED,
                SoundSource.PLAYERS, 1.2F, 0.7F);

        // The pull itself: water running from the pool up to the bender.
        Vec3 from = new Vec3(source.getX() + 0.5, source.getY() + 1.0, source.getZ() + 0.5);
        Vec3 to = player.getEyePosition();
        Vec3 step = to.subtract(from).scale(1.0 / 12.0);

        for (int i = 0; i <= 12; i++) {
            Vec3 at = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.02);
        }
    }

    /** The stream circling the bender while they look for something to aim at. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // Wound tight around the player rather than held out in front, so it reads as
        // water being kept under control rather than as something already thrown.
        double spin = (player.tickCount % 12) / 12.0 * Math.PI * 2.0;
        double radius = 1.0;

        for (int i = 0; i < 3; i++) {
            double angle = spin + (Math.PI * 2.0 * i / 3.0);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + 0.6 + (i * 0.35);

            level.sendParticles(ParticleTypes.FALLING_WATER, px, py, pz, 3, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.SPLASH, px, py, pz, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Left click, with a stream in hand: send it wherever they are looking. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.8));

        com.minecraft.atlamod.abilities.BendingProjectiles.launch(player, from, look, SHOT);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4F, 1.2F);
    }

    /** The window closed with the water unspent: it simply falls. */
    @Override
    public void onArmedExpire(ServerPlayer player, BendingData data) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SPLASH,
                    player.getX(), player.getY() + 0.8, player.getZ(),
                    25, 0.6, 0.4, 0.6, 0.05);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.7F, 0.6F);

        player.displayClientMessage(
                Component.literal("§bThe water slipped away..."), true);
    }

    /** The block of water the player is looking at, or null if they are not. */
    private static BlockPos findWater(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(REACH));

        // SOURCE_ONLY so the thin edge of a flow does not count as a body of water.
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));

        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        return level.getFluidState(pos).is(FluidTags.WATER) ? pos : null;
    }
}
