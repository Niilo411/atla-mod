package com.minecraft.atlamod.abilities.nobending;

import com.minecraft.atlamod.AtlaConfig;
import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.abilities.AbilityUpgrade;
import com.minecraft.atlamod.abilities.PassiveAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * No bending / Defensive. Passive. Nothing about not bending stops a pair of steady
 * hands — this is the path's answer to a skill tree that is otherwise all reach and
 * timing (see {@link Kick}), and its one nod to patience over reflex.
 *
 * VANILLA HASTE rather than a hand-rolled block-break multiplier, the same call
 * every other stat-boosting passive in the mod makes (Lightning Strength's Speed,
 * Flight's creative flight) — the effect already exists, is already synced, already
 * has an icon, and reapplying it is the whole mechanism. See {@link
 * com.minecraft.atlamod.abilities.lightning.LightningStrength#tick} for the same
 * "top up while equipped, take back only OUR instance" shape this follows.
 *
 * THREE CHAINED UPGRADES rather than one, the same shape {@code Mine}'s Obsidian
 * Breaker / Timber pair uses — each requires the one before it, so a bender climbs
 * the tiers in order rather than skipping to the top. Each tier's Haste level is a
 * SETTING (AtlaConfig's noBendingPassives.quickHands section), shipped at 0 through
 * 3 — Haste I through Haste IV.
 *
 * SHIPPED DELIBERATELY LOW, and that is a correction rather than the original plan.
 * A first pass reached all the way to Haste X in three steps, on the reasoning
 * that vanilla's own +20%-per-level formula would keep even the top of that short
 * of instant — which is true of a BARE HAND, and false of anything holding a real
 * pickaxe: tool efficiency and Haste both multiply the same digSpeed figure, so
 * stacking Haste X on top of an already-fast tool broke stone in under a tick.
 * Four levels is short of what any vanilla source normally grants at once (a
 * beacon tops out at Haste II) and stays a real, felt boost without erasing the
 * tool underneath it. A server owner CAN push the setting back up; they just own
 * the consequence of doing so.
 */
public class QuickHands implements PassiveAbility {

    /** Registry key, also what sits in the passive slot. */
    public static final String KEY = "quick hands";

    public static final String STEADY_GRIP = "quick_hands_steady_grip";
    public static final String PRACTICED_SWING = "quick_hands_practiced_swing";
    public static final String MASTERS_TOUCH = "quick_hands_masters_touch";

    @Override
    public String getName() {
        return "Quick Hands";
    }

    @Override
    public String getDescription() {
        return "Mine a little faster while equipped — each upgrade makes it faster"
                + " again, up to three upgrades (exact levels are a server setting)";
    }

    @Override
    public List<AbilityUpgrade> getUpgrades() {
        return List.of(
                new AbilityUpgrade(
                        STEADY_GRIP,
                        "Steady Grip",
                        "Mine a little faster still",
                        5),
                new AbilityUpgrade(
                        PRACTICED_SWING,
                        "Practiced Swing",
                        "Faster again",
                        10,
                        STEADY_GRIP),
                new AbilityUpgrade(
                        MASTERS_TOUCH,
                        "Master's Touch",
                        "Faster again — the maximum tier",
                        15,
                        PRACTICED_SWING));
    }

    /**
     * Which Haste amplifier this bender's own tier grants, highest owned upgrade
     * first. Read from AtlaConfig rather than a fixed constant, so a server owner
     * can retune every tier — including pushing them back up, at their own risk of
     * reintroducing the insta-mine this class's own doc warns about.
     */
    private static int amplifierFor(BendingData data) {
        if (data.hasUpgrade(MASTERS_TOUCH)) return AtlaConfig.quickHandsMastersTouchAmplifier();
        if (data.hasUpgrade(PRACTICED_SWING)) return AtlaConfig.quickHandsPracticedSwingAmplifier();
        if (data.hasUpgrade(STEADY_GRIP)) return AtlaConfig.quickHandsSteadyGripAmplifier();
        return AtlaConfig.quickHandsBaseAmplifier();
    }

    /**
     * Keeps Haste topped up at the bender's current tier while equipped.
     *
     * Refreshed only when the amplifier is wrong or the standing instance is nearly
     * out, exactly as Lightning Strength tops up Speed — not every tick, since each
     * addEffect is a packet.
     *
     * A stronger Haste from somewhere else (a beacon, a potion) survives this
     * unasked: vanilla's own MobEffectInstance#update only lets a NEW instance
     * raise an amplifier, never lower one already running, so offering our tier
     * against something stronger is a no-op every time this checks, with nothing
     * here needing to know the stronger effect is even there.
     */
    public static void tick(ServerPlayer player, BendingData data) {
        boolean equipped = data.hasPassiveEquipped(KEY);
        int amplifier = amplifierFor(data);

        MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);

        if (equipped) {
            if (haste == null || haste.getAmplifier() != amplifier || haste.getDuration() < 40) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SPEED, 200, amplifier, false, true, true));
            }
            return;
        }

        // Unequipped: take back only the exact instance this would have granted,
        // the same care Lightning Strength takes, so a potion the player drank
        // separately is left alone.
        if (haste != null && haste.getAmplifier() == amplifier && haste.getDuration() <= 200) {
            player.removeEffect(MobEffects.DIG_SPEED);
        }
    }
}
