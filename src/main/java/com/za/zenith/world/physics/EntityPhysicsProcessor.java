package com.za.zenith.world.physics;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.chunks.ChunkCache;

public class EntityPhysicsProcessor {

    public static void move(Entity entity, World world, float dx, float dy, float dz) {
        entity.setLastStepUpHeight(0.0f);
        boolean wasOnGround = entity.isOnGround();
        float originalDx = dx;
        float originalDy = dy;
        float originalDz = dz;
        
        org.joml.Vector3f position = entity.getPosition();
        AABB localBox = entity.getLocalBoundingBox();
        org.joml.Vector3f velocity = entity.getVelocity();
        float stepHeight = entity.getStepHeight();
        String stepUpMode = entity.getStepUpMode();
        
        // 1. Initialize local ChunkCache covering the entire motion Broadphase bounding box
        AABB currentBox = localBox.offset(position);
        float minXF = Math.min(currentBox.minX(), currentBox.minX() + dx);
        float maxXF = Math.max(currentBox.maxX(), currentBox.maxX() + dx);
        float minYF = Math.min(currentBox.minY(), currentBox.minY() + dy) - 1.0f; // buffer for unstuck
        float maxYF = Math.max(currentBox.maxY(), currentBox.maxY() + dy) + 3.0f; // +3 for water step-up headroom checks
        float minZF = Math.min(currentBox.minZ(), currentBox.minZ() + dz);
        float maxZF = Math.max(currentBox.maxZ(), currentBox.maxZ() + dz);

        ChunkCache cache = new ChunkCache(
            world,
            (int) Math.floor(minXF),
            (int) Math.floor(minZF),
            (int) Math.floor(maxXF),
            (int) Math.floor(maxZF)
        );

        // 2. UNSTUCK: Softly push the entity up if already stuck inside a solid block
        if (EntityRampPhysicsHelper.isCollidingAt(cache, entity, currentBox)) {
            for (int i = 0; i < 10; i++) {
                float lift = 0.1f * (i + 1);
                if (!EntityRampPhysicsHelper.isCollidingAt(cache, entity, localBox.offset(position.x, position.y + lift, position.z))) {
                    position.y += lift;
                    currentBox = localBox.offset(position);
                    break;
                }
            }
        }

        // 3. VERTICAL COLLISION
        if (dy != 0) {
            if (dy > 0) entity.setOnGround(false);

            int minX = (int) Math.floor(currentBox.minX());
            int maxX = (int) Math.floor(currentBox.maxX());
            int minZ = (int) Math.floor(currentBox.minZ());
            int maxZ = (int) Math.floor(currentBox.maxZ());
            int minY = (int) Math.floor(currentBox.minY() + Math.min(0, dy));
            int maxY = (int) Math.floor(currentBox.maxY() + Math.max(0, dy));

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                                        // Рампа: вертикальное приземление на наклонную поверхность.
                                        // Вычисляем высоту склона в точке центра игрока.
                                        float rampH = StepUpHandler.getRampAbsoluteHeight(cache, shape, x, y, z, position.x, position.z);
                                        if (dy < 0) {
                                            float feetAfterFall = position.y + dy;
                                            if (feetAfterFall < rampH && position.y >= rampH - 1.0f) {
                                                dy = rampH - position.y;
                                                entity.setOnGround(true);
                                                velocity.y = 0;
                                            }
                                        } else if (dy > 0) {
                                            float headAfterMove = position.y + localBox.maxY() + dy;
                                            if (headAfterMove > y && position.y + localBox.maxY() <= y + 0.01f) {
                                                dy = y - (position.y + localBox.maxY()) - 0.001f;
                                                velocity.y = 0;
                                            }
                                        }
                                        continue;
                                    }
                                    if (AABB.intersects(localBox, position.x, position.y + dy, position.z, box, x, y, z)) {
                                        if (EntityRampPhysicsHelper.shouldSkipCollisionWithBlock(cache, entity, x, y, z, box, 0, 0)) {
                                            continue;
                                        }
                                        if (dy > 0) dy = (box.minY() + y) - (localBox.maxY() + position.y) - 0.001f;
                                        else {
                                            dy = (box.maxY() + y) - (localBox.minY() + position.y) + 0.001f;
                                            entity.setOnGround(true);
                                        }
                                        velocity.y = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.y += dy;
            currentBox = localBox.offset(position);
        }

        // Reset onGround if falling through air without hitting anything
        if (originalDy < -0.001f && entity.isOnGround() && Math.abs(dy - originalDy) < 0.0001f) {
            entity.setOnGround(false);
        }

        float xBeforeHorizontal = position.x;
        float yBeforeHorizontal = position.y;
        float zBeforeHorizontal = position.z;

        // 4. HORIZONTAL COLLISION (X)
        if (dx != 0) {
            int minX = (int) Math.floor(currentBox.minX() + Math.min(0, dx));
            int maxX = (int) Math.floor(currentBox.maxX() + Math.max(0, dx));
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
                                        if (StepUpHandler.isRampPassable(cache, shape, x, y, z, position.x, position.y, position.z, stepHeight)) {
                                            continue;
                                        }
                                    }
                                    if (AABB.intersects(localBox, position.x + dx, position.y, position.z, box, x, y, z)) {
                                        if (EntityRampPhysicsHelper.shouldSkipCollisionWithBlock(cache, entity, x, y, z, box, dx, 0)) {
                                            continue;
                                        }
                                        if (originalDx > 0) {
                                            float newDx = (box.minX() + x) - (localBox.maxX() + position.x) - 0.001f;
                                            dx = Math.max(0.0f, Math.min(dx, newDx));
                                        } else if (originalDx < 0) {
                                            float newDx = (box.maxX() + x) - (localBox.minX() + position.x) + 0.001f;
                                            dx = Math.min(0.0f, Math.max(dx, newDx));
                                        }
                                        velocity.x = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.x += dx;
            currentBox = localBox.offset(position);
        }

        // 5. HORIZONTAL COLLISION (Z)
        if (dz != 0) {
            int minX = (int) Math.floor(currentBox.minX());
            int maxX = (int) Math.floor(currentBox.maxX());
            int minZ = (int) Math.floor(currentBox.minZ() + Math.min(0, dz));
            int maxZ = (int) Math.floor(currentBox.maxZ() + Math.max(0, dz));
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
                                        if (StepUpHandler.isRampPassable(cache, shape, x, y, z, position.x, position.y, position.z, stepHeight)) {
                                            continue;
                                        }
                                    }
                                    if (AABB.intersects(localBox, position.x, position.y, position.z + dz, box, x, y, z)) {
                                        if (EntityRampPhysicsHelper.shouldSkipCollisionWithBlock(cache, entity, x, y, z, box, 0, dz)) {
                                            continue;
                                        }
                                        if (originalDz > 0) {
                                            float newDz = (box.minZ() + z) - (localBox.maxZ() + position.z) - 0.001f;
                                            dz = Math.max(0.0f, Math.min(dz, newDz));
                                        } else if (originalDz < 0) {
                                            float newDz = (box.maxZ() + z) - (localBox.minZ() + position.z) + 0.001f;
                                            dz = Math.min(0.0f, Math.max(dz, newDz));
                                        }
                                        velocity.z = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.z += dz;
        }

        boolean collidedHorizontally = (Math.abs(originalDx) > 0.00001f && Math.abs(dx) < Math.abs(originalDx) * 0.9f) ||
                                       (Math.abs(originalDz) > 0.00001f && Math.abs(dz) < Math.abs(originalDz) * 0.9f);

        boolean didStepUp = false;

        if (stepHeight > 0.0f && !stepUpMode.equalsIgnoreCase("NONE") && entity.isOnGround() && collidedHorizontally) {
            StepUpHandler.StepResult result = StepUpHandler.tryStepUp(
                entity, cache,
                xBeforeHorizontal, yBeforeHorizontal, zBeforeHorizontal,
                originalDx, originalDz,
                dx, dz,
                stepHeight, stepUpMode
            );

            if (result.success) {
                didStepUp = true;
                entity.setLastStepUpHeight(result.stepHeight);
                dx = result.actualDx;
                dz = result.actualDz;
                entity.setOnGround(true);
            }
        }

        EntityRampPhysicsHelper.adjustYForRamps(cache, entity, wasOnGround, originalDy);

        entity.setHorizontalCollision(!didStepUp && (
            (Math.abs(originalDx) > 0.00001f && Math.abs(dx) < Math.abs(originalDx) * 0.9f) ||
            (Math.abs(originalDz) > 0.00001f && Math.abs(dz) < Math.abs(originalDz) * 0.9f)
        ));
    }

}
