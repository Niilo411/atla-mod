package com.minecraft.atlamod.client;

import com.minecraft.atlamod.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.ResourceLocation;

@EventBusSubscriber(modid = "atlamod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModHudOverlay {

    public static final LayeredDraw.Layer HUD_LAYER = (guiGraphics, partialTick) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var data = mc.player.getData(ModAttachments.BENDING_DATA);
        String activeElement = data.getActiveElement();

// 1. Draw Element Icon, Name, Y Key & Stats Text in the bottom left
        if (activeElement != null && !activeElement.isEmpty()) {
            String displayText = activeElement.substring(0, 1).toUpperCase() + activeElement.substring(1);
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            int iconSize = 24;
            int x = 20;
            int y = screenHeight - 45; // Shifted up slightly to make room for the icon

            // --- DRAW ICON PLACEHOLDER ---
            guiGraphics.fill(x, y, x + iconSize, y + iconSize, 0xFF222222);
            guiGraphics.renderOutline(x, y, iconSize, iconSize, 0xFF555555);
            guiGraphics.drawCenteredString(mc.font, "?", x + (iconSize / 2), y + (iconSize / 2) - 4, 0x888888);

            // --- DRAW TEXT NEXT TO THE ICON ---
            int textStartX = x + iconSize + 8;

            // Element Name
            guiGraphics.drawString(mc.font, displayText, textStartX, y + 4, 0xFFFFFF);

            // Level and XP nested right next to the element name
            String statsText = "Lvl: " + data.getLevel() + " | XP: " + data.getXp() + "/200";
            guiGraphics.drawString(mc.font, statsText, textStartX + 50, y + 4, 0x55FF55);

            // Draw "Y" Hotkey hint neatly right below the icon box
            guiGraphics.drawString(mc.font, "[Y] Switch", x, y + iconSize + 4, 0xAAAAAA);
        }
        // 2. Draw the Chi Bar above health hearts
        if (data.hasChosenElement()) {
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int screenWidth = mc.getWindow().getGuiScaledWidth();

            int barX = (screenWidth / 2) - 91;
            int barY = screenHeight - 49;
            int barWidth = 81;
            int barHeight = 5;

            int currentChi = data.getCurrentChi();
            int maxChi = data.getMaxChi();

            float fillPercentage = (float) currentChi / (float) maxChi;
            int filledWidth = (int) (barWidth * fillPercentage);

            // Draw Background (Gray)
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF444444);

            // Draw Blue Fill
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFF00AAFF);

            // Draw Chi Text (Centered above the bar)
            String chiText = currentChi + "/" + maxChi;
            guiGraphics.drawString(mc.font, chiText, barX + (barWidth / 2) - (mc.font.width(chiText) / 2), barY - 9, 0xFFFFFF);
        }
    };

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("atlamod", "bending_hud"), HUD_LAYER);
    }
}
