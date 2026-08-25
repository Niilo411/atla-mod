package com.minecraft.atlamod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Disorientation — the victim's movement keys are reversed: forward walks back,
 * left strafes right. Applied by Air pull.
 *
 * The effect class itself holds NO behaviour, and that is deliberate. Movement input
 * only exists on the client, so the reversal is done in ClientEvents.onMovementInput,
 * which simply asks whether the local player has this effect. The server's job is
 * just to hold the effect on the entity; vanilla syncs it to the owning client, and
 * the client does the rest.
 *
 * That also means mobs are unaffected by it in practice — they have no keys to
 * reverse. They still receive it (Air pull applies it to everything it catches), it
 * just shows as a status effect and nothing more.
 */
public class DisorientationEffect extends MobEffect {

    /** Pale wind-blue, used for the swirl of effect particles around the victim. */
    private static final int COLOR = 0x9FD8E8;

    public DisorientationEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
