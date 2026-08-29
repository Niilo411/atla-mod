package com.minecraft.atlamod.abilities.sound;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.ModEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every run of bass waves currently going.
 *
 * The ability is cast once and then runs for fifteen seconds on its own, throwing a
 * wave out every four — so it has to outlive the cast, which is why it is tracked
 * here rather than being done inside the ability class.
 *
 * A wave is not a projectile: it expands outward from the bender as a growing ring
 * and hits whatever it passes over, ONCE. Tracking the ring's radius and catching
 * things in the shell between last tick's and this one's is the whole difference
 * between a wave and an aura.
 */
public final class BassWaves {

    /** How long the ability keeps throwing waves. */
    public static final int DURATION = 300; // 15 seconds

    /** How often a new wave goes out. */
    private static final int EVERY = 80; // 4 seconds

    /** How far a wave travels before it dies. */
    private static final double REACH = 16.0;

    /** How fast it expands, in blocks per tick. */
    private static final double SPEED = 0.8;

    /** 5 hp, as specced. */
    private static final float DAMAGE = 5.0F;

    /**
     * One second of Stunned per wave, cut from three.
     *
     * A wave goes out every four seconds for fifteen, so at three seconds a hold the
     * stun was very nearly continuous for anything that stayed in range — each wave
     * landed while the previous one's hold was still most of the way through. At one
     * second the waves punctuate rather than lock.
     */
    private static final int STUN_TICKS = 20;

    private static final List<Run> ACTIVE = new ArrayList<>();

    private BassWaves() {
    }

    /** One bender's fifteen seconds of throwing waves. */
    private static final class Run {
        final ServerLevel level;
        final UUID ownerId;
        int ticksLeft = DURATION;
        int ticks;

        /** Whether the bender has already been let go of, so it happens exactly once. */
        boolean released;

        /** The rings currently travelling outward, as their radii. */
        final List<Double> rings = new ArrayList<>();

        Run(ServerLevel level, UUID ownerId) {
            this.level = level;
            this.ownerId = ownerId;
        }
    }

    public static boolean has(ServerPlayer player) {
        for (Run run : ACTIVE) {
            if (run.ownerId.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static void start(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (has(player)) return;

        Run run = new Run(level, player.getUUID());
        // The first wave goes out immediately rather than after four seconds, so the
        // cast visibly does something the moment it is paid for.
        run.rings.add(0.0);

        ACTIVE.add(run);

        // Pinned for as long as it throws. Both halves, like every rooting channel:
        // the client is told to stop taking movement input, and the server zeroes the
        // motion every tick below.
        com.minecraft.atlamod.AbilityHandler.setRooted(player, true);

        Sound.boom(level, player.position(), 1.4F);
    }

    /** Ends a run early, which the design asks for explicitly. */
    public static void cancel(ServerPlayer player) {
        for (Run run : List.copyOf(ACTIVE)) {
            if (!run.ownerId.equals(player.getUUID())) continue;

            Sound.play(run.level, player.position(),
                    net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.6F);
            release(player);
            ACTIVE.remove(run);
        }
    }

    /**
     * Lets the bender move again.
     *
     * Every route out of a run goes through here — cancelled, finished, dead, gone —
     * because ClientRootState is a client static: a release missed on any one of them
     * would leave a player who simply cannot move.
     */
    private static void release(ServerPlayer player) {
        com.minecraft.atlamod.AbilityHandler.setRooted(player, false);
    }

    /** Iterates a SNAPSHOT — waves kill, and a death handler calls back in here. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Run run : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(run)) continue;

            if (!advance(run, server)) {
                ACTIVE.remove(run);
            }
        }
    }

    private static boolean advance(Run run, MinecraftServer server) {
        ServerPlayer owner = server.getPlayerList().getPlayer(run.ownerId);
        if (owner == null || owner.level() != run.level || !owner.isAlive()) {
            // Nothing to release: a player who has died, logged out or left the level
            // gets a fresh RootedPacket on login and respawn anyway, and there is no
            // connection to send one down here.
            return false;
        }

        run.ticks++;

        // The server's half of the pin, while it is still THROWING. Waves already in
        // the air finish travelling on their own, and the bender is free to move the
        // moment the last one goes out rather than waiting for it to land.
        if (run.ticksLeft > 0) {
            com.minecraft.atlamod.AbilityHandler.holdStill(owner);
        } else if (!run.released) {
            run.released = true;
            release(owner);
        }

        // Stop THROWING at fifteen seconds, but let whatever is already travelling
        // finish — a wave cut off halfway would simply vanish in mid-air.
        if (run.ticksLeft-- > 0 && run.ticks % EVERY == 0) {
            run.rings.add(0.0);
            Sound.boom(run.level, owner.position(), 1.2F);
        }

        BendingData data = owner.getData(ModAttachments.BENDING_DATA);

        List<Double> next = new ArrayList<>();
        for (double radius : run.rings) {
            double grown = radius + SPEED;
            if (grown > REACH) continue;

            sweep(run, owner, data, radius, grown);
            next.add(grown);
        }

        run.rings.clear();
        run.rings.addAll(next);

        return run.ticksLeft > 0 || !run.rings.isEmpty();
    }

    /**
     * Catches everything in the shell the wave crossed this tick.
     *
     * Between last tick's radius and this one's, so each thing is struck once as the
     * wave passes over it rather than every tick it happens to be inside the circle.
     */
    private static void sweep(Run run, ServerPlayer owner, BendingData data,
                              double from, double to) {
        AABB area = new AABB(owner.position(), owner.position()).inflate(to);

        int stun = Sound.duration(data, STUN_TICKS);
        float damage = Sound.damage(data, DAMAGE);

        for (Entity caught : run.level.getEntities(owner, area)) {
            if (!(caught instanceof LivingEntity living) || !living.isAlive()) continue;

            double distance = Math.sqrt(living.position().distanceToSqr(owner.position()));
            if (distance < from || distance >= to) continue;

            living.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            living.addEffect(new MobEffectInstance(ModEffects.STUNNED, stun, 0, false, true, true));

            Sound.burst(run.level, living.getEyePosition(), 8, 0.3);
        }

        // Drawn at the wave's own radius, so what is seen is what hits.
        Sound.ring(run.level, owner.position(), to, Math.max(8, (int) (to * 3)));
    }

    /**
     * Called on death, logout and dimension change.
     *
     * Releases as well as forgetting: a player who changes dimension mid-run is still
     * connected, and would otherwise arrive on the other side unable to move.
     */
    public static void forgetPlayer(ServerPlayer player) {
        if (has(player)) release(player);
        ACTIVE.removeIf(run -> run.ownerId.equals(player.getUUID()));
    }

    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(run -> run.level == level);
    }
}
