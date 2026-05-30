package com.za.zenith.world.chunks;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;

/**
 * Thread-safe local cache of chunks covering a specific bounding area.
 * Designed to eliminate ConcurrentHashMap lookup overhead in physical update loops.
 */
public class ChunkCache {
    private final Chunk[] chunks;
    private final int minCX;
    private final int minCZ;
    private final int width;
    private final Block airBlock;

    public ChunkCache(World world, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        this.minCX = minBlockX >> 4;
        this.minCZ = minBlockZ >> 4;
        int maxCX = maxBlockX >> 4;
        int maxCZ = maxBlockZ >> 4;
        this.width = maxCX - minCX + 1;
        int height = maxCZ - minCZ + 1;
        
        this.chunks = new Chunk[width * height];
        for (int cz = minCZ; cz <= maxCZ; cz++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                int idx = (cz - minCZ) * width + (cx - minCX);
                this.chunks[idx] = world.getChunkInternal(cx, cz);
            }
        }
        this.airBlock = new Block(Blocks.AIR.getId());
    }

    public Block getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) {
            return airBlock;
        }
        int cx = x >> 4;
        int cz = z >> 4;
        int idx = (cz - minCZ) * width + (cx - minCX);
        if (idx >= 0 && idx < chunks.length) {
            Chunk chunk = chunks[idx];
            if (chunk != null) {
                return chunk.getBlock(x & 15, y, z & 15);
            }
        }
        return airBlock;
    }
}
