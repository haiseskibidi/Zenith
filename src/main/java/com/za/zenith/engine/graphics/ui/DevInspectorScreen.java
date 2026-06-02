package com.za.zenith.engine.graphics.ui;

import com.google.gson.*;
import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.resources.AssetManager;
import com.za.zenith.utils.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DevInspectorScreen v4.0 - Data-Driven JSON Editor.
 * No more Reflection hacks. Pure JSON manipulation.
 */
public class DevInspectorScreen implements Screen {
    private enum Tab { BLOCKS, ITEMS, RECIPES, ENTITIES, WORLD, ANIMATION, REGISTRY, SYSTEM }

    private static Tab activeTab = Tab.BLOCKS;
    private static String selectedPath = null;
    private static JsonElement rootElement = null;
    private static final Set<String> expandedNodes = new HashSet<>(List.of(""));
    private static float sidebarScroll = 0;
    private static float propertiesScroll = 0;
    
    private final UISearchBar searchBar = new UISearchBar("gui.search_placeholder");

    private static final Stack<String> undoStack = new Stack<>();
    private static final Stack<String> redoStack = new Stack<>();

    private final ScrollPanel sidebarScroller = new ScrollPanel();
    private final ScrollPanel propertiesScroller = new ScrollPanel();

    private int guiLeft, guiTop, width, height;
    private final int sidebarWidth = 260;
    private final int topBarHeight = 40;
    private final int tabHeight = 30;

    private String editingPath = null;
    private String editingValue = "";
    private JsonElement editingElement = null;
    
    private String searchFilter = "";

    @Override
    public void init(int sw, int sh) {
    }

    private int getTabWidth(FontRenderer fr, String name) {
        return fr.getStringWidth(name, 14) + 20;
    }

    @Override
    public void render(UIRenderer renderer, int sw, int sh, DynamicTextureAtlas atlas) {
        // Update dynamic layout
        this.width = (int)(sw * 0.95f);
        this.height = (int)(sh * 0.9f);
        this.guiLeft = (sw - width) / 2;
        this.guiTop = (sh - height) / 2;

        sidebarScroller.setBounds(guiLeft + 10, guiTop + 120, sidebarWidth, height - 160);
        propertiesScroller.setBounds(guiLeft + sidebarWidth + 20, guiTop + 90, width - sidebarWidth - 30, height - 130);

        renderer.setupUIProjection(sw, sh);
        renderer.getPrimitivesRenderer().renderDarkenedBackground();

        // Main Panel
        renderer.getPrimitivesRenderer().renderRect(guiLeft, guiTop, width, height, sw, sh, 0.02f, 0.02f, 0.02f, 0.98f);
        renderer.getPrimitivesRenderer().renderRect(guiLeft, guiTop, width, topBarHeight, sw, sh, 0.0f, 0.4f, 0.7f, 1.0f);
        renderer.getFontRenderer().drawString("ZENITH JSON INSPECTOR v4.2", guiLeft + 20, guiTop + 12, 18, sw, sh);

        renderTabs(renderer, sw, sh);

        // Search Bar
        searchBar.setBounds(guiLeft + 10, guiTop + 90, sidebarWidth, 22);
        searchBar.render(renderer, sw, sh);

        // Sidebar
        sidebarScroller.begin(sw, sh);
        renderSidebar(renderer, sw, sh);
        sidebarScroller.end();
        sidebarScroller.renderScrollbar(renderer, sw, sh);

        // Properties
        propertiesScroller.begin(sw, sh);
        renderProperties(renderer, sw, sh);
        propertiesScroller.end();
        propertiesScroller.renderScrollbar(renderer, sw, sh);

        renderFooter(renderer, sw, sh);
    }

    private void renderTabs(UIRenderer renderer, int sw, int sh) {
        int x = guiLeft + 10;
        int y = guiTop + 50;
        int spacing = 8;
        FontRenderer fr = renderer.getFontRenderer();
        for (Tab tab : Tab.values()) {
            boolean active = activeTab == tab;
            String name = tab.name();
            int tw = getTabWidth(fr, name);

            if (active) renderer.getPrimitivesRenderer().renderRect(x, y, tw, tabHeight, sw, sh, 0.0f, 0.6f, 1.0f, 0.8f);
            else renderer.getPrimitivesRenderer().renderRect(x, y, tw, tabHeight, sw, sh, 0.15f, 0.15f, 0.15f, 0.8f);

            renderer.getFontRenderer().drawString(name, x + 10, y + 8, 14, sw, sh, 1, 1, 1, 1);
            x += tw + spacing;
        }
    }

    private void renderSidebar(UIRenderer renderer, int sw, int sh) {
        int x = guiLeft + 15;
        int y = (int) (guiTop + 130 - sidebarScroller.getOffset());
        int h = 25;
        
        String filter = searchBar.getQuery().toLowerCase();
        List<String> paths = AssetManager.getLoadedPaths().stream()
                .filter(this::matchesTab)
                .filter(p -> filter.isEmpty() || p.toLowerCase().contains(filter))
                .sorted()
                .collect(Collectors.toList());

        for (String path : paths) {
            boolean selected = path.equals(selectedPath);
            if (selected) renderer.getPrimitivesRenderer().renderRect(x, y - 2, sidebarWidth - 15, h, sw, sh, 0.0f, 0.6f, 1.0f, 0.3f);
            
            String label = path.substring(path.lastIndexOf('/') + 1);
            renderer.getFontRenderer().drawString(label, x + 10, y, 12, sw, sh, selected ? 0.0f : 0.8f, selected ? 0.8f : 0.8f, selected ? 1.0f : 0.8f, 1.0f);
            y += h;
        }
        sidebarScroller.updateContentHeight(y - (int)(guiTop + 130 - sidebarScroller.getOffset()));
    }

    @Override
    public boolean handleMouseClick(float mx, float my, int button) {
        if (button != 0) return false;

        // Ensure bounds are up-to-date for click detection
        int sw = GameLoop.getInstance().getWindow().getWidth();
        int sh = GameLoop.getInstance().getWindow().getHeight();
        this.width = (int)(sw * 0.95f);
        this.height = (int)(sh * 0.9f);
        this.guiLeft = (sw - width) / 2;
        this.guiTop = (sh - height) / 2;

        // Update scroller bounds for isMouseOver check
        sidebarScroller.setBounds(guiLeft + 10, guiTop + 120, sidebarWidth, height - 160);
        propertiesScroller.setBounds(guiLeft + sidebarWidth + 20, guiTop + 90, width - sidebarWidth - 30, height - 130);

        if (searchBar.handleMouseClick(mx, my, button)) {
            return true;
        }

        // Tabs - Precise Calculation
        int tx = guiLeft + 10;
        int ty = guiTop + 50;
        int spacing = 8;
        FontRenderer fr = GameLoop.getInstance().getRenderer().getUIRenderer().getFontRenderer();
        
        for (Tab tab : Tab.values()) {
            int tw = getTabWidth(fr, tab.name());
            if (mx >= tx && mx <= tx + tw && my >= ty && my <= ty + tabHeight) {
                if (activeTab != tab) {
                    activeTab = tab;
                    selectedPath = null;
                    rootElement = null;
                    sidebarScroller.reset();
                    propertiesScroller.reset();
                }
                return true;
            }
            tx += tw + spacing;
        }

        // Sidebar entries
        if (sidebarScroller.isMouseOver(mx, my)) {
            int x = guiLeft + 15;
            int y = (int) (guiTop + 130 - sidebarScroller.getOffset());
            String filter = searchBar.getQuery().toLowerCase();
            List<String> paths = AssetManager.getLoadedPaths().stream()
                    .filter(this::matchesTab)
                    .filter(p -> filter.isEmpty() || p.toLowerCase().contains(filter))
                    .sorted()
                    .collect(Collectors.toList());
            for (String path : paths) {
                if (mx >= x && mx <= x + sidebarWidth - 15 && my >= y && my <= y + 25) {
                    selectPath(path);
                    return true;
                }
                y += 25;
            }
        }

        // Properties (Recursive Json Tree)
        if (propertiesScroller.isMouseOver(mx, my)) {
            // Logic for tree expansion and value clicking is now handled in a stateful way
            // We'll trigger a flag that renderProperties will check, or just keep it simple:
            lastClickX = mx; lastClickY = my;
            pendingClick = true;
        }

        return false;
    }

    private float lastClickX, lastClickY;
    private boolean pendingClick = false;

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean matchesTab(String path) {
        return switch (activeTab) {
            case BLOCKS -> path.contains("/blocks/");
            case ITEMS -> path.contains("/items/");
            case RECIPES -> path.contains("/recipes/");
            case ENTITIES -> path.contains("/entities/");
            case WORLD -> path.contains("/generation/") || path.contains("/structures/");
            case ANIMATION -> path.contains("/actions/") || path.contains("/animations/") || path.contains("/models/") || path.contains("/grips/");
            case REGISTRY -> path.contains("/registry/") || path.contains("/journal/");
            case SYSTEM -> path.contains("/gui/") || path.contains("/lang/") || path.contains("/texts/") || (!path.contains("/") && path.endsWith(".json"));
        };
    }

    private void selectPath(String path) {
        if (path.equals(selectedPath)) return;
        selectedPath = path;
        String snapshot = AssetManager.getSnapshot(path);
        if (snapshot != null) {
            rootElement = AssetManager.getGson().fromJson(snapshot, JsonElement.class);
        }
    }

    private void renderProperties(UIRenderer renderer, int sw, int sh) {
        if (rootElement == null) {
            int cx = guiLeft + sidebarWidth + (width - sidebarWidth) / 2;
            int cy = guiTop + height / 2;
            String msg = "Select a resource to edit";
            int mw = renderer.getFontRenderer().getStringWidth(msg, 16);
            renderer.getFontRenderer().drawString(msg, cx - mw / 2, cy, 16, sw, sh, 0.4f, 0.4f, 0.4f, 1.0f);
            return;
        }

        int px = guiLeft + sidebarWidth + 20;
        int py = (int) (guiTop + 100 - propertiesScroller.getOffset());
        
        renderer.getFontRenderer().drawString(selectedPath, px, py, 14, sw, sh, 0.0f, 0.8f, 1.0f, 1.0f);
        py += 30;

        py = renderJsonElement(renderer, rootElement, "root", "", px, py, 0, sw, sh);
        
        propertiesScroller.updateContentHeight(py - (int)(guiTop + 100 - propertiesScroller.getOffset()));
    }

    private int renderJsonElement(UIRenderer renderer, JsonElement el, String label, String path, int x, int y, int depth, int sw, int sh) {
        if (el.isJsonObject()) {
            return renderJsonObject(renderer, el.getAsJsonObject(), label, path, x, y, depth, sw, sh);
        } else if (el.isJsonArray()) {
            return renderJsonArray(renderer, el.getAsJsonArray(), label, path, x, y, depth, sw, sh);
        } else {
            return renderJsonValue(renderer, el.getAsJsonPrimitive(), label, path, x, y, sw, sh);
        }
    }

    private int renderJsonObject(UIRenderer renderer, JsonObject obj, String label, String path, int x, int y, int depth, int sw, int sh) {
        boolean expanded = expandedNodes.contains(path);
        String prefix = expanded ? "[-] " : "[+] ";
        renderer.getFontRenderer().drawString(prefix + label, x, y, 14, sw, sh, 0.9f, 0.9f, 0.0f, 1.0f);
        
        if (pendingClick && isHovered((int)lastClickX, (int)lastClickY, x, y, 200, 20)) {
            if (expanded) expandedNodes.remove(path);
            else expandedNodes.add(path);
            pendingClick = false;
        }
        
        y += 22;
        if (expanded) {
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                y = renderJsonElement(renderer, entry.getValue(), entry.getKey(), childPath, x + 20, y, depth + 1, sw, sh);
            }
        }
        return y;
    }

    private int renderJsonArray(UIRenderer renderer, JsonArray arr, String label, String path, int x, int y, int depth, int sw, int sh) {
        boolean expanded = expandedNodes.contains(path);
        String prefix = expanded ? "[-] " : "[+] ";
        renderer.getFontRenderer().drawString(prefix + label + " [" + arr.size() + "]", x, y, 14, sw, sh, 0.0f, 0.8f, 0.5f, 1.0f);
        
        if (pendingClick && isHovered((int)lastClickX, (int)lastClickY, x, y, 200, 20)) {
            if (expanded) expandedNodes.remove(path);
            else expandedNodes.add(path);
            pendingClick = false;
        }
        
        y += 22;
        if (expanded) {
            for (int i = 0; i < arr.size(); i++) {
                String childPath = path + "[" + i + "]";
                y = renderJsonElement(renderer, arr.get(i), "Item " + i, childPath, x + 20, y, depth + 1, sw, sh);
            }
        }
        return y;
    }

    private int renderJsonValue(UIRenderer renderer, JsonPrimitive primitive, String label, String path, int x, int y, int sw, int sh) {
        String fullLabel = label + ": ";
        int lw = renderer.getFontRenderer().getStringWidth(fullLabel, 14);
        renderer.getFontRenderer().drawString(fullLabel, x, y, 14, sw, sh, 0.7f, 0.7f, 0.7f, 1.0f);
        
        boolean isEditing = path.equals(editingPath);
        String valStr = isEditing ? editingValue + "_" : primitive.getAsString();
        
        renderer.getFontRenderer().drawString(valStr, x + lw, y, 14, sw, sh, isEditing ? 0.0f : 1.0f, isEditing ? 1.0f : 1.0f, isEditing ? 0.0f : 1.0f, 1.0f);
        
        if (pendingClick && isHovered((int)lastClickX, (int)lastClickY, x + lw, y, 400, 20)) {
            editingPath = path;
            editingValue = primitive.getAsString();
            editingElement = primitive;
            pendingClick = false;
        }
        
        return y + 22;
    }

    private void applyEditing() {
        if (editingElement == null || selectedPath == null) return;
        
        try {
            // Push current state to undo stack before change
            undoStack.push(AssetManager.getGson().toJson(rootElement));
            redoStack.clear();

            // Update the element in our local Json tree
            updateJsonValue(rootElement, editingPath, editingValue);
            // Save to disk and reload
            AssetManager.saveAndReload(selectedPath, rootElement);
        } catch (Exception e) {
            Logger.error("Failed to apply JSON edit: " + e.getMessage());
        }
        
        editingPath = null;
        editingElement = null;
    }

    private void updateJsonValue(JsonElement root, String path, String value) {
        if (path.isEmpty()) return; // Root editing not supported this way
        String[] parts = path.split("\\.");
        JsonElement current = root;
        
        for (int i = 0; i < parts.length - 1; i++) {
            current = getChild(current, parts[i]);
            if (current == null) return;
        }
        
        String lastPart = parts[parts.length - 1];
        setChildValue(current, lastPart, value);
    }

    private JsonElement getChild(JsonElement parent, String part) {
        if (part.contains("[")) {
            String name = part.substring(0, part.indexOf('['));
            JsonElement el = parent.getAsJsonObject().get(name);
            if (el == null || !el.isJsonArray()) return null;
            
            JsonArray arr = el.getAsJsonArray();
            String indicesStr = part.substring(part.indexOf('['));
            String[] indices = indicesStr.split("\\]\\[|\\[|\\]");
            
            for (String indexPart : indices) {
                if (indexPart.isEmpty()) continue;
                int idx = Integer.parseInt(indexPart);
                if (idx < 0 || idx >= arr.size()) return null;
                JsonElement next = arr.get(idx);
                if (next.isJsonArray()) {
                    arr = next.getAsJsonArray();
                } else {
                    return next;
                }
            }
            return arr;
        }
        return parent.getAsJsonObject().get(part);
    }

    private void setChildValue(JsonElement parent, String part, String value) {
        if (part.contains("[")) {
            String name = part.substring(0, part.indexOf('['));
            JsonElement el = parent.getAsJsonObject().get(name);
            if (el == null || !el.isJsonArray()) return;
            
            JsonArray arr = el.getAsJsonArray();
            String indicesStr = part.substring(part.indexOf('['));
            String[] indices = indicesStr.split("\\]\\[|\\[|\\]");
            
            List<String> validIndices = Arrays.stream(indices).filter(s -> !s.isEmpty()).toList();
            for (int i = 0; i < validIndices.size() - 1; i++) {
                int idx = Integer.parseInt(validIndices.get(i));
                arr = arr.get(idx).getAsJsonArray();
            }
            
            int lastIdx = Integer.parseInt(validIndices.get(validIndices.size() - 1));
            JsonElement existing = arr.get(lastIdx);
            if (existing.isJsonPrimitive()) {
                arr.set(lastIdx, parsePrimitive(existing.getAsJsonPrimitive(), value));
            }
        } else {
            JsonObject obj = parent.getAsJsonObject();
            JsonElement existing = obj.get(part);
            if (existing != null && existing.isJsonPrimitive()) {
                obj.add(part, parsePrimitive(existing.getAsJsonPrimitive(), value));
            }
        }
    }

    private JsonPrimitive parsePrimitive(JsonPrimitive old, String value) {
        if (old.isBoolean()) return new JsonPrimitive(Boolean.parseBoolean(value));
        if (old.isNumber()) return new JsonPrimitive(Double.parseDouble(value));
        return new JsonPrimitive(value);
    }

    private void renderFooter(UIRenderer renderer, int sw, int sh) {
        renderer.getPrimitivesRenderer().renderRect(10, sh - 40, sw - 20, 30, sw, sh, 0.05f, 0.05f, 0.05f, 1.0f);
        String info = "ENTER: Save & Reload | ESC: Close | Click value to edit";
        renderer.getFontRenderer().drawString(info, 30, sh - 30, 12, sw, sh, 0.6f, 0.6f, 0.6f, 1.0f);
    }

    @Override
    public boolean handleKeyPress(int key) {
        if (searchBar.handleKeyPress(key)) {
            return true;
        }

        boolean ctrl = GameLoop.getInstance().getWindow().isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL);
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (!undoStack.isEmpty() && selectedPath != null) {
                redoStack.push(AssetManager.getGson().toJson(rootElement));
                String prevState = undoStack.pop();
                rootElement = AssetManager.getGson().fromJson(prevState, JsonElement.class);
                AssetManager.saveAndReload(selectedPath, rootElement);
                Logger.info("Undo JSON edit");
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            if (!redoStack.isEmpty() && selectedPath != null) {
                undoStack.push(AssetManager.getGson().toJson(rootElement));
                String nextState = redoStack.pop();
                rootElement = AssetManager.getGson().fromJson(nextState, JsonElement.class);
                AssetManager.saveAndReload(selectedPath, rootElement);
                Logger.info("Redo JSON edit");
            }
            return true;
        }

        if (editingPath != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !editingValue.isEmpty()) editingValue = editingValue.substring(0, editingValue.length() - 1);
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) applyEditing();
            else if (key == GLFW.GLFW_KEY_ESCAPE) editingPath = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_F9) {
            return false; // Delegate closing to GameLoop to prevent double-trigger flashing
        }
        return false;
    }

    @Override
    public boolean handleChar(int codepoint) {
        if (searchBar.handleChar(codepoint)) {
            return true;
        }
        if (editingPath != null) {
            editingValue += (char) codepoint;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleScroll(double yoffset) {
        float mouseX = GameLoop.getInstance().getInputManager().getMouseX();
        float mouseY = GameLoop.getInstance().getInputManager().getMouseY();

        if (sidebarScroller.isMouseOver(mouseX, mouseY)) {
            sidebarScroller.handleScroll(yoffset);
            sidebarScroll = sidebarScroller.getOffset();
            return true;
        } else if (propertiesScroller.isMouseOver(mouseX, mouseY)) {
            propertiesScroller.handleScroll(yoffset);
            propertiesScroll = propertiesScroller.getOffset();
            return true;
        }

        return false;
    }

    private int rendererWidth(String s) { return s.length() * 8; }
}
