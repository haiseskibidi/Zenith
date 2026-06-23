package com.za.zenith.world.physics;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import org.joml.Vector3f;

/**
 * Utility class for calculating fluid flow vectors and heights.
 * ponytail: static utility class to isolate fluid dynamics logic and keep files under 250 lines.
 */
public class FluidFlowCalculator {

    public static Vector3f getInterpolatedFluidFlowVector(World world, float px, float py, float pz, int fluidId) {
        return getInterpolatedFluidFlowVector(world, px, py, pz, fluidId, new Vector3f());
    }

    public static Vector3f getInterpolatedFluidFlowVector(World world, float px, float py, float pz, int fluidId, Vector3f dest) {
        int y = (int) Math.floor(py);
        if (y < 0 || y >= 256) {
            dest.set(0, 0, 0);
            return dest;
        }
        
        int x0 = (int) Math.floor(px - 0.5f);
        int z0 = (int) Math.floor(pz - 0.5f);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        
        float tx = px - (x0 + 0.5f);
        float tz = pz - (z0 + 0.5f);
        
        tx = Math.max(0.0f, Math.min(1.0f, tx));
        tz = Math.max(0.0f, Math.min(1.0f, tz));
        
        Vector3f v00 = getFluidFlowVectorAt(world, x0, y, z0, fluidId);
        Vector3f v10 = getFluidFlowVectorAt(world, x1, y, z0, fluidId);
        Vector3f v01 = getFluidFlowVectorAt(world, x0, y, z1, fluidId);
        Vector3f v11 = getFluidFlowVectorAt(world, x1, y, z1, fluidId);
        
        dest.x = (v00.x * (1.0f - tx) + v10.x * tx) * (1.0f - tz) + (v01.x * (1.0f - tx) + v11.x * tx) * tz;
        dest.z = (v00.z * (1.0f - tx) + v10.z * tx) * (1.0f - tz) + (v01.z * (1.0f - tx) + v11.z * tx) * tz;
        dest.y = (v00.y * (1.0f - tx) + v10.y * tx) * (1.0f - tz) + (v01.y * (1.0f - tx) + v11.y * tx) * tz;
        
        return dest;
    }
    
    private static Vector3f getFluidFlowVectorAt(World world, int x, int y, int z, int fluidId) {
        Block block = world.getBlock(x, y, z);
        if (block.getType() != fluidId) {
            return new Vector3f(0, 0, 0);
        }
        int fluidLevel = block.getMetadata() & 0xFF;
        return getFluidFlowVector(world, x, y, z, fluidId, fluidLevel);
    }

    public static Vector3f getFluidFlowVector(World world, int x, int y, int z, int fluidId, int currentLevel) {
        Vector3f flow = new Vector3f(0, 0, 0);
        
        if (y < 255) {
            Block above = world.getBlock(x, y + 1, z);
            if (above.getType() == fluidId) {
                flow.y = -1.0f;
            }
        }
        
        int currentHeight = (currentLevel == 8) ? 8 : (8 - currentLevel);
        
        int hNorth = getFluidHeightForFlow(world, x, y, z + 1, fluidId, currentHeight);
        int hSouth = getFluidHeightForFlow(world, x, y, z - 1, fluidId, currentHeight);
        int hEast  = getFluidHeightForFlow(world, x + 1, y, z,     fluidId, currentHeight);
        int hWest  = getFluidHeightForFlow(world, x - 1, y, z,     fluidId, currentHeight);
        
        flow.x = hWest - hEast;
        flow.z = hSouth - hNorth;
        
        float lenSq = flow.x * flow.x + flow.z * flow.z;
        if (lenSq > 0.0001f) {
            float len = (float) Math.sqrt(lenSq);
            flow.x /= len;
            flow.z /= len;
        }
        
        return flow;
    }
    
    private static int getFluidHeightForFlow(World world, int x, int y, int z, int fluidId, int currentHeight) {
        if (y < 0 || y >= 256) return currentHeight;
        Block block = world.getBlock(x, y, z);
        if (block.getType() != fluidId) {
            if (block.isSolid()) {
                return currentHeight;
            }
            if (y > 0) {
                Block below = world.getBlock(x, y - 1, z);
                if (below.isAir() || below.isReplaceable()) {
                    return currentHeight - 4;
                }
            }
            return currentHeight - 1;
        }
        
        int level = block.getMetadata() & 0xFF;
        if (level == 8) {
            return 8;
        }
        return 8 - level;
    }
}
