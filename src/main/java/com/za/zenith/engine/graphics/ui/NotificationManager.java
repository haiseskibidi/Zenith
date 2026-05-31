package com.za.zenith.engine.graphics.ui;

import com.za.zenith.utils.I18n;
import com.za.zenith.utils.Identifier;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lead Developer Mandate: Centralized Notification System (v1.0).
 * Handles item pickup stacks and alert banners with blueprint integration.
 */
public class NotificationManager {
    private static NotificationManager instance;

    private final List<PickupNote> pickupNotes = new ArrayList<>();
    private AlertNote activeAlert = null;

    private NotificationManager() {}

    public static NotificationManager getInstance() {
        if (instance == null) {
            instance = new NotificationManager();
        }
        return instance;
    }

    /**
     * Pushes a new item pickup notification.
     */
    public void pushPickup(ItemStack stack) {
        if (stack == null || stack.getCount() <= 0) return;

        Identifier stackRarity = stack.getRarity();
        List<Identifier> stackAffixes = new ArrayList<>(stack.getActiveAffixes());

        // Traverse backwards to find the most recent notification for this item type AND rarity AND affixes
        for (int i = pickupNotes.size() - 1; i >= 0; i--) {
            PickupNote note = pickupNotes.get(i);
            if (note.item.getId() == stack.getItem().getId() && note.rarity.equals(stackRarity) && note.affixes.equals(stackAffixes)) {
                // If the item was picked up recently, stack it with the existing notification
                if (note.timeSinceLastAdd < 1.0f) {
                    note.count += stack.getCount();
                    note.timer = 4.0f; // Reset display timer
                    note.timeSinceLastAdd = 0.0f; // Reset accumulation window
                    return;
                }
                break;
            }
        }

        pickupNotes.add(new PickupNote(stack.getItem(), stack.getCount(), stackRarity, stackAffixes));
    }

    /**
     * Pushes a high-priority alert banner.
     */
    public void pushAlert(String message, Identifier blueprint, float duration) {
        activeAlert = new AlertNote(message, blueprint, duration);
    }

    public void update(float dt) {
        // Update pickups
        Iterator<PickupNote> it = pickupNotes.iterator();
        while (it.hasNext()) {
            PickupNote note = it.next();
            note.timer -= dt;
            note.timeSinceLastAdd += dt;
            if (note.timer <= 0) {
                it.remove();
            }
        }

        // Update alert
        if (activeAlert != null) {
            activeAlert.timer -= dt;
            if (activeAlert.timer <= 0) {
                activeAlert = null;
            }
        }
    }

    public void render(UIRenderer renderer, int sw, int sh) {
        com.za.zenith.utils.Identifier hudId = com.za.zenith.utils.Identifier.of("zenith:hud");
        GUIConfig config = GUIRegistry.get(hudId);
        if (config == null || config.hudElements == null) return;

        renderPickups(renderer, sw, sh, config.hudElements.get("pickups"));
        renderAlert(renderer, sw, sh, config.hudElements.get("alerts"));
    }

    private void renderPickups(UIRenderer renderer, int sw, int sh, GUIConfig.HUDElementConfig cfg) {
        if (cfg == null || !cfg.visible || pickupNotes.isEmpty()) return;

        int fontSize = cfg.fontSize;
        int spacing = cfg.spacing; 
        
        int[] basePos = com.za.zenith.engine.graphics.ui.renderers.HUDRenderer.calculateElementPos(cfg, sw, sh, 100, fontSize);
        int x = basePos[0];
        int y = basePos[1];

        renderer.setupUIProjection(sw, sh);

        for (int i = 0; i < pickupNotes.size(); i++) {
            PickupNote note = pickupNotes.get(i);
            float alpha = Math.min(1.0f, note.timer);
            
            ItemStack tempStack = new ItemStack(note.item, note.count);
            tempStack.setRarity(note.rarity);
            for (Identifier affix : note.affixes) {
                tempStack.addAffix(affix);
            }
            
            com.za.zenith.world.items.stats.RarityDefinition rarityDef = com.za.zenith.world.items.stats.RarityRegistry.get(note.rarity);
            String formatPrefix = (rarityDef != null ? rarityDef.colorCode() : "$f") + "$l";
            String text = formatPrefix + "+" + note.count + " " + tempStack.getFullDisplayName();
            
            int currentFontSize = fontSize;
            int textWidth = renderer.getFontRenderer().getStringWidth(text, currentFontSize);
            
            int maxWidth = cfg.maxWidth > 0 ? cfg.maxWidth : 250;
            while (textWidth > maxWidth && currentFontSize > cfg.minFontSize) {
                currentFontSize--;
                textWidth = renderer.getFontRenderer().getStringWidth(text, currentFontSize);
            }
            
            int renderX = x;
            if (cfg.alignX.equals("right")) renderX = x - textWidth;
            else if (cfg.alignX.equals("center")) renderX = x - textWidth / 2;

            int renderY = y;
            if (cfg.anchor.contains("top") || cfg.anchor.equals("center") || cfg.anchor.equals("left")) {
                renderY = y + i * spacing;
            } else {
                renderY = y - i * spacing;
            }

            // 1. Draw Background Gradient
            if (cfg.useGradient) {
                int gradHeight = (int)(currentFontSize * 1.4f);
                int gradY = renderY - (gradHeight - currentFontSize) / 2;
                float[] c1 = cfg.backgroundColor;
                float bgAlpha = c1[3] * alpha; // Fade out background together with text
                float[] colCenter = new float[]{c1[0], c1[1], c1[2], bgAlpha};
                float[] colEdge = new float[]{c1[0], c1[1], c1[2], 0.0f}; // Fade to transparent
                
                if (cfg.anchor.contains("left")) {
                    // Wide fade from Left edge
                    int gradWidth = Math.max(sw / 3, renderX + textWidth + 120);
                    renderer.getPrimitivesRenderer().renderGradientRect(0, gradY, gradWidth, gradHeight, sw, sh, colCenter, colEdge);
                } else if (cfg.anchor.contains("right")) {
                    // Wide fade from Right edge
                    int gradWidth = Math.max(sw / 3, (sw - renderX) + 120);
                    int gradX = sw - gradWidth;
                    renderer.getPrimitivesRenderer().renderGradientRect(gradX, gradY, gradWidth, gradHeight, sw, sh, colEdge, colCenter);
                } else {
                    // Center - double sided soft fade (Transparent -> Black -> Transparent)
                    int gradWidth = textWidth + 240;
                    int gradX = renderX - (gradWidth - textWidth) / 2;
                    renderer.getPrimitivesRenderer().renderGradientRect(gradX, gradY, gradWidth / 2, gradHeight, sw, sh, colEdge, colCenter);
                    renderer.getPrimitivesRenderer().renderGradientRect(gradX + gradWidth / 2, gradY, gradWidth / 2, gradHeight, sw, sh, colCenter, colEdge);
                }
            }

            // 2. Draw Text (Shadowed if configured)
            if (cfg.textShadow) {
                renderer.getPrimitivesRenderer().renderTextWithShadow(text, renderX, renderY, currentFontSize, sw, sh, 1.0f, 1.0f, 1.0f, alpha);
            } else {
                renderer.getFontRenderer().drawString(text, renderX, renderY, currentFontSize, sw, sh, 1.0f, 1.0f, 1.0f, alpha);
            }
        }
    }

    private void renderAlert(UIRenderer renderer, int sw, int sh, GUIConfig.HUDElementConfig cfg) {
        if (activeAlert == null || cfg == null || !cfg.visible) return;

        float alpha = Math.min(1.0f, activeAlert.timer * 2.0f);
        int fontSize = cfg.fontSize;
        
        int textWidth = renderer.getFontRenderer().getStringWidth(activeAlert.message, fontSize);
        int padding = 20;
        int plaqueWidth = textWidth + padding * 2 + (activeAlert.blueprint != null ? fontSize * 2 : 0);
        int plaqueHeight = (int)(fontSize * 2.5f);
        
        int[] pos = com.za.zenith.engine.graphics.ui.renderers.HUDRenderer.calculateElementPos(cfg, sw, sh, plaqueWidth, plaqueHeight);
        int plaqueX = pos[0];
        int plaqueY = pos[1];

        // Background and accent line removed as requested by the user to avoid black box with red stripe artifacts

        // 3. Render Blueprint Icon
        int textX = plaqueX + padding;
        if (activeAlert.blueprint != null) {
            int iconSize = (int)(fontSize * 1.5f);
            int iconX = plaqueX + padding;
            int iconY = plaqueY + (plaqueHeight - iconSize) / 2;
            renderer.getBlueprintRenderer().render(activeAlert.blueprint, iconX, iconY, iconSize, sw, sh, new float[]{1.0f});
            textX += iconSize + 10;
        }

        renderer.setupUIProjection(sw, sh);

        // 4. Render Message
        int textY = plaqueY + (plaqueHeight - fontSize) / 2;
        if (cfg.textShadow) {
            renderer.getPrimitivesRenderer().renderTextWithShadow(activeAlert.message, textX, textY, fontSize, sw, sh, 1.0f, 1.0f, 1.0f, alpha);
        } else {
            renderer.getFontRenderer().drawString(activeAlert.message, textX, textY, fontSize, sw, sh, 1.0f, 1.0f, 1.0f, alpha);
        }
    }

    private static class PickupNote {
        Item item;
        int count;
        Identifier rarity;
        List<Identifier> affixes;
        float timer = 4.0f;
        float timeSinceLastAdd = 0.0f;

        PickupNote(Item item, int count, Identifier rarity, List<Identifier> affixes) {
            this.item = item;
            this.count = count;
            this.rarity = rarity;
            this.affixes = affixes;
        }
    }

    private static class AlertNote {
        String message;
        Identifier blueprint;
        float timer;

        AlertNote(String message, Identifier blueprint, float timer) {
            this.message = message;
            this.blueprint = blueprint;
            this.timer = timer;
        }
    }
}
