package com.za.zenith.engine.input.controllers;

import com.za.zenith.engine.event.EventBus;
import com.za.zenith.engine.event.events.PlayerPickupEvent;
import com.za.zenith.engine.event.events.BlockLeftClickEvent;
import com.za.zenith.world.physics.PhysicsSettings;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockDefinition;

/**
 * Controller handling world interactions, entity pick-ups, and block left clicks.
 */
public class InteractionController {

    public void init() {
        EventBus.getInstance().subscribe(PlayerPickupEvent.class, this::onPlayerPickup);
        EventBus.getInstance().subscribe(BlockLeftClickEvent.class, this::onBlockLeftClick);
    }

    private void onPlayerPickup(PlayerPickupEvent event) {
        var player = event.getPlayer();
        var target = event.getTargetEntity();
        
        if (player.isSwinging()) return;

        if (target instanceof com.za.zenith.entities.ResourceEntity resource) {
            float cooldown = resource.getStack().getItem().getInteractionCooldown();
            if (cooldown <= 0) {
                cooldown = PhysicsSettings.getInstance().baseMiningCooldown;
            }
            player.interact(cooldown);
            if (player.getInventory().addItem(resource.getStack())) {
                resource.setRemoved();
                Logger.info("Picked up resource %s", resource.getStack().getItem().getName());
                event.consume();
            }
        } else if (target instanceof com.za.zenith.entities.ItemEntity itemEntity) {
            float cooldown = itemEntity.getStack().getItem().getInteractionCooldown();
            if (cooldown <= 0) {
                cooldown = PhysicsSettings.getInstance().baseMiningCooldown;
            }
            player.interact(cooldown);
            if (player.getInventory().addItem(itemEntity.getStack(), true)) {
                itemEntity.setRemoved();
                Logger.info("Picked up item %s", itemEntity.getStack().getItem().getName());
                event.consume();
            }
        }
    }

    private void onBlockLeftClick(BlockLeftClickEvent event) {
        int blockType = event.getWorld().getBlock(event.getPos()).getType();
        BlockDefinition blockDef = BlockRegistry.getBlock(blockType);
        
        if (blockDef != null) {
            boolean result = blockDef.onLeftClick(
                event.getWorld(),
                event.getPos(),
                event.getPlayer(),
                event.getHeldItem(),
                event.getHitX(),
                event.getHitY(),
                event.getHitZ(),
                event.isNewClick()
            );
            if (result) {
                event.consume();
            }
        }
    }
}
