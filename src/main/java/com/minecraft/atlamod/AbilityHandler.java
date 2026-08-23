package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Thin dispatcher. It owns the rules that apply to EVERY ability — cooldown gating,
 * chi cost, XP reward, arming/releasing two-phase abilities, channeling lifecycle,
 * and syncing back to the client — then hands off to the ability's own logic.
 *
 * Ability effects themselves live in com.minecraft.atlamod.abilities.*; adding one
 * should never require touching this file.
 */
public class AbilityHandler {

    // ==========================================
    //  PHASE 1: instant cast (slot key pressed)
    // ==========================================
    public static void executeAbility(ServerPlayer player, BendingData data, String abilityName) {
        Ability ability = AbilityRegistry.get(abilityName);
        if (ability == null) return;

        // Channeled abilities do NOT start here — they come in through
        // executeAbilityHold() -> startChannel(). The client can't tell the two
        // shapes apart, so it fires UseAbilityPacket AND AbilityHoldPacket on the
        // same key press. Without this guard the cast path would run a channeled
        // ability's execute() (empty by default, so nothing visible happens) and
        // then stamp its cooldown, which then blocks the hold path from ever
        // starting the channel — the ability looks dead and permanently cooling.
        if (ability instanceof ChanneledAbility) return;

        if (ability.getCooldownTicks() > 0 && data.isOnCooldown(ability.getKey())) {
            player.displayClientMessage(
                    Component.literal("§c" + ability.getName() + " is on cooldown!"), true);
            return;
        }

        // Per-ability precondition runs before chi is spent, so a blocked cast is free.
        if (!ability.canStart(player, data)) return;

        if (!AbilitySupport.consumeChiAndGiveXp(player, data, ability.getChiCost(), ability.getXpReward())) {
            return;
        }

        // Two-phase abilities arm here and fire on the next left click.
        if (ability instanceof TwoPhaseAbility) {
            data.setActiveTwoPhaseAbility(ability.getKey());
        }

        ability.execute(player, data);

        // Two-phase cooldowns start on release instead — see TwoPhaseAbility.
        if (ability.getCooldownTicks() > 0 && !(ability instanceof TwoPhaseAbility)) {
            data.setCooldown(ability.getKey(), ability.getCooldownTicks());
        }

        AbilitySupport.syncData(player, data);
    }

    // ==========================================
    //  PHASE 2: release an armed two-phase ability (left click)
    // ==========================================
    public static void executeLeftClickPhase(ServerPlayer player, BendingData data) {
        String armedKey = data.getActiveTwoPhaseAbility();
        if (armedKey.isEmpty()) return;

        // Clear immediately so the player can't spam left-click into multiple releases.
        data.setActiveTwoPhaseAbility("");

        Ability ability = AbilityRegistry.get(armedKey);
        if (ability instanceof TwoPhaseAbility twoPhase) {
            twoPhase.onRelease(player, data);

            if (ability.getCooldownTicks() > 0) {
                data.setCooldown(ability.getKey(), ability.getCooldownTicks());
            }
        }

        AbilitySupport.syncData(player, data);
    }

    // ==========================================
    //  PHASE 3: channeled abilities (slot key held)
    // ==========================================
    public static void executeAbilityHold(ServerPlayer player, BendingData data, String abilityName, boolean isHeld) {
        Ability ability = AbilityRegistry.get(abilityName);
        if (!(ability instanceof ChanneledAbility channeled)) return;

        if (isHeld) {
            startChannel(player, data, channeled);
        } else {
            stopChannel(player, data, channeled);
        }
    }

    private static void startChannel(ServerPlayer player, BendingData data, ChanneledAbility ability) {
        // Only one channel at a time.
        if (data.isChanneling()) return;

        if (ability.getCooldownTicks() > 0 && data.isOnCooldown(ability.getKey())) {
            int secondsLeft = (data.getCooldownRemaining(ability.getKey()) + 19) / 20;
            player.displayClientMessage(Component.literal(
                    "§c" + ability.getName() + " is on cooldown! (" + secondsLeft + "s)"), true);
            return;
        }

        // A gate, not a cost: nothing is deducted for meeting it, and the channel
        // keeps running below this figure once it is up.
        int requiredChi = ability.getMinimumChiToStart();
        if (data.getCurrentChi() < requiredChi) {
            player.displayClientMessage(Component.literal(
                    "§cNot enough Chi! (Requires " + requiredChi + ")"), true);
            return;
        }

        data.setActiveChanneledAbility(ability.getKey());
        data.setChannelTicks(0);
        ability.onStart(player, data);
        AbilitySupport.syncData(player, data);
    }

    /**
     * Ends a channel. Every route out of a channel comes through here — releasing the
     * key, running dry on chi, or hitting the duration cap — so the cooldown applies
     * uniformly and can't be dodged by picking a particular way to stop.
     */
    private static void stopChannel(ServerPlayer player, BendingData data, ChanneledAbility ability) {
        // Ignore a key-release for an ability that isn't the one currently channeling.
        // This is also what stops an auto-stopped channel from taking a second cooldown
        // when the player eventually lets go of the key.
        if (!data.getActiveChanneledAbility().equals(ability.getKey())) return;

        data.setActiveChanneledAbility("");
        data.setChannelTicks(0);

        if (ability.getCooldownTicks() > 0) {
            data.setCooldown(ability.getKey(), ability.getCooldownTicks());
        }

        ability.onStop(player, data);
        AbilitySupport.syncData(player, data);
    }

    /**
     * Called every tick from ServerEvents while any channel is active.
     * Handles the duration cap, drain, XP and sync cadence so channeled abilities
     * only implement their effect.
     */
    public static void tickChanneled(ServerPlayer player, BendingData data) {
        Ability ability = AbilityRegistry.get(data.getActiveChanneledAbility());
        if (!(ability instanceof ChanneledAbility channeled)) {
            // Unknown ability recorded (e.g. removed from the registry) — clear the stuck flag.
            data.setActiveChanneledAbility("");
            data.setChannelTicks(0);
            return;
        }

        // Duration cap is checked BEFORE the effect runs, so a 200-tick cap yields
        // exactly 200 ticks of effect and stops on the 201st.
        int maxDuration = channeled.getMaxDurationTicks();
        if (maxDuration > 0 && data.getChannelTicks() >= maxDuration) {
            stopChannel(player, data, channeled);
            return;
        }

        int chiThisTick = chiCostForTick(channeled, player.tickCount);
        if (data.getCurrentChi() < chiThisTick) {
            stopChannel(player, data, channeled);
            return;
        }

        data.consumeChi(chiThisTick);

        // Trickle XP once per second, same cadence as Meditate.
        if (player.tickCount % 20 == 0) {
            AbilitySupport.grantXp(data, channeled.getXpPerSecond());
        }

        channeled.onTick(player, data);
        data.setChannelTicks(data.getChannelTicks() + 1);

        // Sync every 4 ticks instead of every tick — keeps the Chi bar responsive
        // without flooding packets.
        if (player.tickCount % 4 == 0) {
            AbilitySupport.syncData(player, data);
        }
    }

    /**
     * How much chi this particular tick of a channel costs.
     *
     * Rates are authored per second but spent per tick, and 20 rarely divides the
     * rate evenly (25/sec is 1.25/tick). Rounding each tick would drift, so this
     * differences a running total instead: any 20 consecutive ticks sum to exactly
     * getChiPerSecond(), while individual ticks alternate (25/sec spends 1 chi on
     * fifteen ticks and 2 chi on five). The chi bar still drains smoothly.
     */
    private static int chiCostForTick(ChanneledAbility ability, int tick) {
        long rate = ability.getChiPerSecond();
        long t = Math.max(0, tick);
        return (int) ((rate * (t + 1)) / 20L - (rate * t) / 20L);
    }

    /**
     * Whether the player's active channel should block this particular damage.
     * Consulted by the LivingIncomingDamageEvent handler in ServerEvents.
     *
     * Kept here rather than in ServerEvents so the answer stays driven by the
     * registry: any future channel (Water Shield, Earth Armor) gets this for free
     * by overriding grantsInvulnerability().
     */
    public static boolean blocksDamage(BendingData data, DamageSource source) {
        if (!data.isChanneling()) return false;

        Ability ability = AbilityRegistry.get(data.getActiveChanneledAbility());
        if (!(ability instanceof ChanneledAbility channeled) || !channeled.grantsInvulnerability()) {
            return false;
        }

        // A bending shield stops what's coming AT the player. It is not a reason to
        // survive the ground or the bottom of the world, so these always land:
        //   IS_FALL                  — fall damage (and stalagmites)
        //   BYPASSES_INVULNERABILITY — the void, and /kill, which should still work
        //                              on a shielded player
        if (source.is(DamageTypeTags.IS_FALL)) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;

        return true;
    }
}
