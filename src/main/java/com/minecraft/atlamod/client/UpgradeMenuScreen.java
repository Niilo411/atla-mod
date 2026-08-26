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

    // --- RESTORED VARIABLES & RECORD ---
    // 0 = Skill Tree, 1 = Equip Abilities, 2 = Passives
    private int activeTab = 0;

    private static final int TAB_W = 90;
    private static final int TAB_H = 20;
    private static final int TAB_Y = 12;
    private static final String[] TAB_NAMES = { "Skill Tree", "Equip Abilities", "Passives" };
    private String selectedAbilityToEquip = null;
    private String selectedPassiveToEquip = null;
    private static final String[] SLOT_LABELS = { "Z", "X", "C", "V", "Shift + Z", "Shift + X", "Shift + C", "Shift + V" };
    private record AbilityNode(String name, String path, int index, int cost) {}

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
    }

    private void attemptBuy(AbilityNode node) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        var data = this.minecraft.player.getData(ModAttachments.BENDING_DATA);

        int playerLevel = data.getLevel();
        java.util.List<String> unlocked = data.getUnlockedAbilities();

        if (unlocked.contains(node.name()) || playerLevel < node.cost()) return;
        if (!checkTreeLogic(node, unlocked)) return;

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

            for (var renderable : this.renderables) {
                if (renderable instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    widget.visible = (activeTab == 0);
                }
            }

            if (activeTab == 1) {
                renderEquipMenu(guiGraphics, mouseX, mouseY, data);
            } else if (activeTab == 2) {
                renderPassiveMenu(guiGraphics, mouseX, mouseY, data);
            } else {
                String centerText = activeElement.isEmpty() ? "None" : activeElement.substring(0, 1).toUpperCase() + activeElement.substring(1);
                guiGraphics.drawCenteredString(this.font, centerText, this.width / 2, this.height / 2 - 4, 0xFFFFFF);
                super.render(guiGraphics, mouseX, mouseY, partialTick);

                int playerLevel = data.getLevel();
                java.util.List<String> unlocked = data.getUnlockedAbilities();

                for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
                    if (renderable instanceof net.minecraft.client.gui.components.Button button) {
                        AbilityNode node = nodeMap.get(button);
                        if (node == null) continue;

                        int bx = button.getX();
                        int by = button.getY();
                        int bw = button.getWidth();
                        int bh = button.getHeight();

                        boolean isUnlocked = unlocked.contains(node.name());
                        boolean meetsTreeReq = checkTreeLogic(node, unlocked);
                        boolean canAfford = playerLevel >= node.cost();

                        int borderColor;
                        String statusText;

                        if (isUnlocked) {
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

                        guiGraphics.fill(bx, by, bx + bw, by + bh, 0xFF222222);
                        guiGraphics.renderOutline(bx, by, bw, bh, borderColor);
                        guiGraphics.drawCenteredString(this.font, "?", bx + (bw / 2), by + (bh / 2) - 4, 0x888888);

                        // --- NEW TOOLTIP LOGIC ---
                        // Not while the upgrade panel is over this node: the panel is
                        // drawn on top, and both tooltips would render at once.
                        if (!mouseOverUpgradePanel(mouseX, mouseY)
                                && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                            java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();

                            // 1. Ability Name (Yellow)
                            tooltip.add(net.minecraft.network.chat.Component.literal("§e" + node.name()));

                            // 2. Cost (Gray)
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

        // 1. Draw the 8 Keybind slots (Z, X, C, V, Shift+Z, etc.)
        for (int i = 0; i < 8; i++) {
            int row = i / 4;
            int col = i % 4;
            int x = centerX - 150 + (col * 75);
            int y = 50 + (row * 45);

            graphics.fill(x, y, x + 70, y + 40, 0xFF333333);
            graphics.renderOutline(x, y, 70, 40, 0xFF555555);
            graphics.drawCenteredString(this.font, SLOT_LABELS[i], x + 35, y + 4, 0xFFAAAAAA);

            String equipped = data.getEquippedAbility(i);
            if (!equipped.isEmpty()) {
                graphics.drawCenteredString(this.font, "§e" + equipped, x + 35, y + 20, 0xFFFFFF);
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

            graphics.fill(ax, ay, ax + 70, ay + 20, 0xFF222222);
            graphics.renderOutline(ax, ay, 70, 20, 0xFF777777);
            graphics.drawCenteredString(this.font, ability, ax + 35, ay + 6, 0xFFFFFF);
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
    private boolean checkTreeLogic(AbilityNode node, java.util.List<String> unlocked) {
        String[] off = getOffensive(activeElement);
        String[] def = getDefensive(activeElement);
        String[] bal = getBalanced(activeElement);
        String[] mas = getMaster(activeElement);

        boolean offComp = isPathComplete(unlocked, off);
        boolean defComp = isPathComplete(unlocked, def);
        boolean balComp = isPathComplete(unlocked, bal);

        boolean anyInProgress = (hasStartedPath(unlocked, off) && !offComp) ||
                (hasStartedPath(unlocked, def) && !defComp) ||
                (hasStartedPath(unlocked, bal) && !balComp);

        if (node.index() == 0) {
            if (node.path().equals("masterclass")) {
                return offComp && defComp && balComp;
            } else {
                return !anyInProgress;
            }
        } else {
            String[] currentPathArr = getPathArray(node.path(), off, def, bal, mas);
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
    private String[] getOffensive(String element) {
        if (element == null) return new String[0];
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Fire leap", "Fire whip", "Fireball", "Fire Breath"};
            case "water" -> new String[]{"Water ball", "Water stream", "Water Bullets"};
            case "air" -> new String[]{"Air splinters", "Air cannon", "wind tunnel"};
            case "earth" -> new String[]{"Earth spike", "Splinters", "Earth block", "Earth trap"};
            // Lightning is a SUB-element with only two paths. They are drawn on the
            // tree's left and right arms, so the existing four-armed layout needs no
            // change at all — the top and bottom arms simply come back empty and
            // nothing is drawn there.
            case "lightning" -> new String[]{
                    "Lightning redirection", "Lightning aura", "Lightning Jump", "Lightning Strength"};
            default -> new String[0];
        };
    }

    private String[] getDefensive(String element) {
        if (element == null) return new String[0];
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Fire push", "Fire shield", "Firewall", "Fire ring"};
            case "water" -> new String[]{"Water shield", "Water push", "Water heal"};
            case "air" -> new String[]{"Air pull", "Air jump", "Air Aura", "Wind"};
            case "earth" -> new String[]{"Earth wall", "Earth pillar", "Earth armor"};
            case "lightning" -> new String[]{
                    "Lightning bolt", "Lightning ball", "Lightning stun", "Lightning Swarm"};
            default -> new String[0];
        };
    }

    private String[] getBalanced(String element) {
        if (element == null) return new String[0];
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"Ignite", "Fire spikes", "Fire rocket", "Taller fire"};
            case "water" -> new String[]{"Water Manipulation", "Water Surf", "Water Sphere"};
            case "air" -> new String[]{"Air scooter", "Airpush", "Air spout"};
            case "earth" -> new String[]{"Mine", "Earth dig", "Earth grab"};
            default -> new String[0];
        };
    }

    private String[] getMaster(String element) {
        if (element == null) return new String[0];
        return switch (element.toLowerCase()) {
            case "fire" -> new String[]{"blue fire", "Fire blow", "Fire immunity", "Fire Rain"};
            case "water" -> new String[]{"Drown", "water breathing", "Tsunami"};
            case "air" -> new String[]{"breathless", "Tornado", "Flight"};
            case "earth" -> new String[]{"Earthquake", "Ravine", "Earth sink"};
            case "energy" -> new String[]{"Give and take"}; // Avatar special element
            default -> new String[0];
        };
    }

    private String[] getPathArray(String path, String[] off, String[] def, String[] bal, String[] mas) {
        return switch (path) {
            case "offensive" -> off;
            case "defensive" -> def;
            case "balanced" -> bal;
            case "masterclass" -> mas;
            default -> new String[0];
        };
    }

    /** Left edge of tab {@code index}, laid out as one centred row. */
    private int tabX(int index) {
        int totalWidth = (TAB_W * TAB_NAMES.length) + (6 * (TAB_NAMES.length - 1));
        return (this.width / 2) - (totalWidth / 2) + index * (TAB_W + 6);
    }

    private void drawTabs(GuiGraphics graphics) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = (activeTab == i);
            int tx = tabX(i);

            graphics.fill(tx, TAB_Y, tx + TAB_W, TAB_Y + TAB_H, selected ? 0xFF448844 : 0xFF222222);
            graphics.renderOutline(tx, TAB_Y, TAB_W, TAB_H, selected ? 0xFF55FF55 : 0xFF555555);
            graphics.drawCenteredString(this.font, TAB_NAMES[i], tx + (TAB_W / 2), TAB_Y + 6, 0xFFFFFF);
        }
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

        java.util.List<String> found = new java.util.ArrayList<>();
        for (String ability : data.getUnlockedAbilities()) {
            if (ability == null) continue;

            if (com.minecraft.atlamod.abilities.AbilityRegistry.get(ability)
                    instanceof com.minecraft.atlamod.abilities.PassiveAbility) {
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

    /** Unlocked abilities that are actually passives, i.e. what can go in a slot. */
    private java.util.List<String> unlockedPassives(BendingData data) {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String name : data.getUnlockedAbilities()) {
            if (name == null) continue;
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
            graphics.drawCenteredString(this.font, filled ? equipped : "- empty -",
                    sx + 35, sy + 20, filled ? 0xFFCC66 : 0x666666);
        }

        graphics.drawCenteredString(this.font,
                "Left click a slot to place the selected passive, right click to clear",
                centerX, 108, 0x888888);

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

            boolean selected = passive.equals(selectedPassiveToEquip);
            graphics.fill(ax, ay, ax + 70, ay + 20, selected ? 0xFF554400 : 0xFF222222);
            graphics.renderOutline(ax, ay, 70, 20, selected ? 0xFFFFAA33 : 0xFF777777);
            graphics.drawCenteredString(this.font, passive, ax + 35, ay + 6, 0xFFFFFF);

            // Hovering shows what the passive actually does.
            if (mouseX >= ax && mouseX <= ax + 70 && mouseY >= ay && mouseY <= ay + 20) {
                var ability = com.minecraft.atlamod.abilities.AbilityRegistry.get(passive);
                if (ability instanceof com.minecraft.atlamod.abilities.PassiveAbility p) {
                    graphics.renderTooltip(this.font,
                            net.minecraft.network.chat.Component.literal("§7" + p.getDescription()),
                            mouseX, mouseY);
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
        net.minecraft.client.gui.components.Button node = openUpgradeButton();
        if (node == null) return false;

        int px = upgradePanelX(node);
        int py = node.getY();
        int height = 22 + Math.max(1, upgradesOf(openUpgradesFor).size()) * UPGRADE_ROW_H;

        return mouseX >= px && mouseX <= px + UPGRADE_PANEL_W
                && mouseY >= py && mouseY <= py + height;
    }

    private void renderUpgradePanel(GuiGraphics graphics, int mouseX, int mouseY, BendingData data) {
        net.minecraft.client.gui.components.Button node = openUpgradeButton();
        if (node == null) return;

        var upgrades = upgradesOf(openUpgradesFor);
        int px = upgradePanelX(node);
        int py = node.getY();
        int height = 22 + Math.max(1, upgrades.size()) * UPGRADE_ROW_H;

        graphics.fill(px, py, px + UPGRADE_PANEL_W, py + height, 0xF0101010);
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
        int height = 22 + Math.max(1, upgrades.size()) * UPGRADE_ROW_H;
        return mouseX >= px && mouseX <= px + UPGRADE_PANEL_W
                && mouseY >= py && mouseY <= py + height;
    }
}