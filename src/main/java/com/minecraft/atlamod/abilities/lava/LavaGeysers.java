package com.minecraft.atlamod.abilities.lava;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Geysers of lava, set down and left to erupt on their own.
 *
 * These have no owner, and that is deliberate — the same distinction {@link
 * com.minecraft.atlamod.abilities.air.AirSpouts} draws between a Tornado and a placed
 * spout. A geyser is a hazard put in a place with a clock of its own, so it keeps
 * erupting whether or not the bender who set it down is still standing there, still in
 * the level, or still logged in.
 *
 * It also throws EVERYONE, its own bender included. Passing {@code null} as the first
 * argument to getEntities is what makes that work — that argument is the entity to
 * SKIP, and something you walk into should not politely step around you.
 */
public final class LavaGeysers {

    /** How long a geyser stands, in ticks. The design's twenty seconds. */
    public static final int LIFETIME = 400;

    /** Ticks between eruptions. INVENTED — the design says only "spews out lava". */
    private static final int ERUPT_EVERY = 40;

    /** How far from the mouth an eruption reaches, in blocks. */
    private static final double REACH = 2.5;

    /** How high an eruption throws whatever is over the mouth. */
    private static final double LAUNCH = 0.9;

    /** How many splashes of lava an eruption scatters around the mouth. */
    private static final int SPLASHES = 4;

    /** How far those splashes land, in blocks. */
    private static final int SPLASH_RANGE = 2;

    /** How long a splash lasts before it cools, in ticks. */
    private static final int SPLASH_TICKS = 60;

    /** How far up and down a splash looks for ground. */
    private static final int UP_SCAN = 1;
    private static final int DOWN_SCAN = 2;

    private static final List<Geyser> ACTIVE = new ArrayList<>();

    private LavaGeysers() {
    }

    private static final class Geyser {
        final ServerLevel level;
        final BlockPos mouth;
        int ticksLeft = LIFETIME;

        Geyser(ServerLevel level, BlockPos mouth) {
            this.level = level;
            this.mouth = mouth;
        }
    }

    /**
     * Sets a geyser down at a spot on the ground.
     *
     * @return false if there was nowhere for the mouth to go
     */
    public static boolean place(ServerLevel level, BlockPos ground) {
        // The mouth itself is lava for the geyser's whole life, so it is a hazard even
        // between eruptions rather than a harmless hole that is occasionally dangerous.
        if (!LavaWorks.pour(level, ground, LIFETIME)) return false;

        ACTIVE.add(new Geyser(level, ground.immutable()));
        Lava.roar(level, Vec3.atCenterOf(ground), 1.2F, 1.2F);
        return true;
    }

    /** Advances every geyser in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        // A snapshot, like every other manager that can kill something: an eruption can
        // finish a player off, and the death handler reaches back into these lists.
        Iterator<Geyser> geysers = ACTIVE.iterator();
        while (geysers.hasNext()) {
            Geyser geyser = geysers.next();

            if (--geyser.ticksLeft <= 0) {
                geysers.remove();
                continue;
            }
            tick(geyser);
        }
    }

    private static void tick(Geyser geyser) {
        Vec3 mouth = Vec3.atCenterOf(geyser.mouth);

        // Between eruptions it smoulders, so a geyser is visible as one the whole time
        // rather than only for the tick it goes off.
        if (geyser.ticksLeft % 4 == 0) {
            geyser.level.sendParticles(ParticleTypes.LAVA,
                    mouth.x, mouth.y + 0.6, mouth.z, 1, 0.2, 0.1, 0.2, 0.0);
            geyser.level.sendParticles(ParticleTypes.SMOKE,
                    mouth.x, mouth.y + 0.8, mouth.z, 2, 0.2, 0.2, 0.2, 0.01);
        }

        if (geyser.ticksLeft % ERUPT_EVERY != 0) return;

        erupt(geyser, mouth);
    }

    private static void erupt(Geyser geyser, Vec3 mouth) {
        ServerLevel level = geyser.level;

        // The plume. Two batched calls rather than a stream of directed particles: a
        // directed velocity needs count 0, which is one particle per packet.
        level.sendParticles(ParticleTypes.LAVA,
                mouth.x, mouth.y + 2.0, mouth.z, 40, 0.4, 1.8, 0.4, 0.0);
        level.sendParticles(ParticleTypes.FLAME,
                mouth.x, mouth.y + 1.5, mouth.z, 30, 0.5, 1.5, 0.5, 0.05);
        Lava.hiss(level, mouth, 1.4F, 0.7F);

        // Everything standing over the mouth is thrown up and burned. null, not the
        // bender: see the class note.
        AABB reach = new AABB(mouth, mouth).inflate(REACH, REACH + 1.0, REACH);
        for (Entity target : level.getEntities((Entity) null, reach)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            living.setDeltaMovement(living.getDeltaMovement().x, LAUNCH, living.getDeltaMovement().z);
            // Players ignore server-side velocity unless it is explicitly pushed to them.
            living.hurtMarked = true;

            Lava.scorch(living);
        }

        // And it throws lava about, which is the part that actually spreads the hazard.
        for (int i = 0; i < SPLASHES; i++) {
            int dx = level.random.nextInt(SPLASH_RANGE * 2 + 1) - SPLASH_RANGE;
            int dz = level.random.nextInt(SPLASH_RANGE * 2 + 1) - SPLASH_RANGE;

            BlockPos ground = Lava.footing(level,
                    geyser.mouth.offset(dx, 0, dz), UP_SCAN, DOWN_SCAN);
            if (ground == null) continue;

            if (LavaWorks.pour(level, ground, SPLASH_TICKS)) {
                LavaWorks.splash(level, ground);
            }
        }
    }

    /**
     * Drops every geyser in a level that is going away.
     *
     * The lava is LavaWorks' business and is settled by its own sweep; this only stops
     * more of it being thrown into a level nothing is watching any more.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(geyser -> geyser.level == level);
    }
}
