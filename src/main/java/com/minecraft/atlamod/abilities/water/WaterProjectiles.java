package com.minecraft.atlamod.abilities.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Bodies of water in flight, advanced by hand rather than being real entities.
 *
 * A custom projectile entity would need its own EntityType and, more awkwardly, a
 * client renderer — and an entity that spawns without one takes the client down.
 * Tracking the shots here keeps all of that out of the picture, and a mass of water
 * is better drawn as particles than as any model anyway.
 *
 * The same machinery should serve Water Bullets and anything else that throws
 * something; only the numbers passed to {@link #launch} differ.
 */
public final class WaterProjectiles {

    /** Blocks travelled per tick is set by the caller; this is just the drag on it. */
    private static final double DRAG = 0.99;

    /** Gentle arc, far lighter than a thrown item, so the shot flies fairly flat. */
    private static final double GRAVITY = 0.014;

    private static final List<Shot> IN_FLIGHT = new ArrayList<>();

    private WaterProjectiles() {
    }

    /** One body of water on its way somewhere. */
    private static final class Shot {
        final UUID ownerId;
        final ServerLevel level;
        Vec3 pos;
        Vec3 velocity;
        int ticksLeft;
        final float damage;
        final double hitRadius;
        final double knockback;

        Shot(UUID ownerId, ServerLevel level, Vec3 pos, Vec3 velocity,
             int ticksLeft, float damage, double hitRadius, double knockback) {
            this.ownerId = ownerId;
            this.level = level;
            this.pos = pos;
            this.velocity = velocity;
            this.ticksLeft = ticksLeft;
            this.damage = damage;
            this.hitRadius = hitRadius;
            this.knockback = knockback;
        }
    }

    /**
     * Sends a body of water on its way.
     *
     * @param speed     blocks per tick
     * @param lifetime  ticks before it falls apart on its own
     * @param hitRadius how close something has to be to be caught
     */
    public static void launch(ServerPlayer owner, Vec3 from, Vec3 direction, double speed,
                              int lifetime, float damage, double hitRadius, double knockback) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        IN_FLIGHT.add(new Shot(owner.getUUID(), level, from,
                direction.normalize().scale(speed), lifetime, damage, hitRadius, knockback));
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

        Vec3 next = shot.pos.add(shot.velocity);

        // Anything solid in the way stops it. Checked at the destination rather than
        // by sweeping, which is close enough at these speeds and far cheaper.
        BlockPos blockPos = BlockPos.containing(next);
        if (shot.level.getBlockState(blockPos).isSolid()) {
            burst(shot);
            return false;
        }

        ServerPlayer owner = shot.level.getServer().getPlayerList().getPlayer(shot.ownerId);

        AABB hitbox = new AABB(next, next).inflate(shot.hitRadius);
        for (Entity target : shot.level.getEntities(owner, hitbox)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            if (owner != null) {
                living.hurt(owner.damageSources().indirectMagic(owner, owner), shot.damage);
            }

            Vec3 push = shot.velocity.normalize().scale(shot.knockback);
            living.setDeltaMovement(push.x, Math.max(0.25, push.y), push.z);
            // Players ignore server-side velocity unless it is explicitly pushed to them.
            living.hurtMarked = true;

            burst(shot);
            return false;
        }

        shot.pos = next;
        shot.velocity = shot.velocity.scale(DRAG).subtract(0.0, GRAVITY, 0.0);

        draw(shot);
        return true;
    }

    /** The body of water itself: a tight clump, not a thin trail. */
    private static void draw(Shot shot) {
        shot.level.sendParticles(ParticleTypes.SPLASH,
                shot.pos.x, shot.pos.y, shot.pos.z, 8, 0.22, 0.22, 0.22, 0.02);
        shot.level.sendParticles(ParticleTypes.FALLING_WATER,
                shot.pos.x, shot.pos.y, shot.pos.z, 4, 0.2, 0.2, 0.2, 0.0);
        shot.level.sendParticles(ParticleTypes.BUBBLE,
                shot.pos.x, shot.pos.y, shot.pos.z, 3, 0.15, 0.15, 0.15, 0.01);
    }

    /** Where it comes apart. */
    private static void burst(Shot shot) {
        shot.level.sendParticles(ParticleTypes.SPLASH,
                shot.pos.x, shot.pos.y, shot.pos.z, 40, 0.6, 0.6, 0.6, 0.12);
        shot.level.sendParticles(ParticleTypes.BUBBLE_POP,
                shot.pos.x, shot.pos.y, shot.pos.z, 15, 0.5, 0.5, 0.5, 0.05);

        shot.level.playSound(null, shot.pos.x, shot.pos.y, shot.pos.z,
                SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 1.1F);
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
