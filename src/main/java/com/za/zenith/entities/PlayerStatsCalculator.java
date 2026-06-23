package com.za.zenith.entities;

import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.items.stats.StatDefinition;
import com.za.zenith.world.items.stats.StatModifier;
import com.za.zenith.world.items.stats.StatRegistry;
import com.za.zenith.utils.Identifier;

/**
 * Utility helper to compute player stats and modifiers.
 * ponytail: offloaded stats logic to reduce Player.java length below the 250-line limit.
 */
public class PlayerStatsCalculator {
    private static final Identifier EQUIPMENT_SOURCE = Identifier.of("zenith:equipment");

    public static float calculateStat(Player player, Identifier statId) {
        if (player.getStats() == null) return 0.0f;
        float total = player.getStats().get(statId);
        
        ItemStack held = player.getInventory().getSelectedItemStack();
        if (held != null) {
            total += held.getStat(statId);
        }

        if (statId.equals(StatRegistry.MOBILITY)) {
            float totalWeight = 0;
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack != null) {
                    totalWeight += stack.getItem().getWeight() * stack.getCount();
                }
            }
            float penalty = Math.max(0, (totalWeight - 5.0f) * 0.5f);
            total = Math.max(1, total - penalty);
        }
        return total;
    }

    public static void updateEquipmentStats(Player player) {
        if (player.getStats() == null) return;
        
        player.getStats().removeModifiersFrom(EQUIPMENT_SOURCE);
        Inventory inv = player.getInventory();
        
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack == null) continue;
            
            if (i >= Inventory.START_EQUIPMENT && i < Inventory.START_EQUIPMENT + Inventory.EQUIPMENT_SIZE) {
                for (StatDefinition def : StatRegistry.getAll()) {
                    float value = stack.getStat(def.identifier());
                    if (value != 0) {
                        player.getStats().addModifier(def.identifier(), new StatModifier(
                            EQUIPMENT_SOURCE,
                            StatModifier.Operation.ADD,
                            value
                        ));
                    }
                }
            }
        }
    }
}
