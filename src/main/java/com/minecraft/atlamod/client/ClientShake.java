package com.minecraft.atlamod.client;

import net.minecraft.util.Mth;

/**
 * How long this client's camera should still be shaking, and by how much.
 *
 * Client-only state with no server counterpart: the server says "shake for N ticks"
 * once and this counts it down. Nothing here is authoritative, so losing it to a
 * disconnect or a world change costs nothing but a steady view.
 */
public final class ClientShake {

    /** Peak displacement in degrees. Enough to be unmistakable, short of nauseating. */
    private static final float AMPLITUDE = 1.1F;

    /** Ticks over which the shake eases off at the end, so it stops rather than cuts. */
    private static final float FADE_TICKS = 20.0F;

    private static int ticksLeft;
    private static int age;

    private ClientShake() {
    }

    public static void start(int ticks) {
        ticksLeft = Math.max(ticksLeft, ticks);
    }

    public static boolean active() {
        return ticksLeft > 0;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
            age++;
        } else {
            age = 0;
        }
    }

    /** Dropped on leaving a world, so a shake cannot follow the player into the next. */
    public static void clear() {
        ticksLeft = 0;
        age = 0;
    }

    /**
     * The offset to add to the camera this frame.
     *
     * Three different frequencies rather than one, so the movement reads as ground
     * shaking rather than as a rocking boat — a single sine on one axis looks
     * mechanical almost immediately. The partial tick is folded in so it is smooth at
     * any framerate rather than stepping once per tick.
     *
     * @param axis 0 yaw, 1 pitch, 2 roll
     */
    public static float offset(int axis, float partialTick) {
        if (ticksLeft <= 0) return 0.0F;

        float t = age + partialTick;
        float amp = AMPLITUDE * Math.min(1.0F, ticksLeft / FADE_TICKS);

        return switch (axis) {
            case 0 -> Mth.cos(t * 0.71F) * amp * 0.6F;
            case 1 -> Mth.sin(t * 0.93F) * amp * 0.6F;
            default -> Mth.sin(t * 0.57F) * amp * 1.6F;
        };
    }
}
