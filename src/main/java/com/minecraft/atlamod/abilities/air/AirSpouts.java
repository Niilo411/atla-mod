package com.minecraft.atlamod.abilities.air;

import com.minecraft.atlamod.abilities.Aiming;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Every column of turning air standing in the world — Air spouts and Tornadoes both.
 *
 * Neither is an entity or a block, which is why they are tracked here: an entity
 * would need an EntityType and a client renderer for something that is only ever
 * particles, and a block would need a blockstate for something that does not occupy
 * the world so much as churn through it.
 *
 * The two differ only in their numbers and in one behaviour — a Tornado has an owner
 * and follows their crosshair, where a spout is set down and stays put. Everything
 * else about them (catching things, throwing them, drawing the column, timing out) is
 * one implementation.
 *
 * Kept in a plain static list like Drownings and Tsunamis, with the same consequence:
 * nothing else knows these exist, so {@link #forgetLevel} has to run when a level goes
 * away or one would keep a dead ServerLevel reachable for the rest of the session.
 */
public final class AirSpouts {

    // --- Air spout: set down and left standing ---
    private static final int SPOUT_HEIGHT = 10;
    private static final double SPOUT_LIFT = 0.55;
    private static final double SPOUT_SWIRL = 0.28;
    public static final int SPOUT_LIFETIME = 1200; // 60 seconds

    // --- Tornado: twice as tall, twice as strong, steered, and half as long-lived ---
    private static final int TORNADO_HEIGHT = 20;
    private static final double TORNADO_LIFT = 1.1;
    private static final double TORNADO_SWIRL = 0.56;
    public static final int TORNADO_LIFETIME = 600; // 30 seconds

    /** How wide either catches, in blocks from the centre. You can walk around one. */
    private static final double RADIUS = 1.5;

    /**
     * How far a Tornado will chase the crosshair, and how fast it follows.
     *
     * A block a tick — 20 blocks a second, comfortably faster than a sprint. Still
     * capped rather than snapped to the crosshair, so flicking the view across the
     * sky drives the column instead of teleporting it.
     */
    private static final double STEER_REACH = 30.0;
    private static final int STEER_GROUND_SCAN = 20;
    private static final double STEER_SPEED = 1.0;

    private static final List<Spout> ACTIVE = new ArrayList<>();

    private AirSpouts() {
    }

    private static final class Spout {
        final ServerLevel level;
        final int height;
        final double lift;
        final double swirl;

        /** Null for a placed spout; the steering owner for a Tornado. */
        @Nullable
        final UUID ownerId;

        Vec3 base;
        int ticksLeft;
        int ticks;

        Spout(ServerLevel level, Vec3 base, int height, double lift, double swirl,
              int lifetime, @Nullable UUID ownerId) {
            this.level = level;
            this.base = base;
            this.height = height;
            this.lift = lift;
            this.swirl = swirl;
            this.ticksLeft = lifetime;
            this.ownerId = ownerId;
        }
    }

    /** Stands a new Air spout up with its foot at {@code base}. It stays where it is put. */
    public static void place(ServerLevel level, Vec3 base) {
        ACTIVE.add(new Spout(level, base, SPOUT_HEIGHT, SPOUT_LIFT, SPOUT_SWIRL,
                SPOUT_LIFETIME, null));

        level.playSound(null, base.x, base.y, base.z,
                SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.4F, 0.6F);
    }

    /** Raises a Tornado that follows the caster's crosshair until it is cancelled. */
    public static void summonTornado(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        Vec3 base = Aiming.groundUnderLook(owner, STEER_REACH, STEER_GROUND_SCAN);

        ACTIVE.add(new Spout(level, base, TORNADO_HEIGHT, TORNADO_LIFT, TORNADO_SWIRL,
                TORNADO_LIFETIME, owner.getUUID()));

        level.playSound(null, base.x, base.y, base.z,
                SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.8F, 0.4F);
    }

    /** Whether this player currently has a Tornado up. */
    public static boolean hasTornado(ServerPlayer owner) {
        return findTornado(owner.getUUID()) != null;
    }

    /** Takes a player's Tornado down early. Safe to call when they have none. */
    public static void cancelTornado(ServerPlayer owner) {
        Spout tornado = findTornado(owner.getUUID());
        if (tornado == null) return;

        ACTIVE.remove(tornado);
        blowOut(tornado);
    }

    /** Runs every column in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Spout> spouts = ACTIVE.iterator();
        while (spouts.hasNext()) {
            Spout spout = spouts.next();
            if (!advance(spout, server)) {
                blowOut(spout);
                spouts.remove();
            }
        }
    }

    /** @return false once the column is spent */
    private static boolean advance(Spout spout, MinecraftServer server) {
        if (spout.ticksLeft-- <= 0) return false;

        // A steered column belongs to its caster and cannot outlive them being here.
        if (spout.ownerId != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(spout.ownerId);
            if (owner == null || !owner.isAlive() || owner.level() != spout.level) return false;

            steer(spout, owner);
        }

        throwEntities(spout);

        // Drawn every other tick. A minute of standing spout is a long time to be
        // sending particles, and at ten a second the column still looks continuous.
        if (spout.ticks % 2 == 0) {
            draw(spout);
        }

        if (spout.ticks % 40 == 0) {
            spout.level.playSound(null, spout.base.x, spout.base.y, spout.base.z,
                    SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.7F, 0.7F);
        }

        spout.ticks++;
        return true;
    }

    /**
     * Walks a Tornado towards wherever its owner is looking.
     *
     * Moved at a capped speed rather than snapped to the crosshair, so it is driven
     * rather than teleported — flicking the view across the sky should not put the
     * column thirty blocks away in a single tick.
     */
    private static void steer(Spout spout, ServerPlayer owner) {
        Vec3 want = Aiming.groundUnderLook(owner, STEER_REACH, STEER_GROUND_SCAN);
        Vec3 delta = want.subtract(spout.base);

        double distance = delta.length();
        if (distance < 1.0E-3) return;

        spout.base = distance <= STEER_SPEED
                ? want
                : spout.base.add(delta.scale(STEER_SPEED / distance));
    }

    /**
     * Throws whatever is standing in the column.
     *
     * Everything living, the bender who put it there included — these are hazards
     * placed in a spot, not spells aimed at somebody, and one that politely stepped
     * around its owner would be a strange thing to walk into.
     */
    private static void throwEntities(Spout spout) {
        AABB column = new AABB(
                spout.base.x - RADIUS, spout.base.y, spout.base.z - RADIUS,
                spout.base.x + RADIUS, spout.base.y + spout.height, spout.base.z + RADIUS);

        for (Entity target : spout.level.getEntities((Entity) null, column, e -> e instanceof LivingEntity)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            // Round column, not the square one the search had to use.
            double dx = living.getX() - spout.base.x;
            double dz = living.getZ() - spout.base.z;
            if ((dx * dx) + (dz * dz) > RADIUS * RADIUS) continue;

            // Tangential to the axis, so everything caught turns the same way round
            // it rather than being blown straight out.
            Vec3 spin = new Vec3(-dz, 0.0, dx);
            if (spin.lengthSqr() < 1.0E-4) {
                spin = new Vec3(1.0, 0.0, 0.0); // dead centre: any direction will do
            }
            spin = spin.normalize().scale(spout.swirl);

            living.setDeltaMovement(spin.x, spout.lift, spin.z);

            // Only players need telling: their client owns their movement and ignores
            // server-side velocity unless it is pushed to them. A mob is simulated on
            // the server, so marking it sends a motion packet every tick for nothing.
            if (living instanceof Player) {
                living.hurtMarked = true;
            }

            living.fallDistance = 0.0F;
        }
    }

    /**
     * The column itself: a helix of cloud climbing its full height.
     *
     * Batched calls (a count above zero) rather than individually directed particles,
     * which cost a packet each — these redraw for as long as a minute.
     */
    private static void draw(Spout spout) {
        double turn = (spout.ticks % 20) / 20.0 * Math.PI * 2.0;

        for (int y = 0; y < spout.height; y++) {
            // Widens as it rises, the way a waterspout does.
            double radius = 0.35 + (y / (double) spout.height) * RADIUS;
            double angle = turn + (y * 0.9);

            double px = spout.base.x + Math.cos(angle) * radius;
            double pz = spout.base.z + Math.sin(angle) * radius;

            spout.level.sendParticles(ParticleTypes.CLOUD,
                    px, spout.base.y + y + 0.5, pz, 2, 0.1, 0.2, 0.1, 0.01);
        }

        spout.level.sendParticles(ParticleTypes.SMALL_GUST,
                spout.base.x, spout.base.y + 1.0, spout.base.z, 1, 0.4, 0.5, 0.4, 0.0);
    }

    /** Where it comes apart, so one ending is visible rather than just absent. */
    private static void blowOut(Spout spout) {
        spout.level.sendParticles(ParticleTypes.CLOUD,
                spout.base.x, spout.base.y + spout.height * 0.5, spout.base.z,
                40, 0.8, spout.height * 0.4, 0.8, 0.1);
        spout.level.playSound(null, spout.base.x, spout.base.y, spout.base.z,
                SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    /**
     * Takes down a player's Tornado when they die, disconnect or change dimension.
     *
     * Placed spouts are deliberately NOT touched: those are hazards left in a place
     * and have their own clock, where a Tornado is something being actively held up.
     */
    public static void forgetPlayer(ServerPlayer player) {
        cancelTornado(player);
    }

    /**
     * Drops every column in a level that is going away. Nothing else holds them, so
     * without this one would keep a dead ServerLevel alive for as long as the server
     * ran.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(spout -> spout.level == level);
    }

    @Nullable
    private static Spout findTornado(UUID ownerId) {
        for (Spout spout : ACTIVE) {
            if (ownerId.equals(spout.ownerId)) return spout;
        }
        return null;
    }
}
