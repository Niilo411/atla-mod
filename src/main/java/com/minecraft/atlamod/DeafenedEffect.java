package com.minecraft.atlamod;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Deafened -- the victim hears nothing at all. Applied by Deafen.
 *
 * The mod's third custom effect, and like the other two it holds NO behaviour, for
 * the same reason: sound only exists on the CLIENT. The server can no more mute
 * somebody than it can read their movement keys, so the silencing is done in
 * ClientEvents by cancelling every sound the game tries to play while the local
 * player carries this. Vanilla syncs the effect to its owner for free, so no packet
 * of our own is needed.
 *
 * That also means MOBS receive it and are unaffected by it, exactly as they are by
 * Disorientation -- they have no ears to stop. Deafen applies it to everything in
 * front regardless, so the effect shows on them and future logic can key off it.
 */
public class DeafenedEffect extends MobEffect {

    /** Muffled grey, for the swirl of particles around the victim. */
    private static final int COLOR = 0x8A8A99;

    public DeafenedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
