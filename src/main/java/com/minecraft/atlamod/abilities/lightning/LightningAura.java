package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Left / Lightning. A channelled shell of live current: anything that comes near
 * the bender is electrocuted for as long as it stays there.
 *
 * The counterpart to Air Aura and the two shields, but the opposite trade — it
 * protects nothing at all, it simply makes standing next to the bender a bad idea.
 * Cheap (10 chi/sec) and uncapped, so it is something to hold up in a crowd rather
 * than a panic button.
 */
public class LightningAura implements ChanneledAbility {

    /** How close something has to be to be caught, in blocks. */
    private static final double RADIUS = 3.0;

    /** Damage per hit — 1.5, as specced. Three quarters of a heart. */
    private static final float DAMAGE = 1.5F;

    /**
     * The aura hits on an explicit one-second beat rather than every tick.
     *
     * Per-tick hits would mostly be swallowed by vanilla's invulnerability frames
     * anyway, but that is working by accident: the moment anything else damages the
     * same target those frames reset and the aura would suddenly hit far harder than
     * its description. Wind tunnel does the same, for the same reason.
     */
    private static final int HIT_EVERY = 20;

    @Override
    public String getName() {
        return "Lightning aura";
    }

    @Override
    public int getChiCost(BendingData data) {
        return 0; // Channels pay by the second.
    }

    @Override
    public int getXpReward() {
        return 0;
    }

    @Override
    public int getChiPerSecond(BendingData data) {
        return 10;
    }

    @Override
    public double getXpPerSecond() {
        return 1.0;
    }

    /** The element's one-second wind-up. See Lightning.MINIMUM_CHARGE_TICKS. */
    @Override
    public int getWindupTicks() {
        return Lightning.MINIMUM_CHARGE_TICKS;
    }

    @Override
    public void onWindupTick(ServerPlayer player, BendingData data, int ticksHeld) {
        Lightning.gather((ServerLevel) player.level(), player, ticksHeld, getWindupTicks());
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Lightning.crack((ServerLevel) player.level(), player.position(), 0.6F, 1.6F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        // The shell itself, drawn every tick so it reads as live rather than as a
        // pulse. Cheap: one batched call, not one packet per spark.
        double angle = (player.tickCount % 40) / 40.0 * Math.PI * 2.0;
        for (int i = 0; i < 3; i++) {
            double a = angle + (i * Math.PI * 2.0 / 3.0);
            Vec3 at = player.position().add(
                    Math.cos(a) * RADIUS, 0.2 + (i * 0.7), Math.sin(a) * RADIUS);
            Lightning.spark(level, at, 3, 0.15);
        }

        // Measured from the END of the wind-up, not from the channel start, so the
        // first shock lands exactly as the aura comes up rather than wherever the
        // beat happened to fall during it.
        if ((data.getChannelTicks() - getWindupTicks()) % HIT_EVERY != 0) return;

        AABB box = new AABB(player.position(), player.position()).inflate(RADIUS);

        for (Entity target : level.getEntities(player, box)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.distanceToSqr(player) > RADIUS * RADIUS) continue;

            living.hurt(player.damageSources().indirectMagic(player, player),
                    Lightning.damage(data, DAMAGE));

            Lightning.spark(level, living.position().add(0.0, living.getBbHeight() * 0.5, 0.0), 6, 0.25);
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away: the aura is only ever particles and damage.
    }
}
