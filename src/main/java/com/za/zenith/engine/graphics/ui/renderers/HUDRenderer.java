package com.za.zenith.engine.graphics.ui.renderers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ui.GUIConfig;
import com.za.zenith.engine.graphics.ui.GUIRegistry;
import com.za.zenith.engine.graphics.ui.Hotbar;
import com.za.zenith.engine.graphics.ui.InventoryLayout;
import com.za.zenith.engine.graphics.ui.LayoutResult;
import com.za.zenith.engine.graphics.ui.ScreenManager;
import com.za.zenith.engine.graphics.ui.SlotUI;
import com.za.zenith.engine.graphics.ui.GroupUI;
import com.za.zenith.engine.graphics.ui.UIEffectsRenderer;
import com.za.zenith.engine.graphics.ui.UIRenderer;
import com.za.zenith.utils.I18n;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.generation.BiomeDefinition;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

public class HUDRenderer {
    private final UIRenderer renderer;
    private final float[] waveAmplitudes = new float[32];
    private float waveTimer = 0;
    private float sonarPhase = 0;

    public HUDRenderer(UIRenderer renderer) {
        this.renderer = renderer;
    }

    private GUIConfig.HUDElementConfig getHUDConfig(String elementId) {
        GUIConfig config = GUIRegistry.get(com.za.zenith.utils.Identifier.of("zenith:hud"));
        if (config != null && config.hudElements != null) {
            return config.hudElements.get(elementId);
        }
        return null;
    }

    public static int[] calculateElementPos(GUIConfig.HUDElementConfig cfg, int sw, int sh, int width, int height) {
        int baseX = sw / 2, baseY = sh / 2;
        String anchor = cfg.anchor.toLowerCase();
        if (anchor.contains("top")) baseY = 0;
        else if (anchor.contains("bottom")) baseY = sh;
        if (anchor.contains("left")) baseX = 0;
        else if (anchor.contains("right")) baseX = sw;
        if (anchor.equals("bottom_center")) { baseX = sw / 2; baseY = sh; }
        else if (anchor.equals("top_center")) { baseX = sw / 2; baseY = 0; }

        int alignX = 0;
        if (cfg.alignX.equals("center")) alignX = -width / 2; else if (cfg.alignX.equals("right")) alignX = -width;
        int alignY = 0;
        if (cfg.alignY.equals("center")) alignY = -height / 2; else if (cfg.alignY.equals("bottom")) alignY = -height;

        int offsetX = InventoryLayout.calculateCoord(cfg.x, sw, 0, 0);
        int offsetY = InventoryLayout.calculateCoord(cfg.y, sh, 0, 0);

        return new int[]{baseX + alignX + offsetX, baseY + alignY + offsetY};
    }

    public void renderHotbar(Hotbar hotbar, int screenWidth, int screenHeight, com.za.zenith.engine.graphics.DynamicTextureAtlas atlas) {
        if (hotbar == null || ScreenManager.getInstance().isAnyScreenOpen()) return;
        GUIConfig config = GUIRegistry.get(com.za.zenith.utils.Identifier.of("zenith:hotbar"));
        if (config == null || !config.hudVisible) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        int slotSize = (int)(20 * Hotbar.HOTBAR_SCALE);
        int spacing = (int)(2 * Hotbar.HOTBAR_SCALE);
        java.util.Map<String, com.za.zenith.world.inventory.IInventory> inventories = new java.util.HashMap<>();
        inventories.put("player", hotbar.getPlayer().getInventory());
        
        LayoutResult layout = InventoryLayout.generateLayout(screenWidth, screenHeight, slotSize, spacing, hotbar.getPlayer(), config, inventories);
        List<SlotUI> slots = layout.slots;

        if (layout.globalBackground != null) {
            renderer.getPrimitivesRenderer().renderGroupBackground(layout.globalBackground.getX(), layout.globalBackground.getY(), layout.globalBackground.getWidth(), layout.globalBackground.getHeight(), config.background);
        }

        int selectedSlot = hotbar.getSelectedSlot();
        for (int i = 0; i < slots.size(); i++) {
            SlotUI ui = slots.get(i);
            renderer.getSlotRenderer().renderSlot(ui.getX(), ui.getY(), slotSize, ui.getSlot().getStack(), null, screenWidth, screenHeight, atlas, false, "hotbar_" + i, true);
            if (i == selectedSlot) {
                UIEffectsRenderer.renderSelection(renderer, renderer.getShader(), renderer.getQuadVAO(), ui.getX(), ui.getY(), slotSize, screenWidth, screenHeight, config.selection);
            }
        }
        
        ItemStack selected = hotbar.getSelectedItemStack();
        if (selected != null && !slots.isEmpty()) {
            GUIConfig.HUDElementConfig nameCfg = getHUDConfig("item_name");
            if (nameCfg != null && nameCfg.visible) {
                String nameText = selected.getFullDisplayName();

                int nameSize = nameCfg.fontSize;
                int textWidth = renderer.getFontRenderer().getStringWidth(nameText, nameSize);

                // Dynamic Scaling Logic
                while (textWidth > nameCfg.maxWidth && nameSize > nameCfg.minFontSize) {
                    nameSize--;
                    textWidth = renderer.getFontRenderer().getStringWidth(nameText, nameSize);
                }

                int[] pos = calculateElementPos(nameCfg, screenWidth, screenHeight, textWidth, nameSize);
                
                // 1. Draw Background Gradient if enabled
                if (nameCfg.useGradient) {
                    int gradWidth = textWidth + 300;
                    int gradHeight = (int)(nameSize * 1.8f);
                    int gradX = pos[0] - (gradWidth - textWidth) / 2;
                    int gradY = pos[1] - (gradHeight - nameSize) / 2;
                    
                    float[] c1 = nameCfg.backgroundColor;
                    float[] c2 = new float[]{c1[0], c1[1], c1[2], 0.0f};
                    
                    renderer.getPrimitivesRenderer().renderGradientRect(gradX, gradY, gradWidth / 2, gradHeight, screenWidth, screenHeight, c2, c1);
                    renderer.getPrimitivesRenderer().renderGradientRect(gradX + gradWidth / 2, gradY, gradWidth / 2, gradHeight, screenWidth, screenHeight, c1, c2);
                }

                if (nameCfg.textShadow) {
                    renderer.getPrimitivesRenderer().renderTextWithShadow(nameText, pos[0], pos[1], nameSize, screenWidth, screenHeight, 1.0f, 1.0f, 1.0f, 1.0f);
                } else {
                    renderer.getFontRenderer().drawString(nameText, pos[0], pos[1], nameSize, screenWidth, screenHeight);
                }
            }
        }
    }

    public void renderOverlay(Hotbar hotbar, int sw, int sh) {
        if (ScreenManager.getInstance().isAnyScreenOpen()) return;
        com.za.zenith.entities.Player player = hotbar.getPlayer();
        renderer.setupUIProjection(sw, sh);
        
        float dt = GameLoop.getInstance().getTimer().getDeltaF();
        float noise = player.getNoiseLevel();

        renderBar("health", sw, sh, player.getHealth() / 20.0f); 
        renderBar("hunger", sw, sh, player.getHunger() / 20.0f);
        if (player.getStamina() < 0.99f) renderBar("stamina", sw, sh, player.getStamina());
        renderBar("noise", sw, sh, noise);

        renderMinimap(player, sw, sh);

        // --- Contextual Interaction HUD ---
        com.za.zenith.world.physics.RaycastResult hit = GameLoop.getInstance().getHighlightedBlock();
        renderer.getInteractionRenderer().updateAndRender(hit, sw, sh);

        renderLogo(sw, sh);
        renderDebugOverlay(sw, sh);

        // --- Notification System v1.0 ---
        com.za.zenith.engine.graphics.ui.NotificationTriggers.getInstance().checkDurability(hotbar.getSelectedItemStack());
        com.za.zenith.engine.graphics.ui.NotificationManager.getInstance().update(dt);
        com.za.zenith.engine.graphics.ui.NotificationManager.getInstance().render(renderer, sw, sh);
    }

    public void renderDebugOverlay(int sw, int sh) {
        if (!com.za.zenith.engine.core.SettingsManager.getInstance().isDebugOverlayVisible()) return;

        GUIConfig config = GUIRegistry.get(com.za.zenith.utils.Identifier.of("zenith:debug_hud"));
        if (config == null || config.hudElements == null) return;

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        GameLoop game = GameLoop.getInstance();
        com.za.zenith.entities.Player player = game.getPlayer();
        org.joml.Vector3f pos = player.getPosition();

        for (GUIConfig.HUDElementConfig el : config.hudElements.values()) {
            if (!el.visible || !"text".equals(el.type) || el.text == null) continue;
            if (el.condition != null && el.condition.equals("debug_mode") && !com.za.zenith.engine.core.SettingsManager.getInstance().isDebugOverlayVisible()) continue;

            String text = el.text
                .replace("{fps}", String.valueOf((int)game.getCurrentFps()))
                .replace("{x}", String.format(java.util.Locale.US, "%.2f", pos.x))
                .replace("{y}", String.format(java.util.Locale.US, "%.2f", pos.y - com.za.zenith.world.chunks.Chunk.LOGICAL_OFFSET_Y))
                .replace("{z}", String.format(java.util.Locale.US, "%.2f", pos.z))
                .replace("{cx}", String.valueOf((int)pos.x >> 4))
                .replace("{cz}", String.valueOf((int)pos.z >> 4))
                .replace("{sections}", String.valueOf(game.getRenderer().getVisibleSectionsCount()))
                .replace("{dc}", String.valueOf(game.getRenderer().getDrawCallCount()));
            
            // Add Biome info
            BiomeDefinition biome = game.getWorld().getBiomeManager().getBiome((int)pos.x, (int)pos.z);
            if (biome != null) {
                text = text.replace("{biome}", biome.getId().toString());
            } else {
                text = text.replace("{biome}", "Unknown");
            }

            // Targeted block info
            com.za.zenith.world.physics.RaycastResult hit = game.getHighlightedBlock();
            if (hit != null && hit.isHit()) {
                com.za.zenith.world.blocks.Block b = game.getWorld().getBlock(hit.getBlockPos());
                com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
                if (def != null) {
                    text = text.replace("{t_id}", def.getIdentifier().toString());
                    text = text.replace("{t_meta}", String.valueOf(b.getMetadata() & 0xFF));
                    
                    StringBuilder flagsStr = new StringBuilder();
                    if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_SOLID)) flagsStr.append("S");
                    if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_TRANSPARENT)) flagsStr.append("T");
                    if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_TRANSLUCENT)) flagsStr.append("Tr");
                    if (b.isNatural()) flagsStr.append("N");
                    if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_TINTED)) flagsStr.append("C");
                    if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_LEAVES)) flagsStr.append("L");
                    
                    text = text.replace("{t_flags}", flagsStr.toString());
                }
            } else {
                text = text.replace("{t_id}", "None").replace("{t_meta}", "-").replace("{t_flags}", "");
            }

            int fontSize = el.fontSize;
            int textWidth = renderer.getFontRenderer().getStringWidth(text, fontSize);
            int[] elementPos = calculateElementPos(el, sw, sh, textWidth, fontSize);

            // Shadow
            renderer.getFontRenderer().drawString(text, elementPos[0] + 1, elementPos[1] + 1, fontSize, sw, sh, 0.0f, 0.0f, 0.0f, 0.5f);
            
            // Main text
            float[] color = el.color != null ? el.color : new float[]{1.0f, 1.0f, 1.0f, 1.0f};
            renderer.getFontRenderer().drawString(text, elementPos[0], elementPos[1], fontSize, sw, sh, color[0], color[1], color[2], color[3]);
        }

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    private void renderMinimap(com.za.zenith.entities.Player player, int sw, int sh) {
        GUIConfig.HUDElementConfig cfg = getHUDConfig("minimap");
        if (cfg == null || !cfg.visible) return;
        
        int size = cfg.width;
        int[] pos = calculateElementPos(cfg, sw, sh, size, size);
        
        MinimapRenderer minimap = renderer.getMinimapRenderer();
        float dt = GameLoop.getInstance().getTimer().getDeltaF();
        
        minimap.update(player, GameLoop.getInstance().getWorld(), dt);
        minimap.render(pos[0], pos[1], size, player, sw, sh);
    }

    private void renderBlueprint(String elementId, int sw, int sh, float[] triggers) {
        GUIConfig.HUDElementConfig cfg = getHUDConfig(elementId);
        if (cfg == null || !cfg.visible || cfg.blueprint == null) return;
        int size = cfg.width; 
        int[] pos = calculateElementPos(cfg, sw, sh, size, size);
        com.za.zenith.utils.Identifier id = com.za.zenith.utils.Identifier.of(cfg.blueprint);
        renderer.getBlueprintRenderer().render(id, pos[0], pos[1], size, sw, sh, triggers);
    }

    private void renderBar(String elementId, int sw, int sh, float progress) {
        GUIConfig.HUDElementConfig cfg = getHUDConfig(elementId);
        if (cfg == null || !cfg.visible) return;
        int width = cfg.width, height = cfg.height;
        int[] pos = calculateElementPos(cfg, sw, sh, width, height);
        float[] bg = cfg.backgroundColor;
        renderer.getPrimitivesRenderer().renderRect(pos[0], pos[1], width, height, sw, sh, bg[0], bg[1], bg[2], bg[3]);
        if (progress <= 0) return;
        float[] fg = cfg.color;
        int segments = cfg.segments;
        if (segments <= 0) {
            renderer.getPrimitivesRenderer().renderRect(pos[0], pos[1], (int)(width * progress), height, sw, sh, fg[0], fg[1], fg[2], fg[3]);
        } else {
            int gap = 2;
            int segmentWidth = (width - (segments - 1) * gap) / segments;
            int activeSegments = (int)Math.ceil(progress * segments);
            for (int i = 0; i < activeSegments; i++) {
                int sx = pos[0] + i * (segmentWidth + gap);
                int curWidth = (i == activeSegments - 1) ? (int)(segmentWidth * (progress * segments - i)) : segmentWidth;
                if (curWidth > 0) renderer.getPrimitivesRenderer().renderRect(sx, pos[1], curWidth, height, sw, sh, fg[0], fg[1], fg[2], fg[3]);
            }
        }
    }

    public void renderLogo(int screenWidth, int screenHeight) {
        GUIConfig.HUDElementConfig cfg = getHUDConfig("logo");
        if (cfg == null || !cfg.visible || cfg.texture == null) return;
        int[] pos = calculateElementPos(cfg, screenWidth, screenHeight, cfg.width, cfg.height);
        String texturePath = cfg.texture.startsWith("src/main/resources/") ? cfg.texture : "src/main/resources/" + cfg.texture;
        renderer.getPrimitivesRenderer().renderExternalImage(texturePath, pos[0], pos[1], cfg.width, cfg.height, screenWidth, screenHeight);
    }

    public void renderFiringProgress(int screenWidth, int screenHeight, float progress) {
        if (progress <= 0.0f) return;
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        String text = I18n.format("gui.firing_progress", (int)(progress * 100));
        int textSize = 18;
        int textWidth = renderer.getFontRenderer().getStringWidth(text, textSize);
        renderer.getFontRenderer().drawString(text, (screenWidth - textWidth) / 2, (screenHeight / 2) + 30, textSize, screenWidth, screenHeight);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    public void renderLootboxOpening(int sw, int sh) {
        float timer = GameLoop.getInstance().getInputManager().getLootboxOpeningTimer();
        if (timer <= 0) return;
        ItemStack stack = GameLoop.getInstance().getInputManager().getLootboxStack();
        if (stack == null) return;
        com.za.zenith.world.items.component.LootboxComponent comp = stack.getItem().getComponent(com.za.zenith.world.items.component.LootboxComponent.class);
        if (comp == null) return;
        float progress = timer / comp.openingTime();
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int barWidth = 100, barHeight = 4, x = (sw - barWidth) / 2, y = (sh / 2) + 80;
        renderer.getPrimitivesRenderer().renderRect(x, y, barWidth, barHeight, sw, sh, 0.1f, 0.1f, 0.1f, 0.5f);
        renderer.getPrimitivesRenderer().renderRect(x, y, (int)(barWidth * progress), barHeight, sw, sh, 1.0f, 1.0f, 1.0f, 0.9f);
        String text = I18n.get("ui.opening_case") + " " + (int)(progress * 100) + "%";
        int textWidth = renderer.getFontRenderer().getStringWidth(text, 14);
        renderer.getFontRenderer().drawString(text, (sw - textWidth) / 2, y - 18, 14, sw, sh, 1.0f, 1.0f, 1.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }
}
