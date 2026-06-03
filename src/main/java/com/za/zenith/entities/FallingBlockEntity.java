package com.za.zenith.entities;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemRegistry;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;

/**
 * Entity representing a falling sand or gravel block.
 * Optimized for maximum performance (Zero allocations in update loop).
 */
public class FallingBlockEntity extends Entity {
    private final int blockType;
    private final byte blockMetadata;
    private final float customGravity;
    private final float customTerminalVelocity;
    private int fallTime = 0;
    private boolean landed = false;
    private int landedTicks = 0;
    private World world;

    public FallingBlockEntity(Vector3f position, int blockType, byte metadata, float gravity, float terminalVelocity) {
        super(position, 0.98f, 0.98f);
        this.blockType = blockType;
        this.blockMetadata = metadata;
        this.customGravity = gravity;
        this.customTerminalVelocity = terminalVelocity;
        
        // Disable flying by default to ensure gravity is applied
        this.flying = false;
    }

    public int getBlockType() {
        return blockType;
    }

    public byte getBlockMetadata() {
        return blockMetadata;
    }

    public boolean isLanded() {
        return landed;
    }

    public World getWorld() {
        return world;
    }

    @Override
    protected void applyGravity(float deltaTime) {
        velocity.y = Math.max(velocity.y + customGravity * deltaTime, customTerminalVelocity);
    }

    @Override
    protected void onUpdate(float deltaTime, World world) {
        this.world = world;
        if (landed) {
            landedTicks++;
            prevPosition.set(position);
            prevRotation.set(rotation);
            
            // Check if the chunk containing this block has completed its mesh update
            int cx = (int) Math.floor(position.x) >> 4;
            int cz = (int) Math.floor(position.z) >> 4;
            com.za.zenith.world.chunks.Chunk chunk = world.getChunk(cx, cz);
            if ((chunk != null && !chunk.needsMeshUpdate()) || landedTicks > 100) {
                setRemoved();
            }
            return;
        }

        fallTime++;
        
        // 1. Save state for 144Hz+ rendering interpolation
        // (Handled by the parent Entity.update class, but we repeat it to ensure stability)
        prevPosition.set(position);
        prevRotation.set(rotation);

        // 2. Specialized vertical-only physics to prevent object allocation (Zero Lag)
        applyGravity(deltaTime);
        float dy = velocity.y * deltaTime;
        float targetY = position.y + dy;

        int bx = (int) Math.floor(position.x);
        int bz = (int) Math.floor(position.z);
        int newMinY = (int) Math.floor(targetY);
        int currentMinY = (int) Math.floor(position.y);

        boolean collided = false;
        // Verify collision against physical blocks below
        for (int y = currentMinY; y >= newMinY; y--) {
            if (y < 0) {
                collided = true;
                position.y = -1.0f;
                break;
            }
            Block block = world.getBlock(bx, y, bz);
            if (!block.isAir() && block.isSolid()) {
                // Land on top of this block
                position.y = y + 1.0f;
                velocity.y = 0;
                onGround = true;
                collided = true;
                break;
            }
        }

        if (!collided) {
            position.y = targetY;
            onGround = false;
        }

        // 3. Handle Landing
        if (onGround) {
            int landingX = (int) Math.floor(position.x);
            int landingY = (int) Math.round(position.y);
            int landingZ = (int) Math.floor(position.z);
            
            Block currentBlock = world.getBlock(landingX, landingY, landingZ);
            
            if (currentBlock.isAir() || currentBlock.isReplaceable() || BlockRegistry.getBlock(currentBlock.getType()).isFluid()) {
                // Land and turn back into solid block
                world.setBlock(landingX, landingY, landingZ, new Block(blockType, blockMetadata));
                com.za.zenith.utils.Logger.info("Falling block landed at (%d, %d, %d)", landingX, landingY, landingZ);
                landed = true;
            } else {
                // Try dropping as ItemEntity if blocked
                BlockDefinition def = BlockRegistry.getBlock(blockType);
                if (def != null) {
                    Item dropItem = ItemRegistry.getItem(def.getIdentifier());
                    if (dropItem != null) {
                        world.spawnEntity(new ItemEntity(new Vector3f(position.x, position.y + 0.5f, position.z), new ItemStack(dropItem)));
                        com.za.zenith.utils.Logger.info("Falling block could not land, spawned ItemEntity at (%f, %f, %f)", position.x, position.y, position.z);
                    }
                }
                setRemoved();
            }
        }
        
        // 4. Safety boundary check
        if (position.y < 0 || fallTime > 600) {
            setRemoved();
        }
    }
}
