package com.za.zenith.engine.graphics.ui;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.utils.I18n;
import com.za.zenith.utils.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Modern Data-Driven Pause Screen in Zenith style.
 */
public class PauseScreen implements Screen {
    private static final Identifier GUI_ID = Identifier.of("zenith:pause_menu");
    
    private GUIConfig config;
    private int guiLeft, guiTop, width, height;
    
    // Zenith Colors (Match Journal/Tablet)
    private static final float[] COLOR_BG = {0.03f, 0.03f, 0.03f, 0.98f};
    private static final float[] COLOR_ACCENT = {0.0f, 0.6f, 1.0f, 1.0f};
    private static final float[] TEXT_PRIMARY = {1.0f, 1.0f, 1.0f, 1.0f};

    @Override
    public void init(int sw, int sh) {
        this.config = GUIRegistry.get(GUI_ID);
        if (config == null) {
            com.za.zenith.utils.Logger.error("Failed to load Pause Menu configuration!");
            return;
        }

        // Compact adaptive window
        this.width = 260;
        this.height = 320;
        this.guiLeft = (sw - width) / 2;
        this.guiTop = (sh - height) / 2;
    }

    @Override
    public void render(UIRenderer renderer, int sw, int sh, DynamicTextureAtlas atlas) {
        if (config == null) return;
        
        renderer.setupUIProjection(sw, sh);

        // Update positions based on current sw/sh (handles resize)
        this.guiLeft = (sw - width) / 2;
        this.guiTop = (sh - height) / 2;

        // 1. Darken background (slightly)
        renderer.getPrimitivesRenderer().renderDarkenedBackground();

        // 2. Window Background
        renderer.renderRect(guiLeft - 2, guiTop - 2, width + 4, height + 4, sw, sh, 0.2f, 0.2f, 0.2f, 1.0f); // Border
        renderer.renderRect(guiLeft, guiTop, width, height, sw, sh, COLOR_BG[0], COLOR_BG[1], COLOR_BG[2], COLOR_BG[3]);

        // 3. Cyan Accent Top Line
        renderer.renderRect(guiLeft, guiTop, width, 2, sw, sh, COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2], 1.0f);

        // 4. Title (Centered)
        if (config.title != null) {
            int titleY = guiTop + 30;
            int titleWidth = renderer.getFontRenderer().getStringWidth(config.title, 18);
            renderer.getFontRenderer().drawString(config.title, sw / 2 - titleWidth / 2, titleY, 18, sw, sh);
        }

        // 5. Buttons
        if (config.buttons != null) {
            for (GUIConfig.ButtonConfig btn : config.buttons) {
                renderButton(renderer, btn, sw, sh);
            }
        }
    }

    private void renderButton(UIRenderer renderer, GUIConfig.ButtonConfig btn, int sw, int sh) {
        int x = sw / 2 - btn.width / 2;
        int y = sh / 2 + btn.y;
        
        boolean hovered = isMouseOver(x, y, btn.width, btn.height, sw, sh);
        
        // Button Background
        float alpha = hovered ? 0.08f : 0.03f;
        renderer.renderRect(x, y, btn.width, btn.height, sw, sh, 1.0f, 1.0f, 1.0f, alpha);
        
        // Optional accent border on hover
        if (hovered) {
            renderer.renderRect(x, y, btn.width, 1, sw, sh, btn.color[0], btn.color[1], btn.color[2], 0.3f);
            renderer.renderRect(x, y + btn.height - 1, btn.width, 1, sw, sh, btn.color[0], btn.color[1], btn.color[2], 0.3f);
        }

        // Button Text
        String text = I18n.get(btn.text);
        int tw = renderer.getFontRenderer().getStringWidth(text, 14);
        float[] c = btn.color;
        renderer.getFontRenderer().drawString(text, sw / 2 - tw / 2, y + btn.height / 2 - 7, 14, sw, sh, 
                hovered ? 1.0f : c[0], hovered ? 1.0f : c[1], hovered ? 1.0f : c[2], 1.0f);
    }

    @Override
    public boolean handleMouseClick(float mx, float my, int button) {
        return true; // Поглощаем нажатие, чтобы не прокидывалось в мир
    }

    @Override
    public boolean handleMouseRelease(float mx, float my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_1 || config == null || config.buttons == null) return false;

        int sw = GameLoop.getInstance().getWindow().getWidth();
        int sh = GameLoop.getInstance().getWindow().getHeight();

        for (GUIConfig.ButtonConfig btn : config.buttons) {
            int x = sw / 2 - btn.width / 2;
            int y = sh / 2 + btn.y;
            
            if (mx >= x && mx <= x + btn.width && my >= y && my <= y + btn.height) {
                handleAction(btn.action);
                return true;
            }
        }
        return true; // Поглощаем отпускание
    }

    private void handleAction(String action) {
        if (action == null) return;
        
        GameLoop game = GameLoop.getInstance();
        int sw = game.getWindow().getWidth();
        int sh = game.getWindow().getHeight();

        switch (action) {
            case "resume":
                game.togglePause();
                break;
            case "settings":
                ScreenManager.getInstance().openScreen(new SettingsScreen(), sw, sh);
                break;
            case "main_menu":
                com.za.zenith.utils.Logger.info("Returning to main menu...");
                // In a real game, this would trigger a scene change
                System.exit(0);
                break;
            case "exit":
                System.exit(0);
                break;
        }
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return false; // Delegate closing to GameLoop to prevent double-trigger flashing
        }
        return false;
    }

    private boolean isMouseOver(int x, int y, int w, int h, int sw, int sh) {
        float mx = GameLoop.getInstance().getInputManager().getCurrentMousePos().x;
        float my = GameLoop.getInstance().getInputManager().getCurrentMousePos().y;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
