package com.za.zenith.world.physics;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.chunks.ChunkCache;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockDefinition;

/**
 * Handles Step-Up collision calculations for entities climbing stairs/slabs
 * and slides up smooth ramps.
 */
public class StepUpHandler {

    public static class StepResult {
        public final boolean success;
        public final float actualDx;
        public final float actualDz;
        public final float stepHeight;

        public StepResult(boolean success, float actualDx, float actualDz, float stepHeight) {
            this.success = success;
            this.actualDx = actualDx;
            this.actualDz = actualDz;
            this.stepHeight = stepHeight;
        }
    }

    public static StepResult tryStepUp(
            Entity entity, 
            ChunkCache cache,
            float xBeforeHorizontal, 
            float yBeforeHorizontal, 
            float zBeforeHorizontal,
            float originalDx, 
            float originalDz,
            float currentDx, 
            float currentDz,
            float stepHeightSetting,
            String stepUpMode
    ) {
        if (stepHeightSetting <= 0.0f || stepUpMode.equalsIgnoreCase("NONE")) {
            return new StepResult(false, currentDx, currentDz, 0.0f);
        }

        AABB localBox = entity.getLocalBoundingBox();
        org.joml.Vector3f position = entity.getPosition();

        float postCollisionX = position.x;
        float postCollisionY = position.y;
        float postCollisionZ = position.z;

        // Calculate horizontal distance moved in normal collision pass
        float normalDistSq = (postCollisionX - xBeforeHorizontal) * (postCollisionX - xBeforeHorizontal) +
                             (postCollisionZ - zBeforeHorizontal) * (postCollisionZ - zBeforeHorizontal);

        // 1. Elevate entity
        position.x = xBeforeHorizontal;
        position.y = yBeforeHorizontal + stepHeightSetting;
        position.z = zBeforeHorizontal;
        AABB currentBox = localBox.offset(position);

        float elevatedDx = originalDx;
        float elevatedDz = originalDz;

        // Run collision check for X at elevated height
        if (elevatedDx != 0) {
            int minX = (int) Math.floor(currentBox.minX() + Math.min(0, elevatedDx));
            int maxX = (int) Math.floor(currentBox.maxX() + Math.max(0, elevatedDx));
            int minZ = (int) Math.floor(currentBox.minZ());
            int maxZ = (int) Math.floor(currentBox.maxZ());
            int minY = (int) Math.floor(currentBox.minY());
            int maxY = (int) Math.floor(currentBox.maxY());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(localBox, position.x + elevatedDx, position.y, position.z, box, x, y, z)) {
                                        if (elevatedDx > 0) elevatedDx = (box.minX() + x) - (localBox.maxX() + position.x) - 0.001f;
                                        else elevatedDx = (box.maxX() + x) - (localBox.minX() + position.x) + 0.001f;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.x += elevatedDx;
            currentBox = localBox.offset(position);
        }

        // Run collision check for Z at elevated height
        if (elevatedDz != 0) {
            int minX = (int) Math.floor(currentBox.minX());
            int maxX = (int) Math.floor(currentBox.maxX());
            int minZ = (int) Math.floor(currentBox.minZ() + Math.min(0, elevatedDz));
            int maxZ = (int) Math.floor(currentBox.maxZ() + Math.max(0, elevatedDz));
            int minY = (int) Math.floor(currentBox.minY());
            int maxY = (int) Math.floor(currentBox.maxY());
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(localBox, position.x, position.y, position.z + elevatedDz, box, x, y, z)) {
                                        if (elevatedDz > 0) elevatedDz = (box.minZ() + z) - (localBox.maxZ() + position.z) - 0.001f;
                                        else elevatedDz = (box.maxZ() + z) - (localBox.minZ() + position.z) + 0.001f;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.z += elevatedDz;
            currentBox = localBox.offset(position);
        }

        // 2. Drop back down to the ground
        float dropDy = -stepHeightSetting;
        int minX = (int) Math.floor(currentBox.minX());
        int maxX = (int) Math.floor(currentBox.maxX());
        int minZ = (int) Math.floor(currentBox.minZ());
        int maxZ = (int) Math.floor(currentBox.maxZ());
        int minY = (int) Math.floor(currentBox.minY() + dropDy);
        int maxY = (int) Math.floor(currentBox.maxY());

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = cache.getBlock(x, y, z);
                    if (!block.isAir() && block.isSolid()) {
                        VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                        if (shape != null) {
                            for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(localBox, position.x, position.y + dropDy, position.z, box, x, y, z)) {
                                        float possibleDrop = (box.maxY() + y) - (localBox.minY() + position.y) + 0.001f;
                                        if (possibleDrop > dropDy) {
                                            dropDy = possibleDrop;
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
        position.y += dropDy;
        currentBox = localBox.offset(position);

        // Calculate distance moved in elevated step
        float elevatedDistSq = (position.x - xBeforeHorizontal) * (position.x - xBeforeHorizontal) +
                               (position.z - zBeforeHorizontal) * (position.z - zBeforeHorizontal);

        float stepDownY = position.y - yBeforeHorizontal;
        if (elevatedDistSq > normalDistSq && stepDownY >= 0.0f && stepDownY <= stepHeightSetting) {
            // Step-up is successful!
            return new StepResult(true, position.x - xBeforeHorizontal, position.z - zBeforeHorizontal, stepDownY);
        } else {
            // Rollback to normal collision state
            position.x = postCollisionX;
            position.y = postCollisionY;
            position.z = postCollisionZ;
            return new StepResult(false, currentDx, currentDz, 0.0f);
        }
    }
}
