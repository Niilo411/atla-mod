package com.minecraft.atlamod.client;

import com.minecraft.atlamod.abilities.AbilityRegistry;
import com.minecraft.atlamod.abilities.PassiveAbility;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One line saying what an ability actually does, for its skill tree tooltip.
 *
 * The tree used to name an ability, price it and say whether it could be bought, which
 * told a player everything except the one thing they were deciding on. Sixty levels is a
 * long way to walk to find out what "Ice over" is.
 *
 * Keyed on the ability's display name lowercased, the same key the registry, the
 * cooldowns and {@link AbilityIcons} use — so the name in the skill tree, the name in
 * AbilityRegistry and the line here cannot drift apart without the description simply not
 * appearing.
 *
 * PASSIVES ARE NOT IN THE TABLE, deliberately. They already answer
 * {@link PassiveAbility#getDescription()}, which the Passives tab has always shown, and
 * {@link #of} asks the registry before it looks here. Copying those thirteen lines into
 * this file would be thirteen pairs of sentences free to disagree with each other, and
 * the one beside the code is the one that gets corrected when the ability changes.
 */
public final class AbilityDescriptions {

    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // FIRE
        // ------------------------------------------------------------------
        put("Fire leap", "Leap forward on a burst of flame, leaving a trail of fire behind you");
        put("Fire whip", "Lash a whip of fire from your hand, burning whatever it touches");
        put("Fireball", "Hold to build a fireball, then left click to throw it. Explodes on impact");
        put("Fire Breath", "Hold to breathe a cone of flame, damaging and igniting everything in a 6 block line");
        put("Fire push", "Throw everything in a wide cone ahead of you back 6 blocks, alight and hurt");
        put("Fire shield", "Hold to stand behind a wall of flame. Nothing but falling and the void gets through");
        put("Firewall", "Lay a 6 block line of fire across the ground ahead of you");
        put("Fire ring", "Ring yourself with fire at 4 blocks. It burns three times as hot for 30s");
        put("Ignite", "Set light to whatever you look at, up to 20 blocks. Fuels a furnace instead if aimed at one");
        put("Fire spikes", "Hold to charge, then scatter fire across the ground out to 15 blocks");
        put("Fire rocket", "Hold to fly, venting flame from your feet. The height you gain is yours to survive");
        put("Fire blow", "Hold up to 10s and release: a wall of flame fans out, reaching further the longer you held");
        put("Fire Rain", "Set the sky burning for 30s over 50 blocks. Everything beneath it burns, you included");

        // ------------------------------------------------------------------
        // WATER
        // ------------------------------------------------------------------
        put("Water ball", "Hold to gather water, then left click to throw it for a heavy shove");
        put("Water stream", "Tear a stream out of water you can see, then throw it within 3s");
        put("Water Bullets", "Hold three bullets ready and fire them one per click. They keep until spent");
        put("Water shield", "Hold to wheel four masses of water around you. Nothing gets through, and it slows anything near");
        put("Water push", "Shove everything in a wide cone 6 blocks back and leave it slowed. No damage at all");
        put("Water heal", "Hold while standing in water to regenerate. Ends the moment you wade out");
        put("Water Manipulation", "Take hold of a water source block. It rides your crosshair until you set it down");
        put("Water Surf", "Ride the waterline wherever you look, lying flat along it. Ends when the water does");
        put("Water Sphere", "Hold the sea back in a 5 block bubble so the ocean floor can be walked");
        put("Drown", "Hold up to 5s and release: empty a victim's lungs for up to 15s. Ends if you lose sight of them");
        put("Tsunami", "Hold 3s, then roll a wall of water 9 wide and 4 high 20 blocks out, carrying everything with it");

        // ------------------------------------------------------------------
        // AIR
        // ------------------------------------------------------------------
        put("Air splinters", "Hold to gather six blades of air, then loose them one per click. Fastest shots in the mod");
        put("Air cannon", "Hold 4s, then left click for one heavy blast of air. The opposite trade to Air splinters");
        put("wind tunnel", "Hold a funnel of wind on a 12 block cone. Nothing caught in it can close the distance");
        put("Air pull", "Drag everything in a cone 12 blocks ahead towards you, disoriented. No damage at all");
        put("Air jump", "Hold up to 2s and release: leap 5 to 20 blocks straight up, and take no fall damage");
        put("Air Aura", "Hold a shell of racing wind that turns projectiles aside and cancels falls. Melee still lands");
        put("Wind", "One enormous gust across everything you can see, for a scratch and 20s of slowness");
        put("Air scooter", "Press to ride a ball of air where you look, a block off the ground. Press again to step off");
        put("Airpush", "Air pull turned around: throw everything in the cone away, disoriented and hurt");
        put("Air spout", "Hold 3s, then set down three tornadoes one per click. Each stands 10 blocks for a minute");
        put("breathless", "Hold up to 3s and release: pull the air from a victim's lungs for up to 15s, and disorient them");
        put("Tornado", "Press to raise a 20 block tornado that follows your crosshair. Press again to put it down");

        // ------------------------------------------------------------------
        // EARTH
        // ------------------------------------------------------------------
        put("Earth spike", "Drive a 3 block spike up wherever you look. Hurts anything beside it, you included");
        put("Splinters", "Hold to gather six shards of stone, then throw them one per click. Needs good aim");
        put("Earth block", "Pull a real block out of the ground onto your crosshair, then throw it");
        put("Earth trap", "A slab closes over the feet of everything in sight within 20 blocks, holding it 10s");
        put("Earth wall", "Hold to raise a wall 6 across, a block a second up to 7. It stands 30s, then sinks");
        put("Earth pillar", "Earth wall narrowed to one column, and faster. Rises up to 7 and stands 30s");
        put("Earth armor", "Wear a suit of stone over your own gear: 10 extra armour for two minutes");
        put("Mine", "Hold up to 5s and release: break 1 block at a tap, up to 30 at a full charge. They drop");
        put("Earth dig", "Press to become a drill and tunnel underground, steered by the mouse. Ends when you surface");
        put("Earth grab", "A wall rises 20 blocks out and rolls home, hauling everything it washes over to your feet");
        put("Earthquake", "30s of heavy slowness and disorientation on everything within 20 blocks, in every direction");
        put("Ravine", "Tear the ground open ahead: 10 blocks out, 5 deep, 5 across. Permanent, and nothing drops");
        put("Earth sink", "Open a pit ahead, hurt everything over it, then close the ground back over whatever fell in");

        // ------------------------------------------------------------------
        // LIGHTNING
        // ------------------------------------------------------------------
        put("Lightning redirection", "Hold to catch a bolt thrown at you, then left click to send it back as hard as it came");
        put("Lightning aura", "Hold a field of current around you, shocking everything near once a second");
        put("Lightning Jump", "Flash forward along the ground. Stops at the first wall rather than passing through it");
        put("Lightning bolt", "Five seconds of wind-up for the hardest single shot in the mod");
        put("Lightning ball", "Send out a ball of current that rides your crosshair. Press again to call it back");
        put("Lightning stun", "Hold a victim where they stand and white out their screen. They cannot act at all");
        put("Lightning Swarm", "Strike everything around you at once, the damage shared out among however many there are");

        // ------------------------------------------------------------------
        // ICE
        // ------------------------------------------------------------------
        put("icicles", "Gather shards of ice, then loose five of them on a left click");
        put("Freeze", "Seal a victim in ice for 10s. They cannot act, and nothing can hurt them either");
        put("Ice over", "Turn the ground around you to ice. The floor stays exactly as tall as it was");
        put("Ice barrage", "Rain forty icicles over 30 blocks, with a handful aimed at up to six chosen targets");
        put("Ice sphere", "Seal yourself in a shell of ice");
        put("Ice Bomb", "Set down a bomb of ice with a 2s fuse. It cannot be aimed at anything that moves");
        put("Freezing Beam", "Press to hold a beam of cold on whatever you aim at for 20s. Press again to switch it off");
        put("Ice Breath", "Hold to breathe freezing air, stunning for a second out of every two");

        // ------------------------------------------------------------------
        // SOUND
        // ------------------------------------------------------------------
        put("Bass Bounce", "Hop into the air and slam down on landing, shaking everything around where you land");
        put("Sound wall", "Press to raise a wall of sound. It shoves anything back and eats arrows, but you pass freely");
        put("Sound Leap", "Launch yourself across the ground on a burst of sound, and land without hurting yourself");
        put("Roar", "Shout down everything ahead of you: a couple of hearts, and 5s of disorientation");
        put("Deafen", "Silence everything a victim hears for 10s, and lock them out of bending for the same");
        put("Compressed punches", "Press to arm your fists: every punch hits harder and throws a wave whether it lands or not");
        put("Bass waves", "Pins you for 15s while rings of sound roll out every few seconds, stunning what they cross");

        // ------------------------------------------------------------------
        // METAL
        // ------------------------------------------------------------------
        put("Metal armor", "Wear a suit of unbreakable metal over your own gear, for 15 extra armour");
        put("Crush", "Pin everything in a corridor ahead, then close two walls of ground in on it from both sides");
        put("Metal shield", "Press to hold a wall of metal on your crosshair. Left click to throw it, press again to drop it");
        put("Extract", "Hold to draw iron out of the ground. Takes no block with it");
        put("Bullets", "Summon twenty metal slugs and fire them one per click. Press again to put the magazine down");
        put("Stone walls", "Send a wall of ground away from you, damaging everything in the corridor it crosses");
        put("Armor pierce", "Sharpen a rod and throw it. It destroys the armour it hits, or deals real damage if there is none");

        // ------------------------------------------------------------------
        // COMBUSTION
        // ------------------------------------------------------------------
        put("Combustion bombardment", "Throw a volley of charges that explode where they land");
        put("Explosive combustion", "One charge, one much larger blast");
        put("Combustion Beam", "Press to hold a beam from your third eye. It burns what it touches and eats through walls");
        put("Combustion nuke", "Ten seconds of rising wind-up, then a line of four enormous blasts. Costs 1000 chi");

        // ------------------------------------------------------------------
        // BLOOD
        // ------------------------------------------------------------------
        put("Blood freeze", "Hold a victim still for 5s while they bleed a heart a second");
        put("Blood Slow", "Sweep the crosshair across a group and leave everything it crosses barely able to move");
        put("Blood suck", "Hold to take a victim's health for your own. The most expensive channel in the mod, and capped at 3s");
        put("Blood manipulation", "Take a victim as a puppet and move them where you please. Glancing away does not drop them");
        put("Flesh shield", "Gather bodies into a wall on your crosshair. A blow is shared among whoever is still alive in it");

        // ------------------------------------------------------------------
        // LAVA
        // ------------------------------------------------------------------
        put("Lava river", "Pour a river of lava 20 blocks ahead. It drains from the near end after 15s");
        put("Lava geyser", "Set down three geysers one per click. Each erupts every 2s and throws everyone, you included");
        put("Lava sinkhole", "Open a pit 5 across and 4 deep under a target and fill it with lava. Both are given back");
        put("Lava tsunami", "Hold 5s, then roll a wall of lava 30 blocks out. Anything near it should see it coming");
        put("Lava wall", "Raise a wall of lava 3 blocks high ahead of you");
        put("Lava throw", "Hold 2s to gather four blobs, then throw them. The only lava in the element that stays");
        put("lava rain", "Rain lava over 15 blocks. Each puddle cools after 5s, and all of it is gone when the storm ends");

        // ------------------------------------------------------------------
        // ENERGY — the Avatar's own
        // ------------------------------------------------------------------
        put("Give and take", "The Avatar's own bending: give a bender their power, or take it away");

        // ------------------------------------------------------------------
        // NO BENDING — the path for people who have none
        // ------------------------------------------------------------------
        put("Chi blocking", "Not an ability. Learning to find the chi points at all, which is what opens both paths");
        put("Chi block", "Mark a target for 10s, then land 5 hits on them. Shuts their chi off for 15s: no bending, no regen, and slowed");
        put("Kick", "A hard kick. 4 hearts to everything within 2 blocks in front, and it sends them 5 blocks back");
    }

    private AbilityDescriptions() {
    }

    private static void put(String ability, String description) {
        DESCRIPTIONS.put(ability.toLowerCase(Locale.ROOT), description);
    }

    /**
     * What this ability does, or null if nothing has been written for it.
     *
     * The registry is asked FIRST so a passive's own getDescription wins: those thirteen
     * lines live beside the code that reads them and are the ones kept up to date when
     * the ability changes. Everything else comes out of the table here.
     *
     * Null rather than a placeholder, so the caller can leave the line out entirely — a
     * tooltip reading "No description" is worse than a tooltip that does not claim to
     * have one.
     */
    public static String of(String ability) {
        if (ability == null) return null;

        if (AbilityRegistry.get(ability) instanceof PassiveAbility passive) {
            return passive.getDescription();
        }
        return DESCRIPTIONS.get(ability.toLowerCase(Locale.ROOT));
    }
}
