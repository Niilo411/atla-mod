package com.minecraft.atlamod;

import com.minecraft.atlamod.spirit.island.IslandFamily;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything about this mod a player is allowed to change, and the one place that holds it.
 *
 * WHY A SERVER CONFIG RATHER THAN A COMMON ONE. Every value here is either a world
 * generation rule or a rule about what a bender may do, and both of those belong to a
 * WORLD rather than to an installation. A common config lives beside the game and would be
 * shared by every save, so turning the ore down for one world would quietly retune every
 * other one — and on a server the client's copy would be its own, so two people could
 * disagree about which abilities exist. SERVER answers both: the file sits in the save's
 * own {@code serverconfig/} directory, and FML syncs it to every client on connection, so
 * the menu a player sees is the server's rules rather than their own.
 *
 * To set the defaults for FUTURE worlds rather than the current one, put a file at
 * {@code config/atlamod-server.toml} — FML copies that into each new save.
 *
 * WORLD GENERATION VALUES APPLY TO CHUNKS NOT YET GENERATED. Islands, ore and shrines are
 * all pure functions of position (see {@link com.minecraft.atlamod.spirit.island.SpiritIslands}),
 * worked out once when a chunk is first visited and then saved like any other block.
 * Changing a rate mid-world therefore leaves everything already explored exactly as it was
 * and only steers what is found from here on, which will show as a seam at the boundary.
 * That is inherent to changing generation on a live world rather than a fault, and it is
 * why these are NOT marked {@code worldRestart()}: a restart would not heal the seam
 * either, and pretending otherwise would only cost the player a reload.
 *
 * THE DEFAULTS REPRODUCE THE OLD HARD-CODED BEHAVIOUR EXACTLY, down to the same island in
 * the same place. That is a deliberate property rather than a happy accident — see
 * {@code SpiritIslands.styleFor} for the one case where it took real care to keep.
 *
 * READ THROUGH THE CACHED GETTERS, NEVER THROUGH THE ModConfigSpec VALUES. Some of these
 * are asked per BLOCK on worldgen worker threads, where a map lookup and a Preconditions
 * check apiece is real cost; and a ConfigValue throws outright when its config is not
 * loaded, which is the normal state at the title screen. The volatile fields below are
 * refreshed from the events at the bottom of this class and answer instantly in every state.
 */
@EventBusSubscriber(modid = Atlamod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AtlaConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==========================================================================
    //  Islands — how much land there is
    // ==========================================================================

    public static final ModConfigSpec.IntValue ISLAND_SPACING = BUILDER
            .comment("How far apart the Spirit World's floating islands sit, in blocks.",
                    "One main island and its satellites are placed per square of this size, so a",
                    "SMALLER number means MORE islands and less open void between them.",
                    "Islands are up to 250 blocks across, so much below 260 has them merging",
                    "into their neighbours.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("islandSpacing", 260, 140, 800);

    public static final ModConfigSpec.IntValue SATELLITE_COUNT = BUILDER
            .comment("How many small islands orbit each main island.",
                    "0 leaves the dimension as widely spaced single islands.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("satellitesPerIsland", 3, 0, 8);

    // ==========================================================================
    //  Biomes — the relative weight of each family of island
    // ==========================================================================

    /**
     * One weight per {@link IslandFamily}, indexed by its ordinal.
     *
     * WEIGHTS RATHER THAN PERCENTAGES, so the numbers never have to be kept adding up to
     * anything. A family at 2 is twice as likely as one at 1; a family at 0 never appears
     * at all, which is the honest way to switch a kind of island off.
     *
     * Every weight defaulting to 1 is what keeps existing worlds intact — see
     * {@code SpiritIslands.styleFor}.
     */
    public static final ModConfigSpec.IntValue[] FAMILY_WEIGHTS;

    static {
        BUILDER.comment("How common each kind of island is, relative to the others.",
                        "These are WEIGHTS, not percentages: they need not add up to anything.",
                        "A family at 2 is twice as likely as one at 1, and one at 0 never generates.",
                        "Setting every weight to 0 is refused - that would be a dimension with no",
                        "islands at all - and falls back to treating them all as equal.",
                        "Affects chunks that have not been generated yet.")
                .push("biomeWeights");

        IslandFamily[] families = IslandFamily.values();
        FAMILY_WEIGHTS = new ModConfigSpec.IntValue[families.length];

        for (IslandFamily family : families) {
            FAMILY_WEIGHTS[family.ordinal()] = BUILDER
                    .comment(family.description())
                    .defineInRange(family.name().toLowerCase(Locale.ROOT), 1, 0, 100);
        }

        BUILDER.pop();
    }

    // ==========================================================================
    //  Spirit ore
    // ==========================================================================

    public static final ModConfigSpec.IntValue ORE_VEIN_CHANCE = BUILDER
            .comment("How many 4x4x4 cells of island rock in a THOUSAND carry a vein of spirit ore.",
                    "11 is the shipped rate and works out at roughly 280 buried blocks in a",
                    "full-sized island. 170 would be iron's density.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("veinChanceInThousand", 11, 0, 1000);

    public static final ModConfigSpec.IntValue ORE_SURFACE_CHANCE = BUILDER
            .comment("Of the veins that exist, how many in a THOUSAND may break the surface.",
                    "THIS IS A SECOND ROLL ON TOP OF veinChanceInThousand, not a rate of its own,",
                    "so lowering the vein chance already takes visible ore down with it.",
                    "62 is the shipped rate and leaves about ONE visible block per island, so most",
                    "islands show none at all. A vein refused the surface simply stays buried.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("surfaceChanceInThousand", 62, 0, 1000);

    public static final ModConfigSpec.IntValue ORE_VEIN_MIN = BUILDER
            .comment("The fewest blocks a vein of spirit ore is made of.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("veinSizeMin", 2, 1, 8);

    public static final ModConfigSpec.IntValue ORE_VEIN_MAX = BUILDER
            .comment("The most blocks a vein of spirit ore is made of.",
                    "A vein cannot cross the 4x4x4 cell it grows in, so veins near the top of this",
                    "range start to look cut off along cell boundaries.",
                    "A value below veinSizeMin is treated as equal to it.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("veinSizeMax", 4, 1, 8);

    public static final ModConfigSpec.IntValue ORE_XP = BUILDER
            .comment("Bending XP granted for mining one block of spirit ore.",
                    "15 is the shipped figure, deliberately a mid-tier ability's reward.",
                    "At 200 XP to a level that is about thirteen blocks per level.",
                    "Applies immediately, to ore already in the ground as well as to new ore.")
            .defineInRange("xpPerBlock", 15, 0, 500);

    // ==========================================================================
    //  Structures
    // ==========================================================================

    public static final ModConfigSpec.IntValue SHRINE_CHANCE = BUILDER
            .comment("The chance in a HUNDRED that any one island carries a spirit shrine.",
                    "Rolled per island including satellites, so a cell offers four chances.",
                    "6 is the shipped rate and works out at about one shrine every 540 blocks",
                    "travelled. A few of those are lost to islands where no flat enough site",
                    "could be found, which is why fewer are placed than are rolled.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("shrineChanceInHundred", 6, 0, 100);

    public static final ModConfigSpec.IntValue TEMPLE_CHANCE = BUILDER
            .comment("The chance in a HUNDRED that a site the game offers for a temple is used.",
                    "This can only make temples RARER than the grid they sit on, never more common:",
                    "the grid itself (one candidate per 40 chunks) lives in the data pack at",
                    "data/atlamod/worldgen/structure_set/spirit_temples.json, and 100 here means",
                    "'use every site offered', which is the shipped behaviour.",
                    "0 is safe - portals fall back to the fixed temple at the world origin.",
                    "Affects chunks that have not been generated yet.")
            .defineInRange("templeChanceInHundred", 100, 0, 100);

    // ==========================================================================
    //  Abilities
    // ==========================================================================

    /**
     * Abilities nobody may use, named by display name and compared without case.
     *
     * A list of what is OFF rather than a switch per ability, which matters for what
     * happens to a save when the mod is updated: an ability added in a later version is
     * simply absent from this list and therefore on, where a table of booleans would need
     * an entry writing for it and would otherwise read as missing.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ABILITIES = BUILDER
            .comment("Abilities that nobody can use, unlock or equip.",
                    "Names are the ones shown in the skill tree, matched ignoring case, for",
                    "example: disabledAbilities = [\"Fire Rain\", \"Tsunami\"]",
                    "A disabled ability is greyed out in the menu and refuses to cast. One already",
                    "bought STAYS bought and simply does nothing until it is enabled again, so no",
                    "levels a player has spent are ever lost.",
                    "Applies immediately; no restart and no new world needed.")
            .defineListAllowEmpty("disabledAbilities", List.of(), () -> "", obj -> obj instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private AtlaConfig() {
    }

    // ==========================================================================
    //  The cached answers. See the class note for why these exist.
    // ==========================================================================

    private static volatile int islandSpacing = 260;
    private static volatile int satellites = 3;
    private static volatile int[] familyWeights = defaultWeights();
    private static volatile int familyWeightTotal = IslandFamily.values().length;
    private static volatile int oreVeinChance = 11;
    private static volatile int oreSurfaceChance = 62;
    private static volatile int oreVeinMin = 2;
    private static volatile int oreVeinMax = 4;
    private static volatile int oreXp = 15;
    private static volatile int shrineChance = 6;
    private static volatile int templeChance = 100;
    private static volatile Set<String> disabled = Set.of();

    /**
     * How many times these settings have changed since the game started.
     *
     * Exists for anything that MEMOISES an answer derived from them — at the time of
     * writing that is only {@code SpiritIslands.Cache}, which remembers where the islands
     * in a cell are. A cached island worked out under the old spacing is not merely stale,
     * it actively disagrees with a freshly computed one, so the cache compares this against
     * the count it was filled under and empties itself when they differ.
     *
     * A COUNTER RATHER THAN A CALLBACK, because the readers are per-thread and short-lived
     * and there is nothing sensible to register with. Comparing a volatile long is free on
     * the path that matters; invalidating from here would need to reach across every
     * worldgen worker thread.
     */
    private static volatile long generation = 0L;

    public static long generation() {
        return generation;
    }

    public static int islandSpacing() {
        return islandSpacing;
    }

    public static int satellites() {
        return satellites;
    }

    /** This family's share of the draw. Indexed by {@link IslandFamily#ordinal()}. */
    public static int familyWeight(int ordinal) {
        return familyWeights[ordinal];
    }

    /** Every weight added up, and never zero — see {@link #refresh}. */
    public static int familyWeightTotal() {
        return familyWeightTotal;
    }

    public static int oreVeinChance() {
        return oreVeinChance;
    }

    public static int oreSurfaceChance() {
        return oreSurfaceChance;
    }

    public static int oreVeinMin() {
        return oreVeinMin;
    }

    /** Never below {@link #oreVeinMin()} — see {@link #refresh}. */
    public static int oreVeinMax() {
        return oreVeinMax;
    }

    public static int oreXp() {
        return oreXp;
    }

    public static int shrineChance() {
        return shrineChance;
    }

    public static int templeChance() {
        return templeChance;
    }

    /**
     * Whether this ability may be used at all.
     *
     * The single question every entry point asks — the dispatcher, the unlock packet, both
     * equip packets and the menu — so there is one answer rather than five that could drift
     * apart. Case-insensitive, because an ability is known by its display name in some
     * places and by its lowercased key in others and both have to match the same line.
     */
    public static boolean abilityEnabled(String name) {
        if (name == null) return true;
        Set<String> off = disabled;

        return off.isEmpty() || !off.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Every disabled ability, lowercased. For the menu, which needs the whole set at once. */
    public static Set<String> disabledAbilities() {
        return disabled;
    }

    // ==========================================================================
    //  Keeping the cache in step
    // ==========================================================================

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) refresh();
    }

    /**
     * Back to the shipped values when the config goes away.
     *
     * Unloading happens when a world closes or a player leaves a server, so the next thing
     * to read these is either a different world or nothing at all. Keeping the last world's
     * numbers would mean a fresh save quietly generating with whatever the previous one was
     * set to, for as long as it took its own config to load.
     */
    @SubscribeEvent
    static void onUnload(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() != SPEC) return;

        islandSpacing = 260;
        satellites = 3;
        familyWeights = defaultWeights();
        familyWeightTotal = IslandFamily.values().length;
        oreVeinChance = 11;
        oreSurfaceChance = 62;
        oreVeinMin = 2;
        oreVeinMax = 4;
        oreXp = 15;
        shrineChance = 6;
        templeChance = 100;
        disabled = Set.of();

        generation++;
    }

    /**
     * Reads every value across into the cache.
     *
     * Public because the config screen calls it the moment it saves: {@code save()} does
     * fire {@link ModConfigEvent.Reloading}, but the file watcher behind it is not
     * instantaneous, and a player who turns an ability off should not be able to cast it in
     * the second afterwards.
     */
    public static void refresh() {
        if (!SPEC.isLoaded()) return;

        islandSpacing = ISLAND_SPACING.get();
        satellites = SATELLITE_COUNT.get();

        int[] weights = new int[FAMILY_WEIGHTS.length];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = FAMILY_WEIGHTS[i].get();
            total += weights[i];
        }

        // Every family switched off would leave the draw with nothing to pick from and the
        // dimension with no islands at all. Treated as "no preference expressed" rather
        // than obeyed, since an empty Spirit World is never what was meant.
        if (total <= 0) {
            weights = defaultWeights();
            total = weights.length;
        }

        familyWeights = weights;
        familyWeightTotal = total;

        oreVeinChance = ORE_VEIN_CHANCE.get();
        oreSurfaceChance = ORE_SURFACE_CHANCE.get();
        oreVeinMin = ORE_VEIN_MIN.get();

        // A maximum under the minimum would leave the vein-size draw with a bound of zero
        // or less, which is an outright crash on a worldgen worker rather than a small
        // vein. Clamped here so nothing downstream has to remember.
        oreVeinMax = Math.max(oreVeinMin, ORE_VEIN_MAX.get());

        oreXp = ORE_XP.get();
        shrineChance = SHRINE_CHANCE.get();
        templeChance = TEMPLE_CHANCE.get();

        Set<String> off = new HashSet<>();
        for (String name : DISABLED_ABILITIES.get()) {
            if (name != null && !name.isBlank()) off.add(name.toLowerCase(Locale.ROOT));
        }
        disabled = Set.copyOf(off);

        // LAST, after every field above it is in place. Anything watching this count
        // reacts by re-reading the values, so raising it first would invite a reader to
        // refill itself from a half-updated set and then believe it was current.
        generation++;
    }

    private static int[] defaultWeights() {
        int[] weights = new int[IslandFamily.values().length];
        Arrays.fill(weights, 1);

        return weights;
    }
}
