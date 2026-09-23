package com.minecraft.atlamod.client;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.abilities.ElementPaths;
import com.minecraft.atlamod.spirit.island.IslandFamily;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The mod's settings, as a screen rather than as a text file.
 *
 * WHY A SCREEN OF OUR OWN rather than NeoForge's generic {@code ConfigurationScreen}, which
 * this replaced and which would have cost nothing to keep. The generic one renders a config
 * exactly as the file is shaped, which is right for a handful of values and wrong for the
 * two things this mod actually needs: a hundred and six ability switches, which it would
 * show as one string list to be typed into by hand, and a set of generation rates whose
 * numbers mean nothing without being told what they buy. Here the abilities are grouped
 * under the element they belong to and toggled by clicking them, and every rate says in
 * plain words what it works out as in the world.
 *
 * IT IS STILL AN ORDINARY {@code ModConfigSpec} UNDERNEATH, which matters more than the
 * screen does. {@code config/atlamod-server.toml} and each save's {@code serverconfig/}
 * copy are still plain TOML with comments, still hand-editable, and a server owner with no
 * client at all can set every one of these without this screen existing. Nothing here is a
 * second way of storing anything — see {@link AtlaConfig}.
 *
 * NOT EVERY SITUATION CAN EDIT, and the screen says which rather than silently ignoring
 * clicks. These are SERVER settings, so they belong to the world that is open: at the title
 * screen there is no world and so nothing to edit, and on somebody else's server they are
 * that server's to set. {@link #editable()} draws exactly the line NeoForge's own screen
 * draws, so the two never disagree about when a value may be changed.
 *
 * VALUES ARE WRITTEN THE MOMENT THEY CHANGE, and the FILE is written when the drag ends or
 * the screen closes. {@code ConfigValue#set} only touches the loaded config in memory, which
 * is what makes dragging a slider cheap; {@code SPEC.save()} is what puts it on disk, and
 * doing that per pixel of a drag would write the file a hundred times for one adjustment.
 */
public class AtlaSettingsScreen extends Screen {

    /** Where to go back to. The mods list, the pause screen, or the bending menu. */
    private final Screen parent;

    private static final String[] TAB_NAMES = { "World", "Items & Chi", "Events", "Abilities" };
    private static final int TAB_W = 82;
    private static final int TAB_H = 20;
    private static final int TAB_Y = 28;

    /** Where the scrolling area begins and how far it stops short of the buttons. */
    private static final int LIST_TOP = TAB_Y + TAB_H + 6;
    private static final int LIST_BOTTOM_GAP = 34;

    /** The widest the rows are allowed to get, so the text does not sprawl on a big window. */
    private static final int MAX_CONTENT = 360;

    private static final int SLIDER_BAR_W = 140;
    private static final int TOGGLE_W = 46;

    private static final int COLOUR_HEADER = 0xFF55AAFF;
    private static final int COLOUR_LABEL = 0xFFFFFFFF;
    private static final int COLOUR_NOTE = 0xFFAAAAAA;
    private static final int COLOUR_OFF_TEXT = 0xFF777777;

    private int activeTab = 0;
    private int scroll = 0;

    /** The rows of the tab being shown, rebuilt whenever the tab or the window changes. */
    private final List<Row> rows = new ArrayList<>();

    /** The slider currently being dragged, or null. */
    private SliderRow dragging = null;

    /**
     * Which abilities are switched off, by DISPLAY name.
     *
     * Held here for the life of the screen rather than read back out of the config on every
     * frame, because it is also what gets written: keeping display names means the TOML says
     * {@code ["Fire Rain"]} rather than {@code ["fire rain"]}, which matters for a file
     * people are expected to open and read. Matching is case-insensitive either way.
     *
     * ENTRIES THAT MATCH NO ABILITY ARE KEPT, not quietly dropped. A config may name an
     * ability from a version of the mod that is not running — an older save, a server
     * rolled back, a typo somebody is midway through fixing — and rewriting the list
     * without them would delete a setting the owner meant, on the strength of this build
     * not recognising the word.
     */
    private final Set<String> disabled = new LinkedHashSet<>();

    public AtlaSettingsScreen(Screen parent) {
        super(Component.literal("Atla Mod Settings"));
        this.parent = parent;
    }

    /**
     * Whether the settings may be changed from here at all.
     *
     * THE SAME THREE TESTS NEOFORGE'S OWN CONFIG SCREEN MAKES, deliberately copied rather
     * than invented: a server config is not loaded when no world is open, is the remote
     * server's business when connected to one, and is shared with the guests when a single
     * player world has been opened to LAN. Drawing the line anywhere else would mean this
     * screen and the Mods-list one disagreed about the same file.
     */
    private static boolean editable() {
        if (!AtlaConfig.SPEC.isLoaded()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && !mc.isSingleplayer()) return false;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer().isPublished()) return false;

        return true;
    }

    /** Why it cannot be changed, in one line, or null when it can. */
    private static String blockedReason() {
        if (!AtlaConfig.SPEC.isLoaded()) {
            return "These belong to a world. Open a world to change them.";
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && !mc.isSingleplayer()) {
            return "This server's settings. Only the server owner can change them.";
        }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer().isPublished()) {
            return "Cannot be changed while the world is open to LAN.";
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        // Read once per opening, not per frame. Reopening is what picks up a change made
        // from outside, which is the same rule every other screen in the mod follows.
        if (disabled.isEmpty()) loadDisabled();

        int bottom = this.height - 26;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 4, bottom, 110, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset this tab"), b -> resetTab())
                .bounds(this.width / 2 - 114, bottom, 110, 20).build());

        buildRows();
    }

    /**
     * Takes the config's list and turns it into display names.
     *
     * Anything the list names that this build has no ability for is carried through as it
     * was written — see the field note on why.
     */
    private void loadDisabled() {
        Set<String> off = AtlaConfig.disabledAbilities();
        Set<String> matched = new LinkedHashSet<>();

        for (String ability : everyAbility()) {
            if (off.contains(ability.toLowerCase(Locale.ROOT))) {
                disabled.add(ability);
                matched.add(ability.toLowerCase(Locale.ROOT));
            }
        }

        for (String unknown : off) {
            if (!matched.contains(unknown)) disabled.add(unknown);
        }
    }

    /** Every ability in every tree, in the order the tabs show them. */
    private static List<String> everyAbility() {
        List<String> all = new ArrayList<>();

        for (String element : ElementPaths.bendable()) {
            for (String ability : abilitiesOf(element)) {
                if (!all.contains(ability)) all.add(ability);
            }
        }
        return all;
    }

    /**
     * One element's abilities, across all four paths and the centre.
     *
     * The centre is included because it is a real ability that a player buys and uses — it
     * simply belongs to no path. Leaving it out would make it the one thing in the tree
     * that could not be switched off.
     */
    private static List<String> abilitiesOf(String element) {
        List<String> found = new ArrayList<>();

        for (String[] path : ElementPaths.all(element)) {
            for (String ability : path) {
                if (!found.contains(ability)) found.add(ability);
            }
        }
        for (String ability : ElementPaths.centre(element)) {
            if (!found.contains(ability)) found.add(ability);
        }
        return found;
    }

    // ==========================================================================
    //  The rows
    // ==========================================================================

    private void buildRows() {
        rows.clear();

        switch (activeTab) {
            case 0 -> buildWorldRows();
            case 1 -> buildItemRows();
            case 2 -> buildEventRows();
            default -> buildAbilityRows();
        }

        clampScroll();
    }

    /**
     * The three world events.
     *
     * EVERY MULTIPLIER IS A PERCENTAGE OF NORMAL, which the note at the top says once
     * rather than every row repeating it. 100 leaves a figure untouched, which is also
     * how an event is turned into pure scenery without switching it off.
     */
    private void buildEventRows() {
        rows.add(new NoteRow("Percentages of normal: 100 changes nothing, 50 halves, 200 doubles."));
        rows.add(new NoteRow("Worked out from the world clock, so no new world is needed."));

        rows.add(new HeaderRow("Blood Moon — every 3rd night, waterbending"));
        rows.add(new ToggleValueRow("Event happens", AtlaConfig.BLOOD_MOON_ENABLED));
        rows.add(new SliderRow("How often", AtlaConfig.BLOOD_MOON_PERIOD_DAYS, 1, 60,
                AtlaSettingsScreen::describeDays,
                "How many days apart blood moons fall."));
        rows.add(new SliderRow("How long it lasts", AtlaConfig.BLOOD_MOON_DURATION_TICKS, 20, 24000,
                AtlaSettingsScreen::describeTicks,
                "How long a blood moon runs, starting at dusk. 11000 ticks (9m10s) is dusk"
                        + " to dawn, a whole night — a duration that outlasts the time left"
                        + " before dawn simply runs into daylight rather than being cut short."));
        rows.add(eventSlider("Cooldowns", AtlaConfig.BLOOD_MOON_COOLDOWN,
                "Waterbending cooldowns while the blood moon is up."));
        rows.add(eventSlider("Charge times", AtlaConfig.BLOOD_MOON_CHARGE,
                "Waterbending charge and wind-up times while the blood moon is up."));
        rows.add(eventSlider("Chi costs", AtlaConfig.BLOOD_MOON_CHI,
                "Waterbending chi costs while the blood moon is up."));
        rows.add(eventSlider("Damage", AtlaConfig.BLOOD_MOON_DAMAGE,
                "Waterbending damage while the blood moon is up."));

        rows.add(new HeaderRow("Sozin's Comet — every 6th day, firebending"));
        rows.add(new ToggleValueRow("Event happens", AtlaConfig.COMET_ENABLED));
        rows.add(new SliderRow("How often", AtlaConfig.COMET_PERIOD_DAYS, 1, 60,
                AtlaSettingsScreen::describeDays,
                "How many days apart the comet falls."));
        rows.add(new SliderRow("How long it lasts", AtlaConfig.COMET_DURATION_TICKS, 20, 24000,
                AtlaSettingsScreen::describeTicks,
                "How long the comet's day runs, starting at midnight. 24000 ticks (20m) is"
                        + " the shipped figure — the whole day, overhead from midnight to"
                        + " midnight."));
        rows.add(eventSlider("Cooldowns", AtlaConfig.COMET_COOLDOWN,
                "Firebending cooldowns while the comet is overhead."));
        rows.add(eventSlider("Charge times", AtlaConfig.COMET_CHARGE,
                "Firebending charge and wind-up times while the comet is overhead."));
        rows.add(eventSlider("Chi costs", AtlaConfig.COMET_CHI,
                "Firebending chi costs while the comet is overhead. 25% is the shipped"
                        + " figure — the comet is meant to feel like fire costing nothing."));
        rows.add(eventSlider("Damage", AtlaConfig.COMET_DAMAGE,
                "Firebending damage while the comet is overhead."));

        rows.add(new HeaderRow("Day of Black Sun — every 12th day, 6 minutes at noon"));
        rows.add(new ToggleValueRow("Event happens", AtlaConfig.BLACK_SUN_ENABLED));
        rows.add(new SliderRow("How often", AtlaConfig.BLACK_SUN_PERIOD_DAYS, 1, 60,
                AtlaSettingsScreen::describeDays,
                "How many days apart the eclipse falls."));
        rows.add(new SliderRow("How long it lasts", AtlaConfig.BLACK_SUN_DURATION_TICKS, 20, 24000,
                AtlaSettingsScreen::describeTicks,
                "How long the eclipse runs, centred on noon. 7200 ticks (6m) is the shipped"
                        + " figure."));
        rows.add(new ToggleValueRow("Firebending goes out", AtlaConfig.BLACK_SUN_DISABLES_FIRE));
        rows.add(new NoteRow("Turn that off to soften it: the four below apply instead."));
        rows.add(eventSlider("Cooldowns", AtlaConfig.BLACK_SUN_COOLDOWN,
                "Firebending cooldowns during the eclipse. Only used when firebending is"
                        + " not switched off outright."));
        rows.add(eventSlider("Charge times", AtlaConfig.BLACK_SUN_CHARGE,
                "Firebending charge and wind-up times during the eclipse. Only used when"
                        + " firebending is not switched off outright."));
        rows.add(eventSlider("Chi costs", AtlaConfig.BLACK_SUN_CHI,
                "Firebending chi costs during the eclipse. Only used when firebending is"
                        + " not switched off outright."));
        rows.add(eventSlider("Damage", AtlaConfig.BLACK_SUN_DAMAGE,
                "Firebending damage during the eclipse. Only used when firebending is not"
                        + " switched off outright."));

        rows.add(new HeaderRow("Spirit World — Low-Gravity Tide"));
        rows.add(new ToggleValueRow("Tide happens", AtlaConfig.SPIRIT_TIDE_ENABLED));
        rows.add(new SliderRow("How often", AtlaConfig.SPIRIT_TIDE_PERIOD_MINUTES, 1, 120,
                AtlaSettingsScreen::describeMinutes,
                "How often the tide comes in, in real minutes."));
        rows.add(new SliderRow("How long it lasts", AtlaConfig.SPIRIT_TIDE_DURATION_MINUTES, 1, 120,
                AtlaSettingsScreen::describeMinutes,
                "How long the tide stays once it comes in, out of each period above. A"
                        + " duration at or past the period leaves it running permanently"
                        + " rather than cycling."));
        rows.add(new SliderRow("Gravity while it's in", AtlaConfig.SPIRIT_TIDE_GRAVITY_PERCENT, 0, 100,
                value -> value + "%" + (value == 100 ? "  (no change)" : value == 0 ? "  (weightless)" : ""),
                "How much of normal gravity is left during the tide. 40% is the shipped"
                        + " figure — a floaty, drifting fall. 0% is true weightlessness;"
                        + " 100% makes the tide purely cosmetic."));
        rows.add(new ToggleValueRow("Falling is free while it's in", AtlaConfig.SPIRIT_TIDE_NO_FALL_DAMAGE));
        rows.add(new NoteRow("Independent of the gravity above — a lighter fall can still hurt"
                + " if this is off."));
        rows.add(new ToggleValueRow("Also lightens mobs", AtlaConfig.SPIRIT_TIDE_AFFECTS_MOBS));
        rows.add(new NoteRow("Off by default. On, hostile and passive mobs drift and land as"
                + " gently as a bender does."));
    }

    /** One event multiplier, all of which share a range and a readout. */
    private SliderRow eventSlider(String label, ModConfigSpec.IntValue value, String tooltip) {
        return new SliderRow(label, value, 1, 400,
                percent -> percent == 100 ? "unchanged" : percent + "%", tooltip);
    }

    private void buildItemRows() {
        rows.add(new HeaderRow("Spirit Armor"));
        rows.add(new NoteRow("Applies immediately, to armor already being worn."));
        rows.add(new SliderRow("Chi regen per piece", AtlaConfig.ARMOR_REGEN_PER_PIECE, 0, 5000,
                AtlaSettingsScreen::describeArmorRegen,
                "How much faster Chi regenerates for each piece of Spirit Armor worn."
                        + " The second figure is what a FULL SET takes to fill an empty bar,"
                        + " which is what the shipped 46.5% was actually chosen for:"
                        + " 100 seconds unaided, 35 in a full set."));

        rows.add(new HeaderRow("Spirit Shrines"));
        rows.add(new SliderRow("Max Chi per shrine", AtlaConfig.SHRINE_CHI, 0, 5000,
                value -> value == 0 ? "nothing" : "+" + value + " max Chi",
                "What one shrine grants, permanently. Flat rather than a multiplier, so it"
                        + " is worth the same at level 20 as at level 1. The Chi bar's colour"
                        + " counts shrines by dividing by this, so changing it on a world"
                        + " where shrines have been used changes what colour it reports —"
                        + " which is cosmetic, no Chi is lost."));

        rows.add(new HeaderRow("Waterbending supply"));
        rows.add(new SliderRow("Canteen capacity", AtlaConfig.CANTEEN_CAPACITY, 1, 200,
                value -> value + " casts",
                "How many waterbending casts a full Water Canteen holds. Applies to canteens"
                        + " that already exist, including ones in a chest — the capacity is"
                        + " answered live rather than stamped on the item when it was crafted."));
        rows.add(new SliderRow("Open water reach", AtlaConfig.WATER_REACH, 0, 64,
                value -> value == 0 ? "must stand in it" : value + " blocks",
                "How far open water counts as a source. Inside this waterbending is free;"
                        + " outside it, a cast drinks a unit from a Water Canteen, and with"
                        + " no canteen it is refused. Raising it makes canteens matter less."));

        rows.add(new HeaderRow("Chi and levels"));
        rows.add(new NoteRow("Maximum Chi is base + (level x per level) + shrines."));
        rows.add(new SliderRow("Base max Chi", AtlaConfig.BASE_MAX_CHI, 100, 20000,
                value -> String.valueOf(value),
                "The pool a level 0 bender has. Several abilities cost 750 or 1000 and are"
                        + " deliberately uncastable until the pool is big enough — Fire Rain,"
                        + " Tsunami, Combustion nuke and an upgraded Lightning Swarm all gate"
                        + " this way, so lowering this locks them further away."));
        rows.add(new SliderRow("Max Chi per level", AtlaConfig.CHI_PER_LEVEL, 0, 5000,
                value -> "+" + value + " a level",
                "How much maximum Chi each bending level adds."));
        rows.add(new SliderRow("Regen delay", AtlaConfig.CHI_REGEN_DELAY, 0, 600,
                AtlaSettingsScreen::describeTicks,
                "How long Chi regeneration is held off after any Chi is spent. This is what"
                        + " stops a cheap ability being paid for by regeneration as fast as it"
                        + " costs. Channels re-arm it every tick, so they drain at full rate"
                        + " and only refill once released."));
        rows.add(new SliderRow("XP per level", AtlaConfig.XP_PER_LEVEL, 1, 2000,
                value -> value + " XP",
                "Bending XP needed for one level, for the ordinary track and the separate"
                        + " bloodbending one. Overflow carries, so a large grant can cross"
                        + " several levels at once."));

        rows.add(new HeaderRow("No Bending — Quick Hands (mining speed)"));
        rows.add(new NoteRow("Each value is a vanilla Haste LEVEL: 0 is Haste I (+20% mining"
                + " speed), 1 is Haste II (+40%), and so on."));
        rows.add(new NoteRow("Keep these low. Haste and a tool's own efficiency multiply the"
                + " SAME speed figure, so a high level on a good pickaxe breaks blocks"
                + " in under a tick."));
        rows.add(new SliderRow("Base (no upgrades)", AtlaConfig.QUICK_HANDS_BASE_AMPLIFIER, 0, 9,
                AtlaSettingsScreen::describeHasteLevel,
                "Haste level with the passive equipped and no upgrades bought."));
        rows.add(new SliderRow("Steady Grip", AtlaConfig.QUICK_HANDS_STEADY_GRIP_AMPLIFIER, 0, 9,
                AtlaSettingsScreen::describeHasteLevel,
                "Haste level with the Steady Grip upgrade."));
        rows.add(new SliderRow("Practiced Swing", AtlaConfig.QUICK_HANDS_PRACTICED_SWING_AMPLIFIER, 0, 9,
                AtlaSettingsScreen::describeHasteLevel,
                "Haste level with the Practiced Swing upgrade."));
        rows.add(new SliderRow("Master's Touch", AtlaConfig.QUICK_HANDS_MASTERS_TOUCH_AMPLIFIER, 0, 9,
                AtlaSettingsScreen::describeHasteLevel,
                "Haste level with the Master's Touch upgrade — the maximum tier."));

        rows.add(new HeaderRow("No Bending — Sword Mastery (sword damage)"));
        rows.add(new NoteRow("Added to whatever the sword and its enchantments already hit for."));
        rows.add(new SliderRow("Base (no upgrades)", AtlaConfig.SWORD_MASTERY_BASE_BONUS_TENTHS, 0, 200,
                AtlaSettingsScreen::describeDamageTenths,
                "Bonus damage with the passive equipped and no upgrades bought."));
        rows.add(new SliderRow("Honed Edge", AtlaConfig.SWORD_MASTERY_HONED_EDGE_BONUS_TENTHS, 0, 200,
                AtlaSettingsScreen::describeDamageTenths,
                "Bonus damage with the Honed Edge upgrade."));
        rows.add(new SliderRow("Practiced Form", AtlaConfig.SWORD_MASTERY_PRACTICED_FORM_BONUS_TENTHS, 0, 200,
                AtlaSettingsScreen::describeDamageTenths,
                "Bonus damage with the Practiced Form upgrade."));
        rows.add(new SliderRow("Master Duelist", AtlaConfig.SWORD_MASTERY_MASTER_DUELIST_BONUS_TENTHS, 0, 200,
                AtlaSettingsScreen::describeDamageTenths,
                "Bonus damage with the Master Duelist upgrade — the maximum tier."));
    }

    private void buildWorldRows() {
        rows.add(new HeaderRow("Islands"));
        rows.add(new NoteRow("Changes apply to Spirit World chunks you have not visited yet."));
        rows.add(new SliderRow("Island spacing", AtlaConfig.ISLAND_SPACING, 140, 800,
                value -> value + " blocks apart",
                "One island and its satellites per square of this size."
                        + " Smaller means more islands and less void."));
        rows.add(new SliderRow("Satellites per island", AtlaConfig.SATELLITE_COUNT, 0, 8,
                value -> value == 0 ? "none" : String.valueOf(value),
                "Small islands orbiting each main one, as stepping stones across the void."));

        rows.add(new HeaderRow("Island biomes"));
        rows.add(new NoteRow("Relative weights. 0 switches a kind of island off entirely."));

        for (IslandFamily family : IslandFamily.values()) {
            rows.add(new SliderRow(family.displayName(), AtlaConfig.FAMILY_WEIGHTS[family.ordinal()], 0, 20,
                    AtlaSettingsScreen::describeWeight, family.description()));
        }

        rows.add(new HeaderRow("Spirit ore"));
        rows.add(new SliderRow("Vein chance", AtlaConfig.ORE_VEIN_CHANCE, 0, 1000,
                value -> value + " in 1000 cells",
                "How much ore is buried in the islands. 11 is the shipped rate,"
                        + " which is about 280 blocks in a full-sized island."));
        rows.add(new SliderRow("Surface chance", AtlaConfig.ORE_SURFACE_CHANCE, 0, 1000,
                value -> value + " in 1000 veins",
                "Of the veins that exist, how many may break the surface where you can see"
                        + " them. This is a second roll on top of the vein chance, so"
                        + " lowering that lowers this too."));
        rows.add(new SliderRow("Smallest vein", AtlaConfig.ORE_VEIN_MIN, 1, 8,
                value -> value + " blocks", "The fewest blocks a vein is made of."));
        rows.add(new SliderRow("Largest vein", AtlaConfig.ORE_VEIN_MAX, 1, 8,
                value -> value + " blocks",
                "The most blocks a vein is made of. A value under the smallest is treated"
                        + " as equal to it."));
        rows.add(new SliderRow("Bending XP per block", AtlaConfig.ORE_XP, 0, 500,
                value -> value + " XP",
                "200 XP is one bending level. This one applies straight away, to ore"
                        + " already in the ground as well."));

        rows.add(new HeaderRow("Structures"));
        rows.add(new SliderRow("Spirit shrines", AtlaConfig.SHRINE_CHANCE, 0, 100,
                value -> value == 0 ? "none" : value + "% of islands",
                "The chance an island carries a shrine. 6% is about one shrine every 540"
                        + " blocks travelled. Find them with /locate structure"
                        + " atlamod:spirit_shrine."));
        rows.add(new SliderRow("Temples", AtlaConfig.TEMPLE_CHANCE, 0, 100,
                value -> value >= 100 ? "every site" : value == 0 ? "none" : value + "% of sites",
                "Temples sit on a grid 40 chunks apart. This can only make them rarer than"
                        + " that, never more common. 0 is safe: portals fall back to the"
                        + " temple at the world origin."));
    }

    private void buildAbilityRows() {
        rows.add(new NoteRow("A disabled ability cannot be cast, bought or equipped."));
        rows.add(new NoteRow("One already bought stays bought and works again when re-enabled."));

        for (String element : ElementPaths.bendable()) {
            List<String> abilities = abilitiesOf(element);
            if (abilities.isEmpty()) continue;

            rows.add(new ElementHeaderRow(element, abilities));
            for (String ability : abilities) {
                rows.add(new ToggleRow(ability));
            }
        }
    }

    /**
     * The armor bonus as a percentage AND as the time it actually buys.
     *
     * THE TIME IS THE POINT. The set was never tuned to a percentage — it was tuned to
     * "a full set fills an empty bar in 35 seconds", and 46.5% is simply the number that
     * lands there. Showing only the percentage would hide the figure anybody adjusting
     * this is actually aiming at.
     */
    private static String describeArmorRegen(int tenths) {
        if (tenths == 0) return "no bonus";

        // Worked out from the value being DRAWN, not from the cached setting — the cache
        // is only refreshed on save, so mid-drag it would report the old time.
        return String.format(java.util.Locale.ROOT, "%.1f%%  (set: %ds)",
                tenths / 10.0F, AtlaConfig.armorSecondsToFull(tenths));
    }

    /** Ticks, with the seconds spelled out beside them. */
    private static String describeTicks(int ticks) {
        if (ticks == 0) return "none";

        return String.format(java.util.Locale.ROOT, "%d ticks (%.1fs)", ticks, ticks / 20.0F);
    }

    /** For an event's period, in days. */
    private static String describeDays(int days) {
        return "every " + days + (days == 1 ? " day" : " days");
    }

    /** For the gravity tide's period and duration, both authored in real minutes. */
    private static String describeMinutes(int minutes) {
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    /** For Quick Hands' tiers, stored as a raw Haste amplifier (0 = Haste I). */
    private static String describeHasteLevel(int amplifier) {
        return "Haste " + (amplifier + 1) + "  (+" + ((amplifier + 1) * 20) + "% speed)";
    }

    /** For Sword Mastery's tiers, stored in TENTHS of a damage point. */
    private static String describeDamageTenths(int tenths) {
        return String.format(java.util.Locale.ROOT, "+%.1f damage", tenths / 10.0F);
    }

    /**
     * What a biome weight works out as against the others, in words.
     *
     * The share is the useful number rather than the weight itself — "3" says nothing on
     * its own, where "3 (25%)" says what a quarter of the islands will be. It has to be
     * worked out fresh on every draw because moving ANY family's slider changes every
     * other family's share.
     *
     * Reads the config directly, so it is guarded: at the title screen nothing is loaded
     * and {@code get()} would throw. There the bare weight is the honest answer, since
     * the shipped values are all equal anyway.
     */
    private static String describeWeight(int weight) {
        if (weight == 0) return "never";
        if (!AtlaConfig.SPEC.isLoaded()) return String.valueOf(weight);

        int total = 0;
        for (IslandFamily family : IslandFamily.values()) {
            total += AtlaConfig.FAMILY_WEIGHTS[family.ordinal()].get();
        }
        if (total <= 0) return String.valueOf(weight);

        return weight + "  (" + Math.round(weight * 100.0F / total) + "%)";
    }

    // ==========================================================================
    //  Drawing
    // ==========================================================================

    /**
     * Everything is drawn here, in an order that is NOT negotiable.
     *
     * THE BACKGROUND MUST COME FIRST, and this is the one thing to know about this method.
     * {@code Screen#renderBackground} ends in {@code renderBlurredBackground}, which is a
     * POST-PROCESS over the whole framebuffer — it blurs whatever has already been drawn,
     * not just the world behind the screen. The obvious shape for a custom screen is to
     * paint your own content and then call {@code super.render} to put the buttons on top,
     * and that shape smears every pixel of the content: the first version of this screen
     * was completely illegible, with only the two buttons sharp, because they are
     * renderables and so were drawn after the blur rather than before it.
     *
     * So {@code super.render} is deliberately NOT called. It is exactly two steps — the
     * background, then the renderables — and they are done separately here with the
     * settings drawn in between, which is the only order in which the backdrop can sit
     * over the blur and the buttons can sit over the backdrop.
     *
     * The tooltip is last for the ordinary reason: it has to cover the buttons.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0xD0101010);

        graphics.drawCenteredString(this.font, "Atla Mod Settings", this.width / 2, 12, 0xFFFFFF);

        drawTabs(graphics);

        int left = contentLeft();
        int right = left + contentWidth();
        int top = LIST_TOP;
        int bottom = this.height - LIST_BOTTOM_GAP;

        String blocked = blockedReason();
        if (blocked != null) {
            graphics.drawCenteredString(this.font, "§e" + blocked, this.width / 2, top + 2, 0xFFFFFF);
            top += 14;
        }

        // A window too short to hold a list at all. Bailing out here rather than pushing
        // an inside-out scissor rectangle, which is not a shape the clip stack expects.
        if (bottom <= top) {
            renderWidgets(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Clipped, so a row halfway off the end of the list is cut rather than drawn over
        // the tabs and the buttons.
        graphics.enableScissor(left - 4, top, right + 4, bottom);

        int y = top - scroll;
        Row hovered = null;

        for (Row row : rows) {
            if (y + row.height >= top && y <= bottom) {
                row.render(graphics, left, right, y, mouseX, mouseY);

                if (mouseY >= y && mouseY < y + row.height && mouseY >= top && mouseY < bottom
                        && mouseX >= left && mouseX <= right) {
                    hovered = row;
                }
            }
            y += row.height;
        }

        graphics.disableScissor();

        drawScrollbar(graphics, right + 6, top, bottom, y + scroll - top);

        renderWidgets(graphics, mouseX, mouseY, partialTick);

        // After the buttons, or they would be drawn over the tooltip.
        if (hovered != null && hovered.tooltip() != null) {
            graphics.renderComponentTooltip(this.font,
                    wrap(hovered.tooltip()), mouseX, mouseY);
        }
    }

    /**
     * The second half of what {@code Screen#render} does, on its own.
     *
     * Split out because the first half — the background — has to run before this screen
     * draws anything, and this half has to run after. See {@link #render}.
     */
    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * A tooltip broken into lines the screen can hold.
     *
     * Wrapped by hand on word boundaries rather than through {@code Font#split}, which
     * returns {@code FormattedCharSequence}s — and {@code renderComponentTooltip} takes
     * {@code Component}s. Converting between the two for a line of grey prose is more
     * machinery than measuring the words is.
     */
    private List<Component> wrap(String text) {
        int limit = Math.min(260, this.width - 40);

        List<Component> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;

            if (this.font.width(candidate) > limit && !current.isEmpty()) {
                lines.add(Component.literal("§7" + current));
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(Component.literal("§7" + current));

        return lines;
    }

    private void drawTabs(GuiGraphics graphics) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = activeTab == i;
            int tx = tabX(i);

            graphics.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H, selected ? 0xFF448844 : 0xFF222222);
            graphics.renderOutline(tx, TAB_Y, TAB_W, TAB_H, selected ? 0xFF55FF55 : 0xFF555555);
            graphics.drawCenteredString(this.font, TAB_NAMES[i], tx + TAB_W / 2, TAB_Y + 6, 0xFFFFFF);
        }
    }

    /**
     * A bar showing how far down the list is, drawn only when there is something to scroll.
     *
     * Worth having rather than leaving the list to look like it simply ends: the abilities
     * tab is well over a hundred rows, and with no bar the only clue that more exists is
     * trying the wheel.
     */
    private void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom, int total) {
        int view = bottom - top;
        if (total <= view) return;

        int barHeight = Math.max(20, view * view / total);
        int barY = top + (int) ((view - barHeight) * (scroll / (double) (total - view)));

        graphics.fill(x, top, x + 4, bottom, 0xFF111111);
        graphics.fill(x, barY, x + 4, barY + barHeight, 0xFF888888);
    }

    private int tabX(int index) {
        int total = TAB_W * TAB_NAMES.length + 6 * (TAB_NAMES.length - 1);
        return this.width / 2 - total / 2 + index * (TAB_W + 6);
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT, this.width - 60);
    }

    private int contentLeft() {
        return this.width / 2 - contentWidth() / 2;
    }

    // ==========================================================================
    //  Input
    // ==========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < TAB_NAMES.length; i++) {
                int tx = tabX(i);
                if (mouseX >= tx && mouseX <= tx + TAB_W && mouseY >= TAB_Y && mouseY <= TAB_Y + TAB_H) {
                    if (activeTab != i) {
                        activeTab = i;
                        scroll = 0;
                        buildRows();
                    }
                    return true;
                }
            }

            if (editable() && rowClicked(mouseX, mouseY)) return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean rowClicked(double mouseX, double mouseY) {
        int left = contentLeft();
        int right = left + contentWidth();
        int top = LIST_TOP + (blockedReason() != null ? 14 : 0);
        int bottom = this.height - LIST_BOTTOM_GAP;

        if (mouseY < top || mouseY >= bottom) return false;

        int y = top - scroll;
        for (Row row : rows) {
            if (mouseY >= y && mouseY < y + row.height) {
                return row.click(left, right, y, mouseX, mouseY);
            }
            y += row.height;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.setFromMouse(contentLeft() + contentWidth() - SLIDER_BAR_W, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // The file is written HERE rather than on every pixel of the drag — see the class
        // note. One adjustment is one write.
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) (scrollY * 16);
        clampScroll();

        return true;
    }

    private void clampScroll() {
        int total = 0;
        for (Row row : rows) total += row.height;

        int top = LIST_TOP + (blockedReason() != null ? 14 : 0);
        int view = this.height - LIST_BOTTOM_GAP - top;

        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - view)));
    }

    // ==========================================================================
    //  Writing back
    // ==========================================================================

    /**
     * Puts the current values on disk and makes them live immediately.
     *
     * {@code refresh()} is called by hand rather than left to the reloading event, because
     * that event comes from a file watcher and is not instant — and somebody who has just
     * switched an ability off should not be able to cast it on the way out of this screen.
     * Refreshing twice is harmless; the second is a re-read of the same values.
     */
    private void save() {
        if (!editable()) return;

        AtlaConfig.SPEC.save();
        AtlaConfig.refresh();
    }

    private void writeDisabled() {
        AtlaConfig.DISABLED_ABILITIES.set(new ArrayList<>(disabled));
        save();
    }

    /** Everything on the tab being shown back to what the mod ships with. */
    private void resetTab() {
        if (!editable()) return;

        // Asks what the rows ARE rather than which tab is open, so a tab added later
        // resets correctly without this method being touched. It went wrong exactly that
        // way once: the ability list was "not tab 0", which stopped being true the moment
        // a second slider tab existed.
        boolean sliders = false;
        for (Row row : rows) {
            if (row instanceof SliderRow slider) {
                slider.reset();
                sliders = true;
            } else if (row instanceof ToggleValueRow toggle) {
                toggle.reset();
                sliders = true;
            }
        }

        if (sliders) {
            save();
        } else {
            disabled.clear();
            writeDisabled();
        }

        buildRows();
    }

    @Override
    public void onClose() {
        save();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==========================================================================
    //  Row kinds
    // ==========================================================================

    private abstract class Row {
        final int height;

        Row(int height) {
            this.height = height;
        }

        abstract void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY);

        boolean click(int left, int right, int y, double mouseX, double mouseY) {
            return false;
        }

        String tooltip() {
            return null;
        }
    }

    /** A section title with a rule under it. */
    private class HeaderRow extends Row {
        private final String title;

        HeaderRow(String title) {
            super(20);
            this.title = title;
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            graphics.drawString(AtlaSettingsScreen.this.font, title, left, y + 6, COLOUR_HEADER);
            graphics.fill(left, y + 17, right, y + 18, 0xFF404040);
        }
    }

    /** A line of explanation, in grey. */
    private class NoteRow extends Row {
        private final String text;

        NoteRow(String text) {
            super(11);
            this.text = text;
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            graphics.drawString(AtlaSettingsScreen.this.font, text, left, y + 1, COLOUR_NOTE);
        }
    }

    /**
     * An element's name, with a switch for the whole element beside it.
     *
     * The bulk toggle is here rather than as a row of its own because an element with
     * thirteen abilities is thirteen clicks to switch off one at a time, and "no
     * firebending on this server" is a far more likely thing to want than any single
     * ability. It reads as "turn them all off" unless they already all are, in which case
     * it turns them all back on — so the button always does something.
     */
    private class ElementHeaderRow extends Row {
        private final String element;
        private final List<String> abilities;

        ElementHeaderRow(String element, List<String> abilities) {
            super(22);
            this.element = element;
            this.abilities = abilities;
        }

        private boolean allOff() {
            for (String ability : abilities) {
                if (!disabled.contains(ability)) return false;
            }
            return true;
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            String name = element.substring(0, 1).toUpperCase(Locale.ROOT) + element.substring(1);

            graphics.drawString(AtlaSettingsScreen.this.font,
                    name + "bending", left, y + 7, COLOUR_HEADER);

            int bx = right - 70;
            boolean off = allOff();

            graphics.fill(bx, y + 4, bx + 70, y + 18, 0xFF222222);
            graphics.renderOutline(bx, y + 4, 70, 14, 0xFF666666);
            graphics.drawCenteredString(AtlaSettingsScreen.this.font,
                    off ? "Enable all" : "Disable all", bx + 35, y + 7, 0xFFDDDDDD);

            graphics.fill(left, y + 19, right, y + 20, 0xFF404040);
        }

        @Override
        boolean click(int left, int right, int y, double mouseX, double mouseY) {
            int bx = right - 70;
            if (mouseX < bx || mouseX > bx + 70) return false;

            if (allOff()) {
                abilities.forEach(disabled::remove);
            } else {
                disabled.addAll(abilities);
            }

            writeDisabled();
            return true;
        }
    }

    /**
     * A plain yes/no setting.
     *
     * Separate from {@link ToggleRow}, which looks almost identical and is a different
     * thing underneath: that one edits a NAME's presence in a list and is what the
     * abilities tab is made of, where this edits one boolean in the config. Merging them
     * would mean a row that had to ask which kind it was on every click.
     */
    private class ToggleValueRow extends Row {
        private final String label;
        private final ModConfigSpec.BooleanValue value;

        ToggleValueRow(String label, ModConfigSpec.BooleanValue value) {
            super(18);
            this.label = label;
            this.value = value;
        }

        /** Falls back to the default when nothing is loaded — see SliderRow.current. */
        private boolean current() {
            return AtlaConfig.SPEC.isLoaded() ? value.get() : value.getDefault();
        }

        void reset() {
            if (AtlaConfig.SPEC.isLoaded()) value.set(value.getDefault());
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            boolean on = current();
            boolean editable = editable();

            graphics.drawString(AtlaSettingsScreen.this.font, label, left + 4, y + 5,
                    editable ? COLOUR_LABEL : COLOUR_OFF_TEXT);

            int bx = right - TOGGLE_W;

            graphics.fill(bx, y + 2, bx + TOGGLE_W, y + 16, on ? 0xFF1E3A1E : 0xFF3A1E1E);
            graphics.renderOutline(bx, y + 2, TOGGLE_W, 14, on ? 0xFF55FF55 : 0xFFFF5555);
            graphics.drawCenteredString(AtlaSettingsScreen.this.font, on ? "Yes" : "No",
                    bx + TOGGLE_W / 2, y + 5, on ? 0xFF99FF99 : 0xFFFF9999);
        }

        @Override
        boolean click(int left, int right, int y, double mouseX, double mouseY) {
            if (mouseX < left || mouseX > right) return false;

            value.set(!current());
            save();
            return true;
        }
    }

    /** One ability, on or off. */
    private class ToggleRow extends Row {
        private final String ability;

        ToggleRow(String ability) {
            super(16);
            this.ability = ability;
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            boolean on = !disabled.contains(ability);

            graphics.drawString(AtlaSettingsScreen.this.font, ability, left + 8, y + 4,
                    on ? COLOUR_LABEL : COLOUR_OFF_TEXT);

            int bx = right - TOGGLE_W;

            graphics.fill(bx, y + 1, bx + TOGGLE_W, y + 14, on ? 0xFF1E3A1E : 0xFF3A1E1E);
            graphics.renderOutline(bx, y + 1, TOGGLE_W, 13, on ? 0xFF55FF55 : 0xFFFF5555);
            graphics.drawCenteredString(AtlaSettingsScreen.this.font, on ? "On" : "Off",
                    bx + TOGGLE_W / 2, y + 3, on ? 0xFF99FF99 : 0xFFFF9999);
        }

        @Override
        boolean click(int left, int right, int y, double mouseX, double mouseY) {
            int bx = right - TOGGLE_W;

            // The whole row is clickable, not just the little button. A sixteen pixel
            // target in a list of a hundred is a lot of precision to ask for, and there is
            // nothing else on the row that a click could have meant.
            if (mouseX < left || mouseX > bx + TOGGLE_W) return false;

            if (!disabled.remove(ability)) disabled.add(ability);

            writeDisabled();
            return true;
        }

        @Override
        String tooltip() {
            // The same line the skill tree shows, so an owner deciding what to switch off
            // is reading exactly what a player reads about it.
            String description = AbilityDescriptions.of(ability);
            return description == null || description.isEmpty() ? null : description;
        }
    }

    /** A number, dragged along a bar. */
    private class SliderRow extends Row {

        private final String label;
        private final ModConfigSpec.IntValue value;
        private final int min;
        private final int max;
        private final java.util.function.IntFunction<String> readout;
        private final String tooltip;

        SliderRow(String label, ModConfigSpec.IntValue value, int min, int max,
                  java.util.function.IntFunction<String> readout, String tooltip) {
            super(22);
            this.label = label;
            this.value = value;
            this.min = min;
            this.max = max;
            this.readout = readout;
            this.tooltip = tooltip;
        }

        /**
         * The value to draw.
         *
         * Falls back to the DEFAULT when the config is not loaded, which is the title
         * screen case: {@code get()} throws outright there, and a settings screen that
         * crashed rather than showing greyed-out shipped values would be a poor way to
         * say "open a world first".
         */
        private int current() {
            return AtlaConfig.SPEC.isLoaded() ? value.get() : value.getDefault();
        }

        void reset() {
            if (AtlaConfig.SPEC.isLoaded()) value.set(value.getDefault());
        }

        void setFromMouse(int barX, double mouseX) {
            double fraction = Math.max(0.0, Math.min(1.0, (mouseX - barX) / (double) SLIDER_BAR_W));
            int wanted = min + (int) Math.round(fraction * (max - min));

            if (wanted != current()) value.set(wanted);
        }

        @Override
        void render(GuiGraphics graphics, int left, int right, int y, int mouseX, int mouseY) {
            boolean on = editable();
            int shown = current();

            graphics.drawString(AtlaSettingsScreen.this.font, label, left + 4, y + 7,
                    on ? COLOUR_LABEL : COLOUR_OFF_TEXT);

            int barX = right - SLIDER_BAR_W;

            graphics.fill(barX, y + 4, barX + SLIDER_BAR_W, y + 18, 0xFF0A0A0A);
            graphics.renderOutline(barX, y + 4, SLIDER_BAR_W, 14, on ? 0xFF888888 : 0xFF444444);

            // The filled part is the value, so the bar reads at a glance even before the
            // number underneath it is.
            double fraction = (max == min) ? 0.0 : (shown - min) / (double) (max - min);
            int filled = (int) Math.round(fraction * (SLIDER_BAR_W - 2));
            if (filled > 0) {
                graphics.fill(barX + 1, y + 5, barX + 1 + filled, y + 17, on ? 0xFF2A4A6A : 0xFF2A2A2A);
            }

            graphics.drawCenteredString(AtlaSettingsScreen.this.font, readout.apply(shown),
                    barX + SLIDER_BAR_W / 2, y + 7, on ? 0xFFFFFFFF : 0xFF888888);
        }

        @Override
        boolean click(int left, int right, int y, double mouseX, double mouseY) {
            int barX = right - SLIDER_BAR_W;
            if (mouseX < barX || mouseX > barX + SLIDER_BAR_W) return false;

            dragging = this;
            setFromMouse(barX, mouseX);
            return true;
        }

        @Override
        String tooltip() {
            return tooltip;
        }
    }
}
