package com.za.zenith.world.blocks;

import com.za.zenith.entities.ItemEntity;
import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.utils.Identifier;
import org.joml.Vector3f;

/**
 * System managing drops generation and physics item spawning upon block destruction.
 * ponytail: isolated to keep BlockDefinition clean.
 */
public class BlockDropManager {

    public static void spawnDrops(World world, BlockPos pos, Block block, Player player, BlockDefinition def) {
        // Advanced drops from DropRules
        if (!def.getDropRules().isEmpty()) {
            String heldTool = "none";
            if (player != null && player.getInventory().getSelectedItemStack() != null) {
                Item item = player.getInventory().getSelectedItemStack().getItem();
                com.za.zenith.world.items.component.ToolComponent tool = item.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
                if (tool != null) {
                    heldTool = tool.type().name().toLowerCase();
                }
            }

            for (DropRule rule : def.getDropRules()) {
                if (rule.requiredToolType().equalsIgnoreCase("none") || rule.requiredToolType().equalsIgnoreCase(heldTool)) {
                    if (Math.random() <= rule.chance()) {
                        Item itemToGive = com.za.zenith.world.items.ItemRegistry.getItem(Identifier.of(rule.dropItemIdentifier()));
                        if (itemToGive != null) {
                            spawnDropEntity(world, pos, new ItemStack(itemToGive));
                        }
                    }
                }
            }
        } else {
            // Legacy drops fallback
            float chance = def.getDropChance();
            if (Math.random() <= chance) {
                Item itemToGive = (def.getDropItem() != null) 
                    ? com.za.zenith.world.items.ItemRegistry.getItem(Identifier.of(def.getDropItem())) 
                    : com.za.zenith.world.items.ItemRegistry.getItem(def.getIdentifier());
                
                if (itemToGive != null) {
                    spawnDropEntity(world, pos, new ItemStack(itemToGive));
                }
            }
        }
    }

    private static void spawnDropEntity(World world, BlockPos pos, ItemStack stack) {
        Vector3f dropPos = new Vector3f(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
        ItemEntity drop = new ItemEntity(dropPos, stack);
        
        // Random velocity boost and angular roll on drops spawning
        drop.getVelocity().set((float) Math.random() * 0.2f - 0.1f, 0.2f, (float) Math.random() * 0.2f - 0.1f);
        drop.setAngularVelocity(new Vector3f(
            (float) (Math.random() - 0.5) * 10f, 
            (float) (Math.random() - 0.5) * 10f, 
            (float) (Math.random() - 0.5) * 10f
        ));
        world.spawnEntity(drop);
    }
}
