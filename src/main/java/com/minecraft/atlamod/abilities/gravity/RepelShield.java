package com.minecraft.atlamod.abilities.gravity;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

/**
 * Defensive / Gravity. A held field of distorted space that takes the weight out of
 * every blow rather than stopping them outright, and throws off anything shot at
 * the bender from two blocks out.
 *
 * NOT full invulnerability, unlike Fire Shield and Water Shield — this is the first
 * channel in the mod that reduces damage by a FACTOR instead of cancelling it, which
 * needed its own hook on ChanneledAbility (damageReductionFactor) rather than
 * reusing blocks()/grantsInvulnerability(), whose whole contract is all-or-nothing.
 * See ChanneledAbility and AbilityHandler#damageReductionFor.
 *
 * The projectile repel reuses Sound Wall's exact trick: anything caught that is a
 * Projectile is simply discarded rather than damaged or deflected, since a
 * projectile that kept flying without hurting anyone would still visibly stick in
 * whatever stood behind the bender.
 */
public class RepelShield implements ChanneledAbility {

    private static final double RADIUS = 2.0;

    /** "8 times less damage", the design's own figure. */
    private static final double REDUCTION = 8.0;

    @Override
    public String getName() {
        return "Repel Shield";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0;
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 3;
    }

    @Override
    public double getXpPerSecond() {
        return 1;
    }

    @Override
    public double damageReductionFactor(BendingData data) {
        return REDUCTION;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // The visible shell.
        double cx = player.getX();
        double cy = player.getY() + player.getBbHeight() * 0.5;
        double cz = player.getZ();
        for (int i = 0; i < 16; i++) {
            double angle = (player.tickCount + i * (20.0 / 16.0)) * 0.15;
            double px = cx + Math.cos(angle) * RADIUS;
            double pz = cz + Math.sin(angle) * RADIUS;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, cy, pz, 1, 0.0, 0.1, 0.0, 0.0);
        }

        AABB box = new AABB(player.position(), player.position()).inflate(RADIUS);
        for (Entity caught : level.getEntities(player, box)) {
            if (caught.position().distanceToSqr(player.position()) > RADIUS * RADIUS) continue;
            if (!(caught instanceof Projectile projectile)) continue;

            level.sendParticles(ParticleTypes.EXPLOSION, caught.getX(), caught.getY(), caught.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            projectile.discard();
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }
}
