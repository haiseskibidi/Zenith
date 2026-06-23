package com.za.zenith.world.physics;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.chunks.ChunkCache;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockRegistry;

public class EntityRampPhysicsHelper {

    public static boolean shouldSkipCollisionWithBlock(
            ChunkCache cache, 
            Entity entity, 
            int blockX, 
            int blockY, 
            int blockZ, 
            AABB box, 
            float dx, 
            float dz
    ) {
        int rampY = blockY + 1;
        Block b = cache.getBlock(blockX, rampY, blockZ);
        if (b.isAir() || !b.isSolid()) {
            return false;
        }
        
        VoxelShape rampShape = BlockRegistry.getBlock(b.getType()).getShape(b.getMetadata());
        if (rampShape == null || rampShape.getGeometry() != VoxelShape.ShapeGeometry.RAMP) {
            return false;
        }
        
        org.joml.Vector3f position = entity.getPosition();
        float targetX = position.x;
        if (blockX > position.x) {
            targetX = blockX;
        } else if (blockX + 1.0f < position.x) {
            targetX = blockX + 1.0f;
        }
        
        float targetZ = position.z;
        if (blockZ > position.z) {
            targetZ = blockZ;
        } else if (blockZ + 1.0f < position.z) {
            targetZ = blockZ + 1.0f;
        }
        
        float rampH = StepUpHandler.getRampAbsoluteHeight(cache, rampShape, blockX, rampY, blockZ, targetX, targetZ);
        float blockMaxY = blockY + box.maxY();
        
        if (position.y < blockMaxY - 0.6f) {
            return false;
        }
        
        return blockMaxY <= rampH + 0.01f;
    }

    public static void adjustYForRamps(ChunkCache cache, Entity entity, boolean wasOnGround, float originalDy) {
        org.joml.Vector3f position = entity.getPosition();
        AABB localBox = entity.getLocalBoundingBox();
        float stepHeight = entity.getStepHeight();
        
        int centerX = (int) Math.floor(position.x);
        int centerZ = (int) Math.floor(position.z);
        AABB currentBox = localBox.offset(position);
        int minY = (int) Math.floor(currentBox.minY() - 1.0f);
        int maxY = (int) Math.floor(currentBox.maxY());

        float bestRampY = -999.0f;
        boolean foundOnCenter = false;

        for (int y = minY; y <= maxY; y++) {
            Block block = cache.getBlock(centerX, y, centerZ);
            if (!block.isAir() && block.isSolid()) {
                VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                if (shape != null && shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                    float rampH = StepUpHandler.getRampAbsoluteHeight(cache, shape, centerX, y, centerZ, position.x, position.z);
                    if (rampH > bestRampY) {
                        bestRampY = rampH;
                        foundOnCenter = true;
                    }
                }
            }
        }

        if (!foundOnCenter) {
            int minBoxX = (int) Math.floor(currentBox.minX());
            int maxBoxX = (int) Math.floor(currentBox.maxX());
            int minBoxZ = (int) Math.floor(currentBox.minZ());
            int maxBoxZ = (int) Math.floor(currentBox.maxZ());

            for (int x = minBoxX; x <= maxBoxX; x++) {
                for (int z = minBoxZ; z <= maxBoxZ; z++) {
                    if (x == centerX && z == centerZ) continue;
                    for (int y = minY; y <= maxY; y++) {
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null && shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                float minX = position.x + localBox.minX();
                                float maxX = position.x + localBox.maxX();
                                float minZ = position.z + localBox.minZ();
                                float maxZ = position.z + localBox.maxZ();
                                float rampH = StepUpHandler.getRampAbsoluteHeight(cache, shape, x, y, z, minX, maxX, minZ, maxZ);
                                if (Math.abs(rampH - position.y) <= stepHeight) {
                                    if (rampH > bestRampY) {
                                        bestRampY = rampH;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bestRampY != -999.0f) {
            float diff = bestRampY - position.y;
            if (diff > 0.0f && diff <= stepHeight && (entity.isOnGround() || wasOnGround)) {
                position.y = bestRampY;
                entity.setOnGround(true);
                if (entity.getVelocity().y < 0) entity.getVelocity().y = 0;
            } else if (diff < 0.0f && diff >= -stepHeight && (entity.isOnGround() || wasOnGround) && originalDy <= 0.001f) {
                position.y = bestRampY;
                entity.setOnGround(true);
                if (entity.getVelocity().y < 0) entity.getVelocity().y = 0;
            }
        }
    }

    public static boolean isCollidingAt(ChunkCache cache, Entity entity, AABB box) {
        int minX = (int) Math.floor(box.minX());
        int maxX = (int) Math.floor(box.maxX());
        int minY = (int) Math.floor(box.minY());
        int maxY = (int) Math.floor(box.maxY());
        int minZ = (int) Math.floor(box.minZ());
        int maxZ = (int) Math.floor(box.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = cache.getBlock(x, y, z);
                    if (!block.isAir() && block.isSolid()) {
                        VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                        if (shape != null) {
                            for (AABB sbox : shape.getBoxes()) {
                                if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                    continue;
                                }
                                if (AABB.intersects(sbox, (float)x, (float)y, (float)z, box, 0, 0, 0)) {
                                    if (shouldSkipCollisionWithBlock(cache, entity, x, y, z, sbox, 0, 0)) {
                                        continue;
                                    }
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}

