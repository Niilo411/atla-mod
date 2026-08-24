package com.minecraft.atlamod.abilities.water;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Masterclass / Water. Three seconds of gathering, then a wall of water nine across
 * and four high rolls twenty blocks out and takes everything with it.
 *
 * Water Sphere in reverse — that one pushes the sea away and this one summons it —
 * and the wave itself is run by Tsunamis, which borrows the same trick of placing
 * blocks without neighbour updates so the water never spreads on its own.
 */
public class Tsunami implements ChargedAbility {

    /** Three seconds of gathering. */
    private static final int CHARGE = 60;

    /** How far the wave rolls. */
    private static final int RANGE = 20;

    /**
     * 12 hearts, enough to take a zombie down in one pass with room to spare — they
     * have 20 health, and Tsunami damages through indirect magic, which vanilla tags
     * as bypassing armour, so a geared one dies the same as a bare one.
     */
    private static final float DAMAGE = 24.0F;

    @Override
    public String getName() {
        return "Tsunami";
    }

    @Override
    public int getChiCost() {
        return 750;
    }

    @Override
    public int getXpReward() {
        return 25;
    }

    @Override
    public int getChargeTicks() {
        return CHARGE;
    }

    /** Waterbending: free near open water, otherwise a unit from the canteen. */
    @Override
    public boolean requiresWater() {
        return true;
    }

    @Override
    public void onChargeStart(ServerPlayer player, BendingData data) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 1.4F, 0.4F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) CHARGE;

        // Water drawn up around the bender, rising as it gathers.
        int count = 6 + (int) (30 * power);
        double spread = 1.0 + (2.0 * power);

        level.sendParticles(ParticleTypes.SPLASH,
                player.getX(), player.getY() + 0.5 + power, player.getZ(),
                count, spread, 0.6 + power, spread, 0.05);

        if (ticksHeld % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS,
                    0.6F + power, 0.4F + (0.3F * power));
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        Vec3 forward = flatLook(player);

        // Started a little ahead so the wave does not break over the bender who called it.
        Vec3 origin = player.position().add(forward.scale(2.0));

        Tsunamis.launch(player, origin, forward, RANGE, DAMAGE);
    }

    /** Facing, flattened onto the ground, falling back to body yaw when looking straight up. */
    private static Vec3 flatLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);

        if (flat.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180F);
            flat = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        return flat.normalize();
    }
}
