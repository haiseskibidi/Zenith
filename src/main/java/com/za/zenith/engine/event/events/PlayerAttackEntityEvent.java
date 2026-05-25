package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Entity;
import com.za.zenith.world.items.ItemStack;

/**
 * Event triggered when a player attacks any entity in the game world.
 */
public record PlayerAttackEntityEvent(Player player, Entity target, ItemStack weapon) implements Event {}
