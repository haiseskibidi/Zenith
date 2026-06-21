package com.za.zenith.engine.graphics.ui;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.ui.renderers.InventoryScreenRenderer;
import com.za.zenith.entities.Player;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.entities.inventory.Slot;
import com.za.zenith.entities.inventory.SlotGroup;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

/**
 * Main player inventory screen.
 */
public class PlayerInventoryScreen extends InventoryScreen {
    private final Player player;
    private final List<GroupUI> groups = new ArrayList<>();
    private GroupUI globalBackground;
    private GUIConfig.BackgroundConfig backgroundConfig;

    private String lastLayoutKey = "";

    public PlayerInventoryScreen(Player player) {
        super("Player Inventory");
        this.player = player;
    }

    private String calculateLayoutKey(int sw, int sh) {
        StringBuilder key = new StringBuilder();
        key.append(sw).append("x").append(sh).append("|");
        key.append(player.getMode().name()).append("|");
        
        // Add activity status and size of all groups
        for (SlotGroup group : player.getInventory().getGroups()) {
            key.append(group.getId()).append(":")
               .append(group.isActive()).append(":")
               .append(group.getSlots().size()).append(",");
        }
        return key.toString();
    }

    @Override
    public void init(int sw, int sh) {
        slots.clear();
        groups.clear();
        int slotSize = getSlotSize();
        int spacing = getSpacing();

        GUIConfig config = GUIRegistry.get(com.za.zenith.utils.Identifier.of("zenith:player_inventory"));
        if (config != null) {
            Map<String, com.za.zenith.world.inventory.IInventory> inventories = new HashMap<>();
            inventories.put("player", player.getInventory());

            LayoutResult result = InventoryLayout.generateLayout(sw, sh, slotSize, spacing, player, config, inventories);
            slots.addAll(result.slots);
            groups.addAll(result.groups);
            this.globalBackground = result.globalBackground;
            this.backgroundConfig = config.background;
        }
        
        this.lastLayoutKey = calculateLayoutKey(sw, sh);
    }

    @Override
    public void render(UIRenderer renderer, int sw, int sh, com.za.zenith.engine.graphics.DynamicTextureAtlas atlas) {
        // Universal dynamic update: check if any layout-affecting state changed
        String currentKey = calculateLayoutKey(sw, sh);
        if (!currentKey.equals(lastLayoutKey)) {
            init(sw, sh);
        }

        // Draw global background if configured
        if (globalBackground != null && backgroundConfig != null) {
            renderer.renderGroupBackground(globalBackground.getX(), globalBackground.getY(), globalBackground.getWidth(), globalBackground.getHeight(), backgroundConfig);
        }

        // Draw individual group backgrounds if they are NOT part of global background
        for (GroupUI group : groups) {
            if (backgroundConfig != null && backgroundConfig.includeGroups != null && backgroundConfig.includeGroups.contains(group.getConfig().id)) {
                continue; // Already covered by global background
            }
        }
        
        super.render(renderer, sw, sh, atlas);
    }

    @Override
    public boolean handleMouseClick(float mx, float my, int button) {
        InventoryScreenRenderer invRenderer = GameLoop.getInstance().getRenderer().getUIRenderer().getInventoryScreenRenderer();
        // Use the actual current window size
        if (invRenderer.handleMouseClick(mx, my, button)) {
            return true;
        }
        return super.handleMouseClick(mx, my, button);
    }

    @Override
    public boolean handleKeyPress(int key) {
        InventoryScreenRenderer invRenderer = GameLoop.getInstance().getRenderer().getUIRenderer().getInventoryScreenRenderer();
        if (invRenderer.handleKeyPress(key)) {
            return true;
        }
        return super.handleKeyPress(key);
    }

    @Override
    public boolean handleChar(int codepoint) {
        InventoryScreenRenderer invRenderer = GameLoop.getInstance().getRenderer().getUIRenderer().getInventoryScreenRenderer();
        if (invRenderer.handleChar(codepoint)) {
            return true;
        }
        return super.handleChar(codepoint);
    }

    @Override
    public boolean isInputFocused() {
        InventoryScreenRenderer invRenderer = GameLoop.getInstance().getRenderer().getUIRenderer().getInventoryScreenRenderer();
        return invRenderer != null && invRenderer.isSearchBarFocused();
    }

    public java.util.List<GroupUI> getGroupsUI() {
        return groups;
    }

    @Override
    protected com.za.zenith.utils.Identifier getScreenIdentifier() {
        return com.za.zenith.utils.Identifier.of("zenith:player_inventory");
    }
}


