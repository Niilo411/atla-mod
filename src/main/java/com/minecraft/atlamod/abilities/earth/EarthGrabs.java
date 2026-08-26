package com.minecraft.atlamod.abilities.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Every wave of earth currently rolling back towards the bender who raised it.
 *
 * Tsunami turned around. The water version rolls AWAY and throws what it catches; this
 * one starts far out and comes home, dragging everything it washes over back with it —
 * which is the whole of what "grab" means here. The machinery is deliberately the same
 * shape: a moving BODY of a few slices, laid at the leading edge and taken up at the
 * trailing one, so the wall travels rather than filling in the ground behind it.
 *
 * Blocks are placed and cleared with {@link Block#UPDATE_CLIENTS} — no neighbour
 * updates. Tsunami needs that so its water does not start flowing on its own; earth
 * needs it so a wave passing under gravel does not bring a hillside down behind it.
 * Only AIR is ever replaced, so a wave cannot break anything on its way through.
 */
public final class EarthGrabs {

    /** Placed and cleared without telling the neighbours. See the class note. */
    private static final int QUIET = Block.UPDATE_CLIENTS;

    /** Blocks moved per step, and how often a step happens. */
    private static final int SPEED = 1;
    private static final int ADVANCE_EVERY = 2;

    /** How many slices of wall exist at once — the thickness of the moving body. */
    private static final int BODY_DEPTH = 2;

    /** Half the width Earth grab itself uses, so seven columns across. Other callers
     * pass their own — a thrown wall is a good deal narrower than a wave. */
    public static final int HALF_WIDTH = 3;

    /** How tall the wall stands. Lower than a tsunami: this is earth, not water. */
    private static final int HEIGHT = 2;

    /** How far each column hunts for its own footing, so the wave rides the ground. */
    private static final int UP_SCAN = 3;
    private static final int DOWN_SCAN = 4;

    /** How hard the wave hauls what it catches, and the lift that keeps it sliding. */
    private static final double DRAG_SPEED = 0.55;
    private static final double DRAG_LIFT = 0.25;

    private static final List<Grab> ACTIVE = new ArrayList<>();

    private EarthGrabs() {
    }

    private static final class Grab {
        final ServerLevel level;
        final UUID ownerId;
        final Vec3 origin;
        final Vec3 forward;
        final Vec3 across;
        final BlockState material;
        final int from;
        final int to;
        final int halfWidth;

        /**
         * Whether each column finds its own footing, or the wall simply hangs on the
         * line it was thrown along.
         *
         * Grounded is what every EARTH wave wants: the wall rides over the terrain
         * rather than ploughing through it. A thrown metal shield wants the opposite —
         * it is a slab in flight, and should go wherever it was aimed including
         * straight up.
         */
        final boolean grounded;

        int front;
        int age;
        final Deque<List<BlockPos>> slices = new ArrayDeque<>();

        Grab(ServerLevel level, UUID ownerId, Vec3 origin, Vec3 forward,
             BlockState material, int from, int to, int halfWidth, boolean grounded) {
            this.level = level;
            this.ownerId = ownerId;
            this.origin = origin;
            this.forward = forward;
            // Always horizontal, even for a wave thrown at a pitch: a wall stays
            // upright, so its width runs level however it is aimed.
            this.across = new Vec3(-forward.z, 0.0, forward.x).normalize();
            this.material = material;
            this.from = from;
            this.to = to;
            this.halfWidth = halfWidth;
            this.grounded = grounded;
            this.front = from;
        }

        /** One step in this wave's direction of travel. */
        int step() {
            return to >= from ? SPEED : -SPEED;
        }

        /** Whether the front has gone past where it was told to stop. */
        boolean past() {
            return to >= from ? front > to : front < to;
        }

        /** The unit direction the wave is travelling, for carrying what it catches. */
        Vec3 travel() {
            return to >= from ? forward : forward.scale(-1.0);
        }
    }

    /**
     * Sends a wave from {@code from} blocks out to {@code to} blocks out.
     *
     * Both distances are measured along {@code forward} from {@code origin}, and the
     * wave travels from one to the other in whichever direction that implies — Earth
     * grab comes HOME (from 20 to 5), Stone walls goes AWAY (from 2 to 20), and Crush
     * sends two of them at each other along the sideways axis.
     *
     * Whatever it catches is carried in the wave's own direction of travel, which is
     * what makes all three read as one thing being done three ways rather than three
     * separate mechanisms.
     */
    public static void launch(ServerPlayer owner, Vec3 origin, Vec3 forward,
                              BlockState material, int from, int to) {
        launch(owner, origin, forward, material, from, to, HALF_WIDTH, true);
    }

    /**
     * The same wave, with its own width and its own idea of where it sits.
     *
     * {@code grounded} false is what lets a thrown metal shield fly: each column hangs
     * on the line it was thrown along instead of finding the surface, so the wall goes
     * wherever it was aimed — straight up included.
     */
    public static void launch(ServerPlayer owner, Vec3 origin, Vec3 forward,
                              BlockState material, int from, int to,
                              int halfWidth, boolean grounded) {
        if (!(owner.level() instanceof ServerLevel level)) return;

        ACTIVE.add(new Grab(level, owner.getUUID(), origin, forward.normalize(),
                material, from, to, halfWidth, grounded));

        level.playSound(null, owner.blockPosition(), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 2.0F, 0.4F);
    }

    /**
     * Advances every wave in the world. Called once per server tick.
     *
     * Iterates a snapshot: hauling entities about runs game code that can reach back
     * here, and mutating the list under its own iterator crashes the server tick loop.
     */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        for (Grab grab : List.copyOf(ACTIVE)) {
            if (!ACTIVE.contains(grab)) continue;

            if (!advance(grab)) {
                ACTIVE.remove(grab);
            }
        }
    }

    /** @return false once the wave has arrived and should be dropped */
    private static boolean advance(Grab grab) {
        // The wall only moves every so many ticks, but it goes on hauling every tick —
        // something that walks into a wave between steps should still be taken by it.
        grab.age++;
        if (grab.age % ADVANCE_EVERY != 0) {
            haul(grab);
            return true;
        }

        // Whichever way this wave was pointed. Earth grab counts DOWN as it comes
        // home; Stone walls counts UP as it goes out.
        grab.front += grab.step();

        if (grab.past()) {
            collapse(grab);
            return false;
        }

        grab.slices.addLast(raiseSlice(grab, grab.front));

        // Take up the back of the wave so it travels instead of leaving a wall behind.
        while (grab.slices.size() > BODY_DEPTH) {
            lowerSlice(grab, grab.slices.removeFirst());
        }

        haul(grab);
        return true;
    }

    /**
     * Lays one slice of the wall.
     *
     * Each column finds its own footing so the wave rides over the ground rather than
     * ploughing through it, and only air is ever replaced.
     */
    private static List<BlockPos> raiseSlice(Grab grab, int distance) {
        List<BlockPos> placed = new ArrayList<>();

        for (int side = -grab.halfWidth; side <= grab.halfWidth; side++) {
            Vec3 spot = grab.origin
                    .add(grab.forward.scale(distance))
                    .add(grab.across.scale(side));

            // A grounded wave finds its own footing so it rides over terrain; a thrown
            // one simply hangs where it was aimed, which is what lets it fly.
            BlockPos ground = grab.grounded
                    ? EarthWorks.surfaceUnder(
                            grab.level, BlockPos.containing(spot), UP_SCAN, DOWN_SCAN)
                    : BlockPos.containing(spot);
            if (ground == null) continue;

            for (int h = 0; h < HEIGHT; h++) {
                BlockPos pos = ground.above(h);
                if (!grab.level.getBlockState(pos).isAir()) break;

                grab.level.setBlock(pos, grab.material, QUIET);
                placed.add(pos);
            }

            grab.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, grab.material),
                    spot.x, ground.getY() + 0.5, spot.z, 3, 0.3, 0.2, 0.3, 0.02);
        }

        return placed;
    }

    /** Takes a slice back out, leaving anything that is no longer ours alone. */
    private static void lowerSlice(Grab grab, List<BlockPos> slice) {
        for (BlockPos pos : slice) {
            if (!grab.level.getBlockState(pos).equals(grab.material)) continue;
            grab.level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        }
    }

    /** Drops the whole wave at once, when it has come as far as it goes. */
    private static void collapse(Grab grab) {
        for (List<BlockPos> slice : grab.slices) {
            lowerSlice(grab, slice);
        }
        grab.slices.clear();

        grab.level.playSound(null, BlockPos.containing(grab.origin), SoundEvents.STONE_PLACE,
                SoundSource.PLAYERS, 1.4F, 0.4F);
    }

    /**
     * Hauls everything the wall is passing over back towards the bender.
     *
     * Applied every tick rather than once, so something caught early is carried the
     * whole way in rather than given one shove and left behind by the wave.
     */
    private static void haul(Grab grab) {
        Vec3 centre = grab.origin.add(grab.forward.scale(grab.front));

        AABB caught = new AABB(centre, centre)
                .inflate(grab.halfWidth + 1.0, HEIGHT + 1.0, grab.halfWidth + 1.0);

        ServerPlayer owner = grab.level.getServer().getPlayerList().getPlayer(grab.ownerId);

        for (Entity target : grab.level.getEntities(owner, caught)) {
            if (!(target instanceof LivingEntity living) || !living.isAlive()) continue;

            // Along the wave's own direction of travel, whichever way that is: Earth
            // grab drags things home, Stone walls shoves them away, and Crush pushes
            // them at each other from both sides.
            Vec3 push = grab.travel().scale(DRAG_SPEED);
            living.setDeltaMovement(push.x, DRAG_LIFT, push.z);

            // Players ignore server-side velocity unless it is explicitly pushed to
            // them. A mob is simulated on the server and needs no packet.
            if (living instanceof Player) {
                living.hurtMarked = true;
            }

            living.fallDistance = 0.0F;
        }
    }

    /**
     * Drops every wave in a level that is going away, clearing its blocks first.
     * Nothing else holds these, so without it a wall would be left standing.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(grab -> {
            if (grab.level != level) return false;
            collapse(grab);
            return true;
        });
    }
}
