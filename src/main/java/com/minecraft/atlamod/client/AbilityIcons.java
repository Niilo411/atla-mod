package com.minecraft.atlamod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The picture on an ability's skill tree node — a VANILLA ITEM, not artwork of ours.
 *
 * This class used to map abilities to PNGs under textures/gui/abilities/, which was the
 * right shape while custom art was expected to arrive. It was not going to: there are
 * over a hundred abilities, seven of them ever got a picture, and every other node drew
 * a "?" box that told the player nothing whatsoever about what it was. Borrowing an item
 * the player already knows the look of does most of a picture's job for none of the
 * drawing — a flint and steel for Ignite, a fishing rod for Air pull, a goat horn for
 * Roar — and it cannot go wrong the way a missing texture can, since every item here is
 * one the game has already loaded anyway.
 *
 * Nothing is SHIPPED for this. An ItemStack is rendered out of the player's own copy of
 * the game, so no pixels of Mojang's end up in our jar — the same distinction the armor
 * sheets note draws between a model that merely references a vanilla texture path and a
 * real PNG sitting in textures/.
 *
 * The KEY is the ability's display name lowercased, which is the same key the registry
 * and the cooldowns use — so the name in the skill tree, the name in AbilityRegistry and
 * the name here cannot drift apart without the icon simply not appearing.
 *
 * Adding an ability's icon is one put() line. An ability with no line falls back to the
 * old "?" box rather than to nothing at all, so a name typed wrong here shows up rather
 * than failing silently.
 */
public final class AbilityIcons {

    /**
     * The side length an ItemStack renders at, which is fixed by GuiGraphics#renderItem.
     *
     * Everything here is scaled from this figure, exactly as the PNG version scaled from
     * its 256. Kept as a constant because it is the game's number rather than ours, and
     * nothing in this class may quietly assume a different one.
     */
    private static final int ITEM_SIZE = 16;

    private static final Map<String, Item> ICONS = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // FIRE
        // ------------------------------------------------------------------
        put("Fire leap", Items.MAGMA_CREAM);              // bouncing, and molten
        put("Fire whip", Items.BLAZE_ROD);
        put("Fireball", Items.FIRE_CHARGE);
        put("Fire Breath", Items.DRAGON_BREATH);
        put("Fire push", Items.BLAZE_POWDER);
        put("Fire shield", Items.SHIELD);
        put("Firewall", Items.CAMPFIRE);                  // a line of fire on the ground
        put("Fire ring", Items.MAGMA_BLOCK);
        put("Ignite", Items.FLINT_AND_STEEL);
        put("Fire spikes", Items.POINTED_DRIPSTONE);
        put("Fire rocket", Items.FIREWORK_ROCKET);
        put("Taller fire", Items.TORCH);                  // fire standing up
        put("blue fire", Items.SOUL_CAMPFIRE);            // the game's only blue flame
        put("Fire blow", Items.FIREWORK_STAR);            // a fanning burst
        put("Fire immunity", Items.NETHERITE_CHESTPLATE); // the fireproof metal
        put("Fire Rain", Items.LAVA_BUCKET);

        // ------------------------------------------------------------------
        // WATER
        // ------------------------------------------------------------------
        put("Water ball", Items.HEART_OF_THE_SEA);        // a round blue mass
        put("Water stream", Items.PRISMARINE_CRYSTALS);
        put("Water Bullets", Items.PRISMARINE_SHARD);
        put("Cold water", Items.ICE);                     // the passive's own source
        put("Water shield", Items.SHIELD);
        put("Water push", Items.WATER_BUCKET);
        put("Water heal", Items.GLISTERING_MELON_SLICE);  // vanilla's healing ingredient
        put("Water Manipulation", Items.BUCKET);          // picking a source block up
        put("Water Surf", Items.OAK_BOAT);
        put("Water Sphere", Items.CONDUIT);               // vanilla's own pocket of air
        put("Drown", Items.PUFFERFISH);
        put("water breathing", Items.TURTLE_HELMET);      // vanilla grants it the same way
        put("Tsunami", Items.TRIDENT);

        // ------------------------------------------------------------------
        // AIR
        // ------------------------------------------------------------------
        put("Air splinters", Items.ARROW);
        put("Air cannon", Items.WIND_CHARGE);             // one heavy blast of air
        put("wind tunnel", Items.HOPPER);                 // a funnel
        put("Air pull", Items.FISHING_ROD);               // drags things towards you
        put("Air jump", Items.RABBIT_FOOT);
        put("Air Aura", Items.PHANTOM_MEMBRANE);
        put("Wind", Items.FEATHER);
        put("Air scooter", Items.SADDLE);                 // a ride
        put("Airpush", Items.PISTON);                     // throws things away
        put("Air spout", Items.END_ROD);                  // a standing column
        put("breathless", Items.GLASS_BOTTLE);            // an empty one
        put("Tornado", Items.BREEZE_ROD);
        put("Flight", Items.ELYTRA);
        put("Advanced meditating", Items.EXPERIENCE_BOTTLE); // what meditating is for

        // ------------------------------------------------------------------
        // EARTH
        // ------------------------------------------------------------------
        put("Earth spike", Items.POINTED_DRIPSTONE);      // the ability's own tip block
        put("Splinters", Items.FLINT);
        put("Earth block", Items.STONE);
        put("Earth trap", Items.STONE_SLAB);              // the ability's own slab
        put("Earth wall", Items.COBBLESTONE_WALL);
        put("Earth pillar", Items.BASALT);                // a column
        put("Earth armor", Items.CHAINMAIL_CHESTPLATE);
        put("Mine", Items.IRON_PICKAXE);
        put("Earth dig", Items.IRON_SHOVEL);
        put("Earth grab", Items.LEAD);                    // hauls things to your feet
        put("Earthquake", Items.CRACKED_STONE_BRICKS);
        put("Ravine", Items.DEEPSLATE);
        put("Earth sink", Items.GRAVEL);                  // ground that swallows you

        // ------------------------------------------------------------------
        // LIGHTNING
        // ------------------------------------------------------------------
        put("Lightning redirection", Items.LIGHTNING_ROD);
        put("Lightning aura", Items.REDSTONE);
        put("Lightning Jump", Items.ENDER_PEARL);
        put("Lightning Strength", Items.REDSTONE_BLOCK);
        put("Lightning bolt", Items.TRIDENT);             // Channeling calls one down
        put("Lightning ball", Items.ENDER_EYE);           // a hovering orb
        put("Lightning stun", Items.GLOWSTONE_DUST);      // the white-out
        put("Lightning Swarm", Items.CHAIN);              // chain lightning

        // ------------------------------------------------------------------
        // ICE
        // ------------------------------------------------------------------
        put("icicles", Items.POINTED_DRIPSTONE);          // the ability's own tip block
        put("Freeze", Items.BLUE_ICE);
        put("Ice over", Items.PACKED_ICE);                // what the ability lays
        put("Ice barrage", Items.SNOWBALL);
        put("Ice sphere", Items.ICE);
        put("Ice Bomb", Items.TNT);
        put("Freezing Beam", Items.AMETHYST_SHARD);
        put("Ice Breath", Items.POWDER_SNOW_BUCKET);

        // ------------------------------------------------------------------
        // SOUND
        // ------------------------------------------------------------------
        put("Bass Bounce", Items.SLIME_BLOCK);
        put("Sound boosting", Items.NOTE_BLOCK);
        put("Sound wall", Items.GLASS);                   // solid, and you see through it
        put("Sound Leap", Items.FIREWORK_ROCKET);
        put("Roar", Items.GOAT_HORN);
        put("Deafen", Items.SCULK_SHRIEKER);
        put("Compressed punches", Items.PISTON);
        put("Bass waves", Items.SCULK_SENSOR);            // vanilla's own vibration reader

        // ------------------------------------------------------------------
        // METAL
        // ------------------------------------------------------------------
        put("Metal armor", Items.IRON_CHESTPLATE);
        put("Crush", Items.ANVIL);
        put("Metal shield", Items.IRON_DOOR);             // a metal slab to stand behind
        put("Extract", Items.RAW_IRON);                   // iron drawn out of the ground
        put("Tough knuckles", Items.IRON_INGOT);
        put("Bullets", Items.IRON_NUGGET);                // twenty little slugs
        put("Stone walls", Items.COBBLESTONE_WALL);
        put("Armor pierce", Items.LIGHTNING_ROD);         // a thrown metal rod

        // ------------------------------------------------------------------
        // COMBUSTION
        // ------------------------------------------------------------------
        put("Combustion bombardment", Items.FIRE_CHARGE);
        put("Explosive combustion", Items.TNT);
        put("Combustion Beam", Items.ENDER_EYE);          // the third eye
        put("Combustion nuke", Items.NETHER_STAR);
        put("Combustion resistance", Items.OBSIDIAN);     // the blast-proof block

        // ------------------------------------------------------------------
        // BLOOD
        // ------------------------------------------------------------------
        put("Blood freeze", Items.COBWEB);                // held where you stand
        put("Blood Slow", Items.SOUL_SAND);
        put("Blood suck", Items.GHAST_TEAR);
        put("Blood manipulation", Items.STRING);          // puppet strings
        put("Blood strength", Items.BLAZE_POWDER);        // vanilla's strength ingredient
        put("Flesh shield", Items.ROTTEN_FLESH);

        // ------------------------------------------------------------------
        // LAVA
        // ------------------------------------------------------------------
        put("Lava river", Items.LAVA_BUCKET);
        put("Lava geyser", Items.MAGMA_BLOCK);            // vanilla's own bubble column
        put("Lava sinkhole", Items.CAULDRON);             // a pit that holds lava
        put("Lava tsunami", Items.MAGMA_CREAM);
        put("Lava wall", Items.BLACKSTONE_WALL);
        put("Lava resistance", Items.NETHERITE_BOOTS);    // for walking through your work
        put("Lava throw", Items.FIRE_CHARGE);
        put("lava rain", Items.DRIPSTONE_BLOCK);          // vanilla's own way to drip lava

        // ------------------------------------------------------------------
        // ENERGY — the Avatar's own, which has no tree of its own to sit in
        // ------------------------------------------------------------------
        put("Give and take", Items.TOTEM_OF_UNDYING);
    }

    private AbilityIcons() {
    }

    private static void put(String ability, Item item) {
        ICONS.put(ability.toLowerCase(Locale.ROOT), item);
    }

    /** Whether this ability has an icon to draw. */
    public static boolean has(String ability) {
        return ability != null && ICONS.containsKey(ability.toLowerCase(Locale.ROOT));
    }

    /**
     * Draws the ability's icon into a square of {@code size} at {@code x, y}.
     *
     * Scaled through the pose stack, exactly as the PNG version was and for a related
     * reason: renderItem draws at a fixed 16x16 and takes no size of its own, so scaling
     * around it is the only way to fill a node that is not exactly that.
     *
     * The Z axis is scaled along with the other two deliberately. renderItem pushes the
     * item to z=150 itself so it lands above the GUI behind it, and a block model has
     * real depth — scaling x and y alone would squash every block icon flat. Scaling all
     * three moves that 150 by the same factor, which is harmless at the sizes a node uses
     * and still leaves the item well clear of everything drawn at z=0.
     */
    public static void draw(GuiGraphics graphics, String ability, int x, int y, int size) {
        Item item = ICONS.get(ability.toLowerCase(Locale.ROOT));
        if (item == null) return;

        float scale = size / (float) ITEM_SIZE;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, scale);

        graphics.renderItem(new ItemStack(item), 0, 0);

        graphics.pose().popPose();
    }
}
