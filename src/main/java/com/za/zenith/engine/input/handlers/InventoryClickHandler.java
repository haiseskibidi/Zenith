package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.Window;
import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Inventory;
import com.za.zenith.entities.inventory.Slot;
import com.za.zenith.world.World;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.input.InputManager;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Обработчик кликов инвентаря, драг-распределения и взаимодействия с панелью разработчика.
 * ponytail: вынесено из InputManager для снижения зацепления и размера файлов.
 */
public class InventoryClickHandler {

    public static Slot getSlotAt(float mx, float my) {
        com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        if (screen instanceof com.za.zenith.engine.graphics.ui.InventoryScreen invScreen) {
            com.za.zenith.engine.graphics.ui.SlotUI ui = invScreen.getSlotAt(mx, my);
            return ui != null ? ui.getSlot() : null;
        }
        return null;
    }

    public static void dropStack(ItemStack stack, Player player, World world, Camera camera, boolean dropAll) {
        if (stack == null) return;

        ItemStack toDrop;
        if (dropAll) {
            toDrop = stack.copy();
            stack.setCount(0);
        } else {
            toDrop = stack.split(1);
        }

        if (toDrop == null) return;

        Vector3f lookDirV = new Vector3f(0, 0, -1)
            .rotateX(camera.getRotation().x)
            .rotateY(camera.getRotation().y)
            .normalize();

        Vector3f rightDir = new Vector3f(1, 0, 0)
            .rotateX(camera.getRotation().x)
            .rotateY(camera.getRotation().y)
            .normalize();

        Vector3f downDir = new Vector3f(0, -1, 0)
            .rotateX(camera.getRotation().x)
            .rotateY(camera.getRotation().y)
            .normalize();

        // Смещение точки спавна чуть вправо и вниз для имитации броска из руки
        Vector3f spawnPos = new Vector3f(camera.getPosition())
            .add(new Vector3f(lookDirV).mul(0.5f))
            .add(rightDir.mul(0.3f))
            .add(downDir.mul(0.2f));

        com.za.zenith.entities.ItemEntity itemEntity = new com.za.zenith.entities.ItemEntity(spawnPos, toDrop);
        itemEntity.setPickupDelay(1.5f); // 1.5 сек задержка для вручную выброшенных предметов

        float throwStrength = 6.0f / toDrop.getItem().getWeight();
        itemEntity.getVelocity().set(lookDirV).mul(throwStrength);
        itemEntity.getVelocity().y += 1.5f / toDrop.getItem().getWeight();

        // Добавляем случайное вращение
        Vector3f angVel = new Vector3f(
            (float) (Math.random() - 0.5) * 15f,
            (float) (Math.random() - 0.5) * 15f,
            (float) (Math.random() - 0.5) * 15f
        );
        itemEntity.setAngularVelocity(angVel);

        world.spawnEntity(itemEntity);
        com.za.zenith.utils.Logger.info("Dropped stack: %s (x%d)", toDrop.getItem().getName(), toDrop.getCount());
    }

    public static void finishDrag(InputManager manager) {
        ItemStack heldStack = manager.getHeldStack();
        java.util.Set<Slot> draggedSlots = manager.getDraggedSlots();
        int dragButton = manager.getDragButton();

        if (heldStack == null || draggedSlots.isEmpty()) return;

        if (dragButton == GLFW_MOUSE_BUTTON_1) {
            int amountPerSlot = heldStack.getCount() / draggedSlots.size();
            if (amountPerSlot > 0) {
                for (Slot slot : draggedSlots) {
                    if (!slot.isItemValid(heldStack)) continue;
                    ItemStack slotStack = slot.getStack();
                    if (slotStack == null) {
                        slot.setStack(heldStack.split(amountPerSlot));
                    } else if (heldStack.isStackableWith(slotStack)) {
                        ItemStack split = heldStack.split(amountPerSlot);
                        if (split != null) {
                            slotStack.setCount(slotStack.getCount() + split.getCount());
                        }
                    }
                }
            }
        } else if (dragButton == GLFW_MOUSE_BUTTON_2) {
            for (Slot slot : draggedSlots) {
                if (heldStack.getCount() <= 0) break;
                if (!slot.isItemValid(heldStack)) continue;
                ItemStack slotStack = slot.getStack();
                if (slotStack == null) {
                    slot.setStack(heldStack.split(1));
                } else if (heldStack.isStackableWith(slotStack)) {
                    ItemStack split = heldStack.split(1);
                    if (split != null) {
                        slotStack.setCount(slotStack.getCount() + 1);
                    }
                }
            }
        }

        if (heldStack.getCount() <= 0) {
            manager.clearHeldStack();
        }
    }

    public static void handleInventoryClickOnSlot(Window window, int button, Slot slot, InputManager manager) {
        Player player = GameLoop.getInstance().getPlayer();
        if (player == null) return;

        boolean shift = window.isKeyPressed(GLFW_KEY_LEFT_SHIFT) || window.isKeyPressed(GLFW_KEY_RIGHT_SHIFT);
        long currentTime = System.currentTimeMillis();
        boolean doubleClick = (currentTime - manager.getLastClickTime() < 250) && (manager.getLastClickSlot() == slot.getIndex());

        manager.setLastClickTime(currentTime);
        manager.setLastClickSlot(slot.getIndex());

        if (shift && button == GLFW_MOUSE_BUTTON_1) {
            com.za.zenith.engine.graphics.ui.Screen activeScreen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
            if (activeScreen instanceof com.za.zenith.engine.graphics.ui.InventoryScreen invScreen) {
                com.za.zenith.engine.graphics.ui.SlotUI slotUI = invScreen.getSlotAt(manager.getCurrentMousePos().x, manager.getCurrentMousePos().y);
                if (slotUI != null) {
                    if (doubleClick) {
                        if (player.getInventory() instanceof Inventory inv) {
                            inv.collectAllTo(slot);
                        }
                    } else {
                        invScreen.onQuickMove(slotUI, player);
                    }
                }
            }
            return;
        }

        ItemStack slotStack = slot.getStack();
        ItemStack heldStack = manager.getHeldStack();

        if (button == GLFW_MOUSE_BUTTON_1) {
            if (heldStack != null && slotStack != null && heldStack.isStackableWith(slotStack)) {
                slotStack.setCount(slotStack.getCount() + heldStack.getCount());
                manager.clearHeldStack();
            } else if (slot.isItemValid(heldStack)) {
                slot.setStack(heldStack);
                manager.setHeldStack(slotStack);

                // Пересоздаем GUI, если изменился слот аксессуаров
                if (slot.getIndex() == Inventory.SLOT_ACCESSORY) {
                    com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
                    if (screen != null) {
                        screen.init(window.getWidth(), window.getHeight());
                    }
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_2) {
            if (heldStack == null) {
                if (slotStack != null) {
                    int toTake = (int) Math.ceil(slotStack.getCount() / 2.0);
                    manager.setHeldStack(slotStack.split(toTake));
                    if (slotStack.getCount() <= 0) slot.setStack(null);

                    // Пересоздаем GUI при изменении аксессуаров
                    if (slot.getIndex() == Inventory.SLOT_ACCESSORY) {
                        com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
                        if (screen != null) {
                            screen.init(window.getWidth(), window.getHeight());
                        }
                    }
                }
            } else if (slot.isItemValid(heldStack)) {
                if (slotStack == null) {
                    slot.setStack(heldStack.split(1));
                    if (heldStack.getCount() <= 0) manager.clearHeldStack();

                    if (slot.getIndex() == Inventory.SLOT_ACCESSORY) {
                        com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
                        if (screen != null) {
                            screen.init(window.getWidth(), window.getHeight());
                        }
                    }
                } else if (heldStack.isStackableWith(slotStack)) {
                    slotStack.setCount(slotStack.getCount() + 1);
                    heldStack.setCount(heldStack.getCount() - 1);
                    if (heldStack.getCount() <= 0) manager.clearHeldStack();
                } else {
                    slot.setStack(heldStack);
                    manager.setHeldStack(slotStack);
                }
            }
        }
    }

    public static void handleInventoryClick(Window window, int button, InputManager manager, float mx, float my) {
        Player player = GameLoop.getInstance().getPlayer();
        if (player == null) return;

        com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        if (screen instanceof com.za.zenith.engine.graphics.ui.InventoryScreen invScreen) {
            com.za.zenith.engine.graphics.ui.SlotUI slotUI = invScreen.getSlotAt(mx, my);

            if (slotUI != null) {
                handleInventoryClickOnSlot(window, button, slotUI.getSlot(), manager);
            } else {
                boolean handledByDev = false;
                if (player.getMode() == PlayerMode.DEVELOPER) {
                    Item devItem = getDevItemAt(mx, my, manager);
                    if (devItem != null) {
                        handleDevPanelClick(window, mx, my, manager);
                        handledByDev = true;
                    }
                }

                ItemStack heldStack = manager.getHeldStack();
                if (!handledByDev && heldStack != null) {
                    dropStack(heldStack, player, GameLoop.getInstance().getWorld(), GameLoop.getInstance().getCamera(), true);
                    manager.clearHeldStack();
                }
            }
        }
    }

    public static Item getDevItemAt(float mx, float my, InputManager manager) {
        com.za.zenith.engine.graphics.ui.renderers.InventoryScreenRenderer invRenderer = GameLoop.getInstance().getRenderer().getUIRenderer().getInventoryScreenRenderer();
        com.za.zenith.engine.graphics.ui.ScrollPanel scroller = invRenderer.getDevScroller();

        if (!scroller.isMouseOver(mx, my)) return null;

        com.za.zenith.engine.graphics.ui.Screen screen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().getActiveScreen();
        if (!(screen instanceof com.za.zenith.engine.graphics.ui.PlayerInventoryScreen pScreen)) return null;

        com.za.zenith.engine.graphics.ui.GroupUI devGroup = null;
        for (com.za.zenith.engine.graphics.ui.GroupUI group : pScreen.getGroupsUI()) {
            if ("developer_items".equals(group.getConfig().type)) {
                devGroup = group;
                break;
            }
        }
        if (devGroup == null) return null;

        int cols = devGroup.getConfig().cols > 0 ? devGroup.getConfig().cols : 7;
        int slotSize = (int)(18 * com.za.zenith.engine.graphics.ui.Hotbar.HOTBAR_SCALE);
        int spacing = devGroup.getConfig().spacing;
        int devX = devGroup.getX();
        int startY = devGroup.getY();

        java.util.List<Item> allItems = invRenderer.getFilteredDevItems();
        float offset = scroller.getOffset();

        for (int i = 0; i < allItems.size(); i++) {
            int col = i % cols;
            int row = i / cols;

            int x = devX + col * (slotSize + spacing);
            int y = startY + row * (slotSize + spacing) - (int)offset;

            if (my >= scroller.getY() && my <= scroller.getY() + scroller.getHeight()) {
                if (mx >= x && mx <= x + slotSize && my >= y && my <= y + slotSize) {
                    return allItems.get(i);
                }
            }
        }
        return null;
    }

    public static void handleDevPanelClick(Window window, float mx, float my, InputManager manager) {
        Item item = getDevItemAt(mx, my, manager);
        if (item != null) {
            boolean shift = window.isKeyPressed(GLFW_KEY_LEFT_SHIFT) || window.isKeyPressed(GLFW_KEY_RIGHT_SHIFT);

            if (shift) {
                Player player = GameLoop.getInstance().getPlayer();
                if (player != null) {
                    player.getInventory().addItem(new ItemStack(item, item.getMaxStackSize()));
                }
            } else {
                ItemStack heldStack = manager.getHeldStack();
                if (heldStack != null && heldStack.getItem().getId() == item.getId()) {
                    manager.clearHeldStack();
                } else {
                    manager.setHeldStack(new ItemStack(item, item.getMaxStackSize()));
                }
            }
        }
    }
}
