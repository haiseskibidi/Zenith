package com.za.zenith.engine.event.events;

import com.za.zenith.engine.event.Event;
import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.items.ItemStack;

/**
 * Event triggered when a player left-clicks on a block in the world.
 * This class is mutable to allow listeners to consume the click action.
 */
public class BlockLeftClickEvent implements Event {
    private final Player player;
    private final World world;
    private final BlockPos pos;
    private final ItemStack heldItem;
    private final float hitX;
    private final float hitY;
    private final float hitZ;
    private final boolean isNewClick;
    private boolean consumed = false;

    public BlockLeftClickEvent(Player player, World world, BlockPos pos, ItemStack heldItem, float hitX, float hitY, float hitZ, boolean isNewClick) {
        this.player = player;
        this.world = world;
        this.pos = pos;
        this.heldItem = heldItem;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.isNewClick = isNewClick;
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
