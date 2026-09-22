package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A kick. Short, hard, and it sends things flying.
 *
 * The plainest ability in the mod and deliberately so — the no-bending path has no
 * projectiles, no channels and no charges, and this is what it has instead of all three.
 * Everything about it is reach and timing.
 */
public class Kick implements Ability {

    /** Two blocks. Genuinely melee: you have to be on top of them. */
    private static final double REACH = 2.0;

    /** 4 hearts. Minecraft health is half-hearts, so 8.0F. */
    private static final float DAMAGE = 8.0F;

    /** 20 seconds. */
    private static final int COOLDOWN_TICKS = 400;

    /**
     * How wide the kick reaches, as a dot product against the look direction.
     *
     * 0.3 is about a 72-degree half angle, which is WIDER than the pushes in this mod use
     * (0.5). That is not generosity for its own sake: those reach eight and twelve blocks,
     * where a two-block cone at 0.5 is a target area barely wider than the player
     * themselves, and an ability you have to be touching something to use should not also
     * demand you be square on to it.
     */
    private static final double CONE_DOT = 0.3;

    /**
     * Five blocks of knockback.
     *
     * Minecraft knockback is an IMPULSE, not a distance: the target is given a velocity
     * and then decays under drag, so no constant here can set a distance exactly.
     *
     * CALIBRATED AGAINST FIRE PUSH RATHER THAN DERIVED, and that is the honest account of
     * where 0.71 comes from. Fire push uses 0.84 at this same lift for a knockback its own
     * note records as about six blocks, and distance scales linearly with this value —
     * drag is multiplicative and the airtime is set by the lift, not by horizontal speed.
     * Five sixths of 0.84 is 0.70, so 0.71 is five blocks IF Fire push's six is right.
     *
     * Worth knowing before trusting the absolute number: simulating vanilla's own step
     * gives noticeably shorter throws than either figure claims, so one of the two models
     * is wrong and it has not been settled in game. What IS certain is the RATIO — this
     * kick throws five sixths as far as a Fire push, whatever that turns out to be.
     */
    private static final double PUSH_SPEED = 0.71;

    /** Enough lift that the target slides rather than being pinned by ground friction. */
    private static final double PUSH_LIFT = 0.2;

    @Override
    public String getName() {
        return "Kick";
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
    public int getCooldownTicks() {
        return COOLDOWN_TICKS;
    }

    /**
     * Deliberately NOT gated on having a target.
     *
     * Almost everything else in the mod refuses a cast with nothing in view so that aiming
     * at the sky is free. A kick is different in kind: it is a thing you DO rather than a
     * thing you aim, it costs no chi, and kicking at air and connecting with nothing is a
     * perfectly ordinary outcome that the game should not step in and undo. What it costs
     * is the twenty seconds.
     */
    @Override
    public void execute(ServerPlayer player, BendingData data) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 eyes = player.getEyePosition();

        level.playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);

        AABB box = player.getBoundingBox().inflate(REACH);

        // null rather than the player, because the first argument to getEntities is the
        // entity to SKIP and the cone test below already excludes anything behind them.
        // Passing the player would work too; this way the exclusion has exactly one cause.
        for (var entity : level.getEntities(null, box)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == player) continue;

            Vec3 toTarget = living.position().add(0, living.getBbHeight() * 0.5, 0).subtract(eyes);
            if (toTarget.length() > REACH + living.getBbWidth()) continue;

            Vec3 direction = toTarget.normalize();
            if (direction.dot(look) < CONE_DOT) continue;

            // indirectMagic so the stated 8 is the 8 that lands. A plain playerAttack
            // would be reduced by armour to a couple of points against anyone geared,
            // which for the only damaging ability a non-bender has would make the whole
            // path useless in exactly the fight it exists for. Same call, and the same
            // reasoning, as Tsunami's.
            living.hurt(player.damageSources().indirectMagic(player, player), DAMAGE);

            // AFTER the damage, never before: hurt() applies a knockback of its own, so a
            // velocity set first is quietly overwritten by a much smaller vanilla one.
            Vec3 away = new Vec3(living.getX() - player.getX(), 0, living.getZ() - player.getZ());
            if (away.lengthSqr() < 1.0E-4) {
                away = new Vec3(look.x, 0, look.z);
            }
            away = away.normalize();

            living.setDeltaMovement(away.x * PUSH_SPEED, PUSH_LIFT, away.z * PUSH_SPEED);

            // Without this a kicked PLAYER's own client ignores the server's velocity.
            living.hurtMarked = true;
        }
    }
}
