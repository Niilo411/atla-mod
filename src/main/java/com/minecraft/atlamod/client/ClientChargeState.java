package com.minecraft.atlamod.client;

/**
 * Client-side mirror of what the player is currently charging, fed by
 * ChargeStatusPacket and read by the HUD.
 */
public final class ClientChargeState {

    public static String ability = "";
    public static int held = 0;
    public static int total = 0;
    public static boolean armed = false;

    private ClientChargeState() {
    }

    public static void update(String ability, int held, int total, boolean armed) {
        ClientChargeState.ability = ability == null ? "" : ability;
        ClientChargeState.held = held;
        ClientChargeState.total = total;
        ClientChargeState.armed = armed;
    }

    /** Whether the HUD has anything to draw. */
    public static boolean isActive() {
        return !ability.isEmpty() && (armed || total > 0);
    }

    /** Charge completion from 0 to 1. */
    public static float progress() {
        if (armed) return 1.0F;
        if (total <= 0) return 0.0F;
        return Math.min(1.0F, held / (float) total);
    }
}
