package com.minecraft.atlamod;

import com.minecraft.atlamod.abilities.Ability;
import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.AbilitySupport;
import com.minecraft.atlamod.abilities.ChanneledAbility;
import com.minecraft.atlamod.abilities.TwoPhaseAbility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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

        if (data.getCurrentChi() < ability.getChiPerTick()) {
            player.displayClientMessage(Component.literal("§cNot enough Chi!"), true);
            return;
        }

        data.setActiveChanneledAbility(ability.getKey());
        ability.onStart(player, data);
        AbilitySupport.syncData(player, data);
    }

    private static void stopChannel(ServerPlayer player, BendingData data, ChanneledAbility ability) {
        // Ignore a key-release for an ability that isn't the one currently channeling.
        if (!data.getActiveChanneledAbility().equals(ability.getKey())) return;

        data.setActiveChanneledAbility("");
        ability.onStop(player, data);
        AbilitySupport.syncData(player, data);
    }

    /**
     * Called every tick from ServerEvents while any channel is active.
     * Handles the drain/XP/sync cadence so channeled abilities only implement their effect.
     */
    public static void tickChanneled(ServerPlayer player, BendingData data) {
        Ability ability = AbilityRegistry.get(data.getActiveChanneledAbility());
        if (!(ability instanceof ChanneledAbility channeled)) {
            // Unknown ability recorded (e.g. removed from the registry) — clear the stuck flag.
            data.setActiveChanneledAbility("");
            return;
        }

        if (data.getCurrentChi() < channeled.getChiPerTick()) {
            stopChannel(player, data, channeled);
            return;
        }

        data.consumeChi(channeled.getChiPerTick());

        // Trickle XP once per second, same cadence as Meditate.
        if (player.tickCount % 20 == 0) {
            AbilitySupport.grantXp(data, channeled.getXpPerSecond());
        }

        channeled.onTick(player, data);

        // Sync every 4 ticks instead of every tick — keeps the Chi bar responsive
        // without flooding packets.
        if (player.tickCount % 4 == 0) {
            AbilitySupport.syncData(player, data);
        }
    }
}
