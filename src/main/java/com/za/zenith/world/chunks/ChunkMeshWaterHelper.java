package com.za.zenith.world.chunks;

import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;

/**
 * Utility class for calculating water height levels and water flow direction during chunk mesh generation.
 */
public class ChunkMeshWaterHelper {

    public static float getCornerWaterHeight(ChunkNeighborhood neighborhood, int wx, int wy, int wz, int dx, int dz, int waterId) {
        int xOffset = (dx == 0) ? -1 : 1;
        int zOffset = (dz == 0) ? -1 : 1;
        
        float sumHeight = 0;
        int count = 0;
        boolean hasSourceOrFalling = false;
        
        // 1. First scan for sources (0) or falling columns (8)
        for (int ox = 0; ox <= 1; ox++) {
            for (int oz = 0; oz <= 1; oz++) {
                int bx = wx + (ox == 0 ? 0 : xOffset);
                int bz = wz + (oz == 0 ? 0 : zOffset);
                
                int rawAbove = neighborhood.getRawBlockData(bx, wy + 1, bz);
                if ((rawAbove >> 8) == waterId) {
                    return 1.0f; // Water above makes the corner full
                }
                
                int raw = neighborhood.getRawBlockData(bx, wy, bz);
                int type = raw >> 8;
                if (type == waterId) {
                    int level = raw & 0xFF;
                    if (level == 0 || level == 8) {
                        hasSourceOrFalling = true;
                    }
                }
            }
        }
        
        // 2. Calculate corner height
        for (int ox = 0; ox <= 1; ox++) {
            for (int oz = 0; oz <= 1; oz++) {
                int bx = wx + (ox == 0 ? 0 : xOffset);
                int bz = wz + (oz == 0 ? 0 : zOffset);
                
                int raw = neighborhood.getRawBlockData(bx, wy, bz);
                int type = raw >> 8;
                if (type == waterId) {
                    int level = raw & 0xFF;
                    if (level == 8) {
                        sumHeight += 1.0f;
                    } else {
                        // Limit max source height to 0.875f (14/16 as in Minecraft)
                        sumHeight += ((8 - level) / 8.0f) * 0.875f;
                    }
                    count++;
                } else if (!hasSourceOrFalling) {
                    // Neighbor voxel is not water. Check if it's a drop (only if no source/falling water)
                    BlockDefinition neighborDef = BlockRegistry.getBlock(type);
                    if (neighborDef != null && neighborDef.isReplaceable()) {
                        int rawBelow = neighborhood.getRawBlockData(bx, wy - 1, bz);
                        int typeBelow = rawBelow >> 8;
                        BlockDefinition belowDef = BlockRegistry.getBlock(typeBelow);
                        if (belowDef != null && belowDef.isReplaceable()) {
                            // This is a cliff drop! Assign height 0.0f
                            sumHeight += 0.0f;
                            count++;
                        }
                    }
                }
            }
        }
        
        if (count == 0) return 0.875f;
        return sumHeight / count;
    }

    public static float getWaterFlowDirection(ChunkNeighborhood neighborhood, int wx, int wy, int wz, int fluidId) {
        int currentLevel = getWaterLevel(neighborhood, wx, wy, wz, fluidId);
        if (currentLevel < 0) return 15.0f;
        
        int levelW = getWaterLevel(neighborhood, wx - 1, wy, wz, fluidId);
        int levelE = getWaterLevel(neighborhood, wx + 1, wy, wz, fluidId);
        int levelN = getWaterLevel(neighborhood, wx, wy, wz - 1, fluidId);
        int levelS = getWaterLevel(neighborhood, wx, wy, wz + 1, fluidId);
        
        float dx = 0;
        float dz = 0;
        
        if (levelW >= 0 && levelW != 8) dx += (currentLevel - levelW);
        if (levelE >= 0 && levelE != 8) dx -= (currentLevel - levelE);
        if (levelN >= 0 && levelN != 8) dz += (currentLevel - levelN);
        if (levelS >= 0 && levelS != 8) dz -= (currentLevel - levelS);
        
        if (dx == 0 && dz == 0) return 15.0f;
        
        double angle = Math.atan2(dz, dx);
        if (angle < 0) angle += 2.0 * Math.PI;
        
        int quantized = (int) Math.round((angle / (2.0 * Math.PI)) * 16.0) % 16;
        return (float) quantized;
    }

    public static int getWaterLevel(ChunkNeighborhood neighborhood, int x, int y, int z, int waterId) {
        int raw = neighborhood.getRawBlockData(x, y, z);
        int type = raw >> 8;
        if (type != waterId) return -1;
        return raw & 0xFF;
    }
}
