package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Fire. Two-phase: the slot key arms the fireball, the next
 * left click hurls it. The 2-second cooldown starts on release.
 */
public class Fireball implements TwoPhaseAbility {

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
        return 40;
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        // Don't let them arm a second ability while one is already held.
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        // Arming is handled by AbilityHandler; this is just the ignition cue.
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
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
