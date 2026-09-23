package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.combustion.CombustionBeam;
import com.minecraft.atlamod.abilities.fire.FireRocket;
import com.minecraft.atlamod.abilities.gravity.GravitySpeedBoost;
import com.minecraft.atlamod.abilities.metal.MetalShield;
import com.minecraft.atlamod.abilities.sound.CompressedPunches;
import com.minecraft.atlamod.abilities.sound.SoundWall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-ability overrides of an ability's chi cost, XP and cooldown, set from the settings
 * screen's Abilities tab (or by hand in the TOML).
 *
 * AN OVERRIDE REPLACES THE ABILITY'S OWN FIGURE, AND ONLY WHERE ONE IS SET. Every field is
 * optional: a line that names only a cooldown leaves the chi and XP exactly as the ability
 * class says. Nothing here stores a default — the ability class is always the default, so
 * a figure retuned in a later build reaches every world that has not overridden it.
 *
 * WHAT "CHI" AND "XP" MEAN DEPENDS ON THE SHAPE, and it is the figure that shape is billed
 * by. An ordinary cast, charge or two-phase is billed ONCE, so it is the price of a cast
 * and the reward for one. A CHANNEL is billed by the second, so for a channel they are
 * chi per second and XP per second — its one-off cost is nearly always zero and
 * overriding that would do nothing anybody could see.
 *
 * TOGGLES BILLED FROM THE PLAYER TICK get a second pair, UPKEEP, because they are billed
 * twice: a price to switch on (the ordinary chi figure, through the dispatcher) and a rate
 * while running (taken by {@code ServerEvents.chargeSoundToggle} or the Rides tick). The
 * dispatcher never sees the second, so it has to be asked for by name — see
 * {@link #UPKEEP}.
 *
 * World events and Sound boosting still apply ON TOP of an override, since they scale
 * whatever the base figure is rather than replacing it.
 *
 * Stored in the config as one line per ability:
 * {@code "Fireball: chi=150, xp=20, cooldown=60, upkeepChi=5, upkeepXp=1"}, cooldown in
 * TICKS. Parsed leniently: an unreadable field is skipped rather than rejecting the line.
 */
public final class AbilityTuning {

    /** One ability's overrides. A null field is not overridden. */
    public record Entry(String name, Integer chi, Integer xp, Integer cooldown,
                        Integer upkeepChi, Integer upkeepXp) {

        public boolean isEmpty() {
            return chi == null && xp == null && cooldown == null
                    && upkeepChi == null && upkeepXp == null;
        }

        /** The line written to the config. Fields that are not overridden are left out. */
        public String format() {
            List<String> parts = new ArrayList<>();
            if (chi != null) parts.add("chi=" + chi);
            if (xp != null) parts.add("xp=" + xp);
            if (cooldown != null) parts.add("cooldown=" + cooldown);
            if (upkeepChi != null) parts.add("upkeepChi=" + upkeepChi);
            if (upkeepXp != null) parts.add("upkeepXp=" + upkeepXp);
            return name + ": " + String.join(", ", parts);
        }
    }

    /**
     * The default per-second upkeep of every toggle that is billed OUTSIDE the dispatcher,
     * as {chi, xp}, keyed by ability key.
     *
     * The one table here that has to be kept by hand, because these rates live in the
     * player tick and the ride tick rather than on the ability. A toggle added later that
     * bills itself by the second needs a line here AND to ask {@link #upkeepChi}/
     * {@link #upkeepXp} where it bills, or its upkeep simply is not tunable — nothing breaks.
     */
    private static final Map<String, int[]> UPKEEP = new LinkedHashMap<>();

    static {
        UPKEEP.put("compressed punches",
                new int[] { CompressedPunches.CHI_PER_SECOND, CompressedPunches.XP_PER_SECOND });
        UPKEEP.put("fire rocket", new int[] { FireRocket.CHI_PER_SECOND, FireRocket.XP_PER_SECOND });
        UPKEEP.put("combustion beam",
                new int[] { CombustionBeam.CHI_PER_SECOND, CombustionBeam.XP_PER_SECOND });
        UPKEEP.put("metal shield", new int[] { MetalShield.CHI_PER_SECOND, MetalShield.XP_PER_SECOND });
        UPKEEP.put("sound wall", new int[] { SoundWall.CHI_PER_SECOND, SoundWall.XP_PER_SECOND });
        UPKEEP.put("speed boost",
                new int[] { GravitySpeedBoost.CHI_PER_SECOND, GravitySpeedBoost.XP_PER_SECOND });
        for (Rides.Kind kind : Rides.Kind.values()) {
            UPKEEP.put(kind.abilityKey(), new int[] { kind.chiPerSecond, kind.xpPerSecond });
        }
    }

    private AbilityTuning() {
    }

    // ==========================================================================
    //  What the game asks
    // ==========================================================================

    private static Entry of(Ability ability) {
        return of(ability.getKey());
    }

    private static Entry of(String key) {
        Map<String, Entry> all = AtlaConfig.abilityTuning();
        return all.isEmpty() ? null : all.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * A cast's one-off chi price. A channel keeps its own (usually zero) figure, since
     * its override is a RATE and is answered by {@link #chiPerSecond}.
     */
    public static int castChiCost(Ability ability, BendingData data) {
        Entry entry = of(ability);
        if (entry != null && entry.chi() != null && !(ability instanceof ChanneledAbility)) {
            return entry.chi();
        }
        return ability.getChiCost(data);
    }

    /** A cast's XP reward. */
    public static int xpReward(Ability ability) {
        Entry entry = of(ability);
        if (entry != null && entry.xp() != null && !(ability instanceof ChanneledAbility)) return entry.xp();

        return ability.getXpReward();
    }

    /** A channel's chi per second. */
    public static int chiPerSecond(ChanneledAbility ability, BendingData data) {
        Entry entry = of(ability);
        return entry != null && entry.chi() != null ? entry.chi() : ability.getChiPerSecond(data);
    }

    /** A channel's XP per second. */
    public static double xpPerSecond(ChanneledAbility ability) {
        Entry entry = of(ability);
        return entry != null && entry.xp() != null ? entry.xp() : ability.getXpPerSecond();
    }

    /** An ability's base cooldown in ticks, before Sound boosting and world events. */
    public static int cooldownTicks(Ability ability) {
        return cooldownTicks(ability.getKey(), ability.getCooldownTicks());
    }

    /**
     * The same, for the few abilities that stamp their own cooldown outside the
     * dispatcher (Bullets, Metal shield, Chi block, Compressed punches) and so pass their
     * own constant in as the fallback.
     */
    public static int cooldownTicks(String key, int fallback) {
        Entry entry = of(key);
        return entry != null && entry.cooldown() != null ? entry.cooldown() : fallback;
    }

    /** A per-second toggle's chi upkeep. */
    public static int upkeepChi(String key, int fallback) {
        Entry entry = of(key);
        return entry != null && entry.upkeepChi() != null ? entry.upkeepChi() : fallback;
    }

    /** A per-second toggle's XP upkeep. */
    public static int upkeepXp(String key, int fallback) {
        Entry entry = of(key);
        return entry != null && entry.upkeepXp() != null ? entry.upkeepXp() : fallback;
    }

    /** The default {chi, xp} upkeep of a toggle billed by the second, or null if it is not one. */
    public static int[] defaultUpkeep(String name) {
        return UPKEEP.get(name.toLowerCase(Locale.ROOT));
    }

    // ==========================================================================
    //  Reading and writing the config lines
    // ==========================================================================

    /** Every line, keyed by lowercased ability name. Blank or nameless lines are skipped. */
    public static Map<String, Entry> parseAll(List<? extends String> lines) {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (String line : lines) {
            Entry entry = parse(line);
            if (entry != null) map.put(entry.name().toLowerCase(Locale.ROOT), entry);
        }
        return map;
    }

    /** One line, or null when it names nothing. */
    public static Entry parse(String line) {
        if (line == null) return null;

        int colon = line.indexOf(':');
        String name = (colon < 0 ? line : line.substring(0, colon)).trim();
        if (name.isEmpty()) return null;

        Integer chi = null, xp = null, cooldown = null, upkeepChi = null, upkeepXp = null;

        if (colon >= 0) {
            for (String part : line.substring(colon + 1).split(",")) {
                int eq = part.indexOf('=');
                if (eq < 0) continue;

                String field = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                Integer value;
                try {
                    value = Math.max(0, Integer.parseInt(part.substring(eq + 1).trim()));
                } catch (NumberFormatException e) {
                    continue;
                }

                switch (field) {
                    case "chi" -> chi = value;
                    case "xp" -> xp = value;
                    case "cooldown" -> cooldown = value;
                    case "upkeepchi" -> upkeepChi = value;
                    case "upkeepxp" -> upkeepXp = value;
                    default -> { }
                }
            }
        }
        return new Entry(name, chi, xp, cooldown, upkeepChi, upkeepXp);
    }
}
