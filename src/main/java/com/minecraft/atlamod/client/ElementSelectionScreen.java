package com.minecraft.atlamod.client;

import com.minecraft.atlamod.network.ElementChoicePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ElementSelectionScreen extends Screen {
    // Map to link buttons to the element names for tooltips
    private final java.util.Map<Button, String> elementButtonMap = new java.util.HashMap<>();

    public ElementSelectionScreen() {
        super(Component.literal("Choose Your Element"));
    }

    @Override
    protected void init() {
        super.init();
        elementButtonMap.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int iconSize = 48; // Larger square icons for the main choice!
        int sp = 12;       // Spacing between the squares

        // 1. Fire (Top Left)
        Button fireBtn = Button.builder(Component.literal(""), b -> chooseElement("fire"))
                .bounds(cx - iconSize - (sp / 2), cy - iconSize - (sp / 2), iconSize, iconSize).build();
        elementButtonMap.put(fireBtn, "Fire");
        this.addRenderableWidget(fireBtn);

        // 2. Water (Top Right)
        Button waterBtn = Button.builder(Component.literal(""), b -> chooseElement("water"))
                .bounds(cx + (sp / 2), cy - iconSize - (sp / 2), iconSize, iconSize).build();
        elementButtonMap.put(waterBtn, "Water");
        this.addRenderableWidget(waterBtn);

        // 3. Earth (Bottom Left)
        Button earthBtn = Button.builder(Component.literal(""), b -> chooseElement("earth"))
                .bounds(cx - iconSize - (sp / 2), cy + (sp / 2), iconSize, iconSize).build();
        elementButtonMap.put(earthBtn, "Earth");
        this.addRenderableWidget(earthBtn);

        // 4. Air (Bottom Right)
        Button airBtn = Button.builder(Component.literal(""), b -> chooseElement("air"))
                .bounds(cx + (sp / 2), cy + (sp / 2), iconSize, iconSize).build();
        elementButtonMap.put(airBtn, "Air");
        this.addRenderableWidget(airBtn);
    }

    private void chooseElement(String element) {
        // 1. Update the client locally so the HUD and Upgrade Menu update instantly!
        if (this.minecraft != null && this.minecraft.player != null) {
            var data = this.minecraft.player.getData(com.minecraft.atlamod.ModAttachments.BENDING_DATA);
            data.setActiveElement(element);

            // Add it to your unlocked elements list locally just in case!
            var unlocked = data.getUnlockedElements();
            if (!unlocked.contains(element)) {
                unlocked.add(element);
            }
        }

        // 2. Send the choice to the server and close the screen
        PacketDistributor.sendToServer(new ElementChoicePacket(element));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Draw the title text at the top
        guiGraphics.drawCenteredString(this.font, "Choose Your Path", this.width / 2, this.height / 2 - 90, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // --- DRAW ICON PLACEHOLDERS & TOOLTIPS ---
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            if (renderable instanceof Button button) {
                int bx = button.getX();
                int by = button.getY();
                int bw = button.getWidth();
                int bh = button.getHeight();

                String elementName = elementButtonMap.getOrDefault(button, "");

                // Draw dark background box and border
                guiGraphics.fill(bx, by, bx + bw, by + bh, 0xFF222222);
                guiGraphics.renderOutline(bx, by, bw, bh, 0xFF555555);

                if (ElementIcons.has(elementName)) {
                    // Inset by a pixel so the emblem sits inside the border, not on it.
                    ElementIcons.draw(guiGraphics, elementName, bx + 1, by + 1, bw - 2);
                } else {
                    guiGraphics.drawCenteredString(this.font, "?",
                            bx + (bw / 2), by + (bh / 2) - 4, 0x888888);
                }

                if (button.isHovered()) {
                    String elName = elementButtonMap.getOrDefault(button, "Unknown");
                    String desc = getElementDescription(elName);

                    java.util.List<Component> tooltip = java.util.List.of(
                            Component.literal(elName).withStyle(net.minecraft.ChatFormatting.GOLD), // Colors the title!
                            Component.literal(desc)
                    );
                    guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    private String getElementDescription(String element) {
        return switch (element.toLowerCase()) {
            case "fire" -> "A hot element with great offensive capabilities.";
            case "water" -> "A fluid element focused on healing and redirection.";
            case "earth" -> "A sturdy element with strong defensive and trapping skills.";
            case "air" -> "A swift element offering high mobility and evasion.";
            default -> "An ancient bending art.";
        };
    }
}