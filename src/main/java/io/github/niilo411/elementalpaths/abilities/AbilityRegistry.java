package io.github.niilo411.elementalpaths.abilities;

import io.github.niilo411.elementalpaths.abilities.fire.FireBreath;
import io.github.niilo411.elementalpaths.abilities.fire.FireLeap;
import io.github.niilo411.elementalpaths.abilities.fire.FirePush;
import io.github.niilo411.elementalpaths.abilities.fire.FireRing;
import io.github.niilo411.elementalpaths.abilities.fire.BlueFire;
import io.github.niilo411.elementalpaths.abilities.fire.FireBlow;
import io.github.niilo411.elementalpaths.abilities.fire.FireImmunity;
import io.github.niilo411.elementalpaths.abilities.fire.FireRain;
import io.github.niilo411.elementalpaths.abilities.fire.FireRocket;
import io.github.niilo411.elementalpaths.abilities.fire.FireShield;
import io.github.niilo411.elementalpaths.abilities.fire.FireSpikes;
import io.github.niilo411.elementalpaths.abilities.fire.FireWhip;
import io.github.niilo411.elementalpaths.abilities.fire.TallerFire;
import io.github.niilo411.elementalpaths.abilities.air.AirAura;
import io.github.niilo411.elementalpaths.abilities.air.AirCannon;
import io.github.niilo411.elementalpaths.abilities.air.AirSplinters;
import io.github.niilo411.elementalpaths.abilities.air.Wind;
import io.github.niilo411.elementalpaths.abilities.air.WindTunnel;
import io.github.niilo411.elementalpaths.abilities.air.AirJump;
import io.github.niilo411.elementalpaths.abilities.air.AirPull;
import io.github.niilo411.elementalpaths.abilities.air.AirPush;
import io.github.niilo411.elementalpaths.abilities.air.GaleRide;
import io.github.niilo411.elementalpaths.abilities.air.AirSpout;
import io.github.niilo411.elementalpaths.abilities.air.Breathless;
import io.github.niilo411.elementalpaths.abilities.earth.EarthArmor;
import io.github.niilo411.elementalpaths.abilities.earth.EarthBlock;
import io.github.niilo411.elementalpaths.abilities.earth.EarthSpike;
import io.github.niilo411.elementalpaths.abilities.earth.EarthTrap;
import io.github.niilo411.elementalpaths.abilities.earth.EarthDig;
import io.github.niilo411.elementalpaths.abilities.earth.EarthGrab;
import io.github.niilo411.elementalpaths.abilities.earth.Earthquake;
import io.github.niilo411.elementalpaths.abilities.earth.EarthSink;
import io.github.niilo411.elementalpaths.abilities.earth.Ravine;
import io.github.niilo411.elementalpaths.abilities.earth.Mine;
import io.github.niilo411.elementalpaths.abilities.earth.Splinters;
import io.github.niilo411.elementalpaths.abilities.earth.EarthPillar;
import io.github.niilo411.elementalpaths.abilities.earth.EarthWall;
import io.github.niilo411.elementalpaths.abilities.air.Flight;
import io.github.niilo411.elementalpaths.abilities.air.Tornado;
import io.github.niilo411.elementalpaths.abilities.water.Drown;
import io.github.niilo411.elementalpaths.abilities.water.Tsunami;
import io.github.niilo411.elementalpaths.abilities.water.WaterBall;
import io.github.niilo411.elementalpaths.abilities.water.WaterBreathing;
import io.github.niilo411.elementalpaths.abilities.water.WaterBullets;
import io.github.niilo411.elementalpaths.abilities.water.WaterHeal;
import io.github.niilo411.elementalpaths.abilities.water.WaterManipulation;
import io.github.niilo411.elementalpaths.abilities.water.WaterPush;
import io.github.niilo411.elementalpaths.abilities.water.WaterShield;
import io.github.niilo411.elementalpaths.abilities.water.WaterStream;
import io.github.niilo411.elementalpaths.abilities.water.WaterSphere;
import io.github.niilo411.elementalpaths.abilities.water.WaterSurf;
import io.github.niilo411.elementalpaths.abilities.fire.Ignite;
import io.github.niilo411.elementalpaths.abilities.fire.Firewall;
import io.github.niilo411.elementalpaths.abilities.fire.Fireball;

import java.util.HashMap;
import java.util.Map;

/**
 * Name -> Ability lookup. Populated once by bootstrap() from the ElementalPaths constructor.
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
        register(new io.github.niilo411.elementalpaths.abilities.water.ColdWater());

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
        register(new GaleRide());
        register(new AirPush());
        register(new AirSpout());

        // --- AIR : Masterclass ---
        register(new Breathless());
        register(new Tornado());
        register(new Flight());

        // --- AIR : Centre (belongs to no path) ---
        register(new io.github.niilo411.elementalpaths.abilities.air.AdvancedMeditating());

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
        register(new io.github.niilo411.elementalpaths.abilities.lightning.BoltDeflection());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningAura());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningJump());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningStrength());

        // --- LIGHTNING : Right ---
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningBolt());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningBall());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningStun());
        register(new io.github.niilo411.elementalpaths.abilities.lightning.LightningSwarm());

        // --- ICE : Left (the second sub-element, two paths again) ---
        register(new io.github.niilo411.elementalpaths.abilities.ice.Icicles());
        register(new io.github.niilo411.elementalpaths.abilities.ice.Freeze());
        register(new io.github.niilo411.elementalpaths.abilities.ice.IceOver());
        register(new io.github.niilo411.elementalpaths.abilities.ice.IceBarrage());

        // --- ICE : Right ---
        register(new io.github.niilo411.elementalpaths.abilities.ice.IceSphere());
        register(new io.github.niilo411.elementalpaths.abilities.ice.IceBomb());
        register(new io.github.niilo411.elementalpaths.abilities.ice.FreezingBeam());
        register(new io.github.niilo411.elementalpaths.abilities.ice.IceBreath());

        // --- SOUND : Right (the third sub-element, two paths again) ---
        register(new io.github.niilo411.elementalpaths.abilities.sound.Roar());
        register(new io.github.niilo411.elementalpaths.abilities.sound.Deafen());
        register(new io.github.niilo411.elementalpaths.abilities.sound.CompressedPunches());
        register(new io.github.niilo411.elementalpaths.abilities.sound.BassWavesAbility());

        // --- SOUND : Left ---
        register(new io.github.niilo411.elementalpaths.abilities.sound.BassBounce());
        register(new io.github.niilo411.elementalpaths.abilities.sound.SoundBoosting());
        register(new io.github.niilo411.elementalpaths.abilities.sound.SoundWall());
        register(new io.github.niilo411.elementalpaths.abilities.sound.SoundLeap());

        // --- METAL : Left (the fourth sub-element, two paths again) ---
        register(new io.github.niilo411.elementalpaths.abilities.metal.MetalArmor());
        register(new io.github.niilo411.elementalpaths.abilities.metal.Crush());
        register(new io.github.niilo411.elementalpaths.abilities.metal.MetalShield());
        register(new io.github.niilo411.elementalpaths.abilities.metal.Extract());

        // --- METAL : Right ---
        register(new io.github.niilo411.elementalpaths.abilities.metal.ToughKnuckles());
        register(new io.github.niilo411.elementalpaths.abilities.metal.Bullets());
        register(new io.github.niilo411.elementalpaths.abilities.metal.StoneWalls());
        register(new io.github.niilo411.elementalpaths.abilities.metal.ArmorPierce());

        // --- COMBUSTION : Left (the fifth sub-element) ---
        register(new io.github.niilo411.elementalpaths.abilities.combustion.CombustionBombardment());
        register(new io.github.niilo411.elementalpaths.abilities.combustion.ExplosiveCombustion());
        register(new io.github.niilo411.elementalpaths.abilities.combustion.CombustionBeam());
        register(new io.github.niilo411.elementalpaths.abilities.combustion.CombustionNuke());

        // --- COMBUSTION : Right (the design has only this one so far) ---
        register(new io.github.niilo411.elementalpaths.abilities.combustion.CombustionResistance());

        // --- BLOOD : Left (the sixth sub-element) ---
        register(new io.github.niilo411.elementalpaths.abilities.blood.BloodFreeze());
        register(new io.github.niilo411.elementalpaths.abilities.blood.BloodSlow());
        register(new io.github.niilo411.elementalpaths.abilities.blood.BloodSuck());
        register(new io.github.niilo411.elementalpaths.abilities.blood.BloodManipulation());

        // --- BLOOD : Right (the design has only these two so far) ---
        register(new io.github.niilo411.elementalpaths.abilities.blood.BloodStrength());
        register(new io.github.niilo411.elementalpaths.abilities.blood.FleshShield());

        // --- LAVA : Left (the seventh sub-element, out of earth) ---
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaRiver());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaGeyser());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaSinkhole());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaTsunami());

        // --- LAVA : Right ---
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaWall());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaResistance());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaThrow());
        register(new io.github.niilo411.elementalpaths.abilities.lava.LavaRain());

        // --- GRAVITY : Offensive (air's second sub-element — no balanced path) ---
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityPull());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityPin());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityThrow());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravitySlam());

        // --- GRAVITY : Defensive ---
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityLevitation());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravitySpeedBoost());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityOrbit());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.RepelShield());

        // --- GRAVITY : Masterclass ---
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityPush());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityEncase());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.GravityCrush());
        register(new io.github.niilo411.elementalpaths.abilities.gravity.Meteor());

        // --- NO CHANNELING : two abilities on each arm ---
        //
        // Its CENTRE node, "Chi blocking", is deliberately absent: it is a step rather
        // than an ability, with nothing to cast and nothing to equip. Buying it only puts
        // its name in the unlocked list, which is all the tree needs to open the arms.
        register(new io.github.niilo411.elementalpaths.abilities.nochanneling.ChiBlock());
        register(new io.github.niilo411.elementalpaths.abilities.nochanneling.SwordMastery());
        register(new io.github.niilo411.elementalpaths.abilities.nochanneling.Kick());
        register(new io.github.niilo411.elementalpaths.abilities.nochanneling.QuickHands());
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
