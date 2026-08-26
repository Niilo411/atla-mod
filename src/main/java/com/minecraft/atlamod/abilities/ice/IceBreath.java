package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModEffects;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Right / Ice. Wind tunnel and Fire Breath crossed: a held cone of freezing air,
 * reaching a little further than the tunnel does, that wears down everything inside
 * it and keeps stunning it.
 *
 * The stun is the real weapon, not the damage. Four hp a second is modest; a second
 * of Stunned every two seconds means nothing caught in the cone can reliably close
 * the distance or get away, and it stacks with the chill's Slowness on top.
 */
public class IceBreath implements ChanneledAbility {

    /** "As big as wind tunnel and a bit larger" — the tunnel reaches 12. */
    private static final double RANGE = 15.0;

    /**
     * Cone width, as the minimum dot product with the look vector. Matches wind
     * tunnel's 0.6 so the two abilities feel like the same shape of thing.
     */
    private static final double CONE_DOT = 0.6;

    /** 4 hp a second, as specced. */
    private static final float DAMAGE_PER_SECOND = 4.0F;

    /** One second of Stunned... */
    private static final int STUN_TICKS = 20;

    /** ...applied every two seconds someone is in range. */
    private static final int STUN_EVERY = 40;

    /** Damage lands on an explicit one-second beat. See LightningAura for why. */
    private static final int HIT_EVERY = 20;

    @Override
    public String getName() {
        return "Ice Breath";
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
        return 15;
    }

    @Override
    public double getXpPerSecond() {
        return 5.0;
    }

    /**
     * Twenty-five seconds, and it starts when the breath ENDS rather than when it
     * begins — every channel in the mod works that way, so holding it longer is not a
     * way to dodge the wait.
     */
    @Override
    public int getCooldownTicks() {
        return 500;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data) {
        Ice.form((ServerLevel) player.level(), player.position(), 1.0F, 0.9F);
    }

    @Override
    public void onTick(ServerPlayer player, BendingData data) {
        ServerLevel level = (ServerLevel) player.level();

        Vec3 mouth = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        // The cone itself, widening with distance. Batched calls rather than directed
        // ones: a directed velocity needs count 0, which is one particle per packet,
        // and this runs every tick for as long as the breath is held.
        for (int i = 1; i <= RANGE; i += 2) {
            Vec3 at = mouth.add(look.scale(i));
            double spread = 0.25 * i;

            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z,
                    8, spread, spread, spread, 0.02);
        }

        boolean damageTick = data.getChannelTicks() % HIT_EVERY == 0;
        boolean stunTick = data.getChannelTicks() % STUN_EVERY == 0;
        if (!damageTick && !stunTick) return;

        AABB search = new AABB(mouth, mouth).inflate(RANGE);

        for (Entity caught : level.getEntities(player, search)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 toTarget = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0).subtract(mouth);
            if (toTarget.lengthSqr() > RANGE * RANGE) continue;
            if (toTarget.normalize().dot(look) < CONE_DOT) continue;

            if (damageTick) {
                living.hurt(player.damageSources().indirectMagic(player, player),
                        Ice.damage(data, DAMAGE_PER_SECOND));
                Ice.chill(living, 60, 0);
            }

            if (stunTick) {
                living.addEffect(new MobEffectInstance(
                        ModEffects.STUNNED, STUN_TICKS, 0, false, true, true));
            }
        }
    }

    @Override
    public void onStop(ServerPlayer player, BendingData data) {
        // Nothing to put away: the breath is only ever particles and effects.
    }
}
