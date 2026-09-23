package com.minecraft.atlamod.client;

import com.minecraft.atlamod.BendingData;
import com.minecraft.atlamod.ModAttachments;
import com.minecraft.atlamod.network.EquipAbilityPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class UpgradeMenuScreen extends Screen {
    private String activeElement = "";
    private final java.util.Map<Button, AbilityNode> nodeMap = new java.util.HashMap<>();

    /**
     * The one widget here that is not a skill tree node.
     *
     * Kept as a field purely so the render pass can tell it apart: that loop hides every
     * widget on the tabs the tree is not drawn on, and without a way to name this one it
     * would vanish along with the nodes.
     */
    private Button settingsButton;

    // --- RESTORED VARIABLES & RECORD ---
    // 0 = Skill Tree, 1 = Equip Abilities, 2 = Passives
    private int activeTab = 0;

    private static final int TAB_W = 90;
    private static final int TAB_H = 20;
    private static final int TAB_Y = 12;
    private static final String[] TAB_NAMES = { "Skill Tree", "Equip Abilities", "Passives" };
    private String selectedAbilityToEquip = null;
    private String selectedPassiveToEquip = null;

    /**
     * The eight equip slots' key hints, read live off the actual KeyMappings rather
     * than the hardcoded "Z, X, C, V" this used to be — a player who has rebound
     * their ability keys in Controls sees THEIR keys here, not the defaults. Slots
     * 4-7 mirror the first four with a Shift prefix, since there is no separate
     * KeyMapping for the shifted slots to read from.
     */
    private static String[] slotLabels() {
        String[] base = {
                com.minecraft.atlamod.KeyBindings.ABILITY_1.getTranslatedKeyMessage().getString(),
                com.minecraft.atlamod.KeyBindings.ABILITY_2.getTranslatedKeyMessage().getString(),
                com.minecraft.atlamod.KeyBindings.ABILITY_3.getTranslatedKeyMessage().getString(),
                com.minecraft.atlamod.KeyBindings.ABILITY_4.getTranslatedKeyMessage().getString(),
        };
        return new String[] {
                base[0], base[1], base[2], base[3],
                "Shift + " + base[0], "Shift + " + base[1], "Shift + " + base[2], "Shift + " + base[3],
        };
    }

    /**
     * What "I have picked this one, now choose a slot" looks like, in both equip tabs.
     *
     * BLUE ON PURPOSE, and the choice is about what the other colours in this screen
     * already mean. Green is the active tab, orange is a slot that is FILLED, and grey is
     * everything at rest — so a selection highlight in orange said the same thing as a
     * full slot, which is what the passives tab used to do. Blue is the only signal here
     * that means nothing else.
     *
     * Shared by the ability list and the passive list rather than written twice, because
     * the two lists are the same control doing the same job and should not be able to
     * drift apart.
     */
    private static final int SELECTED_BORDER = 0xFF55AAFF;

    /** The fill behind a selected row. Dark enough that white text stays readable on it. */
    private static final int SELECTED_FILL = 0xFF14304C;

    /** The same row at rest. */
    private static final int ROW_BORDER = 0xFF777777;
    private static final int ROW_FILL = 0xFF222222;
    private record AbilityNode(String name, String path, int index, int cost) {}

    /**
     * How far the icon sits inside its node.
     *
     * Three pixels, one of which is the border itself, so two pixels of the node's path
     * colour stay visible all the way round the icon. That ring is the ONLY place the
     * path colour shows once the icon is drawn, so it cannot go to zero.
     */
    private static final int ICON_INSET = 3;

    /**
     * The depth the "not yet yours" veil is drawn at.
     *
     * GuiGraphics#renderItem pushes an item to z=150 itself, and AbilityIcons scales that
     * by the node size, so anything meant to sit ON TOP of an icon has to say so. Well
     * clear of the item and well below the 400 vanilla draws tooltips at.
     */
    private static final int ICON_Z = 250;

    /**
     * The depth the tab strip is drawn at.
     *
     * Above ICON_Z so a node reaching up into the tabs cannot show through them, and well
     * below the 400 vanilla draws tooltips at so a tooltip still covers the strip.
     */
    private static final int TAB_Z = 300;

    public UpgradeMenuScreen() {
        super(Component.literal("Upgrade Menu"));
    }

    @Override
    protected void init() {
        super.init();
        nodeMap.clear();

        if (this.minecraft != null && this.minecraft.player != null) {
            // FIX: Force update activeElement whenever the screen opens or reloads
            this.activeElement = this.minecraft.player.getData(ModAttachments.BENDING_DATA).getActiveElement();
            if (this.activeElement == null) this.activeElement = "";
        }

        int cx = this.width / 2;
        int cy = this.height / 2;
        int iconSize = 24;
        int sp = 8;

        String[] off = getOffensive(activeElement);
        String[] def = getDefensive(activeElement);
        String[] bal = getBalanced(activeElement);
        String[] mas = getMaster(activeElement);

        // Build Left Path (Offensive)
        for(int i = 0; i < off.length; i++) {
            int x = cx - (iconSize / 2) - ((i + 1) * (iconSize + sp));
            int y = cy - (iconSize / 2);
            AbilityNode node = new AbilityNode(off[i], "offensive", i, getCost(i));
            Button btn = Button.builder(Component.literal(""), b -> attemptBuy(node)).bounds(x, y, iconSize, iconSize).build();
            nodeMap.put(btn, node);
            this.addRenderableWidget(btn);
        }

        // Build Right Path (Defensive)
        for(int i = 0; i < def.length; i++) {
            int x = cx + (iconSize / 2) + sp + (i * (iconSize + sp));
            int y = cy - (iconSize / 2);
            AbilityNode node = new AbilityNode(def[i], "defensive", i, getCost(i));
            Button btn = Button.builder(Component.literal(""), b -> attemptBuy(node)).bounds(x, y, iconSize, iconSize).build();
            nodeMap.put(btn, node);
            this.addRenderableWidget(btn);
        }

        // Build Top Path (Balanced)
        for(int i = 0; i < bal.length; i++) {
            int x = cx - (iconSize / 2);
            int y = cy - (iconSize / 2) - ((i + 1) * (iconSize + sp));
            AbilityNode node = new AbilityNode(bal[i], "balanced", i, getCost(i));
            Button btn = Button.builder(Component.literal(""), b -> attemptBuy(node)).bounds(x, y, iconSize, iconSize).build();
            nodeMap.put(btn, node);
            this.addRenderableWidget(btn);
        }

        // Build Bottom Path (Masterclass)
        for(int i = 0; i < mas.length; i++) {
            int x = cx - (iconSize / 2);
            int y = cy + (iconSize / 2) + sp + (i * (iconSize + sp));
            AbilityNode node = new AbilityNode(mas[i], "masterclass", i, getCost(i));
            Button btn = Button.builder(Component.literal(""), b -> attemptBuy(node)).bounds(x, y, iconSize, iconSize).build();
            nodeMap.put(btn, node);
            this.addRenderableWidget(btn);
        }

        // The way into the mod's settings from inside the game, so they are reachable
        // without backing all the way out to the Mods list. Bottom left, clear of the
        // tree's bottom arm, which grows down the middle.
        settingsButton = Button.builder(Component.literal("Settings"),
                        b -> this.minecraft.setScreen(new AtlaSettingsScreen(this)))
                .bounds(8, this.height - 28, 70, 20).build();
        this.addRenderableWidget(settingsButton);

        // Build the CENTRE, if this element has one. It belongs to no arm, so it is
        // bought outright whichever way the bender has gone — see checkTreeLogic.
        String[] mid = getCentre(activeElement);
        for (int i = 0; i < mid.length; i++) {
            int x = cx - (iconSize / 2);
            int y = cy - (iconSize / 2);
            AbilityNode node = new AbilityNode(mid[i], "centre", i,
                    com.minecraft.atlamod.abilities.ElementPaths.centreCost(activeElement));
            Button btn = Button.builder(Component.literal(""), b -> attemptBuy(node)).bounds(x, y, iconSize, iconSize).build();
            nodeMap.put(btn, node);
            this.addRenderableWidget(btn);
        }
    }

    /**
     * Draws the settings button on a tab that does not render its widgets.
     *
     * Only the skill tree calls super.render, because the two equip tabs draw themselves
     * entirely and have no widgets of their own to show — every button in this screen is a
     * tree node. The settings button is the exception: it belongs to the screen rather
     * than to the tree, is wanted on all three tabs, and would otherwise be an invisible
     * control that still answered clicks, since mouseClicked reaches it through
     * super.mouseClicked whatever tab is open.
     */
    private void drawSettingsButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (settingsButton != null) settingsButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private void attemptBuy(AbilityNode node) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        var data = this.minecraft.player.getData(ModAttachments.BENDING_DATA);

        int playerLevel = data.getLevel();
        java.util.List<String> unlocked = data.getUnlockedAbilities();

        // Nothing the settings have switched off can be bought. The server refuses this
        // too — see UnlockAbilityPacket — but refusing it here as well is what stops the
        // client deducting the levels locally for a purchase that will not land.
        if (!com.minecraft.atlamod.AtlaConfig.abilityEnabled(node.name())) return;

        if (unlocked.contains(node.name()) || playerLevel < node.cost()) return;
        if (!checkTreeLogic(node, unlocked, treeArmsFor(activeElement, unlocked))) return;

        data.setLevel(playerLevel - node.cost());
        data.unlockAbility(node.name());
        this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);

        PacketDistributor.sendToServer(new com.minecraft.atlamod.network.UnlockAbilityPacket(node.name(), node.cost()));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, (int) 0xD0101010L);

        if (this.minecraft != null && this.minecraft.player != null) {
            var data = this.minecraft.player.getData(ModAttachments.BENDING_DATA);

            // Every widget in this screen is a skill tree node, so all of them are hidden
            // on the two equip tabs — except the settings button, which belongs to the
            // screen rather than to the tree and is wanted on all three.
            for (var renderable : this.renderables) {
                if (renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    widget.visible = (activeTab == 0) || widget == settingsButton;
                }
            }

            if (activeTab == 1) {
                renderEquipMenu(guiGraphics, mouseX, mouseY, data);

                // The equip tabs never call super.render — see the note in the else
                // branch — so the settings button has to be drawn by hand here, or it
                // would be invisible on these two tabs while still taking clicks.
                drawSettingsButton(guiGraphics, mouseX, mouseY, partialTick);
            } else if (activeTab == 2) {
                renderPassiveMenu(guiGraphics, mouseX, mouseY, data);
                drawSettingsButton(guiGraphics, mouseX, mouseY, partialTick);
            } else {
                // BEFORE the element name rather than after it, because super.render
                // ends up in Screen#renderBlurredBackground, which is a post-process
                // over the whole framebuffer — it blurs whatever has already been
                // drawn. The name used to be drawn first and came out smeared.
                super.render(guiGraphics, mouseX, mouseY, partialTick);

                String centerText = activeElement.isEmpty() ? "None" : activeElement.substring(0, 1).toUpperCase() + activeElement.substring(1);
                // Under the tabs rather than in the middle of the tree: an element
                // with a CENTRE ability has a node sitting exactly where this used to
                // be drawn, and the two would overlap.
                guiGraphics.drawCenteredString(this.font, centerText,
                        this.width / 2, TAB_Y + TAB_H + 6, 0xFFFFFF);

                int playerLevel = data.getLevel();
                java.util.List<String> unlocked = data.getUnlockedAbilities();

                // Computed ONCE per frame rather than once per node: every node on
                // screen shares the same active element and the same unlocked list, so
                // the four arms and their completion booleans cannot differ between
                // them. checkTreeLogic used to re-derive all of this — four fresh
                // arrays pulled from ElementPaths — for every single node, every single
                // frame; with fifteen to twenty nodes in a tree that was fifteen to
                // twenty times the allocation this screen actually needed.
                TreeArms treeArms = treeArmsFor(activeElement, unlocked);

                for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
                    if (renderable instanceof net.minecraft.client.gui.components.Button button) {
                        AbilityNode node = nodeMap.get(button);
                        if (node == null) continue;

                        int bx = button.getX();
                        int by = button.getY();
                        int bw = button.getWidth();
                        int bh = button.getHeight();

                        boolean isUnlocked = unlocked.contains(node.name());
                        boolean meetsTreeReq = checkTreeLogic(node, unlocked, treeArms);
                        boolean canAfford = playerLevel >= node.cost();
                        boolean switchedOff =
                                !com.minecraft.atlamod.AtlaConfig.abilityEnabled(node.name());

                        int borderColor;
                        String statusText;

                        // Switched off in this world's settings, which is read FIRST
                        // because it outranks every other state a node can be in: an
                        // ability that cannot be used is not worth buying however
                        // affordable it is, and one already owned is not worth showing as
                        // unlocked when pressing its key does nothing. Both are drawn the
                        // same way for that reason — what matters is that it does not
                        // work, not how it came to be owned.
                        if (switchedOff) {
                            borderColor = 0xFF884444;
                            statusText = "§c[Disabled in this world's settings]";
                        } else if (isUnlocked) {
                            borderColor = 0xFF55FF55;
                            statusText = "§a[Unlocked]";
                        } else if (!meetsTreeReq) {
                            borderColor = 0xFF444444;
                            statusText = (node.index() == 0 && node.path().equals("masterclass")) ? "§c[Master all 3 base paths first!]" :
                                    (node.index() == 0) ? "§c[Finish your active path first!]" : "§c[Unlock previous ability first!]";
                        } else if (canAfford) {
                            borderColor = 0xFFFFAA00;
                            statusText = "§6[Click to Unlock - Lvl " + node.cost() + "]";
                        } else {
                            borderColor = 0xFFFF5555;
                            statusText = "§c[Requires Lvl " + node.cost() + "]";
                        }

                        // The node's BACKGROUND says which path it belongs to and its
                        // BORDER says what state it is in, so the two never compete for
                        // the same pixels. A flat grey box could only ever carry one of
                        // those and it was already spent on the state.
                        guiGraphics.fillGradient(bx, by, bx + bw, by + bh,
                                pathColor(node.path(), isUnlocked, true),
                                pathColor(node.path(), isUnlocked, false));
                        guiGraphics.renderOutline(bx, by, bw, bh, borderColor);

                        // A vanilla item stands in for the ability's picture — see
                        // AbilityIcons. The "?" stays as the fallback for anything the
                        // table has no line for, which should now be nothing: it is
                        // there so a name typed wrong shows up rather than drawing an
                        // empty box.
                        //
                        // Skipped entirely for a node the open upgrade panel is sitting
                        // on. The box and its outline are still drawn, so a node only
                        // half covered still looks like itself; it is the ICON that has
                        // to go, because it was showing through the panel and filling
                        // the upgrade text with question marks. That matters more now
                        // than it did: renderItem draws at z=150 of its own accord, so
                        // an item would punch through the panel even harder than the
                        // art did. See coveredByUpgradePanel.
                        if (!coveredByUpgradePanel(bx, by, bw, bh)) {
                            if (AbilityIcons.has(node.name())) {
                                AbilityIcons.draw(guiGraphics, node.name(),
                                        bx + ICON_INSET, by + ICON_INSET, bw - ICON_INSET * 2);
                            } else {
                                guiGraphics.drawCenteredString(this.font, "?",
                                        bx + (bw / 2), by + (bh / 2) - 4, 0x888888);
                            }

                            // Anything not yet owned is DIMMED rather than drawn in a
                            // different colour, because an item icon cannot be recoloured
                            // the way our own art could — it is the player's own texture
                            // pack's pixels. Two depths of it: a light veil for something
                            // that is merely unbought, and a heavy one for something the
                            // tree will not sell yet, so "locked" and "affordable" read
                            // apart at a glance and not only from the border.
                            //
                            // Drawn at ICON_Z because renderItem puts the item at z=150
                            // and a fill at the default z=0 would land behind it.
                            if (!isUnlocked) {
                                guiGraphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1,
                                        ICON_Z, meetsTreeReq ? 0x66000000 : 0xB4000000);
                            }
                        }

                        // --- NEW TOOLTIP LOGIC ---
                        // Not while the upgrade panel is over this node: the panel is
                        // drawn on top, and both tooltips would render at once.
                        if (!mouseOverUpgradePanel(mouseX, mouseY)
                                && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                            java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();

                            // 1. Ability Name (Yellow)
                            tooltip.add(net.minecraft.network.chat.Component.literal("§e" + node.name()));

                            // 2. What it actually DOES, which is the one thing the tree
                            // never used to say — a node gave a name, a price and whether
                            // it could be bought, and left the player to spend sixty
                            // levels finding out what they had bought. See
                            // AbilityDescriptions, which answers for passives out of the
                            // ability class itself and for everything else out of its own
                            // table.
                            addWrapped(tooltip, AbilityDescriptions.of(node.name()));

                            // 3. Cost (Gray)
                            tooltip.add(net.minecraft.network.chat.Component.literal("§7Cost: " + node.cost() + " Levels"));

                            // 3. Status Text (Red/Green/Gold depending on if you can buy it)
                            tooltip.add(net.minecraft.network.chat.Component.literal(statusText));

                            // Draw the tooltip box
                            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                        }
                    }
                }
                drawTabs(guiGraphics);
                renderUpgradePanel(guiGraphics, mouseX, mouseY, data);
            }
        }
    }

    private void renderEquipMenu(GuiGraphics graphics, int mouseX, int mouseY, BendingData data) {
        int centerX = this.width / 2;

        // 1. Draw the 8 Keybind slots, labelled with whatever the player has these
        // keys bound to right now.
        String[] slotLabels = slotLabels();
        for (int i = 0; i < 8; i++) {
            int row = i / 4;
            int col = i % 4;
            int x = centerX - 150 + (col * 75);
            int y = 50 + (row * 45);

            graphics.fill(x, y, x + 70, y + 40, 0xFF333333);
            graphics.renderOutline(x, y, 70, 40, 0xFF555555);
            graphics.drawCenteredString(this.font, slotLabels[i], x + 35, y + 4, 0xFFAAAAAA);

            String equipped = data.getEquippedAbility(i);
            if (!equipped.isEmpty()) {
                drawFitted(graphics, "§e" + equipped, x + 35, y + 20, 66, 0xFFFFFF);
            }
        }

        graphics.drawCenteredString(this.font, "--- Unlocked Abilities (" + data.getActiveElement().toUpperCase() + ") ---", centerX, 145, 0xAAAAAA);

        // 2. Unlocked abilities for this element, passives excluded
        java.util.List<String> displayAbilities = equippableAbilities(data);

        // 3. Draw the filtered list of clickable ability buttons
        int startX = centerX - (Math.min(displayAbilities.size(), 4) * 75) / 2;
        int startY = 165;

        for (int i = 0; i < displayAbilities.size(); i++) {
            String ability = displayAbilities.get(i);
            int col = i % 4;
            int row = i / 4;
            int ax = startX + (col * 75);
            int ay = startY + (row * 25);

            // The one you have picked is drawn blue until it lands in a slot. Without it
            // the list gave no feedback at all — you clicked an ability, nothing changed,
            // and the only way to know it had registered was to click a slot and see.
            boolean selected = ability.equals(selectedAbilityToEquip);

            graphics.fill(ax, ay, ax + 70, ay + 20, selected ? SELECTED_FILL : ROW_FILL);
            graphics.renderOutline(ax, ay, 70, 20, selected ? SELECTED_BORDER : ROW_BORDER);
            drawFitted(graphics, ability, ax + 35, ay + 6, 66, 0xFFFFFF);
        }

        // Only worth saying once something is waiting to be placed, so the line doubles as
        // part of the selection feedback rather than being permanent furniture.
        if (selectedAbilityToEquip != null && !selectedAbilityToEquip.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    "§bLeft click a slot to bind §f" + selectedAbilityToEquip
                            + "§b, right click a slot to clear",
                    centerX, 155, 0xFFFFFF);
        }

        drawTabs(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // --- TAB CLICK DETECTION ---
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = tabX(i);
            if (mouseX >= tx && mouseX <= tx + TAB_W && mouseY >= TAB_Y && mouseY <= TAB_Y + TAB_H) {
                activeTab = i;
                selectedAbilityToEquip = null;
                selectedPassiveToEquip = null;
                openUpgradesFor = null;
                return true;
            }
        }
        // ---------------------------

        // --- ABILITY UPGRADE PANEL (skill tree only) ---
        if (activeTab == 0 && this.minecraft != null && this.minecraft.player != null) {
            var treeData = this.minecraft.player.getData(ModAttachments.BENDING_DATA);

            // Left clicks inside an open panel belong to it, and must be taken before
            // super() hands the click to the node buttons underneath.
            if (button == 0 && upgradePanelClicked(mouseX, mouseY, treeData)) {
                return true;
            }

            // Right click a node to pop its upgrades out, or to close them again.
            if (button == 1) {
                for (var entry : nodeMap.entrySet()) {
                    var nodeButton = entry.getKey();
                    if (mouseX < nodeButton.getX() || mouseX > nodeButton.getX() + nodeButton.getWidth()
                            || mouseY < nodeButton.getY() || mouseY > nodeButton.getY() + nodeButton.getHeight()) {
                        continue;
                    }

                    String name = entry.getValue().name();
                    openUpgradesFor = name.equals(openUpgradesFor) ? null : name;
                    return true;
                }

                // Right clicking off a node closes whatever was open.
                openUpgradesFor = null;
                return true;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }


        if (activeTab == 2 && this.minecraft != null && this.minecraft.player != null) {
            var passiveData = this.minecraft.player.getData(ModAttachments.BENDING_DATA);
            if (passiveTabClicked(mouseX, mouseY, button, passiveData)) {
                return true;
            }
        }
        // ... (rest of your mouseClicked code below)
        if (activeTab == 1 && this.minecraft != null && this.minecraft.player != null) {
            var data = this.minecraft.player.getData(ModAttachments.BENDING_DATA);
            int centerX = this.width / 2;

            // 1. Check if clicking one of the 8 Equip Slots (Z, X, C, V, etc.)
            for (int i = 0; i < 8; i++) {
                int row = i / 4;
                int col = i % 4;
                int x = centerX - 150 + (col * 75);
                int y = 50 + (row * 45);

                if (mouseX >= x && mouseX <= x + 70 && mouseY >= y && mouseY <= y + 40) {
                    // RIGHT CLICK: Clear/remove the ability from this slot
                    if (button == 1) {
                        data.setEquippedAbility(i, "");
                        this.minecraft.player.setData(ModAttachments.BENDING_DATA, data);

                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.minecraft.atlamod.network.EquipAbilityPacket(i, "")
                        );
                        return true;
                    }

                    // LEFT CLICK: Equip the selected ability to this slot
                    if (selectedAbilityToEquip != null && !selectedAbilityToEquip.isEmpty()) {
                        data.setEquippedAbility(i, selectedAbilityToEquip);
                        this.minecraft.player.setData(ModAttachments.BENDING_DATA, data);

                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.minecraft.atlamod.network.EquipAbilityPacket(i, selectedAbilityToEquip)
                        );

                        selectedAbilityToEquip = null;
                        return true;
                    }
                }
            }

            // 2. Check if clicking a filtered unlocked ability button
            java.util.List<String> displayAbilities = equippableAbilities(data);

            int startX = centerX - (Math.min(displayAbilities.size(), 4) * 75) / 2;
            int startY = 165;

            for (int i = 0; i < displayAbilities.size(); i++) {
                String ability = displayAbilities.get(i);
                int col = i % 4;
                int row = i / 4;
                int ax = startX + (col * 75);
                int ay = startY + (row * 25);

                if (mouseX >= ax && mouseX <= ax + 70 && mouseY >= ay && mouseY <= ay + 20) {
                    selectedAbilityToEquip = ability; // Select this ability to be equipped
                    return true;
                }
            }
        }

        return false;
    }

    // --- HELPER METHODS ---

    /**
     * The four arms of the ACTIVE element's tree, and how far the player has gotten
     * along each — everything {@link #checkTreeLogic} needs that does not depend on
     * which particular node is being asked about.
     *
     * Every node in a tree shares the same answer to all of this, so it is worked out
     * once per {@link #treeArmsFor} call rather than once per node — see the call site
     * in {@link #render}.
     */
    private record TreeArms(String[] off, String[] def, String[] bal, String[] mas,
                             boolean offComp, boolean defComp, boolean balComp, boolean anyInProgress) {
    }

    private TreeArms treeArmsFor(String element, java.util.List<String> unlocked) {
        String[] off = getOffensive(element);
        String[] def = getDefensive(element);
        String[] bal = getBalanced(element);
        String[] mas = getMaster(element);

        boolean offComp = isPathComplete(unlocked, off);
        boolean defComp = isPathComplete(unlocked, def);
        boolean balComp = isPathComplete(unlocked, bal);

        boolean anyInProgress = (hasStartedPath(unlocked, off) && !offComp) ||
                (hasStartedPath(unlocked, def) && !defComp) ||
                (hasStartedPath(unlocked, bal) && !balComp);

        return new TreeArms(off, def, bal, mas, offComp, defComp, balComp, anyInProgress);
    }

    private boolean checkTreeLogic(AbilityNode node, java.util.List<String> unlocked, TreeArms arms) {
        // The centre answers to none of the path rules: it is bought outright whichever
        // way the bender has gone, which is the whole reason it sits in the middle
        // rather than on an arm.
        if (node.path().equals("centre")) return true;

        // ...but for SOME trees it is the thing the arms hang off. No bending's centre is
        // learning to touch chi at all, and both its arms are applications of that, so
        // neither means anything before it is bought. Air's centre is an ordinary extra
        // and gates nothing — hence a question about the element rather than a blanket
        // rule for every centre. See ElementPaths.centreGatesPaths.
        if (com.minecraft.atlamod.abilities.ElementPaths.centreGatesPaths(activeElement)) {
            for (String gate : getCentre(activeElement)) {
                if (!unlocked.contains(gate)) return false;
            }
        }

        if (node.index() == 0) {
            if (node.path().equals("masterclass")) {
                // An element with no balanced arm at all (Gravitybending is the
                // first) can never satisfy balComp — isPathComplete treats an
                // empty path as never complete, on purpose, so an absent path
                // can't be farmed as a free "complete" one elsewhere. Masterclass
                // still has to open for such an element, so a MISSING balanced
                // path is treated as already satisfied rather than permanently
                // blocking; an element that HAS a balanced path still has to
                // finish it, same as always.
                return arms.offComp() && arms.defComp() && (arms.bal().length == 0 || arms.balComp());
            } else {
                return !arms.anyInProgress();
            }
        } else {
            String[] currentPathArr = getPathArray(node.path(), arms.off(), arms.def(), arms.bal(), arms.mas());
            return unlocked.contains(currentPathArr[node.index() - 1]);
        }
    }

    private int getCost(int index) {
        return switch (index) {
            case 0 -> 1;
            case 1 -> 5;
            case 2 -> 10;
            case 3 -> 15;
            default -> 20;
        };
    }

    private boolean isPathComplete(java.util.List<String> unlocked, String[] path) {
        if (path.length == 0) return false;
        for (String ability : path) {
            if (!unlocked.contains(ability)) return false;
        }
        return true;
    }

    private boolean hasStartedPath(java.util.List<String> unlocked, String[] path) {
        for (String ability : path) {
            if (unlocked.contains(ability)) return true;
        }
        return false;
    }

    // --- YOUR CUSTOM ABILITIES ---
    // The tables themselves live in abilities/ElementPaths, in COMMON code, because
    // the sub-element scrolls have to ask the same questions on the server and this
    // screen only exists on the client. These four are thin delegates so the rest of
    // the screen reads exactly as it did.
    private String[] getOffensive(String element) {
        return com.minecraft.atlamod.abilities.ElementPaths.offensive(element);
    }

    private String[] getDefensive(String element) {
        return com.minecraft.atlamod.abilities.ElementPaths.defensive(element);
    }

    private String[] getBalanced(String element) {
        return com.minecraft.atlamod.abilities.ElementPaths.balanced(element);
    }

    private String[] getMaster(String element) {
        return com.minecraft.atlamod.abilities.ElementPaths.master(element);
    }

    private String[] getCentre(String element) {
        return com.minecraft.atlamod.abilities.ElementPaths.centre(element);
    }

    /**
     * What a centre node costs — {@code ElementPaths.centreCost}.
     *
     * Its own figure rather than getCost(index), which ramps 1/5/10/15 by position along
     * an arm; a centre node has no position to ramp from. It moved OUT of this class when
     * it stopped being one number: air's is 20 and no bending's is 15, and the difference
     * is a rule about the tree rather than about the screen, so it belongs in the common
     * tables where the server could ask it too.
     */

    private String[] getPathArray(String path, String[] off, String[] def, String[] bal, String[] mas) {
        return switch (path) {
            case "offensive" -> off;
            case "defensive" -> def;
            case "balanced" -> bal;
            case "masterclass" -> mas;
            default -> new String[0];
        };
    }

    /**
     * The colour of a node's background, one end of its gradient at a time.
     *
     * A BASE element is coloured by PATH, because which arm an ability sits in is the one
     * fact about it the tree cannot show any other way — the four arms all look alike,
     * and a player reading a node in isolation has nothing to tell offensive from
     * balanced. So they are red, blue, green and gold, with purple for the centre, which
     * belongs to no arm at all and should not be mistaken for one.
     *
     * A SUB-element is coloured by ELEMENT, one colour across both its paths, and that is
     * not the same rule bent — it is the same argument reaching a different answer. A
     * sub-element has only two arms and they are LEFT and RIGHT rather than offensive and
     * defensive: {@link com.minecraft.atlamod.abilities.ElementPaths} puts them in the
     * offensive and defensive slots purely so the four-armed layout needs no change.
     * Colouring them red and blue would therefore be labelling them with a distinction
     * the design does not draw. The element itself is the thing worth naming, so ice is
     * blue throughout, blood red, metal white, lava orange, sound purple, combustion grey
     * and lightning yellow.
     *
     * OWNED abilities get the rich version and everything else a dark one, which is a
     * second, coarser reading of the same state the border spells out exactly. Two
     * signals rather than one, because the border is a single pixel and the background
     * is the whole node.
     *
     * @param top true for the upper end of the gradient, false for the lower.
     */
    private int pathColor(String path, boolean unlocked, boolean top) {
        int[] shades = subElementShades(activeElement);
        if (shades == null) shades = pathShades(path);

        // {rich top, rich bottom, dark top, dark bottom}
        return shades[(unlocked ? 0 : 2) + (top ? 0 : 1)];
    }

    /**
     * The four shades a SUB-element's nodes are drawn from, or null if this is not one.
     *
     * Lightning's yellow is the one colour here that was not specified: the other six
     * were named and it was not, and yellow is what is left once ice has blue, blood red,
     * lava orange, sound purple, combustion grey and metal white — and it is what
     * lightning looks like anyway.
     *
     * Metal's "white" is a cool silver rather than a true white, and combustion's grey is
     * a neutral one, so the two stay apart at a glance. A real white would also leave
     * nothing for the border to show against and would wash out the paler item icons
     * sitting on top of it, iron ingots and nuggets especially.
     */
    private static int[] subElementShades(String element) {
        if (element == null) return null;

        return switch (element.toLowerCase(java.util.Locale.ROOT)) {
            case "ice"        -> new int[]{0xFF3C9AD0, 0xFF10334C, 0xFF1B3242, 0xFF0A151C};
            case "blood"      -> new int[]{0xFFA81E1E, 0xFF3E0A0A, 0xFF3C1414, 0xFF190808};
            case "metal"      -> new int[]{0xFFC2C8CE, 0xFF6E757C, 0xFF3A3E42, 0xFF191B1D};
            case "lava"       -> new int[]{0xFFD1701A, 0xFF4A2406, 0xFF412A14, 0xFF1B1007};
            case "sound"      -> new int[]{0xFF8A3CC4, 0xFF2E1046, 0xFF321C42, 0xFF150A1C};
            case "combustion" -> new int[]{0xFF6E6E6E, 0xFF262626, 0xFF2E2E2E, 0xFF141414};
            case "lightning"  -> new int[]{0xFFD6BE1E, 0xFF4A400A, 0xFF3E3814, 0xFF191608};
            case "gravity"    -> new int[]{0xFF6A5ACD, 0xFF241C42, 0xFF2A2242, 0xFF120E1C};
            default -> null;
        };
    }

    /**
     * How wide a description line may run before it is wrapped.
     *
     * Vanilla does not wrap a tooltip for you — every Component handed to
     * renderComponentTooltip is one line however long it is — and a sentence like Blood
     * suck's runs past 500 pixels, which on a small window is a tooltip wider than the
     * screen it is trying to explain something on.
     */
    private static final int TOOLTIP_WIDTH = 200;

    /**
     * Adds a description to a tooltip, broken across as many lines as it needs.
     *
     * Silent on null, which is how an ability with nothing written for it simply has no
     * description line rather than a line saying it has none.
     *
     * Split by the game's own splitter rather than by counting characters: the font is
     * not fixed width, so "Illuminating" and "WWWWWWWWWWWW" are nothing like the same
     * size on screen.
     */
    private void addWrapped(java.util.List<net.minecraft.network.chat.Component> tooltip, String description) {
        if (description == null || description.isEmpty()) return;

        for (var line : this.font.getSplitter().splitLines(
                description, TOOLTIP_WIDTH, net.minecraft.network.chat.Style.EMPTY)) {
            tooltip.add(net.minecraft.network.chat.Component.literal("§7" + line.getString()));
        }
    }

    /** The four shades a BASE element's nodes are drawn from, one arm at a time. */
    private static int[] pathShades(String path) {
        return switch (path) {
            case "offensive"   -> new int[]{0xFF8C2A22, 0xFF3C110D, 0xFF3A1815, 0xFF190A08};
            case "defensive"   -> new int[]{0xFF24558C, 0xFF0D1F3C, 0xFF16253A, 0xFF080F19};
            case "balanced"    -> new int[]{0xFF2B7F35, 0xFF0D2E12, 0xFF16321B, 0xFF08170B};
            case "masterclass" -> new int[]{0xFF9C7716, 0xFF3C2D07, 0xFF3A3015, 0xFF191408};
            case "centre"      -> new int[]{0xFF6E2B8C, 0xFF2C0D3C, 0xFF2F1739, 0xFF140819};
            // Nothing else builds a node, but a flat dark box is the honest answer for an
            // arm that does not exist rather than borrowing another arm's colour.
            default -> new int[]{0xFF222222, 0xFF222222, 0xFF222222, 0xFF222222};
        };
    }

    /**
     * How narrow a name may be squeezed before it is cut short instead.
     *
     * Below about half size the font stops being readable at all, so past that point
     * trimming the name is kinder than shrinking it further.
     */
    private static final float MIN_LABEL_SCALE = 0.5F;

    /**
     * Draws a name centred in a box, shrinking it to fit rather than letting it spill
     * out over its neighbours.
     *
     * Every slot and list row in the equip and passive tabs is 70 pixels wide, which is
     * comfortable for "Ignite" and hopeless for "Combustion bombardment" — at full size
     * that one is nearly twice the width of its own box and runs straight through the
     * two beside it.
     *
     * Scaled through the pose stack rather than by picking a smaller font, because
     * there is only one font: the transform is what makes an arbitrary name fit an
     * arbitrary box. Anything still too wide at the minimum scale is cut and given an
     * ellipsis, so the row is never wider than the thing it is labelling.
     */
    private void drawFitted(GuiGraphics graphics, String text, int centerX, int y,
                            int maxWidth, int colour) {
        int width = this.font.width(text);

        if (width <= maxWidth) {
            graphics.drawCenteredString(this.font, text, centerX, y, colour);
            return;
        }

        float scale = Math.max(MIN_LABEL_SCALE, maxWidth / (float) width);

        // Still over even squeezed as far as it goes: take characters off the end
        // until what is left fits at that scale.
        String shown = text;
        while (this.font.width(shown + "...") * scale > maxWidth && shown.length() > 1) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (!shown.equals(text)) shown = shown + "...";

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        graphics.drawString(this.font, shown, -this.font.width(shown) / 2, 0, colour);

        graphics.pose().popPose();
    }

    /** Left edge of tab {@code index}, laid out as one centred row. */
    private int tabX(int index) {
        int totalWidth = (TAB_W * TAB_NAMES.length) + (6 * (TAB_NAMES.length - 1));
        return (this.width / 2) - (totalWidth / 2) + index * (TAB_W + 6);
    }

    /**
     * Draws the tab strip, ABOVE everything the tree puts under it.
     *
     * The lift is load-bearing rather than tidiness. GuiGraphics#renderItem pushes an item
     * icon to z=150 of its own accord, where the old "?" was ordinary text at z=0 and went
     * quietly under the tabs — so once nodes started wearing items, a node reaching up
     * into the strip punched straight through it. That is not rare: the top arm's fourth
     * node sits 140 pixels above the middle, which lands in the tabs on any window shorter
     * than about 344 scaled pixels, and fire's Taller fire is the only node in the mod
     * that far up.
     *
     * The first attempt at this suppressed the ICON instead, which cost Taller fire its
     * picture on exactly the setups where the node was still perfectly visible. Lifting
     * the tabs fixes the overlap without taking anything away — the strip is drawn last
     * and should win, which is all that was ever wanted.
     *
     * Translated through the pose stack rather than passed as a z argument because
     * renderOutline and drawCenteredString have no z overload, and all three parts of a
     * tab have to travel together.
     */
    private void drawTabs(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TAB_Z);

        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = (activeTab == i);
            int tx = tabX(i);

            graphics.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H, selected ? 0xFF448844 : 0xFF222222);
            graphics.renderOutline(tx, TAB_Y, TAB_W, TAB_H, selected ? 0xFF55FF55 : 0xFF555555);
            graphics.drawCenteredString(this.font, TAB_NAMES[i], tx + (TAB_W / 2), TAB_Y + 6, 0xFFFFFF);
        }

        graphics.pose().popPose();
    }

    /**
     * Unlocked abilities that can go in a keybind slot: this element's abilities,
     * minus passives. A passive has no keybind — being slotted in the Passives tab
     * is its whole activation — so listing it here would offer a bind that does
     * nothing, which is exactly what AbilityHandler refuses to cast.
     */
    private java.util.List<String> equippableAbilities(BendingData data) {
        String activeEl = data.getActiveElement() == null ? "" : data.getActiveElement().toLowerCase();

        java.util.List<String> validForElement = new java.util.ArrayList<>();
        validForElement.addAll(java.util.List.of(getOffensive(activeEl)));
        validForElement.addAll(java.util.List.of(getDefensive(activeEl)));
        validForElement.addAll(java.util.List.of(getBalanced(activeEl)));
        validForElement.addAll(java.util.List.of(getMaster(activeEl)));
        // The centre too, or an ability bought outright could never be bound to a key.
        validForElement.addAll(java.util.List.of(getCentre(activeEl)));

        java.util.List<String> found = new java.util.ArrayList<>();
        for (String ability : data.getUnlockedAbilities()) {
            if (ability == null) continue;

            // Switched off in this world's settings, so a keybind for it would do nothing
            // — the same argument that keeps passives out of this list. The ability is NOT
            // forgotten, only unlistable: it is still unlocked, and it comes back into
            // this list the moment it is enabled again.
            if (!com.minecraft.atlamod.AtlaConfig.abilityEnabled(ability)) continue;

            // Nothing that is not a registered ability at all. Tree nodes are only NAMES
            // in the unlocked list, and not every one of them has a class behind it —
            // "Chi blocking" is a step that opens the no-bending arms and casts nothing,
            // so offering a keybind for it would offer a key that does nothing. Checked
            // BEFORE the passive test below, which asks the same registry and would read
            // a missing ability as "not a passive, therefore bindable".
            com.minecraft.atlamod.abilities.Ability registered =
                    com.minecraft.atlamod.abilities.AbilityRegistry.get(ability);
            if (registered == null) continue;

            if (registered instanceof com.minecraft.atlamod.abilities.PassiveAbility) {
                continue;
            }

            // Case-insensitive, so minor capitalisation differences still match.
            for (String valid : validForElement) {
                if (valid.equalsIgnoreCase(ability)) {
                    found.add(ability);
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Unlocked abilities that are actually passives, i.e. what can go in a slot.
     *
     * Anything the settings have switched off is left out, exactly as
     * {@link #equippableAbilities} leaves it out of the keybind list — a passive that has
     * been disabled does nothing while it sits in a slot, so offering the slot would be
     * offering an empty gesture. It stays unlocked and reappears here when re-enabled.
     */
    private java.util.List<String> unlockedPassives(BendingData data) {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String name : data.getUnlockedAbilities()) {
            if (name == null) continue;
            if (!com.minecraft.atlamod.AtlaConfig.abilityEnabled(name)) continue;

            if (com.minecraft.atlamod.abilities.AbilityRegistry.get(name)
                    instanceof com.minecraft.atlamod.abilities.PassiveAbility) {
                found.add(name);
            }
        }
        return found;
    }

    private void renderPassiveMenu(GuiGraphics graphics, int mouseX, int mouseY, BendingData data) {
        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font,
                "Equipped passives work as soon as they are slotted", centerX, 44, 0xAAAAAA);

        // The four slots.
        for (int i = 0; i < com.minecraft.atlamod.BendingData.PASSIVE_SLOTS; i++) {
            int sx = centerX - 150 + (i * 75);
            int sy = 60;

            String equipped = data.getEquippedPassive(i);
            boolean filled = !equipped.isEmpty();

            graphics.fill(sx, sy, sx + 70, sy + 40, filled ? 0xFF3A2A1A : 0xFF222222);
            graphics.renderOutline(sx, sy, 70, 40, filled ? 0xFFFFAA33 : 0xFF555555);

            graphics.drawCenteredString(this.font, "Slot " + (i + 1), sx + 35, sy + 5, 0xAAAAAA);
            drawFitted(graphics, filled ? equipped : "- empty -",
                    sx + 35, sy + 20, 66, filled ? 0xFFCC66 : 0x666666);
        }

        // Names what is actually waiting once something is picked, the same way the ability
        // tab does — the two lists are the same control and should read the same.
        if (selectedPassiveToEquip != null && !selectedPassiveToEquip.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    "§bLeft click a slot to equip §f" + selectedPassiveToEquip
                            + "§b, right click a slot to clear",
                    centerX, 108, 0xFFFFFF);
        } else {
            graphics.drawCenteredString(this.font,
                    "Left click a passive to select it, right click a slot to clear",
                    centerX, 108, 0x888888);
        }

        // The passives the player owns.
        java.util.List<String> available = unlockedPassives(data);
        if (available.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    "No passive abilities unlocked yet", centerX, 140, 0x888888);
            drawTabs(graphics);
            return;
        }

        int startX = centerX - (Math.min(available.size(), 4) * 75) / 2;
        for (int i = 0; i < available.size(); i++) {
            String passive = available.get(i);
            int col = i % 4;
            int row = i / 4;
            int ax = startX + (col * 75);
            int ay = 140 + (row * 25);

            // Blue, not the orange this used to be: orange is what a FILLED slot is drawn
            // in a few lines above, so a selected row and an occupied slot were saying the
            // same thing in the same colour. See SELECTED_BORDER.
            boolean selected = passive.equals(selectedPassiveToEquip);
            graphics.fill(ax, ay, ax + 70, ay + 20, selected ? SELECTED_FILL : ROW_FILL);
            graphics.renderOutline(ax, ay, 70, 20, selected ? SELECTED_BORDER : ROW_BORDER);
            drawFitted(graphics, passive, ax + 35, ay + 6, 66, 0xFFFFFF);

            // Hovering shows what the passive actually does.
            if (mouseX >= ax && mouseX <= ax + 70 && mouseY >= ay && mouseY <= ay + 20) {
                // Wrapped now, like the skill tree's. Vanilla makes one line of whatever
                // Component it is handed however long it is, and the longer passive
                // descriptions ran wider than a small window.
                java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
                addWrapped(tooltip, AbilityDescriptions.of(passive));
                if (!tooltip.isEmpty()) {
                    graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                }
            }
        }

        drawTabs(graphics);
    }

    /** Slot and list clicks for the passive tab. Returns true if the click was used. */
    private boolean passiveTabClicked(double mouseX, double mouseY, int button, BendingData data) {
        int centerX = this.width / 2;

        for (int i = 0; i < com.minecraft.atlamod.BendingData.PASSIVE_SLOTS; i++) {
            int sx = centerX - 150 + (i * 75);
            int sy = 60;
            if (mouseX < sx || mouseX > sx + 70 || mouseY < sy || mouseY > sy + 40) continue;

            if (button == 1) {
                applyPassive(i, "", data);
                return true;
            }
            if (selectedPassiveToEquip != null && !selectedPassiveToEquip.isEmpty()) {
                applyPassive(i, selectedPassiveToEquip, data);
                selectedPassiveToEquip = null;
                return true;
            }
            return true;
        }

        java.util.List<String> available = unlockedPassives(data);
        int startX = centerX - (Math.min(available.size(), 4) * 75) / 2;
        for (int i = 0; i < available.size(); i++) {
            int col = i % 4;
            int row = i / 4;
            int ax = startX + (col * 75);
            int ay = 140 + (row * 25);

            if (mouseX >= ax && mouseX <= ax + 70 && mouseY >= ay && mouseY <= ay + 20) {
                selectedPassiveToEquip = available.get(i);
                return true;
            }
        }

        return false;
    }

    /** Applies a passive slot change locally and tells the server. */
    private void applyPassive(int slot, String passive, BendingData data) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        data.setEquippedPassive(slot, passive);
        this.minecraft.player.setData(ModAttachments.BENDING_DATA, data);

        PacketDistributor.sendToServer(
                new com.minecraft.atlamod.network.EquipPassivePacket(slot, passive));
    }

    // --- ABILITY UPGRADE PANEL ---
    // Which ability's upgrades are popped out beside its node, or null for none.
    private String openUpgradesFor = null;

    private static final int UPGRADE_PANEL_W = 150;
    private static final int UPGRADE_ROW_H = 30;

    /** Where the popped-out panel sits: beside the node, flipped inward near the edge. */
    private int upgradePanelX(net.minecraft.client.gui.components.Button node) {
        int right = node.getX() + node.getWidth() + 6;
        if (right + UPGRADE_PANEL_W <= this.width) return right;
        return node.getX() - UPGRADE_PANEL_W - 6;
    }

    /** The node button whose upgrades are open, or null. */
    private net.minecraft.client.gui.components.Button openUpgradeButton() {
        if (openUpgradesFor == null) return null;

        for (var entry : nodeMap.entrySet()) {
            if (entry.getValue().name().equals(openUpgradesFor)) return entry.getKey();
        }
        return null;
    }

    private static java.util.List<com.minecraft.atlamod.abilities.AbilityUpgrade> upgradesOf(String abilityName) {
        var ability = com.minecraft.atlamod.abilities.AbilityRegistry.get(abilityName);
        return ability == null ? java.util.List.of() : ability.getUpgrades();
    }

    /** Draws the popped-out upgrade list. Called last, so it sits over the tree. */
    /**
     * Whether the pointer is inside the popped-out upgrade panel.
     *
     * The panel is drawn ON TOP of the ability nodes, so without asking this the node
     * underneath still counts itself as hovered and draws its own tooltip — two
     * tooltips on top of each other, which is what made the panel look broken.
     */
    private boolean mouseOverUpgradePanel(double mouseX, double mouseY) {
        int[] bounds = upgradePanelBounds();
        if (bounds == null) return false;

        return mouseX >= bounds[0] && mouseX <= bounds[0] + UPGRADE_PANEL_W
                && mouseY >= bounds[1] && mouseY <= bounds[1] + bounds[2];
    }

    /**
     * Where the open panel is: {x, y, height}, or null if none is open.
     *
     * One place works this out now, because three callers need the same rectangle —
     * the hover test, the click test, and the node decoration that has to keep out
     * from underneath it. Three copies of the same arithmetic is three chances for the
     * panel to disagree with itself about where it is.
     */
    private int[] upgradePanelBounds() {
        net.minecraft.client.gui.components.Button node = openUpgradeButton();
        if (node == null) return null;

        return new int[] {
                upgradePanelX(node),
                node.getY(),
                22 + Math.max(1, upgradesOf(openUpgradesFor).size()) * UPGRADE_ROW_H
        };
    }

    /**
     * Whether an ability node is (partly) hidden behind the open upgrade panel.
     *
     * The panel pops out BESIDE its own node, which means it lands on top of whatever
     * nodes happen to sit to that side — and their art was bleeding through it and
     * turning the upgrade text into a mess of question marks. A node underneath is
     * skipped entirely rather than drawn and covered: at a glance the covered art was
     * indistinguishable from decoration belonging to the panel itself.
     */
    private boolean coveredByUpgradePanel(int bx, int by, int bw, int bh) {
        int[] bounds = upgradePanelBounds();
        if (bounds == null) return false;

        return bx < bounds[0] + UPGRADE_PANEL_W && bx + bw > bounds[0]
                && by < bounds[1] + bounds[2] && by + bh > bounds[1];
    }


    private void renderUpgradePanel(GuiGraphics graphics, int mouseX, int mouseY, BendingData data) {
        net.minecraft.client.gui.components.Button node = openUpgradeButton();
        if (node == null) return;

        var upgrades = upgradesOf(openUpgradesFor);
        int px = upgradePanelX(node);
        int py = node.getY();
        int height = 22 + Math.max(1, upgrades.size()) * UPGRADE_ROW_H;

        // Fully opaque. It used to be 0xF0, which let the tree behind it show through
        // at six percent — enough for an ability node's art to sit visibly under the
        // upgrade text. A popup over a busy screen has no reason to be translucent.
        graphics.fill(px, py, px + UPGRADE_PANEL_W, py + height, 0xFF101010);
        graphics.renderOutline(px, py, UPGRADE_PANEL_W, height, 0xFF6688AA);
        graphics.drawCenteredString(this.font, "§bUpgrades",
                px + UPGRADE_PANEL_W / 2, py + 6, 0xFFFFFF);

        if (upgrades.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7None",
                    px + UPGRADE_PANEL_W / 2, py + 26, 0xFFFFFF);
            return;
        }

        for (int i = 0; i < upgrades.size(); i++) {
            var upgrade = upgrades.get(i);
            int ry = py + 22 + (i * UPGRADE_ROW_H);

            boolean owned = data.hasUpgrade(upgrade.key());
            boolean abilityOwned = data.getUnlockedAbilities().contains(openUpgradesFor);
            boolean locked = upgrade.requires() != null && !data.hasUpgrade(upgrade.requires());
            boolean affordable = abilityOwned && !locked && data.getLevel() >= upgrade.cost();
            boolean hovered = mouseX >= px + 4 && mouseX <= px + UPGRADE_PANEL_W - 4
                    && mouseY >= ry && mouseY <= ry + UPGRADE_ROW_H - 4;

            int fill = owned ? 0xFF224422 : (hovered && affordable ? 0xFF335577 : 0xFF222222);
            int border = owned ? 0xFF55FF55 : (affordable ? 0xFF6688AA : 0xFF663333);

            graphics.fill(px + 4, ry, px + UPGRADE_PANEL_W - 4, ry + UPGRADE_ROW_H - 4, fill);
            graphics.renderOutline(px + 4, ry, UPGRADE_PANEL_W - 8, UPGRADE_ROW_H - 4, border);

            graphics.drawString(this.font, upgrade.name(), px + 9, ry + 4, 0xFFFFFF);

            String status = owned ? "§aOwned"
                    : !abilityOwned ? "§8Unlock the ability first"
                    : locked ? "§8Buy " + requiredName(upgrade) + " first"
                    : (affordable ? "§6Click - Lvl " + upgrade.cost()
                                  : "§cRequires Lvl " + upgrade.cost());
            graphics.drawString(this.font, status, px + 9, ry + 15, 0xFFFFFF);

            if (hovered) {
                graphics.renderTooltip(this.font,
                        net.minecraft.network.chat.Component.literal("§7" + upgrade.description()),
                        mouseX, mouseY);
            }
        }
    }

    /**
     * The display name of whatever an upgrade is waiting on, for the "buy X first"
     * line. Falls back to the raw key, which should never be seen — an upgrade naming
     * a prerequisite that is not on the same ability is a bug, not a state to render.
     */
    private String requiredName(com.minecraft.atlamod.abilities.AbilityUpgrade upgrade) {
        if (upgrade.requires() == null) return "";

        for (var candidate : upgradesOf(openUpgradesFor)) {
            if (candidate.key().equals(upgrade.requires())) return candidate.name();
        }
        return upgrade.requires();
    }

    /** Clicks inside the popped-out panel. Returns true if the click was used. */
    private boolean upgradePanelClicked(double mouseX, double mouseY, BendingData data) {
        net.minecraft.client.gui.components.Button node = openUpgradeButton();
        if (node == null) return false;

        var upgrades = upgradesOf(openUpgradesFor);
        int px = upgradePanelX(node);
        int py = node.getY();

        for (int i = 0; i < upgrades.size(); i++) {
            var upgrade = upgrades.get(i);
            int ry = py + 22 + (i * UPGRADE_ROW_H);

            if (mouseX < px + 4 || mouseX > px + UPGRADE_PANEL_W - 4
                    || mouseY < ry || mouseY > ry + UPGRADE_ROW_H - 4) {
                continue;
            }

            boolean abilityOwned = data.getUnlockedAbilities().contains(openUpgradesFor);
            boolean locked = upgrade.requires() != null && !data.hasUpgrade(upgrade.requires());
            if (!abilityOwned || locked || data.hasUpgrade(upgrade.key())
                    || data.getLevel() < upgrade.cost()) {
                return true; // inside the panel, just not buyable — swallow it either way
            }

            // Applied locally for an immediate response; the server re-checks all of it.
            data.setLevel(data.getLevel() - upgrade.cost());
            data.unlockUpgrade(upgrade.key());
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.setData(ModAttachments.BENDING_DATA, data);
                this.minecraft.player.playSound(
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0F, 1.4F);
            }

            PacketDistributor.sendToServer(
                    new com.minecraft.atlamod.network.BuyUpgradePacket(openUpgradesFor, upgrade.key()));
            return true;
        }

        // A click anywhere else in the panel body still belongs to the panel.
        return mouseOverUpgradePanel(mouseX, mouseY);
    }
}