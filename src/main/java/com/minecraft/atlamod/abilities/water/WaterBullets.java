package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Water. Three bullets of water held ready, fired one per click at
 * whatever the bender is looking at.
 *
 * Two-phase, and the first ability that is not spent by its first click: the slot
 * stays armed until all three are gone, so each can be aimed separately rather than
 * all being committed to one target at once. There is no window on it — the bullets
 * keep until they are used.
 */
public class WaterBullets implements TwoPhaseAbility {

    /** Fired one at a time. */
    private static final int SHOTS = 3;

    /** 4 hearts each. */
    private static final float DAMAGE = 8.0F;

    /** Faster than anything else the mod throws — these are bullets. */
    private static final double SPEED = 2.6;

    /** Shorter life than Water ball, but the speed still carries it about 40 blocks. */
    private static final int LIFETIME = 16;

    /** Tight, to suit something moving this fast. */
    private static final double HIT_RADIUS = 0.8;
    private static final double KNOCKBACK = 0.35;

    /** One shot of this ability, as the shared projectile system wants it. */
    private static final com.minecraft.atlamod.abilities.BendingProjectiles.Spec SHOT =
            new com.minecraft.atlamod.abilities.BendingProjectiles.Spec(
                    SPEED, LIFETIME, DAMAGE, HIT_RADIUS, KNOCKBACK,
                    com.minecraft.atlamod.abilities.BendingProjectiles.Style.WATER);

    @Override
    public String getName() {
        return "Water Bullets";
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
        return 40; // 2 seconds, starting from the last of the three
    }

    /** Three clicks, not one. */
    @Override
    public int getShots() {
        return SHOTS;
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    /** Nothing else already held. */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    /**
     * The bullets still in hand, circling the bender.
     *
     * Drawn from the live count rather than from SHOTS, so firing one visibly leaves
     * two — the ring is the ammunition counter.
     */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        int remaining = data.getTwoPhaseShots();
        if (remaining <= 0) return;

        double spin = (player.tickCount % 20) / 20.0 * Math.PI * 2.0;
        double radius = 0.9;

        for (int i = 0; i < remaining; i++) {
            double angle = spin + (Math.PI * 2.0 * i / remaining);
            double px = player.getX() + Math.cos(angle) * radius;
            double pz = player.getZ() + Math.sin(angle) * radius;
            double py = player.getY() + 1.3;

            level.sendParticles(ParticleTypes.SPLASH, px, py, pz, 5, 0.12, 0.12, 0.12, 0.01);
            level.sendParticles(ParticleTypes.BUBBLE, px, py, pz, 2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    /** One click, one bullet. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(0.8));

        com.minecraft.atlamod.abilities.BendingProjectiles.launch(player, from, look, SHOT);

        // Pitch climbs as the ammunition runs down, so the last shot is audibly the last.
        float pitch = 1.0F + (0.2F * (SHOTS - data.getTwoPhaseShots()));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.1F, pitch);
    }
}
