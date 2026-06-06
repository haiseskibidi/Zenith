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

        // 1. Elevate entity (with ceiling collision check to prevent clipping into blocks above)
        float elevatedY = stepHeightSetting;
        
        int ceilMinX = (int) Math.floor(localBox.minX() + xBeforeHorizontal);
        int ceilMaxX = (int) Math.floor(localBox.maxX() + xBeforeHorizontal);
        int ceilMinZ = (int) Math.floor(localBox.minZ() + zBeforeHorizontal);
        int ceilMaxZ = (int) Math.floor(localBox.maxZ() + zBeforeHorizontal);
        int ceilMinY = (int) Math.floor(localBox.minY() + yBeforeHorizontal);
        int ceilMaxY = (int) Math.floor(localBox.maxY() + yBeforeHorizontal + elevatedY);

        for (int x = ceilMinX; x <= ceilMaxX; x++) {
            for (int z = ceilMinZ; z <= ceilMaxZ; z++) {
                for (int y = ceilMinY; y <= ceilMaxY; y++) {
                    Block block = cache.getBlock(x, y, z);
                    if (!block.isAir() && block.isSolid()) {
                        VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                        if (shape != null) {
                            for (AABB box : shape.getBoxes()) {
                                if (AABB.intersects(localBox, xBeforeHorizontal, yBeforeHorizontal + elevatedY, zBeforeHorizontal, box, x, y, z)) {
                                    float limitY = (box.minY() + y) - (localBox.maxY() + yBeforeHorizontal) - 0.001f;
                                    if (limitY < elevatedY) {
                                        elevatedY = Math.max(0.0f, limitY);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        position.x = xBeforeHorizontal;
        position.y = yBeforeHorizontal + elevatedY;
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
                                    if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                        if (isRampPassable(cache, shape, x, y, z, position.x, yBeforeHorizontal, position.z, stepHeightSetting)) {
                                            continue;
                                        }
                                    }
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
                                     if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                          if (isRampPassable(cache, shape, x, y, z, position.x, yBeforeHorizontal, position.z + elevatedDz, stepHeightSetting)) {
                                             continue;
                                         }
                                     }
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
        float dropDy = -elevatedY;
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
                                if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                     float rampH = getRampAbsoluteHeight(cache, shape, x, y, z, position.x, position.z);
                                     float feetAfterFall = position.y + dropDy;
                                     if (feetAfterFall < rampH && position.y >= rampH - 1.0f) {
                                         float possibleDrop = rampH - position.y;
                                         if (possibleDrop > dropDy) {
                                             dropDy = possibleDrop;
                                         }
                                     }
                                     continue;
                                 }
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

    private static boolean hasRampNeighbor(ChunkCache cache, int blockX, int blockY, int blockZ, int dx, int dy, int dz, byte expectedDir) {
        if (cache == null) return false;
        Block b = cache.getBlock(blockX + dx, blockY + dy, blockZ + dz);
        if (b.isAir() || !b.isSolid()) return false;
        VoxelShape shape = BlockRegistry.getBlock(b.getType()).getShape(b.getMetadata());
        if (shape == null || shape.getGeometry() != VoxelShape.ShapeGeometry.RAMP) return false;
        return (shape.getMetadata() & 0x0F) == expectedDir;
    }

    public static float getRampAbsoluteHeight(ChunkCache cache, VoxelShape shape, int blockX, int blockY, int blockZ, float entityX, float entityZ) {
        byte dir = (byte)(shape.getMetadata() & 0x0F);
        
        float tx = entityX - blockX;
        float tz = entityZ - blockZ;
        
        if (dir == com.za.zenith.world.blocks.Block.DIR_EAST || dir == com.za.zenith.world.blocks.Block.DIR_WEST) {
            if (tx < 0.0f) {
                int dy = (dir == com.za.zenith.world.blocks.Block.DIR_EAST) ? -1 : 1;
                if (!hasRampNeighbor(cache, blockX, blockY, blockZ, -1, dy, 0, dir)) {
                    tx = 0.0f;
                }
            } else if (tx > 1.0f) {
                int dy = (dir == com.za.zenith.world.blocks.Block.DIR_EAST) ? 1 : -1;
                if (!hasRampNeighbor(cache, blockX, blockY, blockZ, 1, dy, 0, dir)) {
                    tx = 1.0f;
                }
            }
            tx = Math.max(-1.0f, Math.min(2.0f, tx));
            tz = Math.max(0.0f, Math.min(1.0f, tz));
        } else {
            if (tz < 0.0f) {
                int dy = (dir == com.za.zenith.world.blocks.Block.DIR_SOUTH) ? -1 : 1;
                if (!hasRampNeighbor(cache, blockX, blockY, blockZ, 0, dy, -1, dir)) {
                    tz = 0.0f;
                }
            } else if (tz > 1.0f) {
                int dy = (dir == com.za.zenith.world.blocks.Block.DIR_SOUTH) ? 1 : -1;
                if (!hasRampNeighbor(cache, blockX, blockY, blockZ, 0, dy, 1, dir)) {
                    tz = 1.0f;
                }
            }
            tx = Math.max(0.0f, Math.min(1.0f, tx));
            tz = Math.max(-1.0f, Math.min(2.0f, tz));
        }
        
        float h = switch (dir) {
            case com.za.zenith.world.blocks.Block.DIR_EAST  -> tx;
            case com.za.zenith.world.blocks.Block.DIR_WEST  -> 1.0f - tx;
            case com.za.zenith.world.blocks.Block.DIR_SOUTH -> tz;
            default /* DIR_NORTH */                          -> 1.0f - tz;
        };
        
        return blockY + h;
    }

    public static float getRampAbsoluteHeight(ChunkCache cache, VoxelShape shape, int blockX, int blockY, int blockZ, float minX, float maxX, float minZ, float maxZ) {
        float centerX = (minX + maxX) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;
        return getRampAbsoluteHeight(cache, shape, blockX, blockY, blockZ, centerX, centerZ);
    }

    public static float getRampAbsoluteHeight(VoxelShape shape, int blockX, int blockY, int blockZ, float entityX, float entityZ) {
        return getRampAbsoluteHeight(null, shape, blockX, blockY, blockZ, entityX, entityZ);
    }

    public static boolean isRampPassable(
        ChunkCache cache, 
        VoxelShape shape, 
        int blockX, 
        int blockY, 
        int blockZ, 
        float entityX, 
        float entityY, 
        float entityZ, 
        float stepHeight
    ) {
        float rampH = getRampAbsoluteHeight(cache, shape, blockX, blockY, blockZ, entityX, entityZ);
        if (rampH == -999.0f) {
            byte dir = (byte) (shape.getMetadata() & 0x0F);
            float tx = entityX - blockX;
            float tz = entityZ - blockZ;
            float edgeH = blockY;
            
            if (dir == com.za.zenith.world.blocks.Block.DIR_EAST) {
                if (tx > 1.0f) edgeH = blockY + 1.0f;
            } else if (dir == com.za.zenith.world.blocks.Block.DIR_WEST) {
                if (tx < 0.0f) edgeH = blockY + 1.0f;
            } else if (dir == com.za.zenith.world.blocks.Block.DIR_SOUTH) {
                if (tz > 1.0f) edgeH = blockY + 1.0f;
            } else if (dir == com.za.zenith.world.blocks.Block.DIR_NORTH) {
                if (tz < 0.0f) edgeH = blockY + 1.0f;
            }
            return entityY >= edgeH - stepHeight;
        }
        return entityY >= rampH - stepHeight;
    }
}

