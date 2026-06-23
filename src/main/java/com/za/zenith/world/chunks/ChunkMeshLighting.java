package com.za.zenith.world.chunks;

import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.utils.Direction;

/**
 * Utility class for calculating Ambient Occlusion (AO) and smooth lighting during chunk mesh generation.
 */
public class ChunkMeshLighting {

    public static float calculateAO(ChunkNeighborhood neighborhood, int x, int y, int z, int face, float vx, float vy, float vz) {
        if (neighborhood == null) return 1.0f;
        
        int nx = (vx > 0.0f) ? 1 : -1;
        int ny = (vy > 0.0f) ? 1 : -1;
        int nz = (vz > 0.0f) ? 1 : -1;

        int side1, side2, corner;
        
        switch (face) {
            case 0: // North (+Z)
            case 1: // South (-Z)
                int fz = z + (face == 0 ? 1 : -1);
                side1 = isSolid(neighborhood, x + nx, y, fz) ? 1 : 0;
                side2 = isSolid(neighborhood, x, y + ny, fz) ? 1 : 0;
                corner = isSolid(neighborhood, x + nx, y + ny, fz) ? 1 : 0;
                break;
            case 2: // East (+X)
            case 3: // West (-X)
                int fx = x + (face == 2 ? 1 : -1);
                side1 = isSolid(neighborhood, fx, y + ny, z) ? 1 : 0;
                side2 = isSolid(neighborhood, fx, y, z + nz) ? 1 : 0;
                corner = isSolid(neighborhood, fx, y + ny, z + nz) ? 1 : 0;
                break;
            case 4: // Up (+Y)
            case 5: // Down (-Y)
                int fy = y + (face == 4 ? 1 : -1);
                side1 = isSolid(neighborhood, x + nx, fy, z) ? 1 : 0;
                side2 = isSolid(neighborhood, x, fy, z + nz) ? 1 : 0;
                corner = isSolid(neighborhood, x + nx, fy, z + nz) ? 1 : 0;
                break;
            default: return 1.0f;
        }

        if (side1 == 1 && side2 == 1) return 0.3f;
        return 1.0f - (side1 + side2 + corner) * 0.2f;
    }

    public static boolean isSolid(ChunkNeighborhood neighborhood, int x, int y, int z) {
        if (neighborhood == null) return false;
        int rawData = neighborhood.getRawBlockData(x, y, z);
        int type = rawData >> 8;
        if (type == 0) return false;
        BlockDefinition def = BlockRegistry.getBlock(type);
        if (def == null) return false;
        if (def.is(BlockDefinition.FLAG_LEAVES)) return false;
        return def.is(BlockDefinition.FLAG_SOLID) && !def.is(BlockDefinition.FLAG_TRANSPARENT);
    }

    public static void calculateSmoothLight(ChunkNeighborhood neighborhood, int x, int y, int z, int face, float vx, float vy, float vz, float[] out) {
        if (neighborhood == null) {
            out[0] = 15f; out[1] = 0f;
            return;
        }
        
        Direction dir = Direction.values()[face];
        int fx = x + dir.getDx();
        int fy = y + dir.getDy();
        int fz = z + dir.getDz();

        int nx = (vx > 0.0f) ? 1 : -1;
        int ny = (vy > 0.0f) ? 1 : -1;
        int nz = (vz > 0.0f) ? 1 : -1;

        float totalSun = 0;
        float totalBlock = 0;

        float centralSun = neighborhood.getSunlight(fx, fy, fz);
        float centralBlock = neighborhood.getBlockLight(fx, fy, fz);
        
        for (int i = 0; i < 4; i++) {
            int sx = fx, sy = fy, sz = fz;
            if (face < 2) { // Z face
                if (i == 1 || i == 3) sx += nx;
                if (i == 2 || i == 3) sy += ny;
            } else if (face < 4) { // X face
                if (i == 1 || i == 3) sy += ny;
                if (i == 2 || i == 3) sz += nz;
            } else { // Y face
                if (i == 1 || i == 3) sx += nx;
                if (i == 2 || i == 3) sz += nz;
            }
            
            totalSun += neighborhood.getSunlight(sx, sy, sz);
            totalBlock += neighborhood.getBlockLight(sx, sy, sz);
        }

        out[0] = Math.max(centralSun, totalSun * 0.25f);
        out[1] = Math.max(centralBlock, totalBlock * 0.25f);
    }
}
