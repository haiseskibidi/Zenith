package com.za.zenith.world.physics;

import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import org.joml.Vector3f;

public class Raycast {
    private static final float MAX_REACH_DISTANCE = 5.0f;
    
    public static RaycastResult raycast(World world, Vector3f origin, Vector3f direction) {
        Vector3f dir = new Vector3f(direction).normalize();
        if (dir.lengthSquared() == 0) {
            return new RaycastResult();
        }

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = dir.x > 0 ? 1 : (dir.x < 0 ? -1 : 0);
        int stepY = dir.y > 0 ? 1 : (dir.y < 0 ? -1 : 0);
        int stepZ = dir.z > 0 ? 1 : (dir.z < 0 ? -1 : 0);

        float nextVoxelBoundaryX = stepX > 0 ? (x + 1) : x;
        float nextVoxelBoundaryY = stepY > 0 ? (y + 1) : y;
        float nextVoxelBoundaryZ = stepZ > 0 ? (z + 1) : z;

        float tMaxX = stepX != 0 ? (nextVoxelBoundaryX - origin.x) / dir.x : Float.POSITIVE_INFINITY;
        float tMaxY = stepY != 0 ? (nextVoxelBoundaryY - origin.y) / dir.y : Float.POSITIVE_INFINITY;
        float tMaxZ = stepZ != 0 ? (nextVoxelBoundaryZ - origin.z) / dir.z : Float.POSITIVE_INFINITY;

        float tDeltaX = stepX != 0 ? Math.abs(1f / dir.x) : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dir.y) : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? Math.abs(1f / dir.z) : Float.POSITIVE_INFINITY;

        float maxT = MAX_REACH_DISTANCE;

        for (;;) {
            // 1. Проверяем текущий воксель
            RaycastResult hit = checkVoxel(world, x, y, z, origin, dir, maxT);
            if (hit != null) return hit;

            // 2. Проверяем блок СНИЗУ на наличие выступающих предметов (например, на пне)
            // Это критично, так как предметы на поверхности блока y-1 находятся в вокселе y.
            hit = checkVoxel(world, x, y - 1, z, origin, dir, maxT);
            if (hit != null) {
                // Мы проверяем только те хиты, которые попали именно в текущий воксель y
                if (hit.getHitPoint().y >= y && hit.getHitPoint().y <= y + 1) {
                    return hit;
                }
            }

            // Переход к следующему вокселю (стандартный DDA)
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > maxT) break;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > maxT) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    if (tMaxY > maxT) break;
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > maxT) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return new RaycastResult();
    }

    private static RaycastResult checkVoxel(World world, int x, int y, int z, Vector3f origin, Vector3f dir, float maxT) {
        Block block = world.getBlock(x, y, z);
        if (block.isAir()) return null;

        VoxelShape shape = block.getShape(world, new BlockPos(x, y, z));
        if (shape == null) return null;

        float closestDist = Float.POSITIVE_INFINITY;
        Vector3f closestNormal = null;

        for (AABB box : shape.getBoxes()) {
            AABB offsetBox = box.offset(x, y, z);
            AABB.RayHit hit = offsetBox.raycast(origin, dir);
            if (hit != null && hit.distance() < closestDist) {
                closestDist = hit.distance();
                closestNormal = hit.normal();
            }
        }

        if (closestNormal != null && closestDist <= maxT) {
            Vector3f hitPoint = new Vector3f(origin).fma(closestDist, dir);
            return new RaycastResult(new BlockPos(x, y, z), block, hitPoint, closestNormal, closestDist);
        }
        
        return null;
    }
    
    public static com.za.zenith.entities.Entity raycastEntity(World world, Vector3f origin, Vector3f direction) {
        com.za.zenith.entities.Entity closest = null;
        float minDistance = MAX_REACH_DISTANCE;

        int startCx = (int)Math.floor(origin.x / 16.0);
        int startCz = (int)Math.floor(origin.z / 16.0);
        
        // Ray reach is only 5m, so checking 1 chunk radius is more than enough
        for (int cx = startCx - 1; cx <= startCx + 1; cx++) {
            for (int cz = startCz - 1; cz <= startCz + 1; cz++) {
                com.za.zenith.world.chunks.ChunkPos cp = new com.za.zenith.world.chunks.ChunkPos(cx, cz);
                java.util.List<com.za.zenith.entities.Entity> groundEntities = world.getGroundEntitiesInChunk(cp);
                if (groundEntities.isEmpty()) continue;

                synchronized(groundEntities) {
                    for (int i = 0; i < groundEntities.size(); i++) {
                        com.za.zenith.entities.Entity entity = groundEntities.get(i);
                        AABB bounds = entity.getBoundingBox();
                        if (bounds == null) continue;

                        float dist = bounds.intersectDist(origin, direction);
                        if (dist > 0 && dist < minDistance) {
                            minDistance = dist;
                            closest = entity;
                        }
                    }
                }
            }
        }

        // Also check global entities (Players, Scouts) - there are very few of them
        java.util.List<com.za.zenith.entities.Entity> globalEntities = world.getEntities();
        synchronized(globalEntities) {
            for (int i = 0; i < globalEntities.size(); i++) {
                com.za.zenith.entities.Entity entity = globalEntities.get(i);
                if (entity.isGroundEntity()) continue; // Already checked via spatial map

                AABB bounds = entity.getBoundingBox();
                if (bounds == null) continue;

                float dist = bounds.intersectDist(origin, direction);
                if (dist > 0 && dist < minDistance) {
                    minDistance = dist;
                    closest = entity;
                }
            }
        }

        return closest;
    }
}
