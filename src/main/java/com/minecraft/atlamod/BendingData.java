package com.minecraft.atlamod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

public class BendingData {

    /**
     * How many deaths an Avatar gets before the title passes on.
     *
     * Declared first because both the CODEC and the field initialiser below use it,
     * and Java forbids referring to a static field by simple name from anything
     * textually above its declaration.
     */
    public static final int AVATAR_LIVES = 3;

    private String mainElement = "";
    private String activeElement = "";
    private boolean hasChosenElement = false;
    private List<String> unlockedElements = new ArrayList<>();
    private int xp = 0;
    private int level = 0;
    private int currentChi = 500;
    private List<String> unlockedAbilities = new ArrayList<>();

    // --- AVATAR ---
    // Whether this player is currently the Avatar, and how many of their three
    // lives are left. Persisted, because the Avatar has to survive a restart just
    // as much as an unlocked element does.
    private boolean avatar = false;
    private int avatarLives = AVATAR_LIVES;

    /**
     * The elements this player had BEFORE the Avatar gave them all four.
     *
     * The Avatar unlocks every element, and losing it has to give back exactly what
     * was there rather than a guess. Falling back to "keep only the main element"
     * would quietly destroy anything they had earned or been granted beforehand,
     * which is a real loss from what is supposed to be a reversible flag.
     */
    private List<String> preAvatarElements = new ArrayList<>();

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

    // --- AIR JUMP ---
    // Ticks left of the window in which the bender is protected from fall damage.
    // A countdown rather than a plain boolean for two reasons: the flag has to
    // survive the few ticks between the server applying the launch velocity and the
    // client reporting that it has left the ground, and if a landing is somehow
    // never seen, the protection expires on its own instead of lasting forever.
    private transient int airJumpTicks = 0;

    /**
     * Whether the jump has actually got the player off the ground yet. The server
     * applies the launch velocity, but the CLIENT is what moves the player and
     * reports back, so for the first few ticks the server still sees them standing
     * where they were — and a window that closed on "touching the ground" would
     * close on the launch itself before the jump had begun.
     */
    private transient boolean airJumpLeftGround = false;

    public int getAirJumpTicks() { return airJumpTicks; }
    public void setAirJumpTicks(int ticks) { this.airJumpTicks = Math.max(0, ticks); }

    public boolean hasAirJumpLeftGround() { return airJumpLeftGround; }
    public void setAirJumpLeftGround(boolean left) { this.airJumpLeftGround = left; }

    // --- FLIGHT (passive) ---
    // Whether the Flight passive is the thing currently holding the player's flight
    // flags open. Those flags are PERSISTED in player NBT, so something has to
    // remember that we were the ones who set them — otherwise unequipping the passive
    // mid-air would leave permanent creative flight behind.
    private transient boolean passiveFlightGranted = false;

    public boolean isPassiveFlightGranted() { return passiveFlightGranted; }
    public void setPassiveFlightGranted(boolean granted) { this.passiveFlightGranted = granted; }

    // --- EARTH ARMOR (visual sync) ---
    // Whether onlookers have been told this player is wearing the stone suit. Mob
    // effects are only synced to their OWN owner, so the look has to be broadcast by
    // hand; this remembers what was last sent so it is only sent on a change.
    private transient boolean earthArmorShown = false;

    public boolean isEarthArmorShown() { return earthArmorShown; }
    public void setEarthArmorShown(boolean shown) { this.earthArmorShown = shown; }

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
            Codec.STRING.listOf().optionalFieldOf("unlockedUpgrades", new ArrayList<>()).forGetter(BendingData::getUnlockedUpgrades),
            Codec.BOOL.optionalFieldOf("avatar", false).forGetter(BendingData::isAvatar),
            Codec.INT.optionalFieldOf("avatarLives", AVATAR_LIVES).forGetter(BendingData::getAvatarLives),
            Codec.STRING.listOf().optionalFieldOf("preAvatarElements", new ArrayList<>()).forGetter(BendingData::getPreAvatarElements)
    ).apply(instance, (main, active, chosen, unlocked, xp, level, chi, abils, equipped, passives, upgrades,
                       isAvatar, lives, preElements) -> {
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

        data.avatar = isAvatar;
        data.avatarLives = lives;
        data.setPreAvatarElements(preElements);

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

    // --- BASS BOUNCE ---
    // Ticks left of the hop before the slam is given up on, and whether the bender has
    // actually got off the ground yet. Exactly the pair Air jump keeps, for exactly
    // the same reason: the server applies the launch but the CLIENT moves the player,
    // so a landing test without the second flag would fire on the launch itself.
    private transient int bassBounceTicks = 0;
    private transient boolean bassBounceLeftGround = false;

    public int getBassBounceTicks() { return bassBounceTicks; }
    public void setBassBounceTicks(int ticks) { this.bassBounceTicks = Math.max(0, ticks); }

    public boolean hasBassBounceLeftGround() { return bassBounceLeftGround; }
    public void setBassBounceLeftGround(boolean left) { this.bassBounceLeftGround = left; }

    // --- COMPRESSED PUNCHES ---
    // Whether the toggle is up. Transient, so a relog switches it off — which is the
    // right answer for something billed by the second: nobody should come back to an
    // ability quietly draining chi they did not choose to spend.
    private transient boolean punchingCompressed = false;

    /** How long it has been up, so it can switch itself off at the thirty second cap. */
    private transient int compressedPunchTicks = 0;

    public boolean isPunchingCompressed() { return punchingCompressed; }
    public void setPunchingCompressed(boolean punching) { this.punchingCompressed = punching; }

    public int getCompressedPunchTicks() { return compressedPunchTicks; }
    public void setCompressedPunchTicks(int ticks) { this.compressedPunchTicks = Math.max(0, ticks); }

    // --- COMBUSTION SCROLL ---
    // Ticks left in which explosions cannot touch this player at all.
    //
    // Only the Combustionbending Scroll sets it. The four sticks it puts down are real
    // primed TNT and are meant to frighten everything nearby, but blowing up the person
    // who just earned the element is a poor reward — so they are spared, and only them.
    //
    // Transient: the window is a few seconds and a relog through it is not worth
    // persisting.
    private transient int blastImmuneTicks = 0;

    public int getBlastImmuneTicks() { return blastImmuneTicks; }
    public void setBlastImmuneTicks(int ticks) { this.blastImmuneTicks = Math.max(0, ticks); }

    // --- BENDING LOCKOUT ---
    // Ticks left during which this player cannot bend at all. Deafen is the only
    // thing that sets it.
    //
    // A counter on the data rather than another MobEffect, because it runs for a
    // DIFFERENT length of time than the deafness it comes with (10 seconds against
    // 25) — one effect could not carry both, and two effects for one ability would be
    // two icons for a single thing happening.
    //
    // Transient: a relog clears it. Ten seconds of lockout is not worth persisting,
    // and a player who logged out unable to bend and came back the same way would
    // reasonably think the mod had broken.
    private transient int bendingLockedTicks = 0;

    public int getBendingLockedTicks() { return bendingLockedTicks; }
    public void setBendingLockedTicks(int ticks) { this.bendingLockedTicks = Math.max(0, ticks); }
    public boolean isBendingLocked() { return bendingLockedTicks > 0; }

    // --- LIGHTNING REDIRECTION ---
    // How hard the bolt this player CAUGHT will hit when they throw it back.
    //
    // Transient, and it has to be remembered somewhere between the catch and the
    // left click that looses it — the shot that was absorbed is gone by then, so its
    // strength cannot be asked for again. A redirected bolt hits for whatever it was
    // going to hit the catcher for, which is what makes redirecting a strong bolt
    // worth more than redirecting a weak one.
    private transient float caughtLightning = 0.0F;

    public float getCaughtLightning() { return caughtLightning; }
    public void setCaughtLightning(float damage) { this.caughtLightning = Math.max(0.0F, damage); }

    // Left clicks remaining on an armed two-phase ability (Water Bullets fires three).
    private transient int twoPhaseShots = 0;

    public int getTwoPhaseShots() { return twoPhaseShots; }
    public void setTwoPhaseShots(int shots) { this.twoPhaseShots = Math.max(0, shots); }

    // --- AVATAR ACCESSORS ---

    public boolean isAvatar() { return avatar; }
    public void setAvatar(boolean avatar) { this.avatar = avatar; }

    public int getAvatarLives() { return avatarLives; }
    public void setAvatarLives(int lives) { this.avatarLives = Math.max(0, lives); }

    public List<String> getPreAvatarElements() {
        if (preAvatarElements == null) preAvatarElements = new ArrayList<>();
        return preAvatarElements;
    }

    public void setPreAvatarElements(List<String> elements) {
        this.preAvatarElements = new ArrayList<>();
        if (elements != null) {
            for (String element : elements) {
                if (element != null && !element.trim().isEmpty()) this.preAvatarElements.add(element);
            }
        }
    }

    /**
     * Whether the Avatar's emergency buffs are currently applied.
     *
     * Transient on purpose: it exists only so the buffs are taken back off by the
     * thing that put them on, and a relog simply re-derives it from the health.
     */
    private transient boolean avatarBuffed = false;

    public boolean isAvatarBuffed() { return avatarBuffed; }
    public void setAvatarBuffed(boolean buffed) { this.avatarBuffed = buffed; }
}
