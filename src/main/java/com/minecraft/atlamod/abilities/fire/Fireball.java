package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Fire. Held for two seconds to build up, then throws itself.
 *
 * Was previously two-phase — arm on a press, throw on a left click — and is now a
 * charge, so the wind-up is a timer rather than a second input.
 */
public class Fireball implements ChargedAbility {

    @Override
    public String getName() {
        return "Fireball";
    }

    @Override
    public int getChiCost() {
        return 100;
    }

    @Override
    public int getXpReward() {
        return 10;
    }

    @Override
    public int getCooldownTicks() {
        return 40; // 2 seconds
    }

    @Override
    public int getChargeTicks() {
        return 40; // 2 seconds of hold
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // A ball gathering in front of the player, tightening as it fills.
        Vec3 look = player.getLookAngle();
        double px = player.getX() + look.x * 2.0;
        double py = player.getEyeY() + look.y * 2.0;
        double pz = player.getZ() + look.z * 2.0;

        double spread = 0.5 - (0.35 * ticksHeld / (double) getChargeTicks());
        level.sendParticles(ParticleTypes.FLAME, px, py, pz, 4, spread, spread, spread, 0.01);
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // Nerfed Large Fireball (explosion power 1).
        Vec3 movement = new Vec3(look.x * 0.1, look.y * 0.1, look.z * 0.1);
        LargeFireball bigFireball = new LargeFireball(level, player, movement, 1);

        bigFireball.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
        level.addFreshEntity(bigFireball);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.5F, 1.0F);
    }
}
