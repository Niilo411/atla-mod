package com.minecraft.atlamod.abilities.lightning;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every ball of lightning hanging in the world.
 *
 * Tracked in a static list rather than being an entity, for the same reason
 * AirSpouts is: an EntityType and a client renderer for something that is only ever
 * particles is a great deal of machinery for no gain, and a ball of current does not
 * occupy the world the way a block does.
 *
 * A ball belongs to its bender and rides their crosshair, so it is Tornado's
 * behaviour rather than Air spout's — there is no such thing as one that was set
 * down and left.
 */
public final class LightningBalls {

    /** How long a ball lasts if it is not cancelled early. */
    public static final int LIFETIME = 400; // 20 seconds

    /** How close something has to be to the ball to be shocked, in blocks. */
    private static final double RADIUS = 3.0;

    /** Damage a caught target takes, once a second. */
    private static final float DAMAGE = 4.0F;

    /** Damage lands on an explicit one-second beat, not per tick. See LightningAura. */
    private static final int HIT_EVERY = 20;

    /** How far out the ball will follow the crosshair, and how fast it chases it. */
    private static final double STEER_REACH = 25.0;
    private static final double STEER_SPEED = 0.8;

    /** How high off the ground the ball floats when the crosshair is on terrain. */
    private static final double HOVER = 1.2;

    private static final List<Ball> ACTIVE = new ArrayList<>();

    private LightningBalls() {
    }

    private static final class Ball {
        final ServerLevel level;
        final UUID ownerId;
        Vec3 pos;
        int ticksLeft;
        int ticks;

        Ball(ServerLevel level, UUID ownerId, Vec3 pos) {
            this.level = level;
            this.ownerId = ownerId;
            this.pos = pos;
            this.ticksLeft = LIFETIME;
        }
    }

    /** Whether this bender already has a ball up — what makes the ability a toggle. */
    public static boolean has(ServerPlayer player) {
        for (Ball ball : ACTIVE) {
            if (ball.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void summon(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(3.0));
        ACTIVE.add(new Ball(level, player.getUUID(), at));

        Lightning.crack(level, at, 1.0F, 1.3F);
    }

    /** Takes a bender's ball down early. */
    public static void cancel(ServerPlayer player) {
        for (Ball ball : List.copyOf(ACTIVE)) {
            if (!ball.ownerId.equals(player.getUUID())) continue;
            burst(ball);
            ACTIVE.remove(ball);
        }
    }

    /**
     * Iterates a SNAPSHOT, because a ball damages things and damage can kill.
     *
     * A player killed by a ball fires LivingDeathEvent, whose handler calls
     * {@link #forgetPlayer} — which removes from the very list being walked. That is
     * a ConcurrentModificationException straight out of the server tick, and it has
     * crashed this mod for real once already (Earth dig, see Rides).
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Ball ball : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(ball)) continue;

            if (!advance(ball, server)) {
                burst(ball);
                ACTIVE.remove(ball);
            }
        }
    }

    private static boolean advance(Ball ball, MinecraftServer server) {
        if (ball.ticksLeft-- <= 0) return false;
        ball.ticks++;

        ServerPlayer owner = server.getPlayerList().getPlayer(ball.ownerId);

        // A ball dies with its bender's presence — death, disconnect, or simply
        // being in another dimension from the ball they are steering.
        if (owner == null || owner.level() != ball.level || !owner.isAlive()) return false;

        steer(ball, owner);
        draw(ball);

        if (ball.ticks % HIT_EVERY == 0) shock(ball, owner);

        return true;
    }

    /**
     * Moves the ball TOWARDS the crosshair at a capped speed rather than snapping it
     * there — the same choice Tornado makes, and for the same reason: flicking the
     * view across the sky should drive the ball, not teleport it twenty-five blocks
     * in a single tick.
     */
    private static void steer(Ball ball, ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();

        net.minecraft.world.phys.HitResult hit = owner.level().clip(
                new net.minecraft.world.level.ClipContext(
                        eye, eye.add(look.scale(STEER_REACH)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, owner));

        Vec3 wanted = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation().add(0.0, HOVER, 0.0)
                : eye.add(look.scale(STEER_REACH));

        Vec3 toward = wanted.subtract(ball.pos);
        double distance = toward.length();

        if (distance <= STEER_SPEED) {
            ball.pos = wanted;
        } else {
            ball.pos = ball.pos.add(toward.normalize().scale(STEER_SPEED));
        }
    }

    private static void shock(Ball ball, ServerPlayer owner) {
        BendingData data = owner.getData(ModAttachments.BENDING_DATA);
        float damage = Lightning.damage(data, DAMAGE);

        AABB box = new AABB(ball.pos, ball.pos).inflate(RADIUS);

        // The owner is NOT excluded: the first argument to getEntities is the entity
        // to skip, and a ball of live current hanging in the air is a hazard put in a
        // place rather than a spell aimed at somebody. Air spout makes the same call,
        // and steering yours into your own face should hurt.
        for (Entity target : ball.level.getEntities((Entity) null, box, e -> true)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;
            if (living.position().distanceToSqr(ball.pos) > RADIUS * RADIUS) continue;

            living.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            Lightning.spark(ball.level, living.position().add(0.0, living.getBbHeight() * 0.5, 0.0), 5, 0.2);
        }
    }

    private static void draw(Ball ball) {
        // The core, then a shell of sparks orbiting it, so it reads as a sphere of
        // current rather than a smudge. One batched call each — a directed velocity
        // would need count 0, which is one particle per packet.
        Lightning.spark(ball.level, ball.pos, 6, 0.15);
        ball.level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                ball.pos.x, ball.pos.y, ball.pos.z, 12, 0.7, 0.7, 0.7, 0.02);
        ball.level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                ball.pos.x, ball.pos.y, ball.pos.z, 2, 0.35, 0.35, 0.35, 0.005);
    }

    private static void burst(Ball ball) {
        Lightning.spark(ball.level, ball.pos, 40, 0.8);
        Lightning.crack(ball.level, ball.pos, 0.8F, 1.6F);
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(ball -> ball.ownerId.equals(player.getUUID()));
    }

    /**
     * Drops the balls belonging to a level that is going away.
     *
     * Nothing else holds them, so without this a ball left in an unloading dimension
     * would keep a dead ServerLevel reachable for the rest of the session.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(ball -> ball.level == level);
    }
}
