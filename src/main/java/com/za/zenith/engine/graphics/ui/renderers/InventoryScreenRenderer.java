package com.za.zenith.engine.graphics.ui.renderers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.graphics.ui.GroupUI;
import com.za.zenith.engine.graphics.ui.Hotbar;
import com.za.zenith.engine.graphics.ui.InventoryScreen;
import com.za.zenith.engine.graphics.ui.PlayerInventoryScreen;
import com.za.zenith.engine.graphics.ui.Screen;
import com.za.zenith.engine.graphics.ui.ScreenManager;
import com.za.zenith.engine.graphics.ui.ScrollPanel;
import com.za.zenith.engine.graphics.ui.SlotUI;
import com.za.zenith.engine.graphics.ui.UIRenderer;
import com.za.zenith.engine.graphics.ui.UISearchBar;
import com.za.zenith.entities.Player;
import com.za.zenith.utils.I18n;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemRegistry;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.items.ItemSearchEngine;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class InventoryScreenRenderer {
    private final UIRenderer renderer;
    private final ScrollPanel devScroller = new ScrollPanel();
    private int lastSw = 0, lastSh = 0;
    private PlayerMode lastMode = null;

    private final UISearchBar devSearchBar = new UISearchBar("gui.search_placeholder");

    public InventoryScreenRenderer(UIRenderer renderer) {
        this.renderer = renderer;
        devScroller.setBounds(0, 0, 0, 0); // Will be set in render
    }

    public void renderInventory(Hotbar hotbar, int screenWidth, int screenHeight, com.za.zenith.engine.graphics.DynamicTextureAtlas atlas) {
        if (hotbar == null) return;
        Player player = hotbar.getPlayer();
        if (player == null) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Restore full-screen dimming (helps visibility of placeholders)
        renderer.getPrimitivesRenderer().renderDarkenedBackground();
        
        ScreenManager screenManager = ScreenManager.getInstance();
        if (!screenManager.isAnyScreenOpen()) {
            screenManager.openPlayerInventory(player, screenWidth, screenHeight);
        }
        
        Screen activeScreen = screenManager.getActiveScreen();
        
        PlayerMode currentMode = player.getMode();
        if (screenWidth != lastSw || screenHeight != lastSh || currentMode != lastMode) {
            activeScreen.init(screenWidth, screenHeight);
            lastSw = screenWidth;
            lastSh = screenHeight;
            lastMode = currentMode;
        }
        
        activeScreen.render(renderer, screenWidth, screenHeight, atlas);

        if (activeScreen instanceof InventoryScreen invScreen) {
            // Step 1: Handle character stats panel and its tooltip
            List<String> statsTooltip = null;
            if (activeScreen instanceof PlayerInventoryScreen pScreen) {
                for (com.za.zenith.engine.graphics.ui.GroupUI group : pScreen.getGroupsUI()) {
                    if ("stats".equals(group.getConfig().type)) {
                        statsTooltip = renderCharacterStats(group, player, screenWidth, screenHeight);
                    }
                }
            }
            
            int slotSize = (int)(18 * Hotbar.HOTBAR_SCALE);
            float hmx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
            float hmy = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
            SlotUI hoveredUI = invScreen.getSlotAt(hmx, hmy);
            java.util.Set<com.za.zenith.entities.inventory.Slot> dragged = GameLoop.getInstance().getInputManager().getDraggedSlots();
            
            List<SlotUI> slots = invScreen.getSlots();
            for (SlotUI ui : slots) {
                if (dragged.contains(ui.getSlot())) {
                    renderer.getPrimitivesRenderer().renderHighlight(ui.getX(), ui.getY(), slotSize, screenWidth, screenHeight, 0.2f, 0.6f, 1.0f, 0.4f);
                } else if (ui == hoveredUI) {
                    renderer.getPrimitivesRenderer().renderHighlight(ui.getX(), ui.getY(), slotSize, screenWidth, screenHeight, 1.0f, 1.0f, 1.0f, 0.3f);
                }
            }

            if (player.getMode() == PlayerMode.DEVELOPER && activeScreen instanceof com.za.zenith.engine.graphics.ui.PlayerInventoryScreen pScreen) {
                for (com.za.zenith.engine.graphics.ui.GroupUI group : pScreen.getGroupsUI()) {
                    if ("developer_items".equals(group.getConfig().type)) {
                        int devX = group.getX();
                        int startY = group.getY();
                        int cols = group.getConfig().cols > 0 ? group.getConfig().cols : 7;
                        int rows = group.getConfig().rows > 0 ? group.getConfig().rows : 14;
                        int spacing = group.getConfig().spacing;
                        
                        renderDeveloperPanel(devX, startY, cols, rows, slotSize, spacing, screenWidth, screenHeight, atlas, group.getConfig());
                    }
                }
            }

            ItemStack held = GameLoop.getInstance().getInputManager().getHeldStack();
            if (held != null) {
                float mx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
                float my = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
                String animId = "held_stack";
                renderer.getSlotRenderer().renderSlot((int)mx - slotSize/2, (int)my - slotSize/2, slotSize, held, null, screenWidth, screenHeight, atlas, false, animId, false);
            }
            
            // Step 2: Render Tooltips LAST (Z-Order top)
            if (statsTooltip != null) {
                renderTooltip(statsTooltip, hmx, hmy, screenWidth, screenHeight);
            }
            renderInventoryTooltip(invScreen, slotSize, player, screenWidth, screenHeight);
        }
        
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    public List<Item> getFilteredDevItems() {
        List<Item> allItems = new ArrayList<>(ItemRegistry.getAllItems().values());
        
        // Apply Search Filter
        allItems = com.za.zenith.world.items.ItemSearchEngine.filter(allItems, devSearchBar.getQuery());
        
        allItems.sort((a, b) -> {
            // Blocks first
            if (a.isBlock() != b.isBlock()) {
                return a.isBlock() ? -1 : 1;
            }
            // Then alphabetical by identifier
            return a.getIdentifier().toString().compareTo(b.getIdentifier().toString());
        });
        return allItems;
    }

    public void renderDeveloperPanel(int devX, int startY, int cols, int rows, int slotSize, int spacing, int sw, int sh, com.za.zenith.engine.graphics.DynamicTextureAtlas atlas, com.za.zenith.engine.graphics.ui.GUIConfig.GroupConfig config) {
        int padding = config.background != null ? config.background.padding : 12;
        int searchHeight = 24;
        
        int slotsWidth = cols * (slotSize + spacing) - spacing; 
        int devWidth = slotsWidth + padding * 2;
        int devHeight = rows * (slotSize + spacing) - spacing + padding * 2;
        
        int bgX = devX - padding;
        int bgY = startY - padding;
        
        // 1. Panel Background
        if (config.background != null && config.background.color != null) {
            float[] bg = config.background.color;
            renderer.getPrimitivesRenderer().renderRect(bgX, bgY - 24 - searchHeight - 4, devWidth, devHeight + 24 + searchHeight + 4, sw, sh, bg[0], bg[1], bg[2], bg[3]); 
        } else {
            renderer.getPrimitivesRenderer().renderRect(bgX, bgY - 24 - searchHeight - 4, devWidth, devHeight + 24 + searchHeight + 4, sw, sh, 0.05f, 0.05f, 0.05f, 0.95f); 
        }
        
        // 2. Title
        renderer.getPrimitivesRenderer().renderRect(bgX, bgY - 24 - searchHeight - 4, devWidth, 24, sw, sh, 0.15f, 0.15f, 0.15f, 1.0f); 
        renderer.getFontRenderer().drawString(I18n.get("gui.developer_panel").toUpperCase(), devX, bgY - 18 - searchHeight - 4, 14, sw, sh, 0.0f, 0.6f, 1.0f, 1.0f);

        // 3. Search Box
        devSearchBar.setBounds(bgX + 2, bgY - searchHeight - 2, devWidth - 4, searchHeight);
        devSearchBar.render(renderer, sw, sh);

        // 4. Item List
        devScroller.setBounds(bgX, bgY, devWidth, devHeight);
        List<Item> allItems = getFilteredDevItems();
        
        int totalRows = (allItems.size() + cols - 1) / cols;
        int slotsHeight = totalRows * (slotSize + spacing) - spacing;
        devScroller.updateContentHeight(slotsHeight + padding * 2);

        devScroller.begin(sw, sh);
        float offset = devScroller.getOffset();
        float mx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
        float my = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;

        for (int i = 0; i < allItems.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            
            int x = devX + col * (slotSize + spacing);
            int y = startY + row * (slotSize + spacing) - (int)offset;
            
            // CPU Culling
            if (y + slotSize < bgY || y > bgY + devHeight) continue;
            
            boolean isHovered = mx >= x && mx <= x + slotSize && my >= y && my <= y + slotSize;
            renderer.getSlotRenderer().renderSlot(x, y, slotSize, new ItemStack(allItems.get(i)), null, sw, sh, atlas, isHovered, "dev_" + i, true);
        }
        devScroller.end();

        devScroller.renderScrollbar(renderer, sw, sh);

        if (devScroller.isMouseOver(mx, my)) {
            for (int i = 0; i < allItems.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int x = devX + col * (slotSize + spacing);
                int y = startY + row * (slotSize + spacing) - (int)offset;
                
                if (y + slotSize < bgY || y > bgY + devHeight) continue;
                
                if (mx >= x && mx <= x + slotSize && my >= y && my <= y + slotSize) {
                    renderTooltip(java.util.List.of(I18n.get(allItems.get(i).getName())), mx, my, sw, sh);
                    break;
                }
            }
        }
    }

    public boolean handleMouseClick(float mx, float my, int button) {
        if (lastMode != PlayerMode.DEVELOPER) return false;
        return devSearchBar.handleMouseClick(mx, my, button);
    }

    public boolean handleKeyPress(int key) {
        if (lastMode != PlayerMode.DEVELOPER) return false;
        return devSearchBar.handleKeyPress(key);
    }

    public boolean handleChar(int codepoint) {
        if (lastMode != PlayerMode.DEVELOPER) return false;
        return devSearchBar.handleChar(codepoint);
    }

    public boolean isSearchBarFocused() {
        if (lastMode != PlayerMode.DEVELOPER) return false;
        return devSearchBar != null && devSearchBar.isFocused();
    }

    private void renderTooltip(java.util.List<String> lines, float mx, float my, int sw, int sh) {
        if (lines == null || lines.isEmpty()) return;

        int textSize = 14;
        int maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, renderer.getFontRenderer().getStringWidth(line, textSize));
        }

        int padding = 6;
        int rectWidth = maxLineWidth + padding * 2;
        int lineHeight = (int)(textSize * 1.2f);
        int rectHeight = lines.size() * lineHeight + padding * 2;

        // Adaptive position logic
        int tx = (int)mx + 12;
        int ty = (int)my - 12;

        if (tx + rectWidth > sw) tx = (int)mx - rectWidth - 12;
        if (ty + rectHeight > sh) ty = sh - rectHeight - 2;
        if (ty < 0) ty = 2;

        // Border (slightly larger rect)
        renderer.getPrimitivesRenderer().renderRect(tx - 1, ty - 3, rectWidth + 2, rectHeight + 2, sw, sh, 0.2f, 0.2f, 0.2f, 1.0f);
        // Background (opaque and slightly lighter than the panel)
        renderer.getPrimitivesRenderer().renderRect(tx, ty - 2, rectWidth, rectHeight, sw, sh, 0.08f, 0.08f, 0.08f, 1.0f);
        
        for (int i = 0; i < lines.size(); i++) {
            renderer.getFontRenderer().drawString(lines.get(i), tx + padding, ty + padding + i * lineHeight, textSize, sw, sh);
        }
    }

    private void renderInventoryTooltip(InventoryScreen screen, int slotSize, Player player, int sw, int sh) {
        float mx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
        float my = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
        
        SlotUI hoveredUI = screen != null ? screen.getSlotAt(mx, my) : null;
        if (hoveredUI != null) {
            ItemStack hovered = hoveredUI.getSlot().getStack();
            if (hovered != null) {
                java.util.List<String> lines = generateItemTooltip(hovered, player);
                renderTooltip(lines, mx, my, sw, sh);
            }
        }
    }

    private java.util.List<String> generateItemTooltip(ItemStack stack, Player player) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        Item item = stack.getItem();
        int maxTooltipWidth = 360;
        int textSize = 14;
        
        // 1. Name with Rarity Color/Effects
        com.za.zenith.world.items.stats.RarityDefinition rarity = com.za.zenith.world.items.stats.RarityRegistry.get(stack.getRarity());
        String rarityColor = (rarity != null) ? rarity.colorCode() : "$f";
        
        String fullName = rarityColor + "$l" + stack.getDisplayName();
        if (renderer.getFontRenderer().getStringWidth(fullName, textSize) > maxTooltipWidth) {
            String[] parts = stack.getDisplayName().split(" ");
            StringBuilder current = new StringBuilder(rarityColor + "$l");
            for (int i = 0; i < parts.length; i++) {
                String test = current.toString() + parts[i] + " ";
                if (renderer.getFontRenderer().getStringWidth(test, textSize) > maxTooltipWidth && i > 0) {
                    lines.add(current.toString().trim());
                    current = new StringBuilder(rarityColor + "$l" + parts[i] + " ");
                } else {
                    current.append(parts[i]).append(" ");
                }
            }
            lines.add(current.toString().trim());
        } else {
            lines.add(fullName);
        }
        
        // 2. Rarity Title
        if (rarity != null && (!item.isBlock() || !stack.getRarity().getPath().equals("common"))) {
            lines.add("$7" + I18n.get(rarity.translationKey()));
        }

        // 3. Markdown Description
        String descKey = item.getDescriptionKey();
        if (descKey != null) {
            lines.add("");
            String rawDesc = I18n.get(descKey);
            String[] paragraphs = rawDesc.split("\n");
            
            for (String p : paragraphs) {
                String trimmed = p.trim();
                if (trimmed.isEmpty()) {
                    lines.add("");
                    continue;
                }
                
                String processed = trimmed;
                boolean isListItem = processed.startsWith("- ");
                
                if (isListItem) {
                    processed = processed.substring(2);
                }
                
                // --- Apply Formatting ---
                // ### Header -> Turquoise Bold
                if (processed.startsWith("### ")) {
                    lines.add("$b$l" + processed.substring(4).toUpperCase());
                    continue;
                }
                
                // **Bold** -> $l...$r$f
                while (processed.contains("**")) {
                    processed = processed.replaceFirst("\\*\\*", "\\$l");
                    processed = processed.replaceFirst("\\*\\*", "\\$r\\$f");
                }
                
                // *Italic/Lore* -> $7$o...$r$f
                while (processed.contains("*")) {
                    processed = processed.replaceFirst("\\*", "\\$7\\$o");
                    processed = processed.replaceFirst("\\*", "\\$r\\$f");
                }

                // --- Handle Wrapping and Indentation ---
                int availableWidth = isListItem ? maxTooltipWidth - 20 : maxTooltipWidth;
                java.util.List<String> wrapped = renderer.getFontRenderer().wrapText(processed, textSize, availableWidth);
                
                for (int k = 0; k < wrapped.size(); k++) {
                    String lineText = wrapped.get(k);
                    if (isListItem) {
                        if (k == 0) {
                            lines.add("  $7• $f" + lineText);
                        } else {
                            lines.add("    $f" + lineText); // 4 spaces for perfect alignment
                        }
                    } else {
                        lines.add(lineText);
                    }
                }
            }
        }

        // 4. Affixes (SHOW AFTER DESC)
        if (!stack.getActiveAffixes().isEmpty()) {
            lines.add("");
            lines.add("$b$l" + I18n.get("ui.affixes").toUpperCase() + ":");
            for (com.za.zenith.utils.Identifier affId : stack.getActiveAffixes()) {
                com.za.zenith.world.items.stats.AffixDefinition aff = com.za.zenith.world.items.stats.AffixRegistry.get(affId);
                if (aff != null) {
                    lines.add(" $f> " + I18n.get(aff.translationKey()));
                }
            }
        }

        // 4. Stats and Comparison
        com.za.zenith.world.items.component.EquipmentComponent equip = item.getComponent(com.za.zenith.world.items.component.EquipmentComponent.class);
        
        if (!item.isBlock() || !stack.getActiveAffixes().isEmpty()) {
            ItemStack equipped = null;
            if (equip != null && player.getInventory() != null) {
                equipped = player.getInventory().getEquippedItem(equip.getSlotType());
            }

            boolean hasStats = false;
            for (com.za.zenith.world.items.stats.StatDefinition stat : com.za.zenith.world.items.stats.StatRegistry.getAll()) {
                float value = stack.getStat(stat.identifier());
                // Only show if the item actually contributes to this stat
                if (value != 0) {
                    if (!hasStats) { lines.add(""); hasStats = true; }
                    
                    String color = value > 0 ? "$a" : "$c";
                    String sign = value > 0 ? "+" : "";
                    String line = "$7" + I18n.get(stat.translationKey()) + ": " + color + sign + (int)value;
                    
                    if (equipped != null) {
                        float diff = value - equipped.getStat(stat.identifier());
                        if (diff > 0) line += " (+$a" + (int)diff + "$f)";
                        else if (diff < 0) line += " (-$c" + (int)Math.abs(diff) + "$f)";
                    }
                    lines.add(line);
                }
            }
        }

        // 5. Loot Preview (if case)
        com.za.zenith.world.items.component.LootboxComponent box = item.getComponent(com.za.zenith.world.items.component.LootboxComponent.class);
        if (box != null) {
            lines.add("");
            lines.add("$e$l" + com.za.zenith.utils.I18n.get("ui.expected_contents"));
            com.za.zenith.world.items.loot.LootTable table = com.za.zenith.world.items.loot.LootTableRegistry.get(box.lootTable());
            if (table != null) {
                for (com.za.zenith.world.items.loot.LootTable.Pool pool : table.pools()) {
                    for (com.za.zenith.world.items.loot.LootTable.Entry entry : pool.entries()) {
                        Item entryItem = ItemRegistry.getItem(entry.item());
                        if (entryItem != null) {
                            lines.add(" $7- " + entryItem.getName());
                        }
                    }
                }
            }
        }

        return lines;
    }

    private List<String> renderCharacterStats(com.za.zenith.engine.graphics.ui.GroupUI group, Player player, int sw, int sh) {
        int padding = group.getConfig().padding;
        int width = group.getWidth();
        int height = group.getHeight();
        int textSize = group.getConfig().textSize;

        int x = group.getX();
        int y = group.getY();

        // Background
        if (group.getConfig().background != null && "solid".equals(group.getConfig().background.type)) {
            renderer.renderGroupBackground(x, y, width, height, group.getConfig().background);
        } else {
            // Fallback to default if not configured
            renderer.getPrimitivesRenderer().renderRect(x, y, width, height, sw, sh, 0.05f, 0.05f, 0.05f, 0.8f);
        }

        // Title
        renderer.getFontRenderer().drawString("$b$l" + I18n.get("ui.stats").toUpperCase(), x + padding, y + padding, textSize + 2, sw, sh);

        int startY = y + padding + 25;
        int spacing = group.getConfig().spacing;
        int i = 0;

        float hmx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
        float hmy = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
        List<String> activeTooltip = null;

        for (com.za.zenith.world.items.stats.StatDefinition stat : com.za.zenith.world.items.stats.StatRegistry.getAll()) {
            float value = player.getStat(stat.identifier());
            int lineY = startY + i * spacing;

            String text = "$7" + I18n.get(stat.translationKey()) + ": $f" + (int)value;
            renderer.getFontRenderer().drawString(text, x + padding, lineY, textSize, sw, sh);

            // Capture tooltip instead of rendering it immediately
            if (hmx >= x && hmx <= x + width && hmy >= lineY && hmy <= lineY + spacing) {
                String descKey = stat.translationKey() + ".desc";
                activeTooltip = java.util.List.of("$b" + I18n.get(stat.translationKey()), "$7" + I18n.get(descKey));
            }
            
            i++;
        }
        return activeTooltip;
    }

    public ScrollPanel getDevScroller() {
        return devScroller;
    }
}
