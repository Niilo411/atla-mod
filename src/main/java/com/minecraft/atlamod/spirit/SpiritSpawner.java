package com.minecraft.atlamod.spirit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import com.minecraft.atlamod.spirit.island.IslandStyle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Spawns the Spirit World's wildlife, by hand rather than through vanilla's spawner.
 *
 * WHY NOT BIOME SPAWNERS: a biome's {@code spawners} list only says an entity MAY appear
 * there. Whether it actually does is decided by that entity type's own spawn rules,
 * registered in Java, and most of what belongs here fails them. Allays, vexes and
 * sniffers do not spawn naturally in vanilla at all. Endermen and piglins need darkness,
 * and this dimension is held at noon under an open sky. The only way to make them pass
 * would be to change those rules globally, which would change the Overworld too — a
 * mod that quietly made endermen spawn in daylight everywhere would be a bad neighbour.
 *
 * So the spawning is ours: it picks positions near players, reads the biome, and places
 * what belongs there. Nothing outside this dimension is touched, and the odds are exactly
 * what the tables below say rather than whatever survives vanilla's checks.
 */
public final class SpiritSpawner {

    /**
     * How often a spawning attempt runs at all.
     *
     * THIS IS THE RATE. The weights below decide only WHICH mob a successful attempt
     * produces, never how many attempts there are — doubling every weight would change
     * nothing at all, since an attempt yields exactly one mob whatever the numbers are.
     * Halving this interval is what actually doubles how often something appears.
     *
     * Down from 100 originally, then 60, now 30.
     */
    private static final int INTERVAL = 30;

    /** How many positions are tried per player per attempt. */
    private static final int ATTEMPTS = 8;

    /** How far from a player something may appear. Never right on top of them. */
    private static final int MIN_RANGE = 20;
    private static final int MAX_RANGE = 60;

    /**
     * How many of our mobs may be near one player before we stop adding more.
     *
     * THIS IS THE OTHER HALF OF THE RATE, and the one that decides the steady state. A
     * faster interval only fills the cap sooner; the population it settles at is this
     * number. So doubling how many mobs are actually about means doubling both, which is
     * why this went from 26 to 52 alongside the interval.
     */
    private static final int CAP = 52;
    private static final int CAP_RADIUS = 64;

    /** One entry in a biome's table. Weight is relative within that table. */
    private record Spawn(EntityType<?> type, int weight) {
    }

    /**
     * What appears everywhere, whatever the island.
     *
     * Vexes were raised from 2 to 24, which is exactly the weight allays used to carry —
     * so a vex now turns up about as often as an allay did before. Allays went to 60 on
     * top of that, and the shorter {@link #INTERVAL} lifts both in absolute terms rather
     * than just trading one for the other.
     */
    private static final List<Spawn> EVERYWHERE = List.of(
            new Spawn(EntityType.ALLAY, 60),
            new Spawn(EntityType.VEX, 24)
    );

    private static final List<Spawn> END = List.of(new Spawn(EntityType.ENDERMAN, 10));

    private static final List<Spawn> NETHER = List.of(
            new Spawn(EntityType.ZOMBIFIED_PIGLIN, 8),
            new Spawn(EntityType.PIGLIN, 6)
    );

    /** Shared by the plain overworld islands and the crimson mountains. */
    private static final List<Spawn> PASTURE = List.of(
            new Spawn(EntityType.PIG, 7),
            new Spawn(EntityType.SHEEP, 7),
            new Spawn(EntityType.COW, 7)
    );

    /**
     * The warped swamp's own.
     *
     * The SNIFFER's weight was halved from 2 to 1 at the same time the interval was
     * halved, which is what leaves it alone while everything else doubles. Twice as many
     * attempts at half the share is the same number of sniffers — they are meant to stay
     * the rare thing worth stopping for, and doubling them would have made an ancient
     * curiosity into livestock.
     */
    private static final List<Spawn> SWAMP = List.of(
            new Spawn(EntityType.FROG, 6),
            new Spawn(EntityType.SNIFFER, 1),
            new Spawn(EntityType.SHEEP, 5),
            new Spawn(EntityType.CHICKEN, 6),
            new Spawn(EntityType.COW, 5)
    );

    private SpiritSpawner() {
    }

    /** Called every tick for the Spirit World; does nothing on all but one tick in thirty. */
    public static void tick(ServerLevel level) {
        if (!SpiritWorld.isSpiritWorld(level)) return;
        if (level.getGameTime() % INTERVAL != 0) return;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            trySpawnNear(level, player);
        }
    }

    private static void trySpawnNear(ServerLevel level, ServerPlayer player) {
        if (crowded(level, player)) return;

        RandomSource random = level.random;

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            BlockPos pos = groundNear(level, player, random);
            if (pos == null) continue;

            EntityType<?> type = pick(level, pos, random);
            if (type == null) continue;
            if (type == EntityType.ALLAY && tooManyAllays(level, player)) continue;

            type.spawn(level, pos, MobSpawnType.NATURAL);
            return;
        }
    }

    /**
     * A standable block near the player, or null.
     *
     * The heightmap is what makes this cheap in a dimension that is mostly empty: a
     * column with no island in it reports the bottom of the world, so open void is
     * rejected without a single block being read. The chunk has to be loaded first —
     * asking about an ungenerated column would drag it into existence, which is exactly
     * the cascade the island feature is so careful to avoid.
     */
    private static BlockPos groundNear(ServerLevel level, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = MIN_RANGE + random.nextDouble() * (MAX_RANGE - MIN_RANGE);

        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;

        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, 0, z));

        // Open void: the heightmap fell through to the bottom of the world.
        if (surface.getY() <= level.getMinBuildHeight() + 4) return null;

        // Room to stand, and something solid underfoot.
        if (!level.getBlockState(surface).isAir()) return null;
        if (!level.getBlockState(surface.above()).isAir()) return null;
        if (!level.getBlockState(surface.below()).isSolidRender(level, surface.below())) return null;

        return surface;
    }

    /** Picks an entity for this position's biome, by weight. */
    private static EntityType<?> pick(ServerLevel level, BlockPos pos, RandomSource random) {
        Holder<Biome> biome = level.getBiome(pos);

        List<Spawn> local = localTable(biome);
        int total = weightOf(EVERYWHERE) + weightOf(local);
        if (total <= 0) return null;

        int roll = random.nextInt(total);

        for (Spawn spawn : EVERYWHERE) {
            roll -= spawn.weight();
            if (roll < 0) return spawn.type();
        }
        for (Spawn spawn : local) {
            roll -= spawn.weight();
            if (roll < 0) return spawn.type();
        }
        return null;
    }

    /**
     * What this biome adds on top of the allays and vexes.
     *
     * Keyed on the island's FAMILY rather than its individual biome, so all four nether
     * variants share one table and all four overworld climates share another — and a
     * variant added later is covered the moment it names its family, with nothing here to
     * remember to update.
     *
     * The wasteland and the open void between islands get nothing of their own, which is
     * the point of the wasteland: nothing lives there but what drifts over.
     */
    private static List<Spawn> localTable(Holder<Biome> biome) {
        IslandStyle style = IslandStyle.forBiome(biome);
        if (style == null) return List.of();

        return switch (style.family()) {
            case END -> END;
            case NETHER -> NETHER;
            case OVERWORLD, CRIMSON -> PASTURE;
            case WARPED -> SWAMP;
            case WASTELAND -> List.of();
        };
    }

    private static int weightOf(List<Spawn> table) {
        int total = 0;
        for (Spawn spawn : table) total += spawn.weight();
        return total;
    }

    /**
     * Whether there is already enough life around this player.
     *
     * Counts every living thing but players, rather than only what we put there. A cap
     * that ignored the herd a player had led home, or the mobs they brought with them,
     * would keep adding to a crowd it could not see.
     */
    /** How many allays may be near one player. See {@link #tooManyAllays}. */
    private static final int ALLAY_CAP = 18;

    /**
     * Allays need a tighter limit than everything else, because they NEVER GO AWAY.
     *
     * Vanilla's {@code Allay.removeWhenFarAway} returns false — they are meant to be pets
     * — so unlike every other mob here they are not cleaned up once a player wanders off.
     * They are also the commonest thing in the table, so without a limit of their own a
     * long exploring session would leave a permanent trail of them across the dimension.
     *
     * This bounds how thick they get in any one place, which is what is actually visible.
     * It doubled to 18 with everything else, so allays stay the commonest sight.
     * It does NOT bound the total across a large explored area; if that ever becomes a
     * problem the answer is a sweep that removes ones far from any player, since there is
     * no way to make an individual allay despawn on its own.
     */
    private static boolean tooManyAllays(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(CAP_RADIUS);

        return level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.getType() == EntityType.ALLAY).size() >= ALLAY_CAP;
    }

    private static boolean crowded(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(CAP_RADIUS);

        return level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> !(entity instanceof ServerPlayer)).size() >= CAP;
    }
}
