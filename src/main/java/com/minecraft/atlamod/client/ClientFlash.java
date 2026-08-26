package com.minecraft.atlamod.client;

/**
 * The white-out from a lightning strike, counted down on the client.
 *
 * Counted down on the client TICK rather than in the render pass, for the same
 * reason ClientShake is: the HUD layer runs once per FRAME, so counting there would
 * run the flash out at whatever rate the machine happens to render.
 */
public final class ClientFlash {

    private static int ticksLeft = 0;
    private static int total = 0;

    private ClientFlash() {
    }

    public static void start(int ticks) {
        ticksLeft = Math.max(0, ticks);
        total = Math.max(1, ticks);
    }

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    public static boolean isActive() {
        return ticksLeft > 0;
    }

    /** How opaque the flash is right now, fading out over its life. */
    public static float strength(float partialTick) {
        if (ticksLeft <= 0) return 0.0F;
        return Math.max(0.0F, Math.min(1.0F, (ticksLeft - partialTick) / total));
    }

    /** Cleared on the way out of a world, so a relog can't inherit a stuck flash. */
    public static void clear() {
        ticksLeft = 0;
    }
}
