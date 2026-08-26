package com.minecraft.atlamod.abilities.sound;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every wall of sound standing in the world.
 *
 * The wall is made of NOTHING — it places no blocks at all, only particles — which is
 * what the design asks for and is also what makes it interesting to build: it has to
 * stop things moving through a place where the world says there is nothing at all.
 *
 * It does that by pushing back rather than by colliding. Anything that crosses the
 * plane is set back on the side it came from and has its motion into the wall
 * removed, every tick, which reads as a solid barrier without a single block existing.
 * Projectiles are simply discarded on contact.
 *
 * The bender who raised it walks through freely. A wall that stopped its own caster
 * would be a cage rather than cover, and there is nowhere to stand behind a barrier
 * you cannot get behind.
 */
public final class SoundWalls {

    /** How far in front of the bender the wall hangs. */
    private static final double DISTANCE = 2.0;

    /** Half the wall's width, in blocks. */
    private static final double HALF_WIDTH = 3.0;

    /** How tall it stands, from the bender's feet. */
    private static final double HEIGHT = 4.0;

    /** How thick the pushed-back band is. Thin, but not so thin things tunnel through. */
    private static final double THICKNESS = 1.0;

    /** How hard something crossing it is set back. */
    private static final double PUSH = 0.45;

    private static final List<Wall> ACTIVE = new ArrayList<>();

    private SoundWalls() {
    }

    private static final class Wall {
        final ServerLevel level;
        final UUID ownerId;

        Wall(ServerLevel level, UUID ownerId) {
            this.level = level;
            this.ownerId = ownerId;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Wall wall : ACTIVE) {
            if (wall.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void raise(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        ACTIVE.add(new Wall(level, player.getUUID()));
        Sound.play(level, player.position(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
    }

    public static void drop(ServerPlayer player) {
        for (Wall wall : List.copyOf(ACTIVE)) {
            if (!wall.ownerId.equals(player.getUUID())) continue;

            Sound.play(wall.level, player.position(),
                    net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.2F);
            ACTIVE.remove(wall);
        }
    }

    /** Iterates a SNAPSHOT, like every other manager that touches entities. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Wall wall : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(wall)) continue;

            if (!advance(wall, server)) {
                ACTIVE.remove(wall);
            }
        }
    }

    private static boolean advance(Wall wall, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(wall.ownerId);
        if (owner == null || owner.level() != wall.level || !owner.isAlive()) return false;

        // Follows the crosshair, so the wall is aimed rather than placed — flattened
        // to the horizontal, or looking up would tip it over the bender's head.
        Vec3 look = owner.getLookAngle();
        Vec3 facing = new Vec3(look.x, 0.0, look.z);
        if (facing.lengthSqr() < 1.0E-4) facing = new Vec3(0.0, 0.0, 1.0);
        facing = facing.normalize();

        Vec3 centre = owner.position().add(facing.scale(DISTANCE));
        Vec3 across = new Vec3(-facing.z, 0.0, facing.x);

        draw(wall, centre, across);
        hold(wall, owner, centre, facing, across);

        return true;
    }

    /** The wall itself: a grid of notes, dense enough to read as a surface. */
    private static void draw(Wall wall, Vec3 centre, Vec3 across) {
        for (double w = -HALF_WIDTH; w <= HALF_WIDTH; w += 0.5) {
            for (double h = 0.2; h <= HEIGHT; h += 0.5) {
                Vec3 at = centre.add(across.scale(w)).add(0.0, h, 0.0);

                // Thinned out so the wall shimmers rather than being a solid sheet of
                // particles — it is meant to look transparent, and drawing every point
                // every tick is both uglier and far more packets.
                if (wall.level.random.nextFloat() > 0.25F) continue;

                wall.level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Sets back anything trying to cross, and eats anything shot at it. */
    private static void hold(Wall wall, ServerPlayer owner, Vec3 centre, Vec3 facing, Vec3 across) {
        AABB box = new AABB(centre, centre).inflate(HALF_WIDTH + 1.0, HEIGHT, HALF_WIDTH + 1.0);

        for (Entity caught : wall.level.getEntities(owner, box)) {
            Vec3 offset = caught.position().subtract(centre);

            // Inside the wall's own plane? Width and height first, then thickness.
            if (Math.abs(offset.dot(across)) > HALF_WIDTH) continue;
            if (offset.y < -1.0 || offset.y > HEIGHT) continue;

            double through = offset.dot(facing);
            if (Math.abs(through) > THICKNESS) continue;

            if (caught instanceof Projectile projectile) {
                // Deflected, and there is no half measure available: an arrow that
                // kept flying but did no damage would still stick in whoever is behind
                // the wall, which is not what a barrier does.
                Sound.burst(wall.level, projectile.position(), 8, 0.2);
                projectile.discard();
                continue;
            }

            if (!(caught instanceof LivingEntity living)) continue;

            // Pushed back out the side it came in on, so something that has already
            // crossed the middle is not helpfully shoved the rest of the way through.
            double side = through >= 0.0 ? 1.0 : -1.0;

            living.setDeltaMovement(
                    living.getDeltaMovement().add(facing.scale(PUSH * side)));
            living.hurtMarked = true;
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(wall -> wall.ownerId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(wall -> wall.level == level);
    }
}
