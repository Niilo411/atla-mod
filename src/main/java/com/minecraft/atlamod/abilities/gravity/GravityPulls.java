package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everyone currently being hauled in by a Gravity pull.
 *
 * WHY A TRACKER RATHER THAN ONE SHOVE. The pull used to be a single velocity set on
 * the cast, and a single impulse cannot reliably deliver something to a POINT: drag
 * and ground friction eat it at a rate that depends on whether the target is on the
 * ground, in the air, or in water, so the same shove that brought a zombie two blocks
 * short on grass would stop five short on a slope. Steering every tick towards the
 * landing point is what makes "all the way in front of the bender" true wherever it is
 * cast. It is wind tunnel's per-tick velocity trick, aimed at a point.
 *
 * THE LANDING POINT FOLLOWS THE BENDER, worked out fresh every tick from where they
 * stand and which way they face — so a bender who steps back or turns while the target
 * is in flight still gets them delivered in front of them rather than where they were.
 */
public final class GravityPulls {

    /** How far in front of the bender the target is set down. */
    private static final double LANDING_DISTANCE = 1.5;

    /** Close enough to count as arrived. */
    private static final double ARRIVED = 0.6;

    /** Fastest the target is moved, in blocks a tick. Quick, but not a teleport. */
    private static final double MAX_SPEED = 1.4;

    /** How much of the remaining gap is closed each tick, before the cap. */
    private static final double EASING = 0.45;

    /** A backstop: a target that is somehow never delivered is let go after 2s. */
    private static final int MAX_TICKS = 40;

    private static final List<Pull> ACTIVE = new ArrayList<>();

    private GravityPulls() {
    }

    private static final class Pull {
        final ServerLevel level;
        final UUID ownerId;
        final UUID targetId;
        int ticks;

        Pull(ServerLevel level, UUID ownerId, UUID targetId) {
            this.level = level;
            this.ownerId = ownerId;
            this.targetId = targetId;
        }
    }

    /** Starts hauling {@code target} to the spot in front of {@code owner}. */
    public static void start(ServerPlayer owner, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (target.isPassenger()) return;

        // One pull per target: a second cast replaces the first rather than two
        // trackers fighting over the same body.
        ACTIVE.removeIf(pull -> pull.targetId.equals(target.getUUID()));
        ACTIVE.add(new Pull(level, owner.getUUID(), target.getUUID()));
    }

    /** Called once per server tick. Iterates a snapshot, like every manager that moves things. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Pull pull : List.copyOf(ACTIVE)) {
            if (!advance(pull, server)) ACTIVE.remove(pull);
        }
    }

    private static boolean advance(Pull pull, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(pull.ownerId);
        if (owner == null || owner.level() != pull.level || !owner.isAlive()) return false;

        Entity found = pull.level.getEntity(pull.targetId);
        if (!(found instanceof LivingEntity target) || !target.isAlive() || target.isPassenger()) return false;

        // The facing is FLATTENED first, or a bender looking down would have the target
        // delivered into the floor, and one looking up would hang it over their head.
        Vec3 look = owner.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

        Vec3 landing = owner.position().add(flat.scale(LANDING_DISTANCE));
        Vec3 gap = landing.subtract(target.position());

        if (gap.length() <= ARRIVED || ++pull.ticks > MAX_TICKS) {
            stop(target);
            return false;
        }

        Vec3 velocity = gap.scale(EASING);
        if (velocity.length() > MAX_SPEED) velocity = velocity.normalize().scale(MAX_SPEED);

        target.setDeltaMovement(velocity);
        target.fallDistance = 0.0F;

        // A player's own client decides where they are; the velocity only reaches it if
        // it is pushed. A mob is simulated here, so marking it would be a packet a tick
        // for nothing — the same distinction wind tunnel draws.
        if (target instanceof ServerPlayer) target.hurtMarked = true;

        return true;
    }

    /** Sets the target down where it is rather than letting it coast past the bender. */
    private static void stop(LivingEntity target) {
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        if (target instanceof ServerPlayer) target.hurtMarked = true;
    }

    /** Called on death, logout and dimension change — as owner or as target. */
    public static void forgetPlayer(ServerPlayer player) {
        if (ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(pull -> pull.ownerId.equals(player.getUUID()) || pull.targetId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        if (ACTIVE.isEmpty()) return;
        ACTIVE.removeIf(pull -> pull.level == level);
    }
}
