package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingFire;
import com.minecraft.atlamod.abilities.ChargedAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Offensive / Fire. Both shapes at once: hold the slot key for two seconds to
 * build the fireball, then left click to throw it.
 *
 * ChargedAbility drives the wind-up; TwoPhaseAbility is what the wind-up produces.
 * The dispatcher arms the two-phase slot as part of the completed cast, which is
 * also why the cooldown waits for the throw rather than starting when it is built.
 */
public class Fireball implements ChargedAbility, TwoPhaseAbility {

    /**
     * Explosion power. A ghast's is 1, TNT is 4, so the ordinary throw is deliberately
     * modest and the blue one is still well short of a charge of TNT.
     *
     * Worth knowing: power drives block destruction as well as damage, so a blue
     * fireball digs a bigger hole too — though only where mob griefing is on, since
     * that is what decides whether a fireball breaks anything at all.
     */
    private static final int EXPLOSION_POWER = 1;
    private static final int BLUE_EXPLOSION_POWER = 2;

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
        return 40; // 2 seconds, starting from the throw
    }

    @Override
    public int getChargeTicks() {
        return 40; // 2 seconds of hold to build it
    }

    /**
     * Refuses to start a second wind-up while a built fireball is still in hand.
     * Without this the player could charge again, pay another 100 chi, and simply
     * re-arm the slot they already had armed.
     */
    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return data.getActiveTwoPhaseAbility().isEmpty();
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
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
        level.sendParticles(BendingFire.flame(data), px, py, pz, 4, spread, spread, spread, 0.01);
    }

    /**
     * The charge completed. The dispatcher has already armed the two-phase slot,
     * so all that is left is telling the player it is ready to throw.
     */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.6F);
    }

    /** The ball of fire held ready, so others can see it coming and not just the HUD. */
    @Override
    public void onArmedTick(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 look = player.getLookAngle();
        double px = player.getX() + look.x * 2.0;
        double py = player.getY() + 1.2 + look.y * 2.0;
        double pz = player.getZ() + look.z * 2.0;

        level.sendParticles(BendingFire.flame(data), px, py, pz, 10, 0.3, 0.3, 0.3, 0.05);
    }

    /** Left click, with a built fireball in hand. */
    @Override
    public void onRelease(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // Blue Fire's damage boost is applied HERE rather than in the damage handler.
        // A fireball hurts through explosion damage, and by the time that lands there
        // is nothing to tell it apart from any other explosion — the handler's IS_FIRE
        // rule would have to take in TNT as well to include it. The ability knows what
        // it is throwing, so it simply throws a bigger one.
        int power = BendingFire.isBlue(data) ? BLUE_EXPLOSION_POWER : EXPLOSION_POWER;

        Vec3 movement = new Vec3(look.x * 0.1, look.y * 0.1, look.z * 0.1);
        LargeFireball bigFireball = new LargeFireball(level, player, movement, power);

        bigFireball.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
        level.addFreshEntity(bigFireball);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.5F, 1.0F);
    }
}
