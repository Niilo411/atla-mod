package com.minecraft.atlamod.client;

import com.minecraft.atlamod.network.ElementChoicePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class ElementSelectionScreen extends Screen {

    /**
     * Which button chooses which path, keyed by the ELEMENT KEY rather than its label.
     *
     * The key is what the emblem table, the packet and every path list are all named by,
     * so keeping it here means the button carries the one string that agrees with all of
     * them. The pretty name is derived from it by {@link #label}, which used to be the
     * other way round — and that broke the moment a path's name was not simply its key
     * with a capital letter, which "No bending" is not.
     */
    private final java.util.Map<Button, String> elementButtonMap = new java.util.HashMap<>();

    /** The four bending arts. */
    private static final int ICON_SIZE = 48;

    /**
     * How far apart the four sit, centre to centre of the gap.
     *
     * WIDENED FROM 12 to make room for the No bending button in the middle. That button
     * is {@link #CENTRE_SIZE} across, so the gap has to be at least that plus a margin, or
     * the four arts would sit on top of it.
     */
    private static final int SPACING = 34;

    /**
     * The No bending button, deliberately smaller than the four arts.
     *
     * Size is meaning here rather than decoration: the four elements are what this screen
     * is for, and the fifth choice is an opt OUT of them. Drawing it the same size would
     * present it as a fifth element, which is the one thing it is not.
     */
    private static final int CENTRE_SIZE = 28;

    /** The path key for choosing no bending at all. */
    public static final String NO_BENDING = "nobending";

    public ElementSelectionScreen() {
        super(Component.literal("Choose Your Element"));
    }

    @Override
    protected void init() {
        super.init();
        elementButtonMap.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int iconSize = ICON_SIZE;
        int sp = SPACING;

        // 1. Fire (Top Left)
        add("fire", cx - iconSize - (sp / 2), cy - iconSize - (sp / 2), iconSize);
        // 2. Water (Top Right)
        add("water", cx + (sp / 2), cy - iconSize - (sp / 2), iconSize);
        // 3. Earth (Bottom Left)
        add("earth", cx - iconSize - (sp / 2), cy + (sp / 2), iconSize);
        // 4. Air (Bottom Right)
        add("air", cx + (sp / 2), cy + (sp / 2), iconSize);

        // 5. No bending, in the hole the four leave in the middle. Added LAST so it is
        // the last thing drawn and the last thing offered a click, which matters because
        // the four are drawn from the same list and a widget added later wins an overlap.
        // Nothing actually overlaps at these sizes, but the ordering costs nothing and
        // stops a future change to SPACING turning a near miss into an unclickable button.
        add(NO_BENDING, cx - (CENTRE_SIZE / 2), cy - (CENTRE_SIZE / 2), CENTRE_SIZE);
    }

    private void add(String element, int x, int y, int size) {
        Button button = Button.builder(Component.literal(""), b -> chooseElement(element))
                .bounds(x, y, size, size).build();

        elementButtonMap.put(button, element);
        this.addRenderableWidget(button);
    }

    /** What a path is called on screen. Shared with the HUD, so the two cannot disagree. */
    private static String label(String element) {
        String name = com.minecraft.atlamod.abilities.ElementPaths.displayName(element);
        return name.isEmpty() ? "Unknown" : name;
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
        // THE BACKGROUND GOES FIRST AND EXACTLY ONCE. It ends in
        // Screen#renderBlurredBackground, which is a post-process over the whole
        // framebuffer — it blurs whatever has already been drawn, the GUI included. This
        // used to call it here AND again through super.render, with the title drawn
        // between the two, so the title was the one thing on the screen that got smeared.
        // The emblems escaped only because they are drawn further down, after both.
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Draw the title text at the top
        guiGraphics.drawCenteredString(this.font, "Choose Your Path", this.width / 2, this.height / 2 - 90, 0xFFFFFF);

        // super.render would repeat the background. Its other half is just the widgets,
        // so they are drawn directly instead.
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // --- DRAW ICON PLACEHOLDERS & TOOLTIPS ---
        for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
            if (renderable instanceof Button button) {
                int bx = button.getX();
                int by = button.getY();
                int bw = button.getWidth();
                int bh = button.getHeight();

                String element = elementButtonMap.getOrDefault(button, "");

                // Draw dark background box and border
                guiGraphics.fill(bx, by, bx + bw, by + bh, 0xFF222222);
                guiGraphics.renderOutline(bx, by, bw, bh, 0xFF555555);

                if (ElementIcons.has(element)) {
                    // Inset by a pixel so the emblem sits inside the border, not on it.
                    ElementIcons.draw(guiGraphics, element, bx + 1, by + 1, bw - 2);
                } else {
                    guiGraphics.drawCenteredString(this.font, "?",
                            bx + (bw / 2), by + (bh / 2) - 4, 0x888888);
                }

                if (button.isHovered()) {
                    java.util.List<Component> tooltip = java.util.List.of(
                            Component.literal(label(element)).withStyle(net.minecraft.ChatFormatting.GOLD),
                            Component.literal(getElementDescription(element))
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
            case NO_BENDING -> "No bending at all. Learn to shut it off in others instead.";
            default -> "An ancient bending art.";
        };
    }
}