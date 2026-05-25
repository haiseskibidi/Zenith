package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Entity;

/**
 * Event triggered when a player attempts to pick up a resource or item entity from the world.
 * This class is mutable to allow controllers to signal successful pickup consumption.
 */
public class PlayerPickupEvent implements Event {
    private final Player player;
    private final Entity targetEntity;
    private boolean consumed = false;

    public PlayerPickupEvent(Player player, Entity targetEntity) {
        this.player = player;
        this.targetEntity = targetEntity;
    }

    public Player getPlayer() { return player; }
    public Entity getTargetEntity() { return targetEntity; }

    public boolean isConsumed() { return consumed; }
    public void consume() { this.consumed = true; }
}
