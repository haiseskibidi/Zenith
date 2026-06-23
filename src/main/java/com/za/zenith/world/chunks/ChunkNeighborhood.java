package com.za.zenith.world.chunks;

import com.za.zenith.world.World;
import com.za.zenith.world.BlockPos;

/**
 * Grid of 3x3 chunks around the center chunk to support fast, zero-allocation neighbor checks.
 */
public class ChunkNeighborhood {
    private final Chunk[][] neighborhood = new Chunk[3][3];
    private final int centerChunkX;
    private final int centerChunkZ;
    private final BlockPos breakingPos;

    public ChunkNeighborhood(World world, int cx, int cz) {
        this(world, cx, cz, null);
    }

    public ChunkNeighborhood(World world, int cx, int cz, BlockPos breakingPos) {
        this.breakingPos = breakingPos;
        this.centerChunkX = cx;
        this.centerChunkZ = cz;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                neighborhood[dx + 1][dz + 1] = world.getChunkInternal(cx + dx, cz + dz);
            }
        }
    }

    public boolean isBreaking(int x, int y, int z) {
        return breakingPos != null && breakingPos.x() == x && breakingPos.y() == y && breakingPos.z() == z;
    }

    public Chunk getChunk(int worldX, int worldZ) {
        int dx = (worldX >> 4) - centerChunkX + 1;
        int dz = (worldZ >> 4) - centerChunkZ + 1;
        if (dx < 0 || dx > 2 || dz < 0 || dz > 2) return null;
        return neighborhood[dx][dz];
    }

    public int getRawBlockData(int x, int y, int z) {
        if (isBreaking(x, y, z)) return 0;
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
        Chunk c = getChunk(x, z);
        if (c == null) return 0;
        return c.getRawBlockData(x & 15, y, z & 15);
    }

    public int getSunlight(int x, int y, int z) {
        if (y >= Chunk.CHUNK_HEIGHT) return 15;
        if (y < 0) return 0;
        if (breakingPos != null && x == breakingPos.x() && y == breakingPos.y() && z == breakingPos.z()) {
            int maxSun = 0;
            maxSun = Math.max(maxSun, getSunlightRaw(x + 1, y, z));
            maxSun = Math.max(maxSun, getSunlightRaw(x - 1, y, z));
            maxSun = Math.max(maxSun, getSunlightRaw(x, y + 1, z));
            maxSun = Math.max(maxSun, getSunlightRaw(x, y - 1, z));
            maxSun = Math.max(maxSun, getSunlightRaw(x, y, z + 1));
            maxSun = Math.max(maxSun, getSunlightRaw(x, y, z - 1));
            return maxSun;
        }
        return getSunlightRaw(x, y, z);
    }

    private int getSunlightRaw(int x, int y, int z) {
        if (y >= Chunk.CHUNK_HEIGHT) return 15;
        if (y < 0) return 0;
        Chunk c = getChunk(x, z);
        if (c == null || !c.isReady()) return (y >= 128) ? 15 : 0;
        return c.getSunlight(x & 15, y, z & 15);
    }

    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
        if (breakingPos != null && x == breakingPos.x() && y == breakingPos.y() && z == breakingPos.z()) {
            int maxBlock = 0;
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x + 1, y, z));
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x - 1, y, z));
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y + 1, z));
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y - 1, z));
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y, z + 1));
            maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y, z - 1));
            return maxBlock;
        }
        return getBlockLightRaw(x, y, z);
    }

    private int getBlockLightRaw(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
        Chunk c = getChunk(x, z);
        if (c == null || !c.isReady()) return 0;
        return c.getBlockLight(x & 15, y, z & 15);
    }
}
