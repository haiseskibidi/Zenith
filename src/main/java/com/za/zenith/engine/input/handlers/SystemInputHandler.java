package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.SettingsManager;
import com.za.zenith.engine.core.Window;
import com.za.zenith.entities.Player;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.input.InputAction;

/**
 * Обрабатывает системные и административные действия игрока.
 * Полностью переведен на Input-Action Layer без локального отслеживания состояний.
 */
public class SystemInputHandler {
    
    private boolean verticalMode = false;
    
    public void update(Window window, Player player, com.za.zenith.engine.input.InputManager manager) {
        boolean anyScreen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().isAnyScreenOpen();
        boolean nappingOpen = GameLoop.getInstance().isNappingOpen();
        boolean inventoryOpen = GameLoop.getInstance().isInventoryOpen();
        boolean paused = GameLoop.getInstance().isPaused();
        com.za.zenith.engine.graphics.ui.Screen active = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        boolean isInputFocused = active != null && active.isInputFocused();
        
        // F - Toggle Fly
        if (manager.wasJustPressed(InputAction.TOGGLE_FLY) && !anyScreen && !nappingOpen) {
            player.setFlying(!player.isFlying());
        }

        // F3 - Debug Menu / Developer Mode
        if (manager.wasJustPressed(InputAction.DEBUG_MENU) && !anyScreen && !nappingOpen) {
            boolean visible = !SettingsManager.getInstance().isDebugOverlayVisible();
            SettingsManager.getInstance().setDebugOverlayVisible(visible);
            
            PlayerMode newMode = visible ? PlayerMode.DEVELOPER : PlayerMode.SURVIVAL;
            player.setMode(newMode);
            com.za.zenith.utils.Logger.info("Debug HUD: %b, Player mode: %s", visible, newMode);
            }

            // F4 - Toggle Weather
            if (manager.wasJustPressed(InputAction.TOGGLE_WEATHER) && !anyScreen && !nappingOpen) {
            var world = GameLoop.getInstance().getWorld();
            if (world != null && world.getWeatherManager() != null) {
                var wm = world.getWeatherManager();
                if (wm.getCurrentState() == com.za.zenith.world.weather.WeatherManager.WeatherState.CLEAR) {
                    wm.forceWeather(com.za.zenith.world.weather.WeatherManager.WeatherState.RAIN);
                    com.za.zenith.utils.Logger.info("Weather forced to: RAIN");
                } else {
                    wm.forceWeather(com.za.zenith.world.weather.WeatherManager.WeatherState.CLEAR);
                    com.za.zenith.utils.Logger.info("Weather forced to: CLEAR");
                }
            }
            }

            // R - Vertical Mode (for slabs)
        if (manager.wasJustPressed(InputAction.TOGGLE_VERTICAL_MODE) && !anyScreen && !nappingOpen) {
            verticalMode = !verticalMode;
        }
        
        // G - Toggle FXAA
        if (manager.wasJustPressed(InputAction.TOGGLE_FXAA) && !anyScreen && !nappingOpen) {
            GameLoop.getInstance().getRenderer().toggleFXAA();
        }

        // Z - Sort Inventory
        if (manager.wasJustPressed(InputAction.SORT_INVENTORY) && inventoryOpen && !isInputFocused) {
            player.getInventory().sortMainInventory();
        }

        // Q - Drop Item
        if (manager.wasJustPressed(InputAction.DROP) && !paused && !nappingOpen) {
            if (inventoryOpen) {
                if (!isInputFocused) {
                    if (manager.getHoveredSlot() != null) {
                        ItemStack stack = manager.getHoveredSlot().getStack();
                        if (stack != null) {
                            boolean ctrlPressed = window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) || window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
                            manager.dropStack(stack, player, GameLoop.getInstance().getWorld(), GameLoop.getInstance().getCamera(), ctrlPressed);
                            if (stack.getCount() <= 0) manager.getHoveredSlot().setStack(null);
                        }
                    }
                }
            } else {
                ItemStack stack = player.getInventory().getSelectedItemStack();
                if (stack != null) {
                    boolean ctrlPressed = window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) || window.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
                    manager.dropStack(stack, player, GameLoop.getInstance().getWorld(), GameLoop.getInstance().getCamera(), ctrlPressed);
                    if (stack.getCount() <= 0) player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), null);
                }
            }
        }
    }
    
    public boolean isVerticalMode() {
        return verticalMode;
    }
}