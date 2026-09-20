package com.minecraft.atlamod.spirit;

import com.minecraft.atlamod.Atlamod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The Spirit World dimension: its key, and the one temple that stands in it.
 *
 * The dimension itself is data — see data/atlamod/dimension/spirit_world.json and the
 * noise settings, biome and features beside it. All this class holds is the key to
 * reach it by and the handful of questions the rest of the mod asks about it.
 */
public final class SpiritWorld {

    /**
     * Matches data/atlamod/dimension/spirit_world.json.
     *
     * A ResourceKey is just a name — it resolves to nothing if the JSON is missing or
     * fails to load, which is why {@link #level} can return null and every caller has
     * to cope with that rather than assuming the dimension is there.
     */
    public static final ResourceKey<Level> SPIRIT_WORLD = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "spirit_world"));

    /**
     * Where the Spirit World's temple stands.
     *
     * Fixed, and deliberately so: it is the one fixed point in a dimension that is
     * otherwise scattered islands in a void, and a player who walks off the edge needs
     * somewhere findable to come back to. Y is well above the islands so the temple is
     * never buried inside one.
     */
    public static final BlockPos TEMPLE_ORIGIN = new BlockPos(0, 96, 0);

    private SpiritWorld() {
    }

    /** Whether this level IS the Spirit World. */
    public static boolean isSpiritWorld(Level level) {
        return level != null && level.dimension().equals(SPIRIT_WORLD);
    }

    /** The Spirit World itself, or null if the dimension failed to load. */
    public static ServerLevel level(MinecraftServer server) {
        return server == null ? null : server.getLevel(SPIRIT_WORLD);
    }

    /**
     * The Spirit World's temple, building it if it is not there.
     *
     * Checked by looking at the world rather than by remembering in a flag, which means
     * it repairs itself: a temple that was never built, or one whose floor somebody
     * mined out, is simply built again on the next arrival. A flag would have to be
     * persisted, and would be wrong the moment the two disagreed.
     */
    public static TempleStructure.Temple ensureTemple(ServerLevel level) {
        TempleStructure.Temple temple = TempleStructure.isPresentAt(level, TEMPLE_ORIGIN)
                ? TempleStructure.describeAt(TEMPLE_ORIGIN)
                : TempleStructure.placeAt(level, TEMPLE_ORIGIN);

        ensureLit(level, temple);
        return temple;
    }

    /**
     * Makes sure the Spirit World's own portal is burning.
     *
     * This is not decoration — it is the way out. A temple is built with its frame EMPTY,
     * which is right in the Overworld where lighting it is something a bender earns, and
     * completely wrong here: a player who arrived to find an unlit frame would be
     * standing in a sealed room in a dimension with no floor, and no way to bend their
     * way out of it either.
     *
     * Checked and repaired on every arrival rather than only at build time, so a portal
     * that somebody managed to clear out does not strand the next person through.
     * {@link SpiritPortals#activate} is what schedules the one minute closing tick, and
     * it deliberately does not do so here — this portal is meant to burn forever.
     */
    private static void ensureLit(ServerLevel level, TempleStructure.Temple temple) {
        SpiritPortalFrame.Frame frame =
                new SpiritPortalFrame.Frame(temple.portalBottomLeft(), temple.portalAxis());

        for (BlockPos pos : frame.interior()) {
            if (!level.getBlockState(pos).is(com.minecraft.atlamod.Atlamod.SPIRIT_PORTAL.get())) {
                SpiritPortals.activate(level, frame);
                return;
            }
        }
    }
}
