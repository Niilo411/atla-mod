package com.minecraft.atlamod.events;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.abilities.ElementPaths;
import net.minecraft.world.level.Level;

/**
 * The three things the sky does to bending.
 *
 * A blood moon every three days that lifts waterbending, Sozin's comet every six that
 * lifts fire, and the day of black sun every twelve that takes fire away entirely.
 *
 * DERIVED FROM THE LEVEL'S CLOCK, NEVER COUNTED DOWN, which is the one structural
 * decision here and the same one {@code SpiritGravity} makes for the low-gravity tide.
 * Every player asks the same clock, so there is no state to persist, nothing to sync,
 * no drift between somebody who was already online and somebody who just joined, and
 * an event cannot be left half-running by a crash. Ask "is it happening" whenever it
 * matters; the answer is a couple of divisions.
 *
 * THE MULTIPLIERS ARE PERCENTAGES, 100 meaning "unchanged", and they are applied in
 * exactly four places — the dispatcher's cooldown, its charge time, its chi cost, and
 * the damage handler. Nothing an ability class does needs to know an event is running,
 * which is what stops the next ability added from quietly missing out.
 */
public final class WorldEvents {

    /** Ticks in a Minecraft day. */
    private static final int DAY = 24000;

    /**
     * The blood moon runs from dusk to dawn, which is the half of the day a moon is in.
     *
     * 13000 is when vanilla considers it night and the moon is up; the day rolls over at
     * 24000. Starting it at dusk rather than at midnight is what makes it a whole night
     * rather than a moment.
     */
    private static final int NIGHT_FROM = 13000;

    /**
     * The black sun is six real minutes, centred on noon.
     *
     * Six minutes is 7200 ticks, which is a little under a third of a twenty minute day —
     * so it reaches from 2400 to 9600 with midday at its middle. An eclipse that did not
     * straddle noon would be an eclipse of the afternoon.
     */
    private static final int ECLIPSE_LENGTH = 7200;
    private static final int NOON = 6000;

    private WorldEvents() {
    }

    /** The three events, in the order they were added. */
    public enum Event {
        BLOOD_MOON("Blood Moon", "water"),
        SOZINS_COMET("Sozin's Comet", "fire"),
        BLACK_SUN("Day of Black Sun", "fire");

        private final String title;
        private final String element;

        Event(String title, String element) {
            this.title = title;
            this.element = element;
        }

        public String title() {
            return title;
        }

        /** The element this event reaches. Nothing else is touched by it. */
        public String element() {
            return element;
        }
    }

    // ==========================================================================
    //  When
    // ==========================================================================

    /**
     * Whether this event is running right now.
     *
     * TIME IS TAKEN FROM THE LEVEL GIVEN, which in vanilla is one clock shared by every
     * dimension — the Nether and the End derive their day time from the overworld's, so
     * asking whichever level is to hand gives the same answer everywhere and needs no
     * cross-dimension lookup.
     */
    public static boolean isActive(Level level, Event event) {
        if (level == null) return false;
        if (!enabled(event)) return false;

        long time = level.getDayTime();
        long day = Math.floorDiv(time, DAY);
        int hour = (int) Math.floorMod(time, (long) DAY);

        return switch (event) {
            // The NIGHT of every third day. Counted so that day 2 is the first one, which
            // puts the first blood moon on the third night a world is played rather than
            // making a brand new world start under one.
            case BLOOD_MOON -> Math.floorMod(day, 3L) == 2L && hour >= NIGHT_FROM;

            // The WHOLE of every sixth day, dark hours included — the comet is the one
            // event that is meant to be overhead however late it gets.
            case SOZINS_COMET -> Math.floorMod(day, 6L) == 5L;

            // Six minutes across noon on every twelfth day.
            case BLACK_SUN -> Math.floorMod(day, 12L) == 11L
                    && hour >= NOON - ECLIPSE_LENGTH / 2
                    && hour < NOON + ECLIPSE_LENGTH / 2;
        };
    }

    /**
     * How far through the event we are, 0 to 1, or 0 when it is not running.
     *
     * Only the visuals need this — an eclipse that snapped to full darkness and back
     * would read as a bug rather than as the moon crossing the sun.
     */
    public static float progress(Level level, Event event) {
        if (!isActive(level, event)) return 0.0F;

        long time = level.getDayTime();
        int hour = (int) Math.floorMod(time, (long) DAY);

        return switch (event) {
            case BLOOD_MOON -> (hour - NIGHT_FROM) / (float) (DAY - NIGHT_FROM);
            case SOZINS_COMET -> hour / (float) DAY;
            case BLACK_SUN -> (hour - (NOON - ECLIPSE_LENGTH / 2)) / (float) ECLIPSE_LENGTH;
        };
    }

    /**
     * How dark the eclipse is right now, 0 to 1 and back.
     *
     * A sine over the crossing, so the sun dims and returns rather than switching. At the
     * middle of the six minutes it is total.
     */
    public static float eclipseDepth(Level level) {
        float through = progress(level, Event.BLACK_SUN);
        if (through <= 0.0F) return 0.0F;

        return (float) Math.sin(through * Math.PI);
    }

    // ==========================================================================
    //  What it does
    // ==========================================================================

    private static boolean enabled(Event event) {
        return switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonEnabled();
            case SOZINS_COMET -> AtlaConfig.cometEnabled();
            case BLACK_SUN -> AtlaConfig.blackSunEnabled();
        };
    }

    /**
     * Whether an event is reaching this ability at all.
     *
     * WHICHEVER TREE THE ABILITY SITS IN IS ITS ELEMENT, asked of {@link ElementPaths} —
     * the same test Sound boosting uses, and for the same reason: there is no separate
     * label on an ability that could disagree with the tree it is drawn in.
     *
     * Sub-elements are deliberately NOT swept up. A blood moon lifts waterbending, not
     * icebending or bloodbending, however closely those are related — an event that
     * quietly buffed three trees would be three times the change anybody reading the
     * config expected.
     */
    private static boolean reaches(Event event, String abilityName) {
        return event.element().equals(ElementPaths.elementOf(abilityName));
    }

    /** Every multiplier any running event applies to an element, multiplied together. */
    private static float forElement(Level level, String element,
                                    java.util.function.ToIntFunction<Event> percent) {
        if (element == null || element.isEmpty()) return 1.0F;

        float result = 1.0F;

        for (Event event : Event.values()) {
            if (!event.element().equals(element)) continue;
            if (!isActive(level, event)) continue;

            result *= percent.applyAsInt(event) / 100.0F;
        }
        return result;
    }

    /** The same, for an ability named rather than an element. */
    private static float multiplier(Level level, String abilityName,
                                    java.util.function.ToIntFunction<Event> percent) {
        return forElement(level, ElementPaths.elementOf(abilityName), percent);
    }

    /** What an ability's cooldown is multiplied by. */
    public static float cooldownMultiplier(Level level, String abilityName) {
        return multiplier(level, abilityName, event -> switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonCooldown();
            case SOZINS_COMET -> AtlaConfig.cometCooldown();
            case BLACK_SUN -> AtlaConfig.blackSunCooldown();
        });
    }

    /** What an ability's charge time is multiplied by. */
    public static float chargeMultiplier(Level level, String abilityName) {
        return multiplier(level, abilityName, event -> switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonCharge();
            case SOZINS_COMET -> AtlaConfig.cometCharge();
            case BLACK_SUN -> AtlaConfig.blackSunCharge();
        });
    }

    /** What an ability's chi cost is multiplied by. */
    public static float chiMultiplier(Level level, String abilityName) {
        return multiplier(level, abilityName, event -> switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonChi();
            case SOZINS_COMET -> AtlaConfig.cometChi();
            case BLACK_SUN -> AtlaConfig.blackSunChi();
        });
    }

    /**
     * What damage from an ELEMENT is multiplied by.
     *
     * Named by element rather than by ability, because the damage handler is where this
     * is applied and it has no idea which ability is landing — a blow arrives as a damage
     * source and an attacker, and every bending element in the mod uses two or three of
     * the same vanilla sources. What it has instead is the element the dispatcher recorded
     * on the caster for the duration of the cast; see {@code BendingData.castingElement}.
     */
    public static float damageMultiplier(Level level, String element) {
        return forElement(level, element, event -> switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonDamage();
            case SOZINS_COMET -> AtlaConfig.cometDamage();
            case BLACK_SUN -> AtlaConfig.blackSunDamage();
        });
    }

    /**
     * Whether firebending is shut off entirely right now.
     *
     * THE BLACK SUN'S WHOLE POINT, and a separate question from the multipliers because
     * it is a different KIND of answer — no multiplier expresses "you cannot do this at
     * all", and one small enough to try would leave abilities casting for nothing.
     *
     * OVERWORLD ONLY. The sun is what is being eclipsed, so a firebender standing in the
     * Nether is not under it. That also keeps the event from reaching the Spirit World,
     * where nothing bends anyway.
     *
     * Turning it off in the config leaves the multipliers, which is how the event goes
     * from "firebending is gone" to "firebending is worse" without a second setting to
     * decide which.
     */
    public static boolean firebendingSuppressed(Level level, String abilityName) {
        if (!AtlaConfig.blackSunDisablesFire()) return false;
        if (level == null || !level.dimension().equals(Level.OVERWORLD)) return false;
        if (!isActive(level, Event.BLACK_SUN)) return false;

        return reaches(Event.BLACK_SUN, abilityName);
    }
}
