package com.za.zenith.world.blocks;

import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.component.BlockComponent;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * System handling right-click (onUse) and left-click interaction events for blocks.
 * ponytail: isolated to keep BlockDefinition clean and focused.
 */
public class BlockInteractionHandler {

    public static boolean hasOnUse(BlockDefinition def) {
        List<BlockComponent> components = def.getComponents();
        if (components != null) {
            for (int i = 0; i < components.size(); i++) {
                if (components.get(i).hasOnUse()) return true;
            }
        }
        return false;
    }

    public static List<InteractionZone> getInteractionZones(World world, BlockPos pos, BlockDefinition def) {
        List<BlockComponent> components = def.getComponents();
        if (components == null || components.isEmpty()) return Collections.emptyList();
        
        List<InteractionZone> zones = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            zones.addAll(components.get(i).getInteractionZones(world, pos));
        }
        return zones;
    }

    public static boolean isInteractableAt(World world, BlockPos pos, Vector3f localHit, BlockDefinition def) {
        if (!hasOnUse(def)) return false;
        
        List<InteractionZone> zones = getInteractionZones(world, pos, def);
        if (zones.isEmpty()) return true; // Entire block is interactable
        
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).contains(localHit)) return true;
        }
        return false;
    }

    public static boolean onUse(World world, BlockPos pos, Player player, ItemStack heldStack, float hitX, float hitY, float hitZ, BlockDefinition def) {
        List<BlockComponent> components = def.getComponents();
        if (components != null) {
            for (int i = 0; i < components.size(); i++) {
                if (components.get(i).onUse(world, pos, player, heldStack, hitX, hitY, hitZ)) return true;
            }
        }
        return false;
    }

    public static boolean onLeftClick(World world, BlockPos pos, Player player, ItemStack heldStack, float hitX, float hitY, float hitZ, boolean isNewClick, BlockDefinition def) {
        List<BlockComponent> components = def.getComponents();
        if (components != null) {
            for (int i = 0; i < components.size(); i++) {
                if (components.get(i).onLeftClick(world, pos, player, heldStack, hitX, hitY, hitZ, isNewClick)) return true;
            }
        }
        return false;
    }
}
