package com.minecraft.atlamod.abilities.blood;

import com.minecraft.atlamod.ModEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every flesh shield currently raised.
 *
 * Everything alive within ten blocks in front of the bender is dragged in, frozen, and
 * held between them and whatever comes next — and whatever the shield stops is dealt
 * to the bodies making it instead.
 *
 * The most unpleasant ability in the mod on purpose. It is also the only one whose
 * cost is XP rather than chi, which is what the design asks for and what makes it feel
 * like something spent rather than something channelled.
 */
public final class FleshShields {

    /** How far in front the sweep reaches when the shield is raised. */
    private static final double GATHER_REACH = 10.0;

    /** How wide the sweep is, either side of the aim line. */
    private static final double GATHER_HALF_WIDTH = 4.0;

    /** How long a shield stands. */
    public static final int DURATION = 600; // 30 seconds

    /** How far in front of the bender the wall of bodies is held. */
    private static final double DISTANCE = 2.5;

    /** How far apart the bodies are spaced across the wall. */
    private static final double SPACING = 1.1;

    private static final List<Shield> ACTIVE = new ArrayList<>();

    private FleshShields() {
    }

    private static final class Shield {
        final ServerLevel level;
        final UUID ownerId;
        final List<LivingEntity> bodies;
        int ticksLeft = DURATION;

        Shield(ServerLevel level, UUID ownerId, List<LivingEntity> bodies) {
            this.level = level;
            this.ownerId = ownerId;
            this.bodies = bodies;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Shield shield : ACTIVE) {
            if (shield.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    /**
     * Everything the bender could pull in right now.
     *
     * Asked before the xp is spent, so raising a shield with nothing in front of you
     * costs nothing — the one mercy the ability offers.
     */
    public static List<LivingEntity> candidates(ServerPlayer player) {
        List<LivingEntity> found = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel level)) return found;

        Vec3 look = player.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) return found;
        heading = heading.normalize();

        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);
        AABB search = new AABB(player.position(), player.position()).inflate(GATHER_REACH);

        for (Entity caught : level.getEntities(player, search)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            Vec3 offset = living.position().subtract(player.position());

            double along = offset.dot(heading);
            if (along < 0.0 || along > GATHER_REACH) continue;
            if (Math.abs(offset.dot(across)) > GATHER_HALF_WIDTH) continue;

            // The pecking order applies to being made into a shield as much as to
            // anything else — a stronger bloodbender is not somebody's cover.
            if (!Blood.canBend(player, living)) continue;

            found.add(living);
        }
        return found;
    }

    /** Drags them in and freezes them. */
    public static void raise(ServerPlayer player, List<LivingEntity> bodies) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player) || bodies.isEmpty()) return;

        for (LivingEntity body : bodies) {
            body.addEffect(new MobEffectInstance(
                    ModEffects.STUNNED, DURATION, 0, false, true, true));
        }

        ACTIVE.add(new Shield(level, player.getUUID(), new ArrayList<>(bodies)));
        Blood.squelch(level, player.position(), 1.4F, 0.4F);
    }

    /**
     * Passes damage the bender would have taken on to the bodies instead.
     *
     * Called from the incoming-damage handler. Split evenly across whoever is still
     * alive in the wall, so a bigger shield spreads a blow further — which is the only
     * reason to gather more than one body.
     *
     * @return true if the blow was absorbed and should be cancelled
     */
    public static boolean absorb(ServerPlayer player, float amount,
                                 net.minecraft.world.damagesource.DamageSource source) {
        for (Shield shield : ACTIVE) {
            if (!shield.ownerId.equals(player.getUUID())) continue;

            List<LivingEntity> alive = new ArrayList<>();
            for (LivingEntity body : shield.bodies) {
                if (body.isAlive()) alive.add(body);
            }

            // A shield of corpses is no shield. The blow lands on the bender.
            if (alive.isEmpty()) return false;

            float each = amount / alive.size();
            for (LivingEntity body : alive) {
                // Invulnerability frames would swallow most of this otherwise, since
                // the whole wall is being hit at once with the same figure.
                body.invulnerableTime = 0;
                body.hurt(player.damageSources().indirectMagic(player, player), each);
                Blood.wrench(shield.level, body, 6);
            }

            return true;
        }
        return false;
    }

    /** Iterates a SNAPSHOT — this kills, and a death handler calls back in here. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(shield)) continue;

            if (!advance(shield, server)) {
                release(shield);
                ACTIVE.remove(shield);
            }
        }
    }

    private static boolean advance(Shield shield, MinecraftServer server) {
        if (shield.ticksLeft-- <= 0) return false;

        ServerPlayer owner = server.getPlayerList().getPlayer(shield.ownerId);
        if (owner == null || owner.level() != shield.level || !owner.isAlive()) return false;

        Vec3 look = owner.getLookAngle();
        Vec3 heading = new Vec3(look.x, 0.0, look.z);
        if (heading.lengthSqr() < 1.0E-4) heading = new Vec3(0.0, 0.0, 1.0);
        heading = heading.normalize();

        Vec3 across = new Vec3(-heading.z, 0.0, heading.x);
        Vec3 centre = owner.position().add(heading.scale(DISTANCE));

        // Laid out in a row across the bender's front, following the crosshair. Held
        // by setting position outright rather than by velocity: they are frozen, and a
        // shield that drifted would stop being between the bender and anything.
        List<LivingEntity> alive = new ArrayList<>();
        for (LivingEntity body : shield.bodies) {
            if (body.isAlive()) alive.add(body);
        }
        if (alive.isEmpty()) return false;

        for (int i = 0; i < alive.size(); i++) {
            LivingEntity body = alive.get(i);

            double offset = (i - (alive.size() - 1) / 2.0) * SPACING;
            Vec3 spot = centre.add(across.scale(offset));

            body.teleportTo(spot.x, owner.getY(), spot.z);
            body.setDeltaMovement(Vec3.ZERO);
            body.fallDistance = 0.0F;

            if (body instanceof Player) body.hurtMarked = true;

            Blood.wrench(shield.level, body, 1);
        }

        return true;
    }

    /** Lets the bodies go. */
    private static void release(Shield shield) {
        for (LivingEntity body : shield.bodies) {
            if (body.isAlive()) body.removeEffect(ModEffects.STUNNED);
        }
    }

    /** Ends a bender's shield early. */
    public static void drop(ServerPlayer player) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (!shield.ownerId.equals(player.getUUID())) continue;

            release(shield);
            ACTIVE.remove(shield);
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        drop(player);
    }

    public static void forgetLevel(ServerLevel level) {
        for (Shield shield : List.copyOf(ACTIVE)) {
            if (shield.level != level) continue;

            release(shield);
            ACTIVE.remove(shield);
        }
    }
}
