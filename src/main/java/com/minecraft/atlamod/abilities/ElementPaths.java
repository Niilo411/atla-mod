package com.minecraft.atlamod.abilities;

import java.util.List;
import java.util.Locale;

/**
 * Which abilities make up each path of each element's skill tree.
 *
 * This used to live in UpgradeMenuScreen alone, which was fine while the tree was
 * only ever drawn. It stopped being fine the moment the sub-element scrolls needed to
 * ask "has this player finished two firebending paths?" — that question has to be
 * answered on the SERVER, and the screen is client-only, so the first scroll simply
 * kept a copy. A second scroll would have made that two copies of two different
 * tables, all of which had to be kept in step by hand.
 *
 * So the tables live here, in common code, and the menu reads them like everyone
 * else. Adding an ability to a path is one edit again.
 *
 * The names are the DISPLAY names, matched case-insensitively against what the player
 * has unlocked — the same strings the menu shows and the registry keys off.
 */
public final class ElementPaths {

    private static final String[] NONE = new String[0];

    private ElementPaths() {
    }

    /** The tree's LEFT arm. For a two-path sub-element, its whole left path. */
    public static String[] offensive(String element) {
        if (element == null) return NONE;
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Fire leap", "Fire whip", "Fireball", "Fire Breath"};
            case "water" -> new String[]{"Water ball", "Water stream", "Water Bullets", "Cold water"};
            case "air" -> new String[]{"Air splinters", "Air cannon", "wind tunnel"};
            case "earth" -> new String[]{"Earth spike", "Splinters", "Earth block", "Earth trap"};
            // The two SUB-elements have only two paths. They are drawn on the tree's
            // left and right arms, so the four-armed layout needs no change at all —
            // the top and bottom arms come back empty and nothing is drawn there.
            case "lightning" -> new String[]{
                    "Lightning redirection", "Lightning aura", "Lightning Jump", "Lightning Strength"};
            case "ice" -> new String[]{"icicles", "Freeze", "Ice over", "Ice barrage"};
            case "sound" -> new String[]{
                    "Bass Bounce", "Sound boosting", "Sound wall", "Sound Leap"};
            case "metal" -> new String[]{
                    "Metal armor", "Crush", "Metal shield", "Extract"};
            case "combustion" -> new String[]{
                    "Combustion bombardment", "Explosive combustion",
                    "Combustion Beam", "Combustion nuke"};
            case "blood" -> new String[]{
                    "Blood freeze", "Blood Slow", "Blood suck", "Blood manipulation"};
            case "lava" -> new String[]{
                    "Lava river", "Lava geyser", "Lava sinkhole", "Lava tsunami"};
            // Gravitybending, air's second sub-element. It has no balanced path at
            // all (see balanced() below) but DOES have a masterclass — the first
            // sub-element to be shaped that way. isComplete/completedPaths already
            // treat an empty path as never complete, so nothing here needed to
            // change for that; only UpgradeMenuScreen's masterclass gate did.
            case "gravity" -> new String[]{
                    "Gravity pull", "Gravity Pin", "Gravity Throw", "Gravity Slam"};
            case NO_BENDING -> new String[]{"Chi block", "Sword Mastery"};
            default -> NONE;
        };
    }

    /** The tree's RIGHT arm. For a two-path sub-element, its whole right path. */
    public static String[] defensive(String element) {
        if (element == null) return NONE;
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Fire push", "Fire shield", "Firewall", "Fire ring"};
            case "water" -> new String[]{"Water shield", "Water push", "Water heal"};
            case "air" -> new String[]{"Air pull", "Air jump", "Air Aura", "Wind"};
            case "earth" -> new String[]{"Earth wall", "Earth pillar", "Earth armor"};
            case "lightning" -> new String[]{
                    "Lightning bolt", "Lightning ball", "Lightning stun", "Lightning Swarm"};
            case "ice" -> new String[]{"Ice sphere", "Ice Bomb", "Freezing Beam", "Ice Breath"};
            case "sound" -> new String[]{
                    "Roar", "Deafen", "Compressed punches", "Bass waves"};
            case "metal" -> new String[]{
                    "Tough knuckles", "Bullets", "Stone walls", "Armor pierce"};
            // The design has only one ability on combustion's right path so far, and
            // says "Wip" beneath it. One is enough for the tree to draw an arm.
            case "combustion" -> new String[]{"Combustion resistance"};
            // The design has two on blood's right path so far and says "wip" for the
            // third. Two is enough for the tree to draw an arm.
            case "blood" -> new String[]{"Blood strength", "Flesh shield"};
            case "lava" -> new String[]{
                    "Lava wall", "Lava resistance", "Lava throw", "lava rain"};
            case "gravity" -> new String[]{
                    "Levitation", "Speed Boost", "Gravity Orbit", "Repel Shield"};
            case NO_BENDING -> new String[]{"Kick", "Quick Hands"};
            default -> NONE;
        };
    }

    /** The tree's TOP arm. Empty for the sub-elements. */
    public static String[] balanced(String element) {
        if (element == null) return NONE;
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Ignite", "Fire spikes", "Fire rocket", "Taller fire"};
            case "water" -> new String[]{"Water Manipulation", "Water Surf", "Water Sphere"};
            case "air" -> new String[]{"Air scooter", "Airpush", "Air spout"};
            case "earth" -> new String[]{"Mine", "Earth dig", "Earth grab"};
            default -> NONE;
        };
    }

    /** The tree's BOTTOM arm, gated behind the other three. Empty for the sub-elements. */
    public static String[] master(String element) {
        if (element == null) return NONE;
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"blue fire", "Fire blow", "Fire immunity", "Fire Rain"};
            case "water" -> new String[]{"Drown", "water breathing", "Tsunami"};
            case "air" -> new String[]{"breathless", "Tornado", "Flight"};
            case "earth" -> new String[]{"Earthquake", "Ravine", "Earth sink"};
            case "energy" -> new String[]{"Give and take"}; // Avatar special element
            // Gravitybending has a masterclass with no balanced path beneath it —
            // see UpgradeMenuScreen.checkTreeLogic, whose masterclass gate had to
            // stop requiring a completed balanced path when there isn't one.
            case "gravity" -> new String[]{
                    "Gravity Push", "Encase", "Gravity Crush", "Meteor"};
            default -> NONE;
        };
    }

    /**
     * The ability in the MIDDLE of an element's tree, if it has one.
     *
     * Deliberately NOT one of the four arms, and deliberately not part of {@link #all}:
     * a centre ability belongs to no path, is bought outright whichever way the bender
     * has gone, and must not count towards path completion — the sub-element scrolls
     * gate on "two completed paths of X", and a one-ability fifth array would hand
     * everybody a free completed path.
     */
    public static String[] centre(String element) {
        if (element == null) return NONE;
        return switch (element.toLowerCase()) {
            case "air" -> new String[]{"Advanced meditating"};
            case NO_BENDING -> new String[]{CHI_BLOCKING};
            default -> NONE;
        };
    }

    // ==========================================================================
    //  No bending
    // ==========================================================================

    /**
     * The path taken by someone who chose no bending at all.
     *
     * A REAL ENTRY IN THIS CLASS even though it is not a bending art, because everything
     * that reads a tree reads it from here — the menu, the unlock packet, the equip list.
     * Keeping it out would mean a second set of tables for a single tree, which is the
     * exact duplication this class was extracted to end.
     *
     * The Avatar can never hold it — see {@code Avatar.grant}. The cycle only ever looks
     * for the four bending arts, so it is skipped there for free; being NAMED Avatar is
     * the route that had to be closed by hand.
     */
    public static final String NO_BENDING = "nobending";

    /**
     * The centre node, which is NOT an ability.
     *
     * Nothing casts it and nothing equips it — it is a step, and the only thing it does is
     * open the two arms. That makes it the first node in the mod with no class behind it
     * at all, which the menu copes with because a node is only ever a NAME in the unlocked
     * list; the registry is consulted for casting, not for buying. What it did break is
     * the equip list, which offered anything unlocked and not a passive — including this.
     * See {@code UpgradeMenuScreen.equippableAbilities}.
     */
    public static final String CHI_BLOCKING = "Chi blocking";

    /** Whether this is the no-bending path, whatever case it was written in. */
    public static boolean isNoBending(String element) {
        return element != null && NO_BENDING.equalsIgnoreCase(element);
    }

    /**
     * What a path is called on screen.
     *
     * In COMMON code although only the client draws it, because two screens and the HUD
     * all need the same answer and they used to each capitalise the key themselves. That
     * worked for exactly as long as every name was its key with a capital letter, and
     * "No bending" is the first one that is not — three copies of the rule would have
     * meant three places to remember the exception.
     */
    public static String displayName(String element) {
        if (element == null || element.isEmpty()) return "";
        if (isNoBending(element)) return "No bending";

        return element.substring(0, 1).toUpperCase(Locale.ROOT) + element.substring(1);
    }

    /**
     * Whether an element's centre node has to be bought before either arm opens.
     *
     * TRUE ONLY FOR NO BENDING, and the difference from air is real rather than a special
     * case for its own sake. Air's centre is an ordinary extra its bender may buy at any
     * point, so gating the arms behind it would be an arbitrary tax. No bending's centre
     * is the thing that makes the whole path possible — learning to touch chi at all —
     * and both its arms are applications of it, so neither means anything before it.
     */
    public static boolean centreGatesPaths(String element) {
        return isNoBending(element);
    }

    /**
     * What an element's centre node costs in levels.
     *
     * Here rather than in the menu because the menu is client-only and this is a rule
     * about the tree. 20 is the standing figure; no bending's is 15, which buys the whole
     * path rather than one extra — cheaper on purpose, since a non-bender has nothing at
     * all until they pay it, where an airbender buying theirs already has twelve abilities.
     */
    public static int centreCost(String element) {
        return isNoBending(element) ? 15 : 20;
    }

    /** All four arms of an element's tree, in the order the menu draws them. */
    public static String[][] all(String element) {
        return new String[][] {
                offensive(element), defensive(element), balanced(element), master(element)
        };
    }

    /**
     * Whether every ability in a path has been unlocked.
     *
     * An EMPTY path is never complete, which matters: without that, a sub-element's
     * two missing arms would each count as finished and any check on "how many paths
     * are done" would start at two.
     */
    public static boolean isComplete(String[] path, List<String> unlocked) {
        if (path.length == 0) return false;

        for (String ability : path) {
            if (!containsIgnoreCase(unlocked, ability)) return false;
        }
        return true;
    }

    /**
     * How many of an element's paths the player has finished entirely.
     *
     * This is what the sub-element scrolls gate on: lightning wants two completed fire
     * paths, ice wants two completed water paths.
     */
    public static int completedPaths(String element, List<String> unlocked) {
        int complete = 0;
        for (String[] path : all(element)) {
            if (isComplete(path, unlocked)) complete++;
        }
        return complete;
    }

    /**
     * Every element with a tree, in no particular order.
     *
     * This is the mod's answer to "which elements exist", and /bend add asks it rather
     * than keeping a list of its own: the command used to take any word at all, so
     * "/bend add Steve grass" happily granted an element with no abilities, no tree and
     * no emblem, and left the player holding something nothing in the game could do
     * anything with.
     *
     * "energy" is in here although nothing grants it yet — it has an ability in
     * {@link #master} and so is a real element by the only test that matters, and
     * excluding it would make the list disagree with the trees it is drawn from.
     */
    private static final String[] ELEMENTS = {
            "fire", "water", "air", "earth", "lightning", "ice", "sound", "metal", "combustion",
            "blood", "lava", "gravity", "energy",

            // Not a bending art, but it IS a tree with abilities in it, which is the only
            // test this list applies — and /bend add is the one way to hand it to somebody
            // who did not pick it on their first join, so it has to be nameable there.
            NO_BENDING
    };

    /** Every element with a tree, for command suggestions and for validating input. */
    public static List<String> bendable() {
        return List.of(ELEMENTS);
    }

    /**
     * Ability display name (lowercased) to the element it belongs to, built once.
     *
     * {@link #elementOf} used to walk every element's whole tree from scratch on every
     * call — for an ability near the end of {@link #ELEMENTS} that meant allocating on
     * the order of forty short-lived arrays (four to five per element checked, times
     * however many elements came before a match) just to answer "whose ability is
     * this?". That call is on the hottest paths in the mod: once a TICK for every
     * actively channeling player, and up to four times a tick for every player winding
     * up a charge (AbilityHandler#tickChanneled, #tickCharging, #syncChargeStatus all
     * reach it through cooldownFor/chargeTicksFor/boostable/runWithElement).
     *
     * Building the index costs exactly the walk elementOf used to do, but it happens
     * ONCE, at class load, rather than on every single call — and safely so: everything
     * this reads (NONE, NO_BENDING, CHI_BLOCKING, ELEMENTS) is declared textually above
     * this field, so it is already initialised by the time this runs.
     */
    private static final java.util.Map<String, String> ABILITY_ELEMENT_INDEX = buildAbilityElementIndex();

    private static java.util.Map<String, String> buildAbilityElementIndex() {
        java.util.Map<String, String> index = new java.util.HashMap<>();

        for (String element : ELEMENTS) {
            for (String[] path : all(element)) {
                for (String ability : path) {
                    // The first element to claim a name wins, matching elementOf's old
                    // "first match found walking ELEMENTS in order" behaviour exactly —
                    // no two elements share an ability name today, but the tie-break
                    // rule stays the same either way.
                    index.putIfAbsent(ability.toLowerCase(Locale.ROOT), element);
                }
            }
            for (String ability : centre(element)) {
                index.putIfAbsent(ability.toLowerCase(Locale.ROOT), element);
            }
        }
        return index;
    }

    /**
     * Whether this is an element the mod actually has abilities for.
     *
     * Case-insensitive, because the element a player is granted is stored and compared as
     * the string it was typed as — a "Fire" that passed here would be a second element
     * beside "fire" everywhere else.
     */
    public static boolean exists(String element) {
        if (element == null) return false;

        for (String named : ELEMENTS) {
            if (named.equalsIgnoreCase(element)) return true;
        }
        return false;
    }

    /**
     * Which element an ability belongs to, or empty if it belongs to none.
     *
     * Whichever tree an ability sits in IS its element — there is no separate label on
     * the ability to disagree with. Sound boosting is what needed this: it sharpens air
     * and sound abilities and nothing else, and the dispatcher has to be able to ask
     * "is this one of those?" without a table of its own.
     */
    public static String elementOf(String ability) {
        if (ability == null || ability.isEmpty()) return "";

        return ABILITY_ELEMENT_INDEX.getOrDefault(ability.toLowerCase(Locale.ROOT), "");
    }

    private static boolean containsIgnoreCase(List<String> unlocked, String ability) {
        for (String held : unlocked) {
            if (ability.equalsIgnoreCase(held)) return true;
        }
        return false;
    }
}
