package com.minecraft.atlamod.abilities;

import java.util.List;

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
            default -> NONE;
        };
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

    /** Every element with a tree, in no particular order. */
    private static final String[] ELEMENTS = {
            "fire", "water", "air", "earth", "lightning", "ice", "sound", "metal", "combustion",
            "blood", "lava"
    };

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

        for (String element : ELEMENTS) {
            for (String[] path : all(element)) {
                for (String named : path) {
                    if (named.equalsIgnoreCase(ability)) return element;
                }
            }
            // The centre too: it is not part of any path, but it is still very much
            // the element's ability — Sound boosting reaches it like any other.
            for (String named : centre(element)) {
                if (named.equalsIgnoreCase(ability)) return element;
            }
        }
        return "";
    }

    private static boolean containsIgnoreCase(List<String> unlocked, String ability) {
        for (String held : unlocked) {
            if (ability.equalsIgnoreCase(held)) return true;
        }
        return false;
    }
}
