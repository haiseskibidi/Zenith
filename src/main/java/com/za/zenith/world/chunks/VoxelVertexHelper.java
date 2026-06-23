package com.za.zenith.world.chunks;

import com.za.zenith.world.physics.AABB;

/**
 * Helper to calculate face vertices for voxels and fluids without heap allocation.
 */
public class VoxelVertexHelper {

    public static float[] fillFaceVertices(int face, AABB box, float h0, float h1, float h2, float h3, boolean isWater, float[] dest) {
        float minX = box.minX(); float minY = box.minY(); float minZ = box.minZ();
        float maxX = box.maxX(); float maxY = box.maxY(); float maxZ = box.maxZ();
        if (isWater) {
            switch (face) {
                case 0:
                    dest[0] = minX; dest[1] = minY; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = maxZ;
                    dest[6] = maxX; dest[7] = minY + h1; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = minY + h0; dest[11] = maxZ;
                    break;
                case 1:
                    dest[0] = maxX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = minX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = minX; dest[7] = minY + h3; dest[8] = minZ;
                    dest[9] = maxX; dest[10] = minY + h2; dest[11] = minZ;
                    break;
                case 2:
                    dest[0] = maxX; dest[1] = minY; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = maxX; dest[7] = minY + h2; dest[8] = minZ;
                    dest[9] = maxX; dest[10] = minY + h1; dest[11] = maxZ;
                    break;
                case 3:
                    dest[0] = minX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = minX; dest[4] = minY; dest[5] = maxZ;
                    dest[6] = minX; dest[7] = minY + h0; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = minY + h3; dest[11] = minZ;
                    break;
                case 4:
                    dest[0] = minX; dest[1] = minY + h0; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = minY + h1; dest[5] = maxZ;
                    dest[6] = maxX; dest[7] = minY + h2; dest[8] = minZ;
                    dest[9] = minX; dest[10] = minY + h3; dest[11] = minZ;
                    break;
                case 5:
                    dest[0] = minX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = maxX; dest[7] = minY; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = minY; dest[11] = maxZ;
                    break;
            }
        } else {
            switch (face) {
                case 0:
                    dest[0] = minX; dest[1] = minY; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = maxZ;
                    dest[6] = maxX; dest[7] = maxY; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = maxY; dest[11] = maxZ;
                    break;
                case 1:
                    dest[0] = maxX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = minX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = minX; dest[7] = maxY; dest[8] = minZ;
                    dest[9] = maxX; dest[10] = maxY; dest[11] = minZ;
                    break;
                case 2:
                    dest[0] = maxX; dest[1] = minY; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = maxX; dest[7] = maxY; dest[8] = minZ;
                    dest[9] = maxX; dest[10] = maxY; dest[11] = maxZ;
                    break;
                case 3:
                    dest[0] = minX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = minX; dest[4] = minY; dest[5] = maxZ;
                    dest[6] = minX; dest[7] = maxY; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = maxY; dest[11] = minZ;
                    break;
                case 4:
                    dest[0] = minX; dest[1] = maxY; dest[2] = maxZ;
                    dest[3] = maxX; dest[4] = maxY; dest[5] = maxZ;
                    dest[6] = maxX; dest[7] = maxY; dest[8] = minZ;
                    dest[9] = minX; dest[10] = maxY; dest[11] = minZ;
                    break;
                case 5:
                    dest[0] = minX; dest[1] = minY; dest[2] = minZ;
                    dest[3] = maxX; dest[4] = minY; dest[5] = minZ;
                    dest[6] = maxX; dest[7] = minY; dest[8] = maxZ;
                    dest[9] = minX; dest[10] = minY; dest[11] = maxZ;
                    break;
            }
        }
        return dest;
    }
}
