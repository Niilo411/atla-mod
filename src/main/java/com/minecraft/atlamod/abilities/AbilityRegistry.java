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
import com.minecraft.atlamod.abilities.air.AirAura;
import com.minecraft.atlamod.abilities.air.AirCannon;
import com.minecraft.atlamod.abilities.air.AirSplinters;
import com.minecraft.atlamod.abilities.air.Wind;
import com.minecraft.atlamod.abilities.air.WindTunnel;
import com.minecraft.atlamod.abilities.air.AirJump;
import com.minecraft.atlamod.abilities.air.AirPull;
import com.minecraft.atlamod.abilities.air.AirPush;
import com.minecraft.atlamod.abilities.air.AirScooter;
import com.minecraft.atlamod.abilities.air.AirSpout;
import com.minecraft.atlamod.abilities.air.Breathless;
import com.minecraft.atlamod.abilities.earth.EarthArmor;
import com.minecraft.atlamod.abilities.earth.EarthBlock;
import com.minecraft.atlamod.abilities.earth.EarthSpike;
import com.minecraft.atlamod.abilities.earth.EarthTrap;
import com.minecraft.atlamod.abilities.earth.EarthDig;
import com.minecraft.atlamod.abilities.earth.EarthGrab;
import com.minecraft.atlamod.abilities.earth.Earthquake;
import com.minecraft.atlamod.abilities.earth.EarthSink;
import com.minecraft.atlamod.abilities.earth.Ravine;
import com.minecraft.atlamod.abilities.earth.Mine;
import com.minecraft.atlamod.abilities.earth.Splinters;
import com.minecraft.atlamod.abilities.earth.EarthPillar;
import com.minecraft.atlamod.abilities.earth.EarthWall;
import com.minecraft.atlamod.abilities.air.Flight;
import com.minecraft.atlamod.abilities.air.Tornado;
import com.minecraft.atlamod.abilities.water.Drown;
import com.minecraft.atlamod.abilities.water.Tsunami;
import com.minecraft.atlamod.abilities.water.WaterBall;
import com.minecraft.atlamod.abilities.water.WaterBreathing;
import com.minecraft.atlamod.abilities.water.WaterBullets;
import com.minecraft.atlamod.abilities.water.WaterHeal;
import com.minecraft.atlamod.abilities.water.WaterManipulation;
import com.minecraft.atlamod.abilities.water.WaterPush;
import com.minecraft.atlamod.abilities.water.WaterShield;
import com.minecraft.atlamod.abilities.water.WaterStream;
import com.minecraft.atlamod.abilities.water.WaterSphere;
import com.minecraft.atlamod.abilities.water.WaterSurf;
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
        register(new WaterPush());
        register(new WaterHeal());

        // --- WATER : Offensive ---
        register(new WaterBall());
        register(new WaterStream());
        register(new WaterBullets());
        register(new com.minecraft.atlamod.abilities.water.ColdWater());

        // --- WATER : Balanced ---
        register(new WaterManipulation());
        register(new WaterSurf());
        register(new WaterSphere());

        // --- WATER : Masterclass ---
        register(new Drown());
        register(new WaterBreathing());
        register(new Tsunami());

        // --- AIR : Defensive ---
        register(new AirPull());
        register(new AirJump());
        register(new AirAura());
        register(new Wind());

        // --- AIR : Offensive ---
        register(new AirSplinters());
        register(new AirCannon());
        register(new WindTunnel());

        // --- AIR : Balanced ---
        register(new AirScooter());
        register(new AirPush());
        register(new AirSpout());

        // --- AIR : Masterclass ---
        register(new Breathless());
        register(new Tornado());
        register(new Flight());

        // --- EARTH : Defensive ---
        register(new EarthWall());
        register(new EarthPillar());
        register(new EarthArmor());

        // --- EARTH : Offensive ---
        register(new EarthSpike());
        register(new Splinters());
        register(new EarthBlock());
        register(new EarthTrap());

        // --- EARTH : Balanced ---
        register(new Mine());
        register(new EarthDig());
        register(new EarthGrab());

        // --- EARTH : Masterclass ---
        register(new Earthquake());
        register(new Ravine());
        register(new EarthSink());

        // --- LIGHTNING : Left (the sub-element has two paths, not four) ---
        register(new com.minecraft.atlamod.abilities.lightning.LightningRedirection());
        register(new com.minecraft.atlamod.abilities.lightning.LightningAura());
        register(new com.minecraft.atlamod.abilities.lightning.LightningJump());
        register(new com.minecraft.atlamod.abilities.lightning.LightningStrength());

        // --- LIGHTNING : Right ---
        register(new com.minecraft.atlamod.abilities.lightning.LightningBolt());
        register(new com.minecraft.atlamod.abilities.lightning.LightningBall());
        register(new com.minecraft.atlamod.abilities.lightning.LightningStun());
        register(new com.minecraft.atlamod.abilities.lightning.LightningSwarm());

        // --- ICE : Left (the second sub-element, two paths again) ---
        register(new com.minecraft.atlamod.abilities.ice.Icicles());
        register(new com.minecraft.atlamod.abilities.ice.Freeze());
        register(new com.minecraft.atlamod.abilities.ice.IceOver());
        register(new com.minecraft.atlamod.abilities.ice.IceBarrage());

        // --- ICE : Right ---
        register(new com.minecraft.atlamod.abilities.ice.IceSphere());
        register(new com.minecraft.atlamod.abilities.ice.IceBomb());
        register(new com.minecraft.atlamod.abilities.ice.FreezingBeam());
        register(new com.minecraft.atlamod.abilities.ice.IceBreath());

        // --- SOUND : Right (the third sub-element, two paths again) ---
        register(new com.minecraft.atlamod.abilities.sound.Roar());
        register(new com.minecraft.atlamod.abilities.sound.Deafen());
        register(new com.minecraft.atlamod.abilities.sound.CompressedPunches());
        register(new com.minecraft.atlamod.abilities.sound.BassWavesAbility());

        // --- SOUND : Left ---
        register(new com.minecraft.atlamod.abilities.sound.BassBounce());
        register(new com.minecraft.atlamod.abilities.sound.SoundBoosting());
        register(new com.minecraft.atlamod.abilities.sound.SoundWall());
        register(new com.minecraft.atlamod.abilities.sound.SoundLeap());

        // --- METAL : Left (the fourth sub-element, two paths again) ---
        register(new com.minecraft.atlamod.abilities.metal.MetalArmor());
        register(new com.minecraft.atlamod.abilities.metal.Crush());
        register(new com.minecraft.atlamod.abilities.metal.MetalShield());
        register(new com.minecraft.atlamod.abilities.metal.Extract());

        // --- METAL : Right ---
        register(new com.minecraft.atlamod.abilities.metal.ToughKnuckles());
        register(new com.minecraft.atlamod.abilities.metal.Bullets());
        register(new com.minecraft.atlamod.abilities.metal.StoneWalls());
        register(new com.minecraft.atlamod.abilities.metal.ArmorPierce());

        // --- COMBUSTION : Left (the fifth sub-element) ---
        register(new com.minecraft.atlamod.abilities.combustion.CombustionBombardment());
        register(new com.minecraft.atlamod.abilities.combustion.ExplosiveCombustion());
        register(new com.minecraft.atlamod.abilities.combustion.CombustionBeam());
        register(new com.minecraft.atlamod.abilities.combustion.CombustionNuke());

        // --- COMBUSTION : Right (the design has only this one so far) ---
        register(new com.minecraft.atlamod.abilities.combustion.CombustionResistance());

        // --- BLOOD : Left (the sixth sub-element) ---
        register(new com.minecraft.atlamod.abilities.blood.BloodFreeze());
        register(new com.minecraft.atlamod.abilities.blood.BloodSlow());
        register(new com.minecraft.atlamod.abilities.blood.BloodSuck());
        register(new com.minecraft.atlamod.abilities.blood.BloodManipulation());

        // --- BLOOD : Right (the design has only these two so far) ---
        register(new com.minecraft.atlamod.abilities.blood.BloodStrength());
        register(new com.minecraft.atlamod.abilities.blood.FleshShield());
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
