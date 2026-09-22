package com.minecraft.atlamod.abilities.gravity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Who currently has Speed Boost up.
 *
 * A presence set rather than a per-player field on BendingData, matching Sound
 * Wall / Metal Shield's toggle shape: the ability is billed by the second from the
 * player tick (see ServerEvents#chargeSoundToggle), and this is only ever asked
 * "is this player's toggle on" and "top up their Speed while it is".
 */
public final class GravitySpeedBoosts {

    /** Speed IV. */
    private static final int SPEED_LEVEL = 3;

    private static final Set<UUID> ACTIVE = new HashSet<>();

    private GravitySpeedBoosts() {
    }

    public static boolean has(ServerPlayer player) {
        return ACTIVE.contains(player.getUUID());
    }

    public static void start(ServerPlayer player) {
        ACTIVE.add(player.getUUID());
    }

    public static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());

        // Only OUR instance is taken back, so a Speed potion the player drank
        // separately is left alone — the same care LightningStrength takes.
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null && speed.getAmplifier() == SPEED_LEVEL && speed.getDuration() <= 60) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
        }
    }

    /**
     * Keeps Speed IV topped up while the toggle is on. Called every tick from the
     * player tick loop alongside the toggle's own billing.
     */
    public static void tick(ServerPlayer player) {
        if (!has(player)) return;

        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed == null || (speed.getAmplifier() <= SPEED_LEVEL && speed.getDuration() < 40)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, 60, SPEED_LEVEL, false, true, true));
        }
    }

    /** Called on death, logout and dimension change. */
    public static void forgetPlayer(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }
}
