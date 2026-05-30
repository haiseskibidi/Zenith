package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.items.ItemStack;

/**
 * Event triggered when a player left-clicks on a block in the world.
 * Optimized with high-performance object pooling to achieve Zero-Allocation at runtime.
 */
public class BlockLeftClickEvent implements Event {
    private static final java.util.Queue<BlockLeftClickEvent> POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();

    private Player player;
    private World world;
    private BlockPos pos;
    private ItemStack heldItem;
    private float hitX;
    private float hitY;
    private float hitZ;
    private boolean isNewClick;
    private boolean consumed = false;

    public static BlockLeftClickEvent obtain(Player player, World world, BlockPos pos, ItemStack heldItem, float hitX, float hitY, float hitZ, boolean isNewClick) {
        BlockLeftClickEvent event = POOL.poll();
        if (event == null) {
            event = new BlockLeftClickEvent();
        }
        event.init(player, world, pos, heldItem, hitX, hitY, hitZ, isNewClick);
        return event;
    }

    private void init(Player player, World world, BlockPos pos, ItemStack heldItem, float hitX, float hitY, float hitZ, boolean isNewClick) {
        this.player = player;
        this.world = world;
        this.pos = pos;
        this.heldItem = heldItem;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.isNewClick = isNewClick;
        this.consumed = false;
    }

    public void release() {
        this.player = null;
        this.world = null;
        this.pos = null;
        this.heldItem = null;
        POOL.offer(this);
    }

    public Player getPlayer() { return player; }
    public World getWorld() { return world; }
    public BlockPos getPos() { return pos; }
    public ItemStack getHeldItem() { return heldItem; }
    public float getHitX() { return hitX; }
    public float getHitY() { return hitY; }
    public float getHitZ() { return hitZ; }
    public boolean isNewClick() { return isNewClick; }

    public boolean isConsumed() { return consumed; }
    public void consume() { this.consumed = true; }
}
