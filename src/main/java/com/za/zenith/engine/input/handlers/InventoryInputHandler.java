package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.Window;
import com.za.zenith.entities.Player;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.input.InputAction;
import org.joml.Vector2f;

/**
 * Обрабатывает интерфейсы инвентаря, драг-энд-дроп и скалывание (Napping).
 * Полностью переведен на использование InputAction без локального хранения состояния клавиш.
 */
public class InventoryInputHandler {

    private int lastQuickMovedSlot = -1;
    private int lastQuickCopiedDevItem = -1;

    public void update(Window window, Player player, Vector2f currentPos, com.za.zenith.engine.input.InputManager manager) {
        boolean inventoryOpen = GameLoop.getInstance().isInventoryOpen();
        boolean nappingOpen = GameLoop.getInstance().isNappingOpen();

        boolean isLeftPressed = manager.isPressed(InputAction.ATTACK_MINE);
        boolean isLeftJustPressed = manager.wasJustPressed(InputAction.ATTACK_MINE);
        boolean isRightPressed = manager.isPressed(InputAction.INTERACT_PLACE);

        if (nappingOpen) {
            if (isLeftJustPressed) {
                int slotIdx = com.za.zenith.engine.graphics.ui.NappingGUI.getSlotIndexAt(currentPos.x, currentPos.y, window.getWidth(), window.getHeight());
                if (slotIdx != -1) {
                    com.za.zenith.world.recipes.NappingSession session = GameLoop.getInstance().getNappingSession();
                    session.removePiece(slotIdx);
                    
                    com.za.zenith.world.recipes.NappingRecipe result = session.checkMatch();
                    if (result != null) {
                        com.za.zenith.utils.Logger.info("Napping complete!");
                        
                        ItemStack current = player.getInventory().getSelectedItemStack();
                        if (current != null) {
                            ItemStack newStack = current.getCount() > 1 ? new ItemStack(current.getItem(), current.getCount() - 1) : null;
                            player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), newStack);
                        }
                        
                        player.getInventory().addItem(result.getResult());
                        GameLoop.getInstance().closeNapping();
                        
                        manager.setLeftMousePressed(true);
                        manager.setRightMousePressed(false);
                        return;
                    }
                }
            }
            
            manager.setLeftMousePressed(isLeftPressed);
            manager.setRightMousePressed(isRightPressed);
        }

        if (inventoryOpen) {
            com.za.zenith.engine.graphics.ui.SlotUI slotUI = null;
            com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            if (active instanceof com.za.zenith.engine.graphics.ui.InventoryScreen invScreen) {
                slotUI = invScreen.getSlotAt(currentPos.x, currentPos.y);
            }
            
            com.za.zenith.entities.inventory.Slot newHovered = slotUI != null ? slotUI.getSlot() : null;
            if (newHovered != manager.getHoveredSlot()) {
                manager.setHoveredSlot(newHovered);
                
                // Mouse Tweaks: Shift + Drag quick move
                boolean shiftPressed = window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) || window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (isLeftPressed && shiftPressed) {
                    if (slotUI != null && slotUI.getSlot() != null && manager.getHeldStack() == null) {
                        int slotKey = System.identityHashCode(slotUI.getSlot());
                        if (slotKey != lastQuickMovedSlot) {
                            if (active instanceof com.za.zenith.engine.graphics.ui.InventoryScreen invScreen) {
                                invScreen.onQuickMove(slotUI, player);
                                lastQuickMovedSlot = slotKey;
                            }
                        }
                    } else if (slotUI == null && player.getMode() == PlayerMode.DEVELOPER) {
                        Item devItem = manager.getDevItemAt(currentPos.x, currentPos.y);
                        if (devItem != null && devItem.getId() != lastQuickCopiedDevItem) {
                            player.getInventory().addItem(new ItemStack(devItem, devItem.getMaxStackSize()));
                            lastQuickCopiedDevItem = devItem.getId();
                        }
                    }
                }

                if (manager.isDragging() && manager.getDragButton() != -1 && manager.getHeldStack() != null && manager.getHoveredSlot() != null) {
                    ItemStack slotStack = manager.getHoveredSlot().getStack();
                    boolean canReceive = (slotStack == null || manager.getHeldStack().isStackableWith(slotStack));
                    if (canReceive && (manager.getDraggedSlots().contains(manager.getHoveredSlot()) || manager.getDraggedSlots().size() < manager.getHeldStack().getCount())) {
                        if (manager.getHoveredSlot().isItemValid(manager.getHeldStack())) {
                            manager.getDraggedSlots().add(manager.getHoveredSlot());
                        }
                    }
                }
            }
        }
    }
}