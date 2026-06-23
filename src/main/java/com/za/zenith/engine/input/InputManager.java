package com.za.zenith.engine.input;

import com.za.zenith.utils.Identifier;
import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.Window;
import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Inventory;
import com.za.zenith.world.World;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.PlacementType;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.items.Items;
import com.za.zenith.world.physics.Raycast;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector2f;
import org.joml.Vector3f;

import com.za.zenith.engine.input.handlers.SystemInputHandler;
import com.za.zenith.engine.input.handlers.MovementInputHandler;
import com.za.zenith.engine.input.handlers.InventoryInputHandler;
import com.za.zenith.engine.input.handlers.InteractionInputHandler;
import com.za.zenith.engine.input.handlers.HotbarInputHandler;

import static org.lwjgl.glfw.GLFW.*;

public class InputManager {
    
    private final Vector2f previousPos;
    private final Vector2f currentPos;
    private boolean inWindow = false;
    private boolean firstMouse = true;
    private boolean leftMousePressed = false;
    private boolean rightMousePressed = false;

    private ItemStack heldStack = null;
    private com.za.zenith.entities.inventory.Slot hoveredSlot = null;
    
    // Handlers
    private final SystemInputHandler systemHandler = new SystemInputHandler();
    private final MovementInputHandler movementHandler = new MovementInputHandler();
    private final InventoryInputHandler inventoryHandler = new InventoryInputHandler();
    private final InteractionInputHandler interactionHandler = new InteractionInputHandler();
    private final HotbarInputHandler hotbarHandler = new HotbarInputHandler();

    // Controllers
    private final com.za.zenith.engine.input.controllers.CombatController combatController = new com.za.zenith.engine.input.controllers.CombatController();
    private final com.za.zenith.engine.input.controllers.InteractionController interactionController = new com.za.zenith.engine.input.controllers.InteractionController();

    // Action State Buffers for Zero-Alloc Input-Action Layer
    private final boolean[] pressedStates = new boolean[InputAction.values().length];
    private final boolean[] prevPressedStates = new boolean[InputAction.values().length];

    // Event handling sync
    private int lastHandledKey = -1;
    private long lastHandledFrame = -1;

    public boolean isKeyHandled(int key) {
        long currentFrame = GameLoop.getInstance().getTimer().getFrames();
        // Allow match for current or previous frame due to callback timing
        return lastHandledKey == key && (lastHandledFrame == currentFrame || lastHandledFrame == currentFrame - 1);
    }

    private void markKeyHandled(int key) {
        lastHandledKey = key;
        lastHandledFrame = GameLoop.getInstance().getTimer().getFrames();
    }
    
    // UI tracking
    private long lastClickTime = 0;
    private int lastClickSlot = -1;
    
    // Drag-to-Distribute state
    private final java.util.Set<com.za.zenith.entities.inventory.Slot> draggedSlots = new java.util.LinkedHashSet<>();
    private int dragButton = -1;
    private boolean isDragging = false;
    
    // Breaking block state
    private final MiningController miningController;
    private com.za.zenith.entities.Entity hitEntity;
    private float placeDelayTimer = 0.0f;
    private float lootboxOpeningTimer = 0.0f;
    private com.za.zenith.world.items.ItemStack lootboxStack = null;
    public static final float PLACE_COOLDOWN = 0.25f; // 4 bps (5 ticks)

    public float getLootboxOpeningTimer() { return lootboxOpeningTimer; }
    public void setLootboxOpeningTimer(float timer) { this.lootboxOpeningTimer = timer; }
    public com.za.zenith.world.items.ItemStack getLootboxStack() { return lootboxStack; }
    public void setLootboxStack(ItemStack stack) { this.lootboxStack = stack; }

    public boolean isActionPressedRaw(String actionId) {
        int keyCode = com.za.zenith.engine.core.SettingsManager.getInstance().getKeyCode(actionId);
        if (keyCode == -1) return false;
        
        if (keyCode < 10) {
            return GameLoop.getInstance().getWindow().isMouseButtonPressed(keyCode);
        }
        
        return GameLoop.getInstance().getWindow().isKeyPressed(keyCode);
    }

    public boolean isActionPressed(String actionId) {
        return isActionPressedRaw(actionId);
    }

    public boolean isPressed(InputAction action) {
        return pressedStates[action.ordinal()];
    }

    public boolean wasJustPressed(InputAction action) {
        return pressedStates[action.ordinal()] && !prevPressedStates[action.ordinal()];
    }

    public boolean wasJustReleased(InputAction action) {
        return !pressedStates[action.ordinal()] && prevPressedStates[action.ordinal()];
    }

    public InputManager() {
        previousPos = new Vector2f();
        currentPos = new Vector2f();
        this.miningController = new MiningController();
        
        // Initialize event-driven controllers
        this.combatController.init();
        this.interactionController.init();
    }
    
    public MiningController getMiningController() { return miningController; }
    public com.za.zenith.entities.Entity getHitEntity() { return hitEntity; }
    public boolean isFirstMouse() { return firstMouse; }
    public void setFirstMouse(boolean fm) { this.firstMouse = fm; }
    public boolean isInWindow() { return inWindow; }
    public Vector2f getPreviousMousePos() { return previousPos; }
    public float getMouseX() { return currentPos.x; }
    public float getMouseY() { return currentPos.y; }
    public boolean isLeftMousePressed() { return leftMousePressed; }
    public void setLeftMousePressed(boolean pressed) { this.leftMousePressed = pressed; }
    public boolean isRightMousePressed() { return rightMousePressed; }
    public void setRightMousePressed(boolean pressed) { this.rightMousePressed = pressed; }
    public int getDragButton() { return dragButton; }
    public boolean isDragging() { return isDragging; }
    public void setHoveredSlot(com.za.zenith.entities.inventory.Slot slot) { this.hoveredSlot = slot; }
    public float getPlaceDelayTimer() { return placeDelayTimer; }
    public void setPlaceDelayTimer(float timer) { this.placeDelayTimer = timer; }
    
    public void init(Window window) {
        glfwSetCursorPos(window.getWindowHandle(), window.getWidth() / 2.0, window.getHeight() / 2.0);
        
        currentPos.x = window.getWidth() / 2.0f;
        currentPos.y = window.getHeight() / 2.0f;
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
        
        glfwSetCursorPosCallback(window.getWindowHandle(), (windowHandle, xpos, ypos) -> {
            currentPos.x = (float) xpos;
            currentPos.y = (float) ypos;

            com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            if (active != null) {
                active.handleMouseMove(currentPos.x, currentPos.y);
            }
        });
        
        glfwSetCursorEnterCallback(window.getWindowHandle(), (windowHandle, entered) -> {
            inWindow = entered;
        });

        glfwSetKeyCallback(window.getWindowHandle(), (windowHandle, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
                if (active != null && active.handleKeyPress(key)) {
                    markKeyHandled(key);
                }
            }
        });

        glfwSetCharCallback(window.getWindowHandle(), (windowHandle, codepoint) -> {
            com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            if (active != null) {
                active.handleChar(codepoint);
            }
        });
        
        glfwSetMouseButtonCallback(window.getWindowHandle(), (windowHandle, button, action, mode) -> {
            com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            
            if (action == GLFW_PRESS) {
                if (active != null && active.handleMouseClick(currentPos.x, currentPos.y, button)) {
                    if (button == GLFW_MOUSE_BUTTON_1) leftMousePressed = true;
                    if (button == GLFW_MOUSE_BUTTON_2) rightMousePressed = true;
                    return;
                }

                if (GameLoop.getInstance().isInventoryOpen()) {
                    dragButton = button;
                    draggedSlots.clear();
                    isDragging = false;
                    
                    if (heldStack == null) {
                        com.za.zenith.engine.input.handlers.InventoryClickHandler.handleInventoryClick(window, button, this, currentPos.x, currentPos.y);
                    } else {
                        com.za.zenith.entities.inventory.Slot slot = com.za.zenith.engine.input.handlers.InventoryClickHandler.getSlotAt(currentPos.x, currentPos.y);
                        if (slot != null) {
                            draggedSlots.add(slot);
                            isDragging = true;
                        } else {
                            com.za.zenith.engine.input.handlers.InventoryClickHandler.handleInventoryClick(window, button, this, currentPos.x, currentPos.y);
                        }
                    }
                }
            } else if (action == GLFW_RELEASE) {
                if (active != null && active.handleMouseRelease(currentPos.x, currentPos.y, button)) {
                    if (button == GLFW_MOUSE_BUTTON_1) leftMousePressed = false;
                    if (button == GLFW_MOUSE_BUTTON_2) rightMousePressed = false;
                    return;
                }

                if (GameLoop.getInstance().isInventoryOpen()) {
                    if (dragButton == button) {
                        if (isDragging && !draggedSlots.isEmpty()) {
                            if (draggedSlots.size() == 1) {
                                com.za.zenith.engine.input.handlers.InventoryClickHandler.handleInventoryClickOnSlot(window, button, draggedSlots.iterator().next(), this);
                            } else {
                                com.za.zenith.engine.input.handlers.InventoryClickHandler.finishDrag(this);
                            }
                        }
                        dragButton = -1;
                        draggedSlots.clear();
                        isDragging = false;
                    }
                }
            }
        });
        
        glfwSetScrollCallback(window.getWindowHandle(), (windowHandle, xoffset, yoffset) -> {
            com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            if (screen != null && screen.handleScroll(yoffset)) {
                return;
            }
            
            Player p = GameLoop.getInstance().getPlayer();
            if (p != null && !GameLoop.getInstance().isNappingOpen()) {
                if (GameLoop.getInstance().isInventoryOpen() && p.getMode() == PlayerMode.DEVELOPER) {
                    com.za.zenith.engine.graphics.ui.UIRenderer ui = GameLoop.getInstance().getRenderer().getUIRenderer();
                    if (ui.getDevScroller().isMouseOver(currentPos.x, currentPos.y)) {
                        ui.getDevScroller().handleScroll(yoffset);
                        return;
                    }
                }
                
                if (yoffset > 0) p.getInventory().previousSlot();
                else if (yoffset < 0) p.getInventory().nextSlot();
            }
        });
        
        enableMouseCapture(window);
    }
    
    public void dropStack(ItemStack stack, Player player, World world, Camera camera, boolean dropAll) {
        com.za.zenith.engine.input.handlers.InventoryClickHandler.dropStack(stack, player, world, camera, dropAll);
    }

    public Item getDevItemAt(float mx, float my) {
        return com.za.zenith.engine.input.handlers.InventoryClickHandler.getDevItemAt(mx, my, this);
    }

    public ItemStack getHeldStack() {
        return heldStack;
    }

    public void setHeldStack(ItemStack stack) {
        this.heldStack = stack;
    }

    public void clearHeldStack() {
        heldStack = null;
    }

    public Vector2f getCurrentMousePos() {
        return currentPos;
    }

    public com.za.zenith.entities.inventory.Slot getHoveredSlot() {
        return hoveredSlot;
    }
    
    public java.util.Set<com.za.zenith.entities.inventory.Slot> getDraggedSlots() {
        return draggedSlots;
    }

    public void setDragButton(int button) {
        this.dragButton = button;
    }

    public void setDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    public long getLastClickTime() {
        return lastClickTime;
    }

    public void setLastClickTime(long lastClickTime) {
        this.lastClickTime = lastClickTime;
    }

    public int getLastClickSlot() {
        return lastClickSlot;
    }

    public void setLastClickSlot(int lastClickSlot) {
        this.lastClickSlot = lastClickSlot;
    }

    public void enableMouseCapture(Window window) {
        glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        firstMouse = true;
    }
    
    public void disableMouseCapture(Window window) {
        glfwSetInputMode(window.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }
    
    public float getBreakingProgress() {
        return miningController.getBreakingProgress();
    }

    public RaycastResult input(Window window, Camera camera, Player player, float deltaTime, com.za.zenith.engine.graphics.Renderer renderer, World world, com.za.zenith.network.GameClient networkClient) {
        // Копируем текущее состояние в предыдущее
        System.arraycopy(pressedStates, 0, prevPressedStates, 0, pressedStates.length);

        // Опрашиваем новые физические нажатия для Zero-Alloc Input-Action слоя
        for (InputAction action : InputAction.values()) {
            pressedStates[action.ordinal()] = isActionPressedRaw(action.getSettingsKey());
        }

        miningController.setDependencies(renderer, networkClient);

        // Обновляем Raycast 1 раз
        Vector3f lookDir = new Vector3f(0, 0, -1).rotateX(camera.getRotation().x).rotateY(camera.getRotation().y).normalize();
        RaycastResult raycast = Raycast.raycast(world, camera.getPosition(), lookDir);
        this.hitEntity = Raycast.raycastEntity(world, camera.getPosition(), lookDir);

        placeDelayTimer = Math.max(0, placeDelayTimer - deltaTime);
        miningController.update(deltaTime);

        // Делегируем логику хендлерам
        systemHandler.update(window, player, this);
        hotbarHandler.update(window, player, this);
        inventoryHandler.update(window, player, currentPos, this);
        movementHandler.update(window, camera, player, deltaTime, this);
        return interactionHandler.update(window, camera, player, world, raycast, deltaTime, this, networkClient);
    }

    public boolean isSpecialInteracting(Player player, RaycastResult raycast, ItemStack currentStack) {
        if (!player.isSneaking() || !raycast.isHit() || currentStack == null) return false;
        
        Block hitBlock = GameLoop.getInstance().getWorld().getBlock(raycast.getBlockPos());
        BlockDefinition hitDef = BlockRegistry.getBlock(hitBlock.getType());
        if (hitDef == null) return false;
        
        Identifier hitId = hitDef.getIdentifier();
        Item currentItem = currentStack.getItem();
        Identifier itemId = currentItem.getIdentifier();
        Vector3f normal = raycast.getNormal();

        if (hitId.equals(Blocks.UNFIRED_VESSEL.getIdentifier()) && itemId.equals(Items.STRAW.getIdentifier())) {
            return normal.y > 0.5f;
        }
        if (hitId.equals(Blocks.PIT_KILN.getIdentifier()) || hitId.equals(Blocks.BURNING_PIT_KILN.getIdentifier())) {
            if (itemId.getPath().contains("log") || itemId.equals(Items.FIRE_STARTER.getIdentifier())) {
                return true;
            }
        }
        return false;
    }
    
    public boolean needsPreview(int type) {
        return com.za.zenith.world.blocks.BlockRegistry.getBlock(type).getPlacementType() != com.za.zenith.world.blocks.PlacementType.DEFAULT;
    }

    public byte calculateMetadata(int type, Vector3f normal, Vector3f hitPoint, Camera camera) {
        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(type);
        float yaw = camera.getRotation().y;
        float deg = (float) Math.toDegrees(yaw) % 360;
        if (deg < 0) deg += 360;
        byte viewDir;
        if (deg >= 45 && deg < 135) viewDir = Block.DIR_WEST;
        else if (deg >= 135 && deg < 225) viewDir = Block.DIR_SOUTH;
        else if (deg >= 225 && deg < 315) viewDir = Block.DIR_EAST;
        else viewDir = Block.DIR_NORTH;

        switch (def.getPlacementType()) {
            case SLAB:
                if (systemHandler.isVerticalMode()) return viewDir;
                if (Math.abs(normal.y) > 0.5f) return normal.y > 0 ? Block.DIR_DOWN : Block.DIR_UP;
                float relativeY = hitPoint.y - (float)Math.floor(hitPoint.y);
                return relativeY > 0.5f ? Block.DIR_UP : Block.DIR_DOWN;
            case STAIRS: return viewDir;
            case LOG:
                if (Math.abs(normal.y) > 0.5f) return Block.DIR_UP;
                if (Math.abs(normal.x) > 0.5f) return Block.DIR_EAST;
                if (Math.abs(normal.z) > 0.5f) return Block.DIR_SOUTH;
                return 0;
            default: return 0;
        }
    }
    
    public boolean isPlayerAt(Player player, BlockPos blockPos) {
        Vector3f p = player.getPosition();
        return !(p.x + 0.3f <= blockPos.x() || p.x - 0.3f >= blockPos.x() + 1 || p.y + 1.8f <= blockPos.y() || p.y >= blockPos.y() + 1 || p.z + 0.3f <= blockPos.z() || p.z - 0.3f >= blockPos.z() + 1);
    }
}