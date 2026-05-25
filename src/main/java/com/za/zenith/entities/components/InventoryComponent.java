package com.za.zenith.entities.components;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.World;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.entities.Inventory;

/**
 * Component managing inventory access for entities.
 */
public class InventoryComponent implements EntityComponent {
    private final Inventory inventory;

    public InventoryComponent(Inventory inventory) {
        this.inventory = inventory;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public boolean addItem(ItemStack stack, boolean merge) {
        return inventory.addItem(stack, merge);
    }

    public boolean isFull() {
        return inventory.isFull();
    }
}
