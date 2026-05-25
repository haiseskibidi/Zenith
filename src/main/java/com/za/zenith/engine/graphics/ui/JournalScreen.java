package com.za.zenith.engine.graphics.ui;

import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.utils.Identifier;
import com.za.zenith.utils.I18n;
import com.za.zenith.world.journal.JournalCategory;
import com.za.zenith.world.journal.JournalElement;
import com.za.zenith.world.journal.JournalEntry;
import com.za.zenith.world.journal.JournalRegistry;
import com.za.zenith.world.recipes.NappingRecipe;
import com.za.zenith.world.recipes.RecipeRegistry;
import java.util.List;

/**
 * Survivor's Tablet v4.7 - Refactored for modularity.
 * Now uses the reusable ScrollPanel component.
 */
public class JournalScreen implements Screen {
    private int guiLeft, guiTop, width, height;
    private final int sidebarWidth = 160;
    private final int topBarHeight = 38;
    
    private final ScrollPanel contentScroller = new ScrollPanel();
    
    private JournalCategory selectedCategory;
    private JournalEntry selectedEntry;
    
    // Theme Colors
    private static final float[] COLOR_SCREEN = {0.03f, 0.03f, 0.03f, 0.98f}; 
    private static final float[] COLOR_SIDEBAR = {0.05f, 0.05f, 0.05f, 1.0f};
    private static final float[] COLOR_CARD_BG = {1.0f, 1.0f, 1.0f, 0.02f}; 
    private static final float[] COLOR_CARD_BORDER = {1.0f, 1.0f, 1.0f, 0.05f};
    private static final float[] COLOR_ACCENT = {0.0f, 0.6f, 1.0f, 1.0f}; 
    
    private static final float[] TEXT_PRIMARY = {1.0f, 1.0f, 1.0f, 1.0f};
    private static final float[] TEXT_SECONDARY = {0.6f, 0.6f, 0.6f, 1.0f};

    @Override
    public void init(int sw, int sh) {
        this.width = (int)(sw * 0.55f);
        this.height = (int)(sh * 0.7f);
        this.guiLeft = (sw - width) / 2;
        this.guiTop = (sh - height) / 2;
        
        // Настройка области прокрутки
        contentScroller.setBounds(guiLeft + sidebarWidth + 5, guiTop + topBarHeight, width - sidebarWidth - 10, height - topBarHeight - 10);
        
        // Restore last state from registry
        Identifier lastCatId = JournalRegistry.getLastSelectedCategoryId();
        Identifier lastEntryId = JournalRegistry.getLastSelectedEntryId();
        
        if (lastCatId != null) {
            selectedCategory = JournalRegistry.getCategory(lastCatId);
        }
        if (lastEntryId != null) {
            selectedEntry = JournalRegistry.getEntry(lastEntryId);
            // Restore scroll for this specific entry
            contentScroller.setScrollY(JournalRegistry.getLastScrollY());
        }
        
        List<JournalCategory> categories = JournalRegistry.getAllCategories();
        if (selectedCategory == null && !categories.isEmpty()) {
            selectedCategory = categories.get(0);
        }
    }

    @Override
    public void render(UIRenderer renderer, int sw, int sh, DynamicTextureAtlas atlas) {
        // 1. Tablet Body
        renderer.renderRect(guiLeft - 2, guiTop - 2, width + 4, height + 4, sw, sh, 0.2f, 0.2f, 0.2f, 1.0f);
        renderer.renderRect(guiLeft, guiTop, width, height, sw, sh, COLOR_SCREEN[0], COLOR_SCREEN[1], COLOR_SCREEN[2], COLOR_SCREEN[3]);
        
        // 2. Navigation Sidebar
        renderSidebar(renderer, sw, sh, atlas);
        
        // 3. Main Content Area (With Reusable Scroller)
        renderContentArea(renderer, sw, sh, atlas);
        
        // 4. Top Bar (Always on top)
        renderTopBar(renderer, sw, sh);
        
        // 5. Scrollbar
        contentScroller.renderScrollbar(renderer, sw, sh);
    }

    private void renderTopBar(UIRenderer renderer, int sw, int sh) {
        renderer.renderRect(guiLeft, guiTop, width, topBarHeight, sw, sh, 0.08f, 0.08f, 0.08f, 1.0f);
        renderer.renderRect(guiLeft, guiTop + topBarHeight - 1, width, 1, sw, sh, 1, 1, 1, 0.1f);
        renderer.getFontRenderer().drawString(I18n.get("journal.title").toUpperCase(), guiLeft + 15, guiTop + 12, 14, sw, sh, TEXT_PRIMARY[0], TEXT_PRIMARY[1], TEXT_PRIMARY[2], 1.0f);
    }

    private void renderSidebar(UIRenderer renderer, int sw, int sh, DynamicTextureAtlas atlas) {
        int x = guiLeft;
        int y = guiTop + topBarHeight;
        int h = height - topBarHeight;
        int maxW = sidebarWidth - 30;
        
        renderer.renderRect(x, y, sidebarWidth, h, sw, sh, COLOR_SIDEBAR[0], COLOR_SIDEBAR[1], COLOR_SIDEBAR[2], 1.0f);
        renderer.renderRect(x + sidebarWidth - 1, y, 1, h, sw, sh, 1, 1, 1, 0.08f);

        int curY = y + 15;
        FontRenderer fr = renderer.getFontRenderer();
        
        for (JournalCategory cat : JournalRegistry.getAllCategories()) {
            boolean selected = cat == selectedCategory;
            int nameH = fr.getWrappedStringHeight(I18n.get(cat.name()), 12, maxW - 15);
            int itemH = Math.max(28, nameH + 12);
            
            if (selected) {
                renderer.renderRect(x, curY, 2, itemH, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
                renderer.renderRect(x + 2, curY, sidebarWidth - 2, itemH, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 0.1f);
            } else if (isMouseIn(x, curY, sidebarWidth, itemH)) {
                renderer.renderRect(x, curY, sidebarWidth, itemH, sw, sh, 1, 1, 1, 0.04f);
            }

            com.za.zenith.world.items.Item icon = com.za.zenith.world.items.ItemRegistry.getItem(cat.icon());
            if (icon != null) renderer.renderSlot(x + 8, curY + (itemH - 16) / 2, 16, new com.za.zenith.world.items.ItemStack(icon), null, sw, sh, atlas);

            fr.drawWrappedString(I18n.get(cat.name()), x + 30, curY + 7, 12, maxW - 15, sw, sh, (selected ? 1f : 0.6f), (selected ? 1f : 0.6f), (selected ? 1f : 0.6f), 1.0f);
            curY += itemH + 2;
            
            if (selected) {
                curY += 2;
                for (Identifier entryId : cat.entries()) {
                    JournalEntry entry = JournalRegistry.getEntry(entryId);
                    if (entry == null) continue;
                    int eH = fr.getWrappedStringHeight(I18n.get(entry.title()), 13, maxW - 20) + 10;
                    float[] color = (entry == selectedEntry) ? COLOR_ACCENT : (isMouseIn(x + 5, curY, sidebarWidth - 10, eH) ? TEXT_PRIMARY : TEXT_SECONDARY);
                    
                    if (isMouseIn(x + 5, curY, sidebarWidth - 10, eH)) renderer.renderRect(x + 5, curY, sidebarWidth - 10, eH, sw, sh, 1, 1, 1, 0.03f);
                    if (entry.icon() != null) {
                        com.za.zenith.world.items.Item eIcon = com.za.zenith.world.items.ItemRegistry.getItem(entry.icon());
                        if (eIcon != null) renderer.renderSlot(x + 12, curY + (eH - 12) / 2, 12, new com.za.zenith.world.items.ItemStack(eIcon), null, sw, sh, atlas);
                    }
                    fr.drawWrappedString(I18n.get(entry.title()), x + 30, curY + 5, 13, maxW - 20, sw, sh, color[0], color[1], color[2], 1.0f);
                    curY += eH + 1;
                }
                curY += 5;
            }
        }
    }

    private void renderContentArea(UIRenderer renderer, int sw, int sh, DynamicTextureAtlas atlas) {
        int contentX = guiLeft + sidebarWidth + 20;
        int contentWidth = width - sidebarWidth - 40;
        
        contentScroller.begin(sw, sh);
        
        int currentY = (int)(guiTop + topBarHeight + 25 - contentScroller.getOffset());

        if (selectedEntry == null) {
            renderer.getFontRenderer().drawWrappedString(I18n.get("journal.welcome"), contentX, guiTop + height/2 - 20, 15, contentWidth, sw, sh, 0.4f, 0.4f, 0.4f, 1.0f);
            contentScroller.updateContentHeight(0);
        } else {
            // Render Title
            renderer.getFontRenderer().drawString(I18n.get(selectedEntry.title()).toUpperCase(), contentX, currentY, 18, sw, sh, TEXT_PRIMARY[0], TEXT_PRIMARY[1], TEXT_PRIMARY[2], 1.0f);
            renderer.renderRect(contentX, currentY + 24, 30, 2, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
            
            int drawY = currentY + 50;
            float totalH = 80;

            for (JournalElement el : selectedEntry.elements()) {
                int blockH = calculateBlockHeight(renderer, el, contentWidth);
                
                // Drawing actual block
                renderer.renderRect(contentX - 10, drawY - 8, contentWidth + 20, blockH + 16, sw, sh, COLOR_CARD_BG[0], COLOR_CARD_BG[1], COLOR_CARD_BG[2], COLOR_CARD_BG[3]);
                renderer.renderRect(contentX - 10, drawY - 8, contentWidth + 20, 1, sw, sh, COLOR_CARD_BORDER[0], COLOR_CARD_BORDER[1], COLOR_CARD_BORDER[2], COLOR_CARD_BORDER[3]);
                renderBlock(renderer, el, contentX, drawY, contentWidth, sw, sh, atlas);
                
                drawY += blockH + 25; 
                totalH += blockH + 25;
            }
            contentScroller.updateContentHeight(totalH);
        }

        contentScroller.end();
    }

    private void renderBlock(UIRenderer renderer, JournalElement el, int x, int y, int w, int sw, int sh, DynamicTextureAtlas atlas) {
        switch (el.type()) {
            case HEADER: renderer.getFontRenderer().drawString(I18n.get(el.value()), x, y, 15, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f); break;
            case TEXT: renderer.getFontRenderer().drawWrappedString(I18n.get(el.value()), x, y, 13, w, sw, sh, TEXT_PRIMARY[0], TEXT_PRIMARY[1], TEXT_PRIMARY[2], 1.0f); break;
            case TIP:
                int h = calculateBlockHeight(renderer, el, w);
                renderer.renderRect(x - 10, y - 8, 2, h + 16, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
                renderer.getFontRenderer().drawString(I18n.get("journal.tip_header"), x, y - 4, 10, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
                renderer.getFontRenderer().drawWrappedString(I18n.get(el.value()), x, y + 10, 12, w, sw, sh, 0.8f, 0.9f, 1.0f, 1.0f);
                break;
            case IMAGE:
                if (el.path() != null) {
                    int imgW = (int)(w * el.scale());
                    int imgH = (int)(imgW * 0.56f);
                    renderer.renderExternalImage(el.path(), x + (w-imgW)/2, y, imgW, imgH, sw, sh);
                }
                break;
            case ITEM_ROW:
                if (el.items() != null) {
                    int curX = x;
                    for (Identifier id : el.items()) {
                        com.za.zenith.world.items.Item item = com.za.zenith.world.items.ItemRegistry.getItem(id);
                        if (item != null) {
                            renderer.renderRect(curX - 2, y - 2, 36, 36, sw, sh, 1, 1, 1, 0.05f);
                            renderer.renderSlot(curX, y, 32, new com.za.zenith.world.items.ItemStack(item), null, sw, sh, atlas);
                            curX += 40;
                        }
                    }
                }
                break;
            case RECIPE:
                renderRecipe(renderer, Identifier.of(el.value()), x, y, w, sw, sh, atlas);
                break;
        }
    }

    private void renderRecipe(UIRenderer renderer, Identifier recipeId, int x, int y, int w, int sw, int sh, DynamicTextureAtlas atlas) {
        com.za.zenith.world.recipes.IRecipe recipe = RecipeRegistry.get(recipeId);
        if (recipe == null) return;

        if (recipe instanceof NappingRecipe nr) {
            renderer.getFontRenderer().drawString(I18n.get("journal.recipe_napping"), x, y, 12, sw, sh, TEXT_SECONDARY[0], TEXT_SECONDARY[1], TEXT_SECONDARY[2], 1.0f);
            
            // Draw inputs (cycling if multiple)
            List<Identifier> inputs = nr.getInputIds();
            int inputIdx = (int)((System.currentTimeMillis() / 1500) % inputs.size());
            com.za.zenith.world.items.Item inputItem = com.za.zenith.world.items.ItemRegistry.getItem(inputs.get(inputIdx));
            if (inputItem != null) {
                renderer.renderRect(x - 2, y + 15 - 2, 28, 28, sw, sh, 1, 1, 1, 0.05f);
                renderer.renderSlot(x, y + 15, 24, new com.za.zenith.world.items.ItemStack(inputItem), null, sw, sh, atlas);
            }

            int gridX = x + 35;
            int gridY = y + 15;
            int cellSize = 10;
            
            for (int i = 0; i < 25; i++) {
                int row = i / 5;
                int col = i % 5;
                float alpha = nr.getPattern()[i] ? 0.6f : 0.1f;
                renderer.renderRect(gridX + col * (cellSize + 2), gridY + row * (cellSize + 2), cellSize, cellSize, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], alpha);
            }
            
            int arrowX = gridX + 5 * (cellSize + 2) + 15;
            renderer.getFontRenderer().drawString("->", arrowX, gridY + 18, 16, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
            
            int resSlotX = arrowX + 35;
            renderer.renderRect(resSlotX - 2, gridY - 2, 36, 36, sw, sh, 1, 1, 1, 0.05f);
            renderer.renderSlot(resSlotX, gridY, 32, nr.getResult(), null, sw, sh, atlas);

        } else if (recipe instanceof com.za.zenith.world.recipes.InWorldRecipe iwr) {
            // Drawing Stump Crafting
            int curX = x;
            int slotY = y + 15;
            int slotSize = 32;

            // 1. Ingredients
            for (Identifier ing : iwr.getIngredients()) {
                com.za.zenith.world.items.Item item = com.za.zenith.world.items.ItemRegistry.getItem(ing);
                if (item != null) {
                    renderer.renderRect(curX - 2, slotY - 2, slotSize + 4, slotSize + 4, sw, sh, 1, 1, 1, 0.05f);
                    renderer.renderSlot(curX, slotY, slotSize, new com.za.zenith.world.items.ItemStack(item), null, sw, sh, atlas);
                    curX += slotSize + 8;
                }
            }

            // 2. Tool
            renderer.getFontRenderer().drawString("+", curX, slotY + 8, 14, sw, sh, TEXT_SECONDARY[0], TEXT_SECONDARY[1], TEXT_SECONDARY[2], 1.0f);
            curX += 15;
            
            Identifier toolId = iwr.getToolId();
            int toolX = curX;
            if (toolId != null && !toolId.equals(Identifier.of("zenith:hand"))) {
                com.za.zenith.world.items.Item tool = com.za.zenith.world.items.ItemRegistry.getItem(toolId);
                if (tool != null) {
                    renderer.renderRect(curX - 2, slotY - 2, slotSize + 4, slotSize + 4, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 0.15f);
                    renderer.renderSlot(curX, slotY, slotSize, new com.za.zenith.world.items.ItemStack(tool), null, sw, sh, atlas);
                }
            } else {
                // Hand icon or "Any"
                renderer.renderRect(curX - 2, slotY - 2, slotSize + 4, slotSize + 4, sw, sh, 1, 1, 1, 0.02f);
                renderer.getFontRenderer().drawString("?", curX + 10, slotY + 8, 14, sw, sh, TEXT_SECONDARY[0], TEXT_SECONDARY[1], TEXT_SECONDARY[2], 0.5f);
            }
            
            // Draw hits count at the bottom-right of the tool slot (with shadow for readability)
            String hitsText = iwr.getRequiredHits() + "x";
            int tx = toolX + slotSize - 12;
            int ty = slotY + slotSize - 8;
            // Shadow
            renderer.getFontRenderer().drawString(hitsText, tx + 1, ty + 1, 10, sw, sh, 0, 0, 0, 1.0f);
            // Main text
            renderer.getFontRenderer().drawString(hitsText, tx, ty, 10, sw, sh, 1, 1, 1, 1.0f);
            curX += slotSize + 8;

            // 3. Arrow
            renderer.getFontRenderer().drawString("->", curX, slotY + 10, 16, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);
            curX += 35; // Match napping distance

            // 4. Result
            renderer.renderRect(curX - 2, slotY - 2, slotSize + 4, slotSize + 4, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 0.1f);
            renderer.renderSlot(curX, slotY, slotSize, iwr.getResult(), null, sw, sh, atlas);
        }
    }

    private int calculateBlockHeight(UIRenderer renderer, JournalElement el, int w) {
        switch (el.type()) {
            case HEADER: return 20;
            case TEXT: return renderer.getFontRenderer().getWrappedStringHeight(I18n.get(el.value()), 13, w);
            case TIP: return renderer.getFontRenderer().getWrappedStringHeight(I18n.get(el.value()), 12, w) + 20;
            case IMAGE: return (int)(w * el.scale() * 0.56f);
            case ITEM_ROW: return 40;
            case RECIPE: return 90;
            case SPACER: return 15;
            default: return 0;
        }
    }

    private boolean isMouseIn(int x, int y, int w, int h) {
        float mx = com.za.zenith.engine.core.GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
        float my = com.za.zenith.engine.core.GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean handleMouseClick(float mx, float my, int button) {
        if (button != 0) return false;
        int x = guiLeft;
        int y = guiTop + topBarHeight;
        int curY = y + 15;
        int maxW = sidebarWidth - 30;
        FontRenderer fr = com.za.zenith.engine.core.GameLoop.getInstance().getRenderer().getUIRenderer().getFontRenderer();
        for (JournalCategory cat : JournalRegistry.getAllCategories()) {
            int itemH = Math.max(28, fr.getWrappedStringHeight(I18n.get(cat.name()), 12, maxW - 15) + 12);
            if (mx >= x && mx <= x + sidebarWidth && my >= curY && my <= curY + itemH) {
                selectedCategory = cat; 
                selectedEntry = null; 
                JournalRegistry.setLastSelectedCategoryId(cat.id());
                JournalRegistry.setLastSelectedEntryId(null);
                JournalRegistry.setLastScrollY(0);
                contentScroller.reset(); 
                return true;
            }
            curY += itemH + 2;
            if (cat == selectedCategory) {
                curY += 2;
                for (Identifier entryId : cat.entries()) {
                    JournalEntry entry = JournalRegistry.getEntry(entryId);
                    if (entry == null) continue;
                    int eH = fr.getWrappedStringHeight(I18n.get(entry.title()), 13, maxW - 20) + 10;
                    if (mx >= x + 5 && mx <= x + sidebarWidth - 5 && my >= curY && my <= curY + eH) {
                        selectedEntry = entry; 
                        JournalRegistry.setLastSelectedEntryId(entry.id());
                        JournalRegistry.setLastScrollY(0);
                        contentScroller.reset(); 
                        return true;
                    }
                    curY += eH + 1;
                }
                curY += 5;
            }
        }
        return false;
    }

    @Override
    public boolean handleScroll(double yoffset) {
        contentScroller.handleScroll(yoffset);
        return true;
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_J || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            // Save scroll state before closing centralistically in GameLoop
            JournalRegistry.setLastScrollY(contentScroller.getOffset());
            return false; // Delegate closing to GameLoop to prevent double-trigger flashing
        }
        return false;
    }
}


