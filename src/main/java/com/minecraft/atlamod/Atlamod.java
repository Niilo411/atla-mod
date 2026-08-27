package com.minecraft.atlamod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Atlamod.MODID)
public class Atlamod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "atlamod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "atlamod" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "atlamod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "atlamod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "atlamod:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "atlamod:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Waterbending's fuel away from open water. See WaterCanteenItem / WaterSupply.
    // Bloodbending's key. Bought from a village cleric, read once, spent.
    public static final DeferredItem<Item> BLOOD_SCROLL = ITEMS.register("blood_scroll",
            () -> new BloodScrollItem(new Item.Properties()));

    // Lavabending's lava: never flows, never spreads, and always taken back. It is
    // UNBREAKABLE for the same reason BENDING_METAL is — every ability that places it
    // is borrowing it. No BlockItem: nothing should ever hold one.
    //
    // noCollission is what makes it behave like lava rather than like a glowing wall:
    // things fall into it rather than standing on it, and nothing suffocates in it,
    // since vanilla only smothers something inside a block that blocks motion.
    public static final DeferredBlock<Block> BENDING_LAVA = BLOCKS.register("bending_lava",
            () -> new BendingLavaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .noCollission()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 15)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()));

    // Lavabending's key. Bought from a village shepherd, read once, spent.
    public static final DeferredItem<Item> LAVA_SCROLL = ITEMS.register("lava_scroll",
            () -> new LavaScrollItem(new Item.Properties()));

    // Combustionbending's key. Bought from a village armorer, read once, spent.
    public static final DeferredItem<Item> COMBUSTION_SCROLL = ITEMS.register("combustion_scroll",
            () -> new CombustionScrollItem(new Item.Properties()));

    // Metalbending's blocks: unbreakable, and always taken back. No BlockItem —
    // nothing should ever hold one.
    public static final DeferredBlock<Block> BENDING_METAL = BLOCKS.register("bending_metal",
            () -> new BendingMetalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(-1.0F, 3600000.0F)
                    .sound(net.minecraft.world.level.block.SoundType.METAL)
                    .noLootTable()));

    // Metalbending's key. Bought from a village mason, read once, spent.
    public static final DeferredItem<Item> METAL_SCROLL = ITEMS.register("metal_scroll",
            () -> new MetalScrollItem(new Item.Properties()));

    // Soundbending's key. Bought from a village fletcher, read once, spent.
    public static final DeferredItem<Item> SOUND_SCROLL = ITEMS.register("sound_scroll",
            () -> new SoundScrollItem(new Item.Properties()));

    // Icebending's key. Bought from a village fisherman, read once, spent.
    public static final DeferredItem<Item> ICE_SCROLL = ITEMS.register("ice_scroll",
            () -> new IceScrollItem(new Item.Properties()));

    // Lightningbending's key. Bought from a village weaponsmith, read once, spent.
    public static final DeferredItem<Item> LIGHTNING_SCROLL = ITEMS.register("lightning_scroll",
            () -> new LightningScrollItem(new Item.Properties()));

    public static final DeferredItem<Item> WATER_CANTEEN = ITEMS.register("water_canteen",
            () -> new WaterCanteenItem(new Item.Properties()));

    // Ability-placed fire that vanilla can't provide: stackable, and blue on demand.
    // No BlockItem — nothing should ever hold one.
    public static final DeferredBlock<Block> BENDING_FIRE = BLOCKS.register("bending_fire",
            () -> new BendingFireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .sound(net.minecraft.world.level.block.SoundType.WOOL)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .noLootTable()));

    // Creates a new food item with the id "atlamod:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "atlamod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.atlamod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());// Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Atlamod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModEffects.register(modEventBus);
        ModEntities.register(modEventBus);

        // Populate the ability registry once, before any packet can dispatch a cast.
        com.minecraft.atlamod.abilities.AbilityRegistry.bootstrap();
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Atlamod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }

        // The mod's own items belong somewhere reachable in creative. Both are meant
        // to be earned in survival — the canteen crafted, the scroll bought — but
        // having to remember an item id to test either is needless friction.
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WATER_CANTEEN);
            event.accept(LIGHTNING_SCROLL);
            event.accept(ICE_SCROLL);
            event.accept(SOUND_SCROLL);
            event.accept(METAL_SCROLL);
            event.accept(COMBUSTION_SCROLL);
            event.accept(BLOOD_SCROLL);
            event.accept(LAVA_SCROLL);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
