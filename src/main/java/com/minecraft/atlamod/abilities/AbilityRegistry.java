package com.minecraft.atlamod.abilities;

import com.minecraft.atlamod.abilities.fire.FireBreath;
import com.minecraft.atlamod.abilities.fire.FireLeap;
import com.minecraft.atlamod.abilities.fire.FirePush;
import com.minecraft.atlamod.abilities.fire.FireRing;
import com.minecraft.atlamod.abilities.fire.BlueFire;
import com.minecraft.atlamod.abilities.fire.FireBlow;
import com.minecraft.atlamod.abilities.fire.FireImmunity;
import com.minecraft.atlamod.abilities.fire.FireRain;
import com.minecraft.atlamod.abilities.fire.FireRocket;
import com.minecraft.atlamod.abilities.fire.FireShield;
import com.minecraft.atlamod.abilities.fire.FireSpikes;
import com.minecraft.atlamod.abilities.fire.FireWhip;
import com.minecraft.atlamod.abilities.fire.TallerFire;
import com.minecraft.atlamod.abilities.water.WaterShield;
import com.minecraft.atlamod.abilities.fire.Ignite;
import com.minecraft.atlamod.abilities.fire.Firewall;
import com.minecraft.atlamod.abilities.fire.Fireball;

import java.util.HashMap;
import java.util.Map;

/**
 * Name -> Ability lookup. Populated once by bootstrap() from the Atlamod constructor.
 *
 * Adding a new ability is now a two-step job: write the class, register it here.
 * Nothing in AbilityHandler needs to change.
 */
public final class AbilityRegistry {

    private static final Map<String, Ability> ABILITIES = new HashMap<>();

    private AbilityRegistry() {
    }

    public static void bootstrap() {
        ABILITIES.clear();

        // --- FIRE : Offensive ---
        register(new FireLeap());
        register(new FireWhip());
        register(new Fireball());
        register(new FireBreath());

        // --- FIRE : Defensive ---
        register(new FirePush());
        register(new FireShield());
        register(new Firewall());
        register(new FireRing());

        // --- FIRE : Balanced ---
        register(new Ignite());
        register(new FireSpikes());
        register(new FireRocket());
        register(new TallerFire());

        // --- FIRE : Masterclass ---
        register(new BlueFire());
        register(new FireBlow());
        register(new FireImmunity());
        register(new FireRain());

        // --- WATER : Defensive ---
        register(new WaterShield());
    }

    public static void register(Ability ability) {
        ABILITIES.put(ability.getKey(), ability);
    }

    /** Returns null when the name is unknown or blank. */
    public static Ability get(String name) {
        if (name == null || name.isEmpty()) return null;
        return ABILITIES.get(name.toLowerCase());
    }

    public static Map<String, Ability> all() {
        return java.util.Collections.unmodifiableMap(ABILITIES);
    }
}
