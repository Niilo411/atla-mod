package io.github.niilo411.elementalpaths;

import io.github.niilo411.elementalpaths.client.PathsSettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing io.github.niilo411.elementalpaths.client side code from here is safe.
@Mod(value = ElementalPaths.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ElementalPaths.MODID, value = Dist.CLIENT)
public class ElementalPathsClient {
    public ElementalPathsClient(ModContainer container) {
        // What the Config button on the Mods screen opens. OUR screen rather than
        // NeoForge's generic one, which this replaced — see PathsSettingsScreen for why a
        // hundred and six ability switches and a set of generation rates are worth a
        // screen of their own. The same screen is reachable from the paths menu, so
        // there is one settings UI rather than two that could disagree.
        // The second argument is the mod list screen, which is what Done goes back to.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, modListScreen) -> new PathsSettingsScreen(modListScreen));
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
    }
}