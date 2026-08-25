package com.minecraft.atlamod.abilities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything the mod throws, advanced by hand rather than as real entities.
 *
 * A custom projectile entity would need its own EntityType and, more awkwardly, a
 * client renderer — and an entity that spawns without one takes the client down.
 * Tracking shots here keeps all of that out of the picture, and a mass of water or a
 * blade of air is better drawn as particles than as any model anyway.
 *
 * Written element-agnostic, like HeldBlocks: nothing here knows what it is carrying.
 * An ability describes its shot once as a {@link Spec} constant and the only thing
 * that differs between water and air is the {@link Style} used to draw it.
 */
public final class BendingProjectiles {

    /** Blocks travelled per tick is set by the caller; this is just the drag on it. */
    private static final double DRAG = 0.99;

    /** Gentle arc, far lighter than a thrown item, so shots fly fairly flat. */
    private static final double GRAVITY = 0.014;

    /**
     * Longest a shot may move between collision checks, in blocks.
     *
     * A tick's movement is walked in steps no bigger than this rather than tested
     * only at the far end. Air Splinters travel over 3 blocks a tick, which would
     * otherwise step clean through a wall — and past anything standing in front of
     * it — without ever being tested against either.
     */
    private static final double STEP = 0.9;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private BendingProjectiles() {
    }

    /** How a shot looks and sounds. */
    public enum Style {
        WATER,
        AIR
    }

    /**
     * One kind of shot. Abilities declare theirs once, as a constant.
     *
     * @param speed     blocks per tick
     * @param lifetime  ticks before it falls apart on its own
     * @param hitRadius how close something has to be to be caught
     * @param onHit     effect applied to whatever it hits, or null for none. A
     *                  supplier rather than an instance, because a MobEffectInstance
     *                  is stateful once applied and must not be shared between hits.
     */
    public record Spec(double speed, int lifetime, float damage, double hitRadius,
                       double knockback, Style style,
                       @Nullable Supplier<MobEffectInstance> onHit) {

        /** A shot that only hits, with no lingering effect. */
        public Spec(double speed, int lifetime, float damage, double hitRadius,
                    double knockback, Style style) {
            this(speed, lifetime, damage, hitRadius, knockback, style, null);
        }
    }

    /** One shot on its way somewhere. */
    private static final class Shot {
        final UUID ownerId;
        final ServerLevel level;
        final Spec spec;
        Vec3 pos;
        Vec3 velocity;
        int ticksLeft;

        Shot(UUID ownerId, ServerLevel level, Spec spec, Vec3 pos, Vec3 velocity) {
            this.ownerId = ownerId;
            this.level = level;
            this.spec = spec;
            this.pos = pos;
            this.velocity = velocity;
            this.ticksLeft = spec.lifetime();
        }
    }

    /** Sends a shot on its way. */
    public static void launch(ServerPlayer owner, Vec3 from, Vec3 direction, Spec spec) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        IN_FLIGHT.add(new Shot(owner.getUUID(), level, spec, from,
                direction.normalize().scale(spec.speed())));
    }

    /** Advances every shot in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (IN_FLIGHT.isEmpty()) return;

        Iterator<Shot> shots = IN_FLIGHT.iterator();
        while (shots.hasNext()) {
            Shot shot = shots.next();
            if (!advance(shot)) {
                shots.remove();
            }
        }
    }

    /** @return false once the shot is spent and should be dropped */
    private static boolean advance(Shot shot) {
        if (shot.ticksLeft-- <= 0) {
            burst(shot);
            return false;
        }

        // Walk this tick's movement in short steps, testing as we go. At these speeds
        // a single test at the destination misses thin walls and passing targets.
        double distance = shot.velocity.length();
        int steps = Math.max(1, (int) Math.ceil(distance / STEP));
        Vec3 step = shot.velocity.scale(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            Vec3 next = shot.pos.add(step);

            if (shot.level.getBlockState(BlockPos.containing(next)).isSolid()) {
                burst(shot);
                return false;
            }

            shot.pos = next;

            if (strike(shot)) {
                return false;
            }
        }

        shot.velocity = shot.velocity.scale(DRAG).subtract(0.0, GRAVITY, 0.0);

        draw(shot);
        return true;
    }

    /** @return true if the shot hit something and is spent */
    private static boolean strike(Shot shot) {
        ServerPlayer owner = shot.level.getServer().getPlayerList().getPlayer(shot.ownerId);

        AABB hitbox = new AABB(shot.pos, shot.pos).inflate(shot.spec.hitRadius());
        for (Entity target : shot.level.getEntities(owner, hitbox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            if (owner != null) {
                living.hurt(owner.damageSources().indirectMagic(owner, owner), shot.spec.damage());
            }

            if (shot.spec.onHit() != null) {
                living.addEffect(shot.spec.onHit().get());
            }

            Vec3 push = shot.velocity.normalize().scale(shot.spec.knockback());
            living.setDeltaMovement(push.x, Math.max(0.25, push.y), push.z);
            // Players ignore server-side velocity unless it is explicitly pushed to them.
            living.hurtMarked = true;

            burst(shot);
            return true;
        }

        return false;
    }

    /** The shot itself: a tight clump, not a thin trail. */
    private static void draw(Shot shot) {
        double x = shot.pos.x;
        double y = shot.pos.y;
        double z = shot.pos.z;

        switch (shot.spec.style()) {
            case WATER -> {
                shot.level.sendParticles(ParticleTypes.SPLASH, x, y, z, 8, 0.22, 0.22, 0.22, 0.02);
                shot.level.sendParticles(ParticleTypes.FALLING_WATER, x, y, z, 4, 0.2, 0.2, 0.2, 0.0);
                shot.level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 3, 0.15, 0.15, 0.15, 0.01);
            }
            case AIR -> {
                shot.level.sendParticles(ParticleTypes.CLOUD, x, y, z, 5, 0.1, 0.1, 0.1, 0.0);
                shot.level.sendParticles(ParticleTypes.SMALL_GUST, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    /** Where it comes apart. */
    private static void burst(Shot shot) {
        double x = shot.pos.x;
        double y = shot.pos.y;
        double z = shot.pos.z;

        switch (shot.spec.style()) {
            case WATER -> {
                shot.level.sendParticles(ParticleTypes.SPLASH, x, y, z, 40, 0.6, 0.6, 0.6, 0.12);
                shot.level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 15, 0.5, 0.5, 0.5, 0.05);
                shot.level.playSound(null, x, y, z,
                        SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.1F);
            }
            case AIR -> {
                // Scaled off the shot's own hit radius, which is already the mod's
                // measure of how big the thing is: an Air splinter pops, an Air
                // cannon round bursts. Water is left on its fixed figures rather
                // than being retuned for the sake of it.
                double size = shot.spec.hitRadius();

                shot.level.sendParticles(ParticleTypes.CLOUD, x, y, z,
                        (int) (12 + 40 * size), size * 0.6, size * 0.6, size * 0.6, 0.1);
                shot.level.sendParticles(
                        size >= 1.0 ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST,
                        x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                shot.level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_BURST,
                        SoundSource.PLAYERS, (float) (0.5 + 0.5 * size), (float) (1.8 - 0.6 * size));
            }
        }
    }

    /**
     * Drops every shot belonging to a level being unloaded.
     *
     * These are held in a plain static list rather than by the world, so nothing else
     * would ever clear them — a shot fired into a dimension that then unloaded would
     * keep a reference to a dead ServerLevel for as long as the server ran.
     */
    public static void forgetLevel(ServerLevel level) {
        IN_FLIGHT.removeIf(shot -> shot.level == level);
    }
}
