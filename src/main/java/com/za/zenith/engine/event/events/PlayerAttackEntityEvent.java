package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.Entity;
import com.za.zenith.world.items.ItemStack;

/**
 * Event triggered when a player attacks any entity in the game world.
 * Optimized with high-performance object pooling to achieve Zero-Allocation at runtime.
 */
public class PlayerAttackEntityEvent implements Event {
    private static final java.util.Queue<PlayerAttackEntityEvent> POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private Player player;
    private Entity target;
    private ItemStack weapon;

    public static PlayerAttackEntityEvent obtain(Player player, Entity target, ItemStack weapon) {
        PlayerAttackEntityEvent event = POOL.poll();
        if (event == null) {
            event = new PlayerAttackEntityEvent();
        }
        event.init(player, target, weapon);
        return event;
    }

    private void init(Player player, Entity target, ItemStack weapon) {
        this.player = player;
        this.target = target;
        this.weapon = weapon;
    }

    public void release() {
        this.player = null;
        this.target = null;
        this.weapon = null;
        POOL.offer(this);
    }

    public Player player() { return player; }
    public Entity target() { return target; }
    public ItemStack weapon() { return weapon; }
}
