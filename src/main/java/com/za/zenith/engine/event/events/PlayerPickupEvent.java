package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Entity;

/**
 * Event triggered when a player attempts to pick up a resource or item entity from the world.
 * Optimized with high-performance object pooling to achieve Zero-Allocation at runtime.
 */
public class PlayerPickupEvent implements Event {
    private static final java.util.Queue<PlayerPickupEvent> POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private Player player;
    private Entity targetEntity;
    private boolean consumed = false;

    public static PlayerPickupEvent obtain(Player player, Entity targetEntity) {
        PlayerPickupEvent event = POOL.poll();
        if (event == null) {
            event = new PlayerPickupEvent();
        }
        event.init(player, targetEntity);
        return event;
    }

    private void init(Player player, Entity targetEntity) {
        this.player = player;
        this.targetEntity = targetEntity;
        this.consumed = false;
    }

    public void release() {
        this.player = null;
        this.targetEntity = null;
        POOL.offer(this);
    }

    public Player getPlayer() { return player; }
    public Entity getTargetEntity() { return targetEntity; }

    public boolean isConsumed() { return consumed; }
    public void consume() { this.consumed = true; }
}
