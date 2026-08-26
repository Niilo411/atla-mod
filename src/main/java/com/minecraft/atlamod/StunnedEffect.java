package com.minecraft.atlamod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Stunned — the victim cannot move at all. Applied by Lightning stun.
 *
 * The mod's second custom effect, and it needed to be one for the same reason
 * Disorientation did: nothing in vanilla stops a player walking. Slowness, even at
 * absurd amplifiers, only makes them slow, and the two things that really do pin
 * something (a vehicle, or setting its position every tick) are not effects and so
 * cannot be given a duration, an icon and a timer for free.
 *
 * Like Disorientation, this class holds NO behaviour, and the reason is the same:
 * movement input exists only on the CLIENT, so a stunned player is stopped in
 * ClientEvents.onMovementInput, which just asks whether the local player has this.
 * Vanilla already syncs the effect to the owning client, so no packet is needed.
 *
 * Unlike Disorientation, this one DOES work on mobs — see ServerEvents.onEntityTick,
 * which zeroes the movement of anything carrying it and stops its pathfinding. A mob
 * has no keys to take away, so the server has to hold it still directly, and that
 * same handler covers the server's half of stunning a player.
 */
public class StunnedEffect extends MobEffect {

    /** Pale electric yellow, for the swirl of particles around the victim. */
    private static final int COLOR = 0xF6E27A;

    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
