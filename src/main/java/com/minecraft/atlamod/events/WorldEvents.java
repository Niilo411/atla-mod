package com.minecraft.atlamod.events;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.abilities.ElementPaths;
import net.minecraft.world.level.Level;

/**
 * The three things the sky does to bending.
 *
 * A blood moon that lifts waterbending, Sozin's comet that lifts fire, and the day of
 * black sun that takes fire away entirely — every three, six and twelve days by
 * default, all three configurable in AtlaConfig's worldEvents section.
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
    /**
     * MINECRAFT'S DAY COUNTER ROLLS OVER AT SUNRISE, NOT AT MIDNIGHT.
     *
     * {@code dayTime} 0 is six in the morning; noon is 6000, sunset 12000 and midnight
     * 18000. So {@code floorDiv(time, DAY)} counts sunrise to sunrise, and anything meant
     * to run for "a day" from midnight has to say so.
     *
     * THAT WAS A REAL BUG. The comet ran on the raw day counter, so its twenty minutes
     * began and ended at sunrise — which put its last half in the same calendar day as the
     * blood moon that follows it, and made {@code /bend event} from inside the comet land
     * somewhere the comet was still up. Shifting its epoch to midnight is the fix, and as
     * a side effect it is what stops the comet and the eclipse ever coinciding.
     */
    private static final int MIDNIGHT = 18000;

    /**
     * How far an event's own calendar is shifted from Minecraft's.
     *
     * Zero for the two events that happen WITHIN a day and so do not care where the day
     * begins — a night is a night and noon is noon whatever the counter says. The comet
     * occupies a whole day, so where that day starts is the entire question.
     */
    private static int epoch(Event event) {
        return event == Event.SOZINS_COMET ? MIDNIGHT : 0;
    }

    /** How far into its own day an event begins. */
    private static int startWithin(Event event) {
        return switch (event) {
            case BLOOD_MOON -> NIGHT_FROM;
            case SOZINS_COMET -> 0;
            case BLACK_SUN -> NOON - ECLIPSE_LENGTH / 2;
        };
    }

    /**
     * Which of the event's own days this moment falls in.
     *
     * Counted from that event's epoch, so the comet's days run midnight to midnight and
     * the other two run sunrise to sunrise. Everything below is expressed against this,
     * which is what keeps the three from needing three different shapes of arithmetic.
     */
    private static long dayIndex(long time, Event event) {
        return Math.floorDiv(time - epoch(event), (long) DAY);
    }

    /** How far into that day this moment is, always 0 up to one day. */
    private static long within(long time, Event event) {
        return time - epoch(event) - dayIndex(time, event) * DAY;
    }

    public static boolean isActive(Level level, Event event) {
        if (level == null) return false;
        if (!enabled(event)) return false;

        long time = level.getDayTime();

        // The right day of the cycle — every third, sixth or twelfth. Counted so that the
        // first of each falls a little way in rather than on a brand new world's first
        // night.
        if (Math.floorMod(dayIndex(time, event), (long) period(event)) != dayOfPeriod(event)) {
            return false;
        }

        long into = within(time, event);
        long from = startWithin(event);

        return into >= from && into < from + length(event);
    }

    /**
     * How far through the event we are, 0 to 1, or 0 when it is not running.
     *
     * Only the visuals need this — an eclipse that snapped to full darkness and back
     * would read as a bug rather than as the moon crossing the sun.
     */
    public static float progress(Level level, Event event) {
        if (!isActive(level, event)) return 0.0F;

        long into = within(level.getDayTime(), event) - startWithin(event);

        return into / (float) length(event);
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

    // ==========================================================================
    //  Jumping to one
    // ==========================================================================

    /**
     * How many days apart an event's occurrences are. A SETTING — see AtlaConfig's
     * worldEvents section — so "3, 6 and 12" are this method's shipped defaults
     * rather than a fact anything else may assume.
     */
    private static int period(Event event) {
        return switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonPeriodDays();
            case SOZINS_COMET -> AtlaConfig.cometPeriodDays();
            case BLACK_SUN -> AtlaConfig.blackSunPeriodDays();
        };
    }

    /** Which day of that period it falls on, as {@code day % period}. */
    private static int dayOfPeriod(Event event) {
        return period(event) - 1;
    }

    /**
     * How long it runs for, in ticks. Also a SETTING now — the doc comments on the
     * config entries record what each one still means by default (dusk to dawn, a
     * whole day, six minutes centred on noon) since the number alone no longer says
     * so the way a hardcoded {@code DAY - NIGHT_FROM} once did.
     */
    public static int length(Event event) {
        return switch (event) {
            case BLOOD_MOON -> AtlaConfig.bloodMoonDurationTicks();
            case SOZINS_COMET -> AtlaConfig.cometDurationTicks();
            case BLACK_SUN -> AtlaConfig.blackSunDurationTicks();
        };
    }

    /**
     * The next moment this event begins, at or after {@code now}.
     *
     * FOR THE COMMAND THAT STARTS ONE. Everything about an event is derived from the
     * clock, so there is nothing to switch on — the only honest way to make one happen is
     * to move the clock to where it already happens, which is what this works out and
     * {@code /bend event} then sets. That also means the event runs its natural length and
     * ends by itself, exactly as it would have; nothing is special-cased for having been
     * asked for.
     *
     * NEVER BACKWARDS. A start already passed today is not the next one — winding the
     * clock back would un-do a day for everything else in the world that counts them, and
     * "start it now" means the next time it starts rather than the last.
     *
     * The walk is bounded by twice the period, which is more than enough: within any run
     * of {@code period} days exactly one matches, so the worst case is that today is that
     * day and its start hour has already gone by.
     */
    public static long nextStart(long now, Event event) {
        int period = period(event);
        int wanted = dayOfPeriod(event);

        // Counted in the EVENT'S OWN days, which for the comet begin at midnight — the
        // same arithmetic isActive uses, so the moment this returns is by construction one
        // isActive agrees is the first tick.
        long index = dayIndex(now, event);

        for (int ahead = 0; ahead <= period * 2; ahead++) {
            long candidate = index + ahead;
            if (Math.floorMod(candidate, (long) period) != wanted) continue;

            long start = candidate * DAY + epoch(event) + startWithin(event);
            if (start >= now) return start;
        }

        // Unreachable while the loop runs longer than the period. A sane answer rather
        // than a throw if that ever stops being true.
        return now;
    }

    /** Whether this event is switched on at all. The command warns rather than lying. */
    public static boolean isEnabled(Event event) {
        return enabled(event);
    }

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
