package com.minecraft.atlamod.abilities.lava;

import com.minecraft.atlamod.abilities.BendingProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Storms of falling lava, hanging over the place they were called down on.
 *
 * A tracker rather than a channel, for the reason Fire Rain documents: the ability is
 * cast once and then runs on its own for twenty seconds, so there is no key being held
 * and nothing to bill by the second.
 *
 * Fixed to WHERE it was cast rather than following the caster. A storm is a place, and
 * one that walked around with its bender would be a permanent thirty-block aura rather
 * than something to get out from under.
 */
public final class LavaRains {

    /** How long a storm lasts, in ticks. The design's twenty seconds. */
    public static final int DURATION = 400;

    /** Half the width, so the storm covers the design's thirty by thirty. */
    private static final double RADIUS = 15.0;

    /** How far above the caster the drops start. */
    private static final double SKY = 24.0;

    /**
     * Drops loosed per tick.
     *
     * Two rather than a number picked to fill the area: a circle of radius fifteen is
     * seven hundred columns, and a storm dense enough to cover all of them would be
     * seven hundred light-emitting blocks alive at once. Two a tick against a puddle
     * life of five seconds settles at around two hundred, which reads as heavy rain
     * without asking the light engine to relight the neighbourhood every tick.
     */
    private static final int DROPS_PER_TICK = 2;

    /**
     * How long one drop's puddle lasts, in ticks — and it is capped a second time by
     * whatever is left of the storm.
     *
     * That second cap is what makes the design's "after that it all disappears" true
     * exactly: a drop landing in the storm's last second leaves lava for one second,
     * so nothing outlives the storm by even a tick. It also means the puddles cool
     * continuously rather than every one of them vanishing on the same tick, which
     * would be both a visible pop and a spike of block updates.
     */
    private static final int PUDDLE_TICKS = 100;

    /** The design's eight hp a drop. */
    private static final float DROP_DAMAGE = 8.0F;

    /** Drops fall fast and hit a small area — a drop, not a boulder. */
    private static final double DROP_SPEED = 1.6;
    private static final double DROP_RADIUS = 0.8;

    private static final List<Storm> ACTIVE = new ArrayList<>();

    private LavaRains() {
    }

    private static final class Storm {
        final ServerLevel level;
        final UUID ownerId;
        final Vec3 centre;
        int ticksLeft = DURATION;

        Storm(ServerLevel level, UUID ownerId, Vec3 centre) {
            this.level = level;
            this.ownerId = ownerId;
            this.centre = centre;
        }
    }

    /** Calls a storm down over the ground the bender is standing on. */
    public static void call(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        ACTIVE.add(new Storm(level, owner.getUUID(), owner.position()));
        Lava.roar(level, owner.position(), 3.0F, 0.6F);
    }

    /** Advances every storm in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        // A snapshot, like every other manager that can kill something: a drop can
        // finish a player off, whose death handler reaches back into these lists.
        Iterator<Storm> storms = ACTIVE.iterator();
        while (storms.hasNext()) {
            Storm storm = storms.next();

            if (--storm.ticksLeft <= 0) {
                storms.remove();
                continue;
            }
            rain(storm);
        }
    }

    private static void rain(Storm storm) {
        ServerPlayer owner = storm.level.getServer().getPlayerList().getPlayer(storm.ownerId);
        if (owner == null || owner.level() != storm.level) return;

        for (int i = 0; i < DROPS_PER_TICK; i++) {
            loose(storm, owner);
        }

        // The ceiling of the storm, drawn in one batched call rather than one particle
        // per packet: a directed velocity needs count 0, which is a packet each, and
        // this is scenery rather than the thing that hurts. Same reasoning as Fire Rain.
        storm.level.sendParticles(ParticleTypes.FALLING_LAVA,
                storm.centre.x, storm.centre.y + SKY * 0.6, storm.centre.z,
                40, RADIUS, SKY * 0.4, RADIUS, 0.0);
    }

    /** One drop, straight down from the sky onto a random point under the storm. */
    private static void loose(Storm storm, ServerPlayer owner) {
        // Scattered by AREA, not by radius: picking a uniform radius bunches everything
        // near the middle, since a ring at r=15 holds five times the ground of one at
        // r=3. The square root spreads them evenly. Same trick Ice barrage uses.
        double angle = storm.level.random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(storm.level.random.nextDouble()) * RADIUS;

        Vec3 from = storm.centre.add(
                Math.cos(angle) * distance, SKY, Math.sin(angle) * distance);

        // Capped by what is left of the storm, so no puddle outlives it.
        int life = Math.min(PUDDLE_TICKS, storm.ticksLeft);

        BendingProjectiles.Spec drop = new BendingProjectiles.Spec(
                DROP_SPEED, 80, DROP_DAMAGE, DROP_RADIUS, 0.0,
                BendingProjectiles.Style.LAVA)
                .withImpact((level, at) -> puddle(level, at, life))
                // A drop that lands on somebody burns them as well as hitting them —
                // the same terms as standing in the stuff, so Lava resistance answers
                // both halves at once.
                .withHitEntity((thrower, struck) -> Lava.scorch(struck));

        BendingProjectiles.launch(owner, from, new Vec3(0.0, -1.0, 0.0), drop);
    }

    /** What a drop leaves where it lands. */
    private static void puddle(ServerLevel level, Vec3 at, int life) {
        if (life <= 0) return;

        // A shot bursts at the last position it reached BEFORE the block that stopped
        // it, so this is already the free space on top of whatever the drop landed on.
        //
        // No fallback if that space is taken, and that is deliberate: our lava does not
        // block a projectile, so a drop landing where an earlier one has already puddled
        // falls straight through it and reports the puddle's own position. Retrying a
        // block higher would stack a second one on top and a storm would build towers.
        LavaWorks.pour(level, BlockPos.containing(at), life);

        Lava.spatter(level, at, 8, 0.4);
        Lava.hiss(level, at, 0.6F, 1.2F);
    }

    /**
     * Drops every storm in a level that is going away.
     *
     * The lava itself is LavaWorks' business and is settled by its own sweep; this only
     * stops more of it being called down into a level nothing is watching any more.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(storm -> storm.level == level);
    }

    /** Ends a storm belonging to a player who has died, logged out or left the level. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.removeIf(storm -> storm.ownerId.equals(player.getUUID()));
    }
}
