package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.Window;
import com.za.zenith.entities.Player;
import com.za.zenith.engine.input.InputAction;

/**
 * Обрабатывает выбор и быстрые действия с хотбаром.
 * Полностью интегрирован со слоем InputAction, исключая локальное отслеживание состояний.
 */
public class HotbarInputHandler {
    
    public void update(Window window, Player player, com.za.zenith.engine.input.InputManager manager) {
        boolean inventoryOpen = GameLoop.getInstance().isInventoryOpen();
        boolean nappingOpen = GameLoop.getInstance().isNappingOpen();
        boolean anyScreen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().isAnyScreenOpen();
        com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        boolean isInputFocused = active != null && active.isInputFocused();

        // Блокируем хотбар во время скалывания, инвентаря или любых других экранов (настройки, инспектор)
        if (!nappingOpen && !anyScreen) {
            for (int i = 0; i < 9; i++) {
                InputAction action = InputAction.values()[InputAction.SLOT_1.ordinal() + i];
                if (manager.isPressed(action)) {
                    player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        } else if (inventoryOpen && !isInputFocused) {
            for (int i = 0; i < 9; i++) {
                InputAction action = InputAction.values()[InputAction.SLOT_1.ordinal() + i];
                if (manager.wasJustPressed(action)) {
                    if (manager.getHoveredSlot() != null) {
                        player.getInventory().swapWithHotbar(manager.getHoveredSlot(), i);
                    } else if (player.getMode() == PlayerMode.DEVELOPER) {
                        com.za.zenith.world.items.Item devItem = manager.getDevItemAt(manager.getCurrentMousePos().x, manager.getCurrentMousePos().y);
                        if (devItem != null) {
                            player.getInventory().copyFromDevPanel(devItem, i);
                        }
                    }
                }
            }
        }
    }
}