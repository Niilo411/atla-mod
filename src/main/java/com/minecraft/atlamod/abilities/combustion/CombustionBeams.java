package com.minecraft.atlamod.abilities.combustion;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import net.minecraft.core.BlockPos;
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
 * Every beam of destruction currently running.
 *
 * A held line of white heat that eats through whatever the bender looks at, block by
 * block, and burns anything standing in it. Icebending's Freezing Beam is the nearest
 * thing to it and the comparison is instructive: that one damages and chills, where
 * this one takes the world apart.
 *
 * Blocks are broken SLOWLY and one at a time — the design's word, and the right one.
 * A beam that deleted a tunnel instantly would be a digging tool; taking a block every
 * few ticks makes it something you have to hold on a target while it works.
 */
public final class CombustionBeams {

    /** How far the beam reaches, in blocks. */
    private static final double REACH = 24.0;

    /** How far above the eyes the beam leaves from. See advance(). */
    private static final double BROW_HEIGHT = 0.35;

    /** How close to the line something has to be to be caught. */
    private static final double WIDTH = 1.0;

    /** 4 hp a second, as specced. */
    private static final float DAMAGE = 4.0F;

    /**
     * Damage lands on an explicit one-second beat rather than every tick.
     *
     * Per-tick hits would mostly be swallowed by invulnerability frames, but that is
     * working by accident — the moment anything else resets those frames the beam
     * would hit twenty times harder than advertised. Same reasoning as wind tunnel.
     */
    private static final int HIT_EVERY = 20;

    /** One block eaten every this many ticks. "Slowly", as the design asks. */
    private static final int BREAK_EVERY = 6;

    /** How far along the line the particles are stepped when drawing it. */
    private static final double DRAW_STEP = 0.5;

    private static final List<Beam> ACTIVE = new ArrayList<>();

    private CombustionBeams() {
    }

    private static final class Beam {
        final ServerLevel level;
        final UUID ownerId;
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
        Combustion.boom(level, player.position(), 0.9F, 1.5F);
    }

    /** Switches a running beam off, which is what the second key press does. */
    public static void stop(ServerPlayer player) {
        for (Beam beam : List.copyOf(ACTIVE)) {
            if (!beam.ownerId.equals(player.getUUID())) continue;

            Combustion.boom(beam.level, player.position(), 0.6F, 1.8F);
            ACTIVE.remove(beam);
        }
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
        ServerPlayer owner = server.getPlayerList().getPlayer(beam.ownerId);
        if (owner == null || owner.level() != beam.level || !owner.isAlive()) return false;

        beam.ticks++;

        // Started above the eyes, not at them. A beam leaving from exactly the camera's
        // own position is invisible to the bender firing it — every particle spawns
        // inside their head and is culled — so from the inside the ability looked like
        // it was doing nothing at all. Raising the origin is also the truer picture:
        // the charge comes off the third eye, above the brow.
        //
        // The whole origin moves, not just the drawing. The line that is drawn and the
        // line that burns have to be the same line, or the beam hits things it visibly
        // missed.
        Vec3 from = owner.getEyePosition().add(0.0, BROW_HEIGHT, 0.0);
        Vec3 look = owner.getLookAngle();

        // Where the beam currently stops: the first solid thing, or its full reach.
        net.minecraft.world.phys.HitResult hit = beam.level.clip(
                new net.minecraft.world.level.ClipContext(
                        from, from.add(look.scale(REACH)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, owner));

        boolean onBlock = hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
        Vec3 to = onBlock ? hit.getLocation() : from.add(look.scale(REACH));

        draw(beam, from, to);

        if (beam.ticks % HIT_EVERY == 0) {
            burn(beam, owner, from, to);
        }

        // The block at the far end goes, one at a time, so the beam bores rather than
        // deletes. Whatever is behind it becomes the new far end next tick.
        if (onBlock && beam.ticks % BREAK_EVERY == 0) {
            eat(beam, owner, (net.minecraft.world.phys.BlockHitResult) hit);
        }

        return true;
    }

    /** The white line itself. */
    private static void draw(Beam beam, Vec3 from, Vec3 to) {
        Vec3 along = to.subtract(from);
        double length = along.length();
        if (length < 0.01) return;

        Vec3 step = along.scale(DRAW_STEP / length);
        int steps = (int) (length / DRAW_STEP);

        for (int i = 1; i <= steps; i++) {
            Combustion.stripe(beam.level, from.add(step.scale(i)));
        }
    }

    /** Everything close enough to the line takes the damage. */
    private static void burn(Beam beam, ServerPlayer owner, Vec3 from, Vec3 to) {
        BendingData data = owner.getData(ModAttachments.BENDING_DATA);
        float damage = Combustion.damage(data, DAMAGE);

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
            living.setRemainingFireTicks(60);
        }
    }

    /**
     * Takes the block the beam is resting on.
     *
     * Destroyed rather than dropped: this is a beam of destruction, not a mining tool,
     * and boring a twenty-block tunnel that filled the floor with items to wade back
     * through would be neither. The same call Earth dig makes.
     *
     * Anything unbreakable is skipped rather than stopping the beam, so bedrock simply
     * holds it.
     */
    private static void eat(Beam beam, ServerPlayer owner, net.minecraft.world.phys.BlockHitResult hit) {
        BlockPos at = hit.getBlockPos();

        var state = beam.level.getBlockState(at);
        if (state.isAir()) return;
        if (state.getDestroySpeed(beam.level, at) < 0.0F) return;
        if (!state.getFluidState().isEmpty()) return;

        beam.level.destroyBlock(at, false, owner);
        Combustion.boom(beam.level, Vec3.atCenterOf(at), 0.35F, 1.9F);
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(beam -> beam.ownerId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(beam -> beam.level == level);
    }
}
