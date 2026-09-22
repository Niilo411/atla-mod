package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everyone currently being crushed into the ground.
 *
 * Five seconds held in place — horizontal motion is zeroed every tick, the same
 * "rooted but not suspended" shape {@link com.minecraft.atlamod.AbilityHandler#holdStill}
 * uses for a channel — while the ground is cleared out from under them so they can
 * actually sink, rather than pressing them against still-solid rock. That was the
 * bug in the first version: the block below only opened once every ten ticks while
 * a much faster forced velocity fought the nine ticks of solid ground in between,
 * so the victim sat pinned in place and only snapped down a block at a time instead
 * of falling. Clearing the ground EVERY tick and driving the descent at exactly the
 * pace the depth budget allows is what makes it read as a fall.
 */
public final class GravityCrushes {

    private static final int DURATION_TICKS = 100; // 5 seconds
    private static final int DEPTH = 10; // blocks cleared over the whole descent

    /** Blocks per tick, worked out FROM the depth and duration rather than guessed,
     *  so a full five seconds of holding on drives exactly ten blocks down. */
    private static final double DRIVE_SPEED = -(double) DEPTH / DURATION_TICKS;

    private static final int HIT_EVERY = 20; // once a second
    private static final float BONUS_DAMAGE = 4.0F;

    private static final List<Crushed> ACTIVE = new ArrayList<>();

    private GravityCrushes() {
    }

    private static final class Crushed {
        final ServerLevel level;
        final UUID victimId;
        final UUID casterId;
        int ticksLeft = DURATION_TICKS;
        int age = 0;
        int cleared = 0;

        Crushed(ServerLevel level, UUID victimId, UUID casterId) {
            this.level = level;
            this.victimId = victimId;
            this.casterId = casterId;
        }
    }

    public static void start(ServerPlayer caster, LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        ACTIVE.add(new Crushed(level, victim.getUUID(), caster.getUUID()));
    }

    /** Iterates a SNAPSHOT: the damage this deals can kill the victim mid-crush. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Crushed crushed : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(crushed)) continue;

            if (!advance(crushed, server)) {
                ACTIVE.remove(crushed);
            }
        }
    }

    private static boolean advance(Crushed crushed, MinecraftServer server) {
        if (!(crushed.level.getEntity(crushed.victimId) instanceof LivingEntity victim) || !victim.isAlive()) {
            return false;
        }
        if (crushed.ticksLeft-- <= 0) return false;
        crushed.age++;

        // Held in place horizontally — not suspended, only rooted — while the
        // downward drive is what actually moves them, at exactly the depth's pace.
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(0.0, crushed.cleared < DEPTH ? DRIVE_SPEED : Math.min(0.0, motion.y), 0.0);
        victim.hurtMarked = true;
        victim.fallDistance = 0.0F;

        // Checked EVERY tick rather than in occasional bursts, so there is always
        // open air for the drive speed above to actually move them into — a hole
        // that only opens once every ten ticks cannot be fallen through continuously.
        // Only an ACTUAL destroy counts against the depth budget: the block below
        // stays air for several ticks in a row while the slow drive speed carries
        // the victim down through the gap already opened, and none of those ticks
        // are a new block being taken.
        if (crushed.cleared < DEPTH) {
            BlockPos below = victim.blockPosition().below();
            BlockState state = crushed.level.getBlockState(below);
            if (!state.isAir() && state.getDestroySpeed(crushed.level, below) >= 0.0F) {
                crushed.level.destroyBlock(below, false);
                crushed.cleared++;
                Gravity.burst(crushed.level, victim.position(), 8, 0.35);
            }
        }

        if (crushed.age % HIT_EVERY == 0) {
            ServerPlayer caster = server.getPlayerList().getPlayer(crushed.casterId);
            if (caster != null) {
                victim.hurt(caster.damageSources().indirectMagic(caster, caster), BONUS_DAMAGE);
            } else {
                victim.hurt(victim.damageSources().magic(), BONUS_DAMAGE);
            }
        }

        return true;
    }

    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(crushed -> crushed.victimId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(crushed -> crushed.level == level);
    }
}
