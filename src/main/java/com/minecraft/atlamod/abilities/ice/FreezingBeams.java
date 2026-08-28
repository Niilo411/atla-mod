package com.minecraft.atlamod.abilities.ice;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
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
 * Every freezing beam currently running.
 *
 * The beam is a ball of ice that floats beside the bender and fires a continuous line
 * down their crosshair for ten seconds — so it needs to outlive the click that
 * started it, which is why it is tracked here rather than being done inside the
 * ability.
 *
 * Drawn as a swept line of particles and hit-tested the same way: everything within
 * a short distance of the line takes the damage and the chill, so the beam catches
 * what it visibly crosses rather than only what is exactly on the crosshair.
 */
public final class FreezingBeams {

    /** How long the beam runs once started. */
    public static final int DURATION = 200; // 10 seconds

    /** How far the beam reaches, in blocks. */
    private static final double REACH = 25.0;

    /** How close to the line something has to be to be caught. */
    private static final double WIDTH = 1.2;

    /** 2 hp a second, as specced. */
    private static final float DAMAGE = 2.0F;

    /**
     * Damage lands on an explicit one-second beat rather than every tick.
     *
     * Per-tick hits would mostly be swallowed by invulnerability frames, but that is
     * working by accident — the moment anything else resets those frames the beam
     * would hit twenty times harder than advertised. Same reasoning as wind tunnel.
     */
    private static final int HIT_EVERY = 20;

    /** How far along the line the particles are stepped when drawing it. */
    private static final double DRAW_STEP = 0.6;

    private static final List<Beam> ACTIVE = new ArrayList<>();

    private FreezingBeams() {
    }

    private static final class Beam {
        final ServerLevel level;
        final UUID ownerId;
        int ticksLeft = DURATION;
        int ticks;

        Beam(ServerLevel level, UUID ownerId) {
            this.level = level;
            this.ownerId = ownerId;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Beam beam : ACTIVE) {
            if (beam.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void start(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        ACTIVE.add(new Beam(level, player.getUUID()));
        Ice.crack(level, player.position(), 0.9F, 1.4F);
    }

    /** Iterates a SNAPSHOT — the beam kills, and a death handler calls back in here. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Beam beam : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(beam)) continue;

            if (!advance(beam, server)) {
                ACTIVE.remove(beam);
            }
        }
    }

    private static boolean advance(Beam beam, MinecraftServer server) {
        if (beam.ticksLeft-- <= 0) return false;
        beam.ticks++;

        ServerPlayer owner = server.getPlayerList().getPlayer(beam.ownerId);
        if (owner == null || owner.level() != beam.level || !owner.isAlive()) return false;

        Vec3 from = origin(owner);
        Vec3 look = owner.getLookAngle();

        // The line stops at the first wall, so the beam does not carry on through
        // terrain and freeze whatever is in the cave beyond it.
        net.minecraft.world.phys.HitResult hit = beam.level.clip(
                new net.minecraft.world.level.ClipContext(
                        from, from.add(look.scale(REACH)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, owner));

        Vec3 to = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? hit.getLocation()
                : from.add(look.scale(REACH));

        draw(beam, owner, from, to);

        if (beam.ticks % HIT_EVERY == 0) {
            strike(beam, owner, from, to);
        }

        return true;
    }

    /** The floating ball, and the line it throws. */
    private static void draw(Beam beam, ServerPlayer owner, Vec3 from, Vec3 to) {
        // The ball itself, turning beside the bender.
        double phase = owner.tickCount * 0.3;
        for (int i = 0; i < 3; i++) {
            double a = phase + (i * Math.PI * 2.0 / 3.0);
            Vec3 at = from.add(Math.cos(a) * 0.3, Math.sin(a * 1.5) * 0.15, Math.sin(a) * 0.3);
            beam.level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.01) return;

        Vec3 step = along.scale(DRAW_STEP / length);
        int steps = (int) (length / DRAW_STEP);

        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(step.scale(i));
            beam.level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /** Everything close enough to the line takes the damage and the chill. */
    private static void strike(Beam beam, ServerPlayer owner, Vec3 from, Vec3 to) {
        BendingData data = owner.getData(ModAttachments.BENDING_DATA);
        float damage = Ice.damage(data, DAMAGE);

        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.01) return;

        Vec3 direction = along.scale(1.0 / length);

        AABB search = new AABB(from, to).inflate(WIDTH);

        for (Entity caught : beam.level.getEntities(owner, search)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            // Distance from the LINE, not from either end — a beam should catch what
            // it passes through, not only what is near where it started or stopped.
            Vec3 toTarget = living.position().add(0.0, living.getBbHeight() * 0.5, 0.0).subtract(from);
            double alongLine = toTarget.dot(direction);
            if (alongLine < 0.0 || alongLine > length) continue;

            if (toTarget.subtract(direction.scale(alongLine)).length() > WIDTH) continue;

            living.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            Ice.chill(living, 100, 1);

            Ice.frost(beam.level, living.position().add(0.0, living.getBbHeight() * 0.5, 0.0), 8, 0.3);
        }
    }

    /** Where the ball floats: beside the bender, at chest height. */
    private static Vec3 origin(ServerPlayer owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize().scale(0.6);

        return owner.getEyePosition().add(side).subtract(0.0, 0.2, 0.0);
    }

    /**
     * Shuts a running beam off early, at the bender's own request.
     *
     * Separate from forgetPlayer, which is the silent cleanup for a bender who has
     * died or left: this one is a deliberate act and says so.
     */
    public static void cancel(ServerPlayer player) {
        for (Beam beam : List.copyOf(ACTIVE)) {
            if (!beam.ownerId.equals(player.getUUID())) continue;

            Ice.shatter(beam.level, origin(player), 20, 0.4);
            Ice.crack(beam.level, player.position(), 0.7F, 1.5F);
            ACTIVE.remove(beam);
        }
    }

    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(beam -> beam.ownerId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(beam -> beam.level == level);
    }
}
