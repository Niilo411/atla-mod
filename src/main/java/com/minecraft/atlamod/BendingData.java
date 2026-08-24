package com.minecraft.atlamod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

public class BendingData {
    private String mainElement = "";
    private String activeElement = "";
    private boolean hasChosenElement = false;
    private List<String> unlockedElements = new ArrayList<>();
    private int xp = 0;
    private int level = 0;
    private int currentChi = 500;
    private List<String> unlockedAbilities = new ArrayList<>();

    // THE FIX: Use explicit "EMPTY" text to stop Minecraft from deleting empty slots!
    private List<String> equippedAbilities = new ArrayList<>(List.of("EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY", "EMPTY"));

    private transient boolean isMeditating = false;
    private transient int meditateTickTimer = 0;
    private transient boolean isFireLeaping = false;
    // Which channeled ability (if any) the player is currently holding down.
    // Generalised from the old single isBreathingFire boolean so more than one
    // channeled ability can exist without each needing its own flag.
    private transient String activeChanneledAbility = "";

    public String getActiveChanneledAbility() {
        return activeChanneledAbility == null ? "" : activeChanneledAbility;
    }

    public void setActiveChanneledAbility(String ability) {
        this.activeChanneledAbility = ability == null ? "" : ability;
    }

    public boolean isChanneling() {
        return !getActiveChanneledAbility().isEmpty();
    }

    // How many ticks the current channel has been running, for abilities that
    // cap their duration. Reset by AbilityHandler when a channel starts.
    private transient int channelTicks = 0;

    public int getChannelTicks() {
        return channelTicks;
    }

    public void setChannelTicks(int ticks) {
        this.channelTicks = ticks;
    }

    // --- CHARGED ABILITIES (hold to wind up, fires itself when full) ---
    private transient String activeChargingAbility = "";
    private transient int chargeTicks = 0;

    public String getActiveChargingAbility() {
        return activeChargingAbility == null ? "" : activeChargingAbility;
    }

    public void setActiveChargingAbility(String ability) {
        this.activeChargingAbility = ability == null ? "" : ability;
    }

    public boolean isCharging() {
        return !getActiveChargingAbility().isEmpty();
    }

    public int getChargeTicks() {
        return chargeTicks;
    }

    public void setChargeTicks(int ticks) {
        this.chargeTicks = ticks;
    }

    /**
     * How far the last charge got, in ticks, recorded just before the cast runs.
     * Abilities that scale with charge read this — the live counter is cleared
     * before casting so the key release can't fire them a second time.
     */
    private transient int lastChargeTicks = 0;

    public int getLastChargeTicks() { return lastChargeTicks; }
    public void setLastChargeTicks(int ticks) { this.lastChargeTicks = Math.max(0, ticks); }

    // --- FIRE RAIN ---
    // Ticks left of an active downpour. Cast once and left running, so it needs a
    // countdown rather than the channel or charge machinery.
    private transient int fireRainTicks = 0;

    public int getFireRainTicks() { return fireRainTicks; }
    public void setFireRainTicks(int ticks) { this.fireRainTicks = Math.max(0, ticks); }

    private boolean isFireWhipping = false;

    public boolean isFireWhipping() {
        return isFireWhipping;
    }

    public void setFireWhipping(boolean isFireWhipping) {
        this.isFireWhipping = isFireWhipping;
    }

    public static final Codec<BendingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("mainElement", "").forGetter(BendingData::getMainElement),
            Codec.STRING.optionalFieldOf("activeElement", "").forGetter(BendingData::getActiveElement),
            Codec.BOOL.optionalFieldOf("hasChosenElement", false).forGetter(BendingData::hasChosenElement),
            Codec.STRING.listOf().optionalFieldOf("unlockedElements", new ArrayList<>()).forGetter(BendingData::getUnlockedElements),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(BendingData::getXp),
            Codec.INT.optionalFieldOf("level", 0).forGetter(BendingData::getLevel),
            Codec.INT.optionalFieldOf("currentChi", 500).forGetter(BendingData::getCurrentChi),
            Codec.STRING.listOf().optionalFieldOf("unlockedAbilities", new ArrayList<>()).forGetter(BendingData::getUnlockedAbilities),
            Codec.STRING.listOf().optionalFieldOf("equippedAbilities", new ArrayList<>()).forGetter(BendingData::getEquippedAbilities),
            Codec.STRING.listOf().optionalFieldOf("equippedPassives", new ArrayList<>()).forGetter(BendingData::getEquippedPassives),
            Codec.STRING.listOf().optionalFieldOf("unlockedUpgrades", new ArrayList<>()).forGetter(BendingData::getUnlockedUpgrades)
    ).apply(instance, (main, active, chosen, unlocked, xp, level, chi, abils, equipped, passives, upgrades) -> {
        BendingData data = new BendingData();
        data.mainElement = main != null ? main : "";
        data.activeElement = active != null ? active : "";
        data.hasChosenElement = chosen;
        data.unlockedElements = unlocked != null ? new ArrayList<>(unlocked) : new ArrayList<>();
        data.xp = xp;
        data.level = level;
        data.currentChi = chi;
        data.unlockedAbilities = abils != null ? new ArrayList<>(abils) : new ArrayList<>();

        // Bulletproof assignment
        data.setAllEquippedAbilities(equipped);
        data.setAllEquippedPassives(passives);
        data.setAllUnlockedUpgrades(upgrades);

        return data;
    }));

    // --- ELEMENT GETTERS/SETTERS ---
    public String getMainElement() { return mainElement == null ? "" : mainElement; }
    public void setMainElement(String element) {
        this.mainElement = element == null ? "" : element;
        if (!this.unlockedElements.contains(this.mainElement) && !this.mainElement.isEmpty()) {
            this.unlockedElements.add(this.mainElement);
        }
        if (this.activeElement == null || this.activeElement.isEmpty()) {
            this.activeElement = this.mainElement;
        }
        this.hasChosenElement = true;
    }

    public String getActiveElement() { return activeElement == null ? "" : activeElement; }
    public void setActiveElement(String element) { this.activeElement = element == null ? "" : element; }

    public boolean hasChosenElement() { return hasChosenElement; }
    public void setHasChosenElement(boolean chosen) { this.hasChosenElement = chosen; }

    public List<String> getUnlockedElements() {
        if (unlockedElements == null) unlockedElements = new ArrayList<>();
        return unlockedElements;
    }

    // --- STAT GETTERS/SETTERS ---
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getCurrentChi() { return currentChi; }
    public void setCurrentChi(int currentChi) { this.currentChi = currentChi; }
    /**
     * Spends chi and restarts the regen delay. Every ability goes through here, so
     * the delay applies to all of them without each having to remember it — including
     * channels, which re-arm it every tick and so only start regenerating once the
     * channel has been off for the full delay.
     */
    public void consumeChi(int amount) {
        if (amount <= 0) return;
        this.currentChi = Math.max(0, this.currentChi - amount);
        this.chiRegenDelay = CHI_REGEN_DELAY_TICKS;
    }

    public int getMaxChi() { return 500 + (this.level * 100); }

    // --- CHI REGEN DELAY ---
    // Spending chi holds off passive regen briefly, so regen can't be used to pay
    // for an ability as fast as the ability costs.

    /** Ticks of quiet required after spending chi before regen resumes (3 seconds). */
    public static final int CHI_REGEN_DELAY_TICKS = 60;

    private transient int chiRegenDelay = 0;

    public int getChiRegenDelay() { return chiRegenDelay; }
    public void setChiRegenDelay(int ticks) { this.chiRegenDelay = Math.max(0, ticks); }

    // --- ABILITY FLAGS ---
    public boolean isMeditating() { return isMeditating; }
    public void setMeditating(boolean isMeditating) { this.isMeditating = isMeditating; }
    public int getMeditateTickTimer() { return meditateTickTimer; }
    public void setMeditateTickTimer(int timer) { this.meditateTickTimer = timer; }
    public boolean isFireLeaping() { return isFireLeaping; }
    public void setFireLeaping(boolean isFireLeaping) { this.isFireLeaping = isFireLeaping; }
    // Stores EVERY ability's cooldown in one neat list!
    private java.util.Map<String, Integer> cooldowns = new java.util.HashMap<>();

    public boolean isOnCooldown(String ability) {
        return cooldowns.getOrDefault(ability.toLowerCase(), 0) > 0;
    }

    /** Ticks left on an ability's cooldown, 0 if it's ready. */
    public int getCooldownRemaining(String ability) {
        return Math.max(0, cooldowns.getOrDefault(ability.toLowerCase(), 0));
    }

    public void setCooldown(String ability, int ticks) {
        cooldowns.put(ability.toLowerCase(), ticks);
    }

    public void tickCooldowns() {
        for (String ability : new java.util.HashSet<>(cooldowns.keySet())) {
            int current = cooldowns.get(ability);
            if (current > 0) {
                cooldowns.put(ability, current - 1);
            }
        }
    }

    // --- UNLOCKED ABILITIES ---
    public List<String> getUnlockedAbilities() {
        if (unlockedAbilities == null) unlockedAbilities = new ArrayList<>();
        return unlockedAbilities;
    }

    public void unlockAbility(String abilityName) {
        if (abilityName != null && !getUnlockedAbilities().contains(abilityName)) {
            this.unlockedAbilities.add(abilityName);
        }
    }

    // --- EQUIPPED ABILITIES (BULLETPROOF) ---
    public List<String> getEquippedAbilities() {
        if (equippedAbilities == null) equippedAbilities = new ArrayList<>();
        while (equippedAbilities.size() < 8) equippedAbilities.add("EMPTY");
        while (equippedAbilities.size() > 8) equippedAbilities.remove(equippedAbilities.size() - 1);
        return equippedAbilities;
    }

    public void setAllEquippedAbilities(List<String> newAbilities) {
        this.equippedAbilities = new ArrayList<>();
        if (newAbilities != null) {
            for (int i = 0; i < 8; i++) {
                if (i < newAbilities.size()) {
                    String ab = newAbilities.get(i);
                    this.equippedAbilities.add((ab == null || ab.trim().isEmpty()) ? "EMPTY" : ab);
                } else {
                    this.equippedAbilities.add("EMPTY");
                }
            }
        }
        while (this.equippedAbilities.size() < 8) this.equippedAbilities.add("EMPTY");
        while (this.equippedAbilities.size() > 8) this.equippedAbilities.remove(this.equippedAbilities.size() - 1);
    }

    public void setEquippedAbility(int slot, String abilityName) {
        getEquippedAbilities();
        if (slot >= 0 && slot < 8) {
            this.equippedAbilities.set(slot, (abilityName == null || abilityName.trim().isEmpty()) ? "EMPTY" : abilityName);
        }
    }

    public String getEquippedAbility(int slot) {
        getEquippedAbilities();
        if (slot >= 0 && slot < 8) {
            String ab = this.equippedAbilities.get(slot);
            return "EMPTY".equals(ab) ? "" : ab; // Translates back to empty for the UI!
        }
        return "";
    }

    // --- EQUIPPED PASSIVES ---
    // Four slots. Passives have no keybind: being in a slot IS the activation, and
    // whatever the passive affects asks whether it's equipped. Same "EMPTY" sentinel
    // as the ability slots, so Minecraft can't quietly drop blank entries.
    public static final int PASSIVE_SLOTS = 4;

    private List<String> equippedPassives = new ArrayList<>(List.of("EMPTY", "EMPTY", "EMPTY", "EMPTY"));

    public List<String> getEquippedPassives() {
        if (equippedPassives == null) equippedPassives = new ArrayList<>();
        while (equippedPassives.size() < PASSIVE_SLOTS) equippedPassives.add("EMPTY");
        while (equippedPassives.size() > PASSIVE_SLOTS) equippedPassives.remove(equippedPassives.size() - 1);
        return equippedPassives;
    }

    public void setAllEquippedPassives(List<String> newPassives) {
        this.equippedPassives = new ArrayList<>();
        if (newPassives != null) {
            for (String passive : newPassives) {
                this.equippedPassives.add((passive == null || passive.trim().isEmpty()) ? "EMPTY" : passive);
            }
        }
        while (this.equippedPassives.size() < PASSIVE_SLOTS) this.equippedPassives.add("EMPTY");
        while (this.equippedPassives.size() > PASSIVE_SLOTS) this.equippedPassives.remove(this.equippedPassives.size() - 1);
    }

    public void setEquippedPassive(int slot, String passive) {
        if (slot < 0 || slot >= PASSIVE_SLOTS) return;
        getEquippedPassives().set(slot, (passive == null || passive.trim().isEmpty()) ? "EMPTY" : passive);
    }

    public String getEquippedPassive(int slot) {
        if (slot < 0 || slot >= PASSIVE_SLOTS) return "";
        String passive = getEquippedPassives().get(slot);
        return "EMPTY".equals(passive) ? "" : passive;
    }

    /** Whether a named passive is sitting in any slot, i.e. whether it's doing its job. */
    public boolean hasPassiveEquipped(String passiveKey) {
        if (passiveKey == null || passiveKey.isEmpty()) return false;
        for (String equipped : getEquippedPassives()) {
            if (passiveKey.equalsIgnoreCase(equipped)) return true;
        }
        return false;
    }

    // --- ABILITY UPGRADES ---
    // Purchased improvements to individual abilities, keyed by AbilityUpgrade.key.
    // Persisted and synced, since the abilities that read them run on the server but
    // the menu that sells them runs on the client.
    private List<String> unlockedUpgrades = new ArrayList<>();

    public List<String> getUnlockedUpgrades() {
        if (unlockedUpgrades == null) unlockedUpgrades = new ArrayList<>();
        return unlockedUpgrades;
    }

    public void setAllUnlockedUpgrades(List<String> upgrades) {
        this.unlockedUpgrades = new ArrayList<>();
        if (upgrades != null) {
            for (String upgrade : upgrades) {
                if (upgrade != null && !upgrade.trim().isEmpty()) this.unlockedUpgrades.add(upgrade);
            }
        }
    }

    public void unlockUpgrade(String upgradeKey) {
        if (upgradeKey == null || upgradeKey.isEmpty()) return;
        if (!getUnlockedUpgrades().contains(upgradeKey)) getUnlockedUpgrades().add(upgradeKey);
    }

    /** Whether this improvement has been bought. */
    public boolean hasUpgrade(String upgradeKey) {
        return upgradeKey != null && getUnlockedUpgrades().contains(upgradeKey);
    }
    private String activeTwoPhaseAbility = "";

    public String getActiveTwoPhaseAbility() {
        return activeTwoPhaseAbility;
    }

    public void setActiveTwoPhaseAbility(String ability) {
        this.activeTwoPhaseAbility = ability;
    }

    // Ticks left on an armed two-phase ability that has a time limit (Water stream).
    // Zero when whatever is armed waits indefinitely.
    private transient int twoPhaseTicks = 0;

    public int getTwoPhaseTicks() { return twoPhaseTicks; }
    public void setTwoPhaseTicks(int ticks) { this.twoPhaseTicks = Math.max(0, ticks); }

    // Left clicks remaining on an armed two-phase ability (Water Bullets fires three).
    private transient int twoPhaseShots = 0;

    public int getTwoPhaseShots() { return twoPhaseShots; }
    public void setTwoPhaseShots(int shots) { this.twoPhaseShots = Math.max(0, shots); }
}