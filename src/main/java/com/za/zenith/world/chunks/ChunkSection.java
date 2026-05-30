package com.za.zenith.world.chunks;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkSection {
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    
    private final short[] blockIndices;
    private final byte[] lightData;
    private int nonEmptyBlockCount;
    private long visibilityMask = -1L; // Bits 0-35 representing connectivity between 6 faces
    private final java.util.concurrent.atomic.AtomicLong dirtyCounter = new java.util.concurrent.atomic.AtomicLong(0);
    private long lastMeshCounter = -1;
    
    public ChunkSection() {
        this.blockIndices = new short[SECTION_VOLUME];
        this.lightData = new byte[SECTION_VOLUME];
        this.nonEmptyBlockCount = 0;
    }
    
    public long getVisibilityMask() {
        return visibilityMask;
    }
    
    public boolean needsMeshUpdate() {
        return dirtyCounter.get() != lastMeshCounter;
    }

    public void setMeshUpdated(long version) {
        this.lastMeshCounter = version;
    }
    
    public void setVisibilityMask(long mask) {
        this.visibilityMask = mask;
    }

    public void calculateVisibility(Chunk chunk, int sectionIndex) {
        if (isEmpty()) {
            visibilityMask = -1L; // All reachable
            return;
        }

        // 0. Setup
        int startY = sectionIndex * SECTION_SIZE;
        java.util.BitSet visited = new java.util.BitSet(SECTION_VOLUME);
        java.util.List<Integer> reachableFaces = new java.util.ArrayList<>();
        
        long newMask = 0;

        // 1. Flood fill from each face
        for (int f = 0; f < 6; f++) {
            if (isFaceFull(chunk, sectionIndex, f)) continue; // Optimization: skip if face is solid
            
            int foundFacesMask = 0;
            // Scan the face for an air block to start flood fill
            for (int i = 0; i < SECTION_SIZE * SECTION_SIZE; i++) {
                int x = 0, y = 0, z = 0;
                switch (f) {
                    case 0: x = i % 16; y = i / 16; z = 15; break; // +Z (North)
                    case 1: x = i % 16; y = i / 16; z = 0; break;  // -Z (South)
                    case 2: x = 15; y = i % 16; z = i / 16; break; // +X (East)
                    case 3: x = 0; y = i % 16; z = i / 16; break;  // -X (West)
                    case 4: x = i % 16; y = 15; z = i / 16; break; // +Y (Up)
                    case 5: x = i % 16; y = 0; z = i / 16; break;  // -Y (Down)
                }

                int idx = getIndex(x, y, z);
                if (!visited.get(idx) && isTransparent(chunk, x, startY + y, z)) {
                    foundFacesMask |= floodFill(chunk, sectionIndex, x, y, z, visited);
                }
            }

            // Map bitmask to visibility bits (6x6)
            for (int f1 = 0; f1 < 6; f1++) {
                if ((foundFacesMask & (1 << f1)) != 0) {
                    for (int f2 = 0; f2 < 6; f2++) {
                        if ((foundFacesMask & (1 << f2)) != 0) {
                            newMask |= (1L << (f1 * 6 + f2));
                        }
                    }
                }
            }
        }

        this.visibilityMask = newMask;
    }

    private boolean isFaceFull(Chunk chunk, int secIdx, int face) {
        // Simple heuristic: if all blocks on a face are opaque, it's "full"
        // But for cave culling, we need exact flood fill. 
        // This is just a placeholder for more advanced checks if needed.
        return false; 
    }

    private boolean isTransparent(Chunk chunk, int x, int y, int z) {
        int raw = chunk.getRawBlockData(x, y, z);
        int type = raw >> 8;
        if (type == 0) return true;
        return com.za.zenith.world.blocks.BlockRegistry.getBlock(type).isTransparent();
    }

    private int floodFill(Chunk chunk, int secIdx, int sx, int sy, int sz, java.util.BitSet visited) {
        int foundFaces = 0;
        int startY = secIdx * SECTION_SIZE;
        
        java.util.Deque<Integer> queue = new java.util.ArrayDeque<>();
        int startIdx = getIndex(sx, sy, sz);
        queue.add(startIdx);
        visited.set(startIdx);

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int x = idx % 16;
            int z = (idx / 16) % 16;
            int y = idx / 256;

            // Check if on face
            if (z == 15) foundFaces |= (1 << 0);
            if (z == 0)  foundFaces |= (1 << 1);
            if (x == 15) foundFaces |= (1 << 2);
            if (x == 0)  foundFaces |= (1 << 3);
            if (y == 15) foundFaces |= (1 << 4);
            if (y == 0)  foundFaces |= (1 << 5);

            // Neighbors
            for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();

                if (nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && nz >= 0 && nz < 16) {
                    int nIdx = getIndex(nx, ny, nz);
                    if (!visited.get(nIdx) && isTransparent(chunk, nx, startY + ny, nz)) {
                        visited.set(nIdx);
                        queue.add(nIdx);
                    }
                }
            }
        }
        return foundFaces;
    }

    public boolean canSeeThrough(com.za.zenith.utils.Direction from, com.za.zenith.utils.Direction to) {
        if (from == to) return true;
        int bit = from.ordinal() * 6 + to.ordinal();
        return (visibilityMask & (1L << bit)) != 0;
    }
    
    private int getIndex(int x, int y, int z) {
        return y * (SECTION_SIZE * SECTION_SIZE) + z * SECTION_SIZE + x;
    }
    
    public short getBlockIndex(int x, int y, int z) {
        return blockIndices[getIndex(x, y, z)];
    }
    
    public void setBlockIndex(int x, int y, int z, short index, boolean wasAir, boolean isAir) {
        blockIndices[getIndex(x, y, z)] = index;
        if (wasAir && !isAir) {
            nonEmptyBlockCount++;
        } else if (!wasAir && isAir) {
            nonEmptyBlockCount--;
        }
        markDirty();
    }
    
    public int getSunlight(int x, int y, int z) {
        return (lightData[getIndex(x, y, z)] >> 4) & 0xF;
    }

    public boolean setSunlight(int x, int y, int z, int level) {
        int idx = getIndex(x, y, z);
        int oldVal = (lightData[idx] >> 4) & 0xF;
        if (oldVal == level) return false;
        lightData[idx] = (byte) ((lightData[idx] & 0x0F) | ((level & 0xF) << 4));
        markDirty();
        return true;
    }

    public int getBlockLight(int x, int y, int z) {
        return lightData[getIndex(x, y, z)] & 0xF;
    }

    public boolean setBlockLight(int x, int y, int z, int level) {
        int idx = getIndex(x, y, z);
        int oldVal = lightData[idx] & 0xF;
        if (oldVal == level) return false;
        lightData[idx] = (byte) ((lightData[idx] & 0xF0) | (level & 0xF));
        markDirty();
        return true;
    }
    
    public boolean isEmpty() {
        return nonEmptyBlockCount == 0;
    }
    
    public long getDirtyCounter() {
        return dirtyCounter.get();
    }
    
    public void markDirty() {
        dirtyCounter.incrementAndGet();
    }
    
    public void setNeedsMeshUpdate(boolean needsUpdate) {
        if (needsUpdate) markDirty();
    }

    public short[] getBlockIndices() {
        return blockIndices;
    }

    public byte[] getLightData() {
        return lightData;
    }
    
    public void fillLightData(byte[] data, int sourceOffset) {
        System.arraycopy(data, sourceOffset, this.lightData, 0, SECTION_VOLUME);
    }
}
