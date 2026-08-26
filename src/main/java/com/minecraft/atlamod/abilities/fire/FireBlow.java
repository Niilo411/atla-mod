package com.minecraft.atlamod.abilities.fire;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.BendingFire;
import com.minecraft.atlamod.abilities.ChargedAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Masterclass / Fire. A wall of flame erupting from the ground in front of the
 * bender, burning everything caught in it and leaving the ground alight.
 *
 * Charges for up to 10 seconds and grows the whole way: reach, width, damage and
 * how high the flames climb all scale together. Unlike the other charged abilities
 * it fires on release as well as at full charge, so a half-held blow is a real if
 * weaker blow rather than a wasted one.
 */
public class FireBlow implements ChargedAbility {

    /** 10 seconds to reach full strength. */
    private static final int MAX_CHARGE = 200;

    /** Below half a second it is a twitch, not a cast, and releasing costs nothing. */
    private static final int MIN_CHARGE = 10;

    // Everything below scales from the first figure at no charge to the second at full.
    private static final double MIN_RANGE = 4.0;
    private static final double MAX_RANGE = 16.0;
    private static final double MIN_HALF_WIDTH = 2.0;
    private static final double MAX_HALF_WIDTH = 6.0;
    private static final float MIN_DAMAGE = 4.0F;
    private static final float MAX_DAMAGE = 20.0F;
    private static final int MIN_COLUMN_HEIGHT = 2;
    private static final int MAX_COLUMN_HEIGHT = 6;

    /** Its fire burns at masterclass strength. */
    private static final float DAMAGE_MULTIPLIER = 3.0F;
    private static final int ENHANCED_LIFETIME = 600;

    /** Scan depth for laying fire on uneven ground. */
    private static final int UP_SCAN = 2;
    private static final int DOWN_SCAN = 4;

    @Override
    public String getName() {
        return "Fire blow";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 150;
    }

    @Override
    public int getXpReward() {
        return 20;
    }

    @Override
    public int getCooldownTicks() {
        return 20; // 1 second
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
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.4F);
    }

    @Override
    public void onChargeTick(ServerPlayer player, BendingData data, int ticksHeld) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float power = ticksHeld / (float) MAX_CHARGE;

        // Heat gathering around the bender, growing with the charge.
        int count = 3 + (int) (17 * power);
        double spread = 0.5 + (1.0 * power);

        level.sendParticles(BendingFire.flame(data),
                player.getX(), player.getY() + 0.4, player.getZ(),
                count, spread, 0.4, spread, 0.03);

        // A rising pulse once a second, so the build is audible as well as visible.
        if (ticksHeld % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                    0.5F + (0.8F * power), 0.5F + (0.8F * power));
        }
    }

    @Override
    public void execute(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // How far the charge actually got. Recorded by the dispatcher just before the
        // cast, because the live counter is cleared to stop a double fire.
        float power = Math.min(1.0F, data.getLastChargeTicks() / (float) MAX_CHARGE);

        double range = lerp(MIN_RANGE, MAX_RANGE, power);
        double halfWidth = lerp(MIN_HALF_WIDTH, MAX_HALF_WIDTH, power);
        float damage = (float) lerp(MIN_DAMAGE, MAX_DAMAGE, power);
        int columnHeight = (int) Math.round(lerp(MIN_COLUMN_HEIGHT, MAX_COLUMN_HEIGHT, power));

        Vec3 forward = flatLook(player);
        Vec3 across = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 origin = player.position();

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.0F + power, 0.6F);

        eruptAlong(level, data, origin, forward, across, range, halfWidth, columnHeight);
        burnEntities(player, level, origin, forward, range, halfWidth, damage);
    }

    /** Lays the fire and throws up the columns of flame that give the blow its shape. */
    private static void eruptAlong(ServerLevel level, BendingData data, Vec3 origin,
                                   Vec3 forward, Vec3 across, double range,
                                   double halfWidth, int columnHeight) {
        Set<BlockPos> touched = new HashSet<>();

        for (double forwardStep = 1.0; forwardStep <= range; forwardStep += 1.0) {
            // The blow fans out as it travels: narrow at the bender, widest at the far end.
            double spreadHere = halfWidth * (forwardStep / range);

            for (double sideStep = -spreadHere; sideStep <= spreadHere; sideStep += 1.0) {
                Vec3 spot = origin.add(forward.scale(forwardStep)).add(across.scale(sideStep));
                BlockPos column = BlockPos.containing(spot);

                // Rounding makes neighbouring samples land on the same block, and
                // placing twice would double the particles piled on that spot.
                if (!touched.add(column)) continue;

                BendingFire.placeGrounded(level, data, column,
                        UP_SCAN, DOWN_SCAN, ENHANCED_LIFETIME, DAMAGE_MULTIPLIER);

                // The flames themselves: a column climbing out of the ground, thinning
                // as it rises.
                for (int y = 0; y < columnHeight; y++) {
                    double thinning = 1.0 - (y / (double) columnHeight);
                    int count = Math.max(1, (int) (6 * thinning));

                    level.sendParticles(BendingFire.flame(data),
                            spot.x, spot.y + 0.3 + y, spot.z,
                            count, 0.3, 0.3, 0.3, 0.04);
                }

                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        spot.x, spot.y + columnHeight * 0.6, spot.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    /** Burns whatever is standing in the blow. This is the bite behind the flames. */
    private static void burnEntities(ServerPlayer player, ServerLevel level, Vec3 origin,
                                     Vec3 forward, double range, double halfWidth, float damage) {
        Vec3 centre = origin.add(forward.scale(range / 2.0));
        AABB search = new AABB(centre, centre).inflate(range);

        for (Entity target : level.getEntities(player, search)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().subtract(origin);

            // How far along the blow the target stands, and how far off its centre line.
            double along = toTarget.dot(forward);
            if (along < 0.0 || along > range) continue;

            double offCentre = toTarget.subtract(forward.scale(along)).horizontalDistance();
            if (offCentre > halfWidth * (along / range) + 1.0) continue;

            living.hurt(player.damageSources().inFire(), damage);
            living.setRemainingFireTicks(160);
        }
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

    private static double lerp(double from, double to, float t) {
        return from + (to - from) * t;
    }
}
