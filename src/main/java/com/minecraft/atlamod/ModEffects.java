package com.minecraft.atlamod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own status effects.
 *
 * First one is Disorientation (Air pull), and there is no vanilla effect that comes
 * close — nothing in the game reverses a player's controls — so it has to be a real
 * registered MobEffect rather than a repurposed vanilla one. Registering it properly
 * also buys the whole status-effect UI for free: the inventory icon, the timer, the
 * particles, and automatic syncing to the affected player's client, which is exactly
 * where the reversal has to happen.
 */
public final class ModEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Atlamod.MODID);

    /** Reverses the victim's movement keys. See DisorientationEffect. */
    public static final DeferredHolder<MobEffect, MobEffect> DISORIENTATION =
            EFFECTS.register("disorientation", DisorientationEffect::new);

    /** Stops the victim moving outright. See StunnedEffect. */
    public static final DeferredHolder<MobEffect, MobEffect> STUNNED =
            EFFECTS.register("stunned", StunnedEffect::new);

    /**
     * Ten points of armor, worn as stone. The bonus is declared here rather than in
     * the effect class because vanilla wants it as an attribute modifier on the
     * MobEffect itself — it then applies and removes it in step with the effect, which
     * is a great deal safer than adding one by hand and hoping to remember to take it
     * off again.
     */
    public static final DeferredHolder<MobEffect, MobEffect> EARTH_ARMOR =
            EFFECTS.register("earth_armor", () -> new EarthArmorEffect().addAttributeModifier(
                    net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            Atlamod.MODID, "earth_armor"),
                    10.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));

    private ModEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
