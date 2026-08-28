package com.minecraft.atlamod.abilities.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Every Earth wall currently standing, growing or coming back down.
 *
 * A wall has three lives and the manager owns all of them, because only the first is
 * tied to the bender: it GROWS while the key is held, STANDS for half a minute after
 * they let go, then SINKS. The channel that raises it is long finished by the time
 * the last two happen, so none of it can live on the ability class.
 *
 * Held in a plain static list like Drownings and AirSpouts, so {@link #forgetLevel}
 * has to run when a level goes away or a wall would stand forever and keep a dead
 * ServerLevel reachable with it.
 */
public final class EarthWalls {

    /** Tallest a wall will grow, however long the key is held. */
    public static final int MAX_LAYERS = 7;

    /**
     * One block per second of holding — the ordinary rate, and Earth wall's.
     *
     * A wall is not the only thing raised through here any more, so this is a DEFAULT
     * rather than the rate: Earth pillar comes up half again as fast, on the grounds
     * that one column is a step to stand on and waiting seven seconds for it defeats
     * the point. Each wall carries its own figure — see {@link #begin}.
     */
    public static final int TICKS_PER_LAYER = 20;

    /** How long a finished wall stands before it sinks. */
    private static final int STAND_TICKS = 600; // 30 seconds

    /**
     * Ticks between one layer sinking and the next. Short, so the wall comes down as
     * one collapsing thing rather than seven separate events — each block's own slide
     * takes longer than this, so several are in motion at once.
     */
    private static final int SINK_INTERVAL = 4;

    private static final List<Wall> ACTIVE = new ArrayList<>();

    private EarthWalls() {
    }

    private static final class Wall {
        final ServerLevel level;
        final UUID ownerId;
        final List<BlockPos> surfaces;
        final List<BlockState> materials;

        /** What was actually placed, layer by layer, so it can be taken back exactly. */
        final List<List<BlockPos>> layers = new ArrayList<>();

        /** How long this particular raise takes per layer. See TICKS_PER_LAYER. */
        final int ticksPerLayer;

        boolean growing = true;
        int sinceLayer;
        int standTicks;
        boolean sinking;
        int sinceSink;

        Wall(ServerLevel level, UUID ownerId, List<BlockPos> surfaces, List<BlockState> materials,
             int ticksPerLayer) {
            this.level = level;
            this.ownerId = ownerId;
            this.surfaces = surfaces;
            this.materials = materials;
            this.ticksPerLayer = ticksPerLayer;
        }
    }

    /**
     * Starts a wall growing, with its first layer going up straight away — a press
     * that is over in a moment should still leave one block of wall behind.
     *
     * @return false if there was nowhere to put it, in which case nothing was changed
     */
    public static boolean begin(ServerPlayer player, List<BlockPos> surfaces, List<BlockState> materials,
                                int ticksPerLayer) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (surfaces.isEmpty()) return false;

        // Only one at a time: a second wall while the first is still growing would
        // leave the first one growing forever with nothing to stop it.
        stopGrowing(player);

        Wall wall = new Wall(level, player.getUUID(), surfaces, materials, ticksPerLayer);
        if (!addLayer(wall)) return false;

        ACTIVE.add(wall);
        return true;
    }

    /** The key was let go, or the wall reached its full height. */
    public static void stopGrowing(ServerPlayer player) {
        Wall wall = findGrowing(player.getUUID());
        if (wall == null) return;

        finishGrowing(wall);
    }

    /** Whether this bender still has a wall rising. Drives the channel's canContinue. */
    public static boolean isGrowing(ServerPlayer player) {
        return findGrowing(player.getUUID()) != null;
    }

    /** Runs every wall in the world. Called once per server tick. */
    public static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Wall> walls = ACTIVE.iterator();
        while (walls.hasNext()) {
            Wall wall = walls.next();
            if (!advance(wall, server)) {
                walls.remove();
            }
        }
    }

    /** @return false once the wall is gone and should be dropped */
    private static boolean advance(Wall wall, MinecraftServer server) {
        if (wall.growing) {
            // A bender who logged out or died mid-raise is not coming back to let go
            // of the key, so the wall finishes at whatever height it reached.
            if (server.getPlayerList().getPlayer(wall.ownerId) == null) {
                finishGrowing(wall);
            } else if (++wall.sinceLayer >= wall.ticksPerLayer) {
                wall.sinceLayer = 0;
                if (!addLayer(wall) || wall.layers.size() >= MAX_LAYERS) {
                    finishGrowing(wall);
                }
            }
            return true;
        }

        if (!wall.sinking) {
            if (--wall.standTicks > 0) return true;

            wall.sinking = true;
            wall.sinceSink = 0;
            return true;
        }

        if (++wall.sinceSink < SINK_INTERVAL) return true;
        wall.sinceSink = 0;

        return sinkTopLayer(wall);
    }

    /** Puts one more layer on top of every column that still has room. */
    private static boolean addLayer(Wall wall) {
        int y = wall.layers.size();
        List<BlockPos> placed = new ArrayList<>();

        for (int i = 0; i < wall.surfaces.size(); i++) {
            BlockPos pos = wall.surfaces.get(i).above(y);
            if (EarthWorks.riseInto(wall.level, pos, wall.materials.get(i))) {
                placed.add(pos);
            }
        }

        if (placed.isEmpty()) return false;

        wall.layers.add(placed);
        wall.level.playSound(null, placed.get(0).getX(), placed.get(0).getY(), placed.get(0).getZ(),
                SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F + (y * 0.05F));
        return true;
    }

    /** @return false once the last layer has gone and the wall is finished with */
    private static boolean sinkTopLayer(Wall wall) {
        if (wall.layers.isEmpty()) return false;

        int top = wall.layers.size() - 1;
        List<BlockPos> layer = wall.layers.remove(top);

        for (int i = 0; i < layer.size(); i++) {
            BlockPos pos = layer.get(i);
            EarthWorks.sinkFrom(wall.level, pos, materialAt(wall, pos));
        }

        if (!layer.isEmpty()) {
            BlockPos first = layer.get(0);
            wall.level.playSound(null, first.getX(), first.getY(), first.getZ(),
                    SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.7F, 0.4F);
        }

        return !wall.layers.isEmpty();
    }

    /** Which column a position belongs to, so it sinks as the block it went up as. */
    private static BlockState materialAt(Wall wall, BlockPos pos) {
        for (int i = 0; i < wall.surfaces.size(); i++) {
            BlockPos surface = wall.surfaces.get(i);
            if (surface.getX() == pos.getX() && surface.getZ() == pos.getZ()) {
                return wall.materials.get(i);
            }
        }
        return wall.materials.get(0);
    }

    private static void finishGrowing(Wall wall) {
        wall.growing = false;
        wall.standTicks = STAND_TICKS;
    }

    private static Wall findGrowing(UUID ownerId) {
        for (Wall wall : ACTIVE) {
            if (wall.growing && wall.ownerId.equals(ownerId)) return wall;
        }
        return null;
    }

    /**
     * Takes down every wall in a level that is going away, putting the earth back
     * first. Nothing else holds these, so without it a wall would stand forever and
     * keep a dead ServerLevel alive with it.
     */
    public static void forgetLevel(ServerLevel level) {
        ACTIVE.removeIf(wall -> {
            if (wall.level != level) return false;

            for (List<BlockPos> layer : wall.layers) {
                for (BlockPos pos : layer) {
                    BlockState placed = materialAt(wall, pos);
                    if (level.getBlockState(pos).equals(placed)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
            return true;
        });
    }
}
