package com.za.zenith.world.chunks;

import com.za.zenith.world.BlockPos;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 512;
    public static final int CHUNK_HEIGHT = MAX_Y - MIN_Y;
    public static final int LOGICAL_OFFSET_Y = 128; // Logical Y 0 is at Internal Y 128
    public static final int NUM_SECTIONS = CHUNK_HEIGHT / ChunkSection.SECTION_SIZE;
    
    private final ChunkPos position;
    private final ChunkSection[] sections;
    private final java.util.List<Integer> palette = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final short[] heightMap;
    private final AtomicLong dirtyCounter = new AtomicLong(0);
    private long lastMeshCounter = -1;
    private volatile boolean isReady = false;
    
    private float firstSpawnTime;
    private long lastSeenFrame = 0;
    private com.za.zenith.world.chunks.ChunkMeshGenerator.ChunkMeshResult currentMeshResult;

    public long getLastSeenFrame() { return lastSeenFrame; }
    public void setLastSeenFrame(long frame) { this.lastSeenFrame = frame; }
    public float getFirstSpawnTime() { return firstSpawnTime; }
    public void setFirstSpawnTime(float time) { this.firstSpawnTime = time; }

    public com.za.zenith.world.chunks.ChunkMeshGenerator.ChunkMeshResult getCurrentMeshResult() {
        return currentMeshResult;
    }

    public void setCurrentMeshResult(com.za.zenith.world.chunks.ChunkMeshGenerator.ChunkMeshResult result) {
        this.currentMeshResult = result;
    }

    public Chunk(ChunkPos position) {
        this.position = position;
        this.sections = new ChunkSection[NUM_SECTIONS];
        for (int i = 0; i < NUM_SECTIONS; i++) {
            this.sections[i] = new ChunkSection();
        }
        this.heightMap = new short[CHUNK_SIZE * CHUNK_SIZE];
        java.util.Arrays.fill(this.heightMap, (short)-1);
        
        // Initial palette: Air at index 0
        palette.add(0);
        this.firstSpawnTime = (float)(org.lwjgl.glfw.GLFW.glfwGetTime() % 3600.0);
    }

    public Chunk(ChunkPos position, int[] rawBlockData, byte[] lightData) {
        this(position);
        
        // Fill light data into sections
        int sectionVol = ChunkSection.SECTION_VOLUME;
        for (int i = 0; i < NUM_SECTIONS; i++) {
            this.sections[i].fillLightData(lightData, i * sectionVol);
        }
        
        for (int y = 0; y < CHUNK_HEIGHT; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    int idx = y * (CHUNK_SIZE * CHUNK_SIZE) + z * CHUNK_SIZE + x;
                    int packedData = rawBlockData[idx];
                    if (packedData != 0) {
                        setBlockByIndexInternal(x, y, z, packedData);
                    }
                }
            }
        }
    }
    
    public ChunkSection[] getSections() {
        return sections;
    }
    
    public ChunkSection getSection(int y) {
        if (y < 0 || y >= CHUNK_HEIGHT) return null;
        return sections[y >> 4];
    }

    // internal unsynchronized version for constructor
    private void setBlockByIndexInternal(int x, int y, int z, int packedData) {
        ChunkSection section = getSection(y);
        if (section == null) return;
        
        short paletteIndex = -1;
        for (int i = 0; i < palette.size(); i++) {
            if (palette.get(i) == packedData) {
                paletteIndex = (short) i;
                break;
            }
        }
        if (paletteIndex == -1) {
            paletteIndex = (short) palette.size();
            palette.add(packedData);
        }
        
        short oldIndex = section.getBlockIndex(x, y & 15, z);
        int oldPacked = palette.get(oldIndex);
        boolean wasAir = (oldPacked >> 8) == 0;
        boolean isAir = (packedData >> 8) == 0;
        
        section.setBlockIndex(x, y & 15, z, paletteIndex, wasAir, isAir);
    }

    private synchronized void setBlockByIndex(int x, int y, int z, int packedData) {
        setBlockByIndexInternal(x, y, z, packedData);
    }

    public boolean isReady() { return isReady; }
    public void setReady(boolean ready) { this.isReady = ready; }

    public int getHighestBlock(int x, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return 0;
        int idx = z * CHUNK_SIZE + x;
        int y = heightMap[idx];
        if (y == -1) {
            for (y = CHUNK_HEIGHT - 1; y > 0; y--) {
                int type = getBlockType(x, y, z);
                if (type != 0 && !com.za.zenith.world.blocks.BlockRegistry.getBlock(type).isTransparent()) {
                    heightMap[idx] = (short)y;
                    return y;
                }
            }
            heightMap[idx] = 0;
            return 0;
        }
        return y;
    }

    public int getSunlight(int x, int y, int z) {
        if (y >= CHUNK_HEIGHT) return 15;
        ChunkSection section = getSection(y);
        if (section == null || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return 0;
        return section.getSunlight(x, y & 15, z);
    }

    public void setSunlight(int x, int y, int z, int level) {
        ChunkSection section = getSection(y);
        if (section == null || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return;
        section.setSunlight(x, y & 15, z, level);
        dirtyCounter.incrementAndGet();
    }

    public int getBlockLight(int x, int y, int z) {
        ChunkSection section = getSection(y);
        if (section == null || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return 0;
        return section.getBlockLight(x, y & 15, z);
    }

    public void setBlockLight(int x, int y, int z, int level) {
        ChunkSection section = getSection(y);
        if (section == null || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return;
        section.setBlockLight(x, y & 15, z, level);
        dirtyCounter.incrementAndGet();
    }
    
    private static final ThreadLocal<Block> FLYWEIGHT_BLOCK = ThreadLocal.withInitial(() -> new Block(0));

    public Block getBlock(int x, int y, int z) {
        Block b = FLYWEIGHT_BLOCK.get();
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            b.setType(com.za.zenith.world.blocks.Blocks.AIR.getId());
            b.setMetadata((byte)0);
            return b;
        }
        int data = getRawBlockData(x, y, z);
        b.setType(data >> 8);
        b.setMetadata((byte)(data & 0xFF));
        return b;
    }

    public int getBlockType(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return 0;
        return getRawBlockData(x, y, z) >> 8;
    }

    public com.za.zenith.world.BlockPos toWorldPos(int localX, int localY, int localZ) {
        return new com.za.zenith.world.BlockPos(
            position.x() * CHUNK_SIZE + localX,
            localY,
            position.z() * CHUNK_SIZE + localZ
        );
    }

    public byte getBlockMetadata(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return 0;
        return (byte)(getRawBlockData(x, y, z) & 0xFF);
    }
    
    public synchronized void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block.getType(), block.getMetadata());
    }

    public synchronized void setBlock(int x, int y, int z, int type, int metadata) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return;
        int packed = (type << 8) | (metadata & 0xFF);
        setBlockByIndex(x, y, z, packed);
        heightMap[z * CHUNK_SIZE + x] = -1;
        dirtyCounter.incrementAndGet();
    }
    
    public int getRawBlockData(int x, int y, int z) {
        ChunkSection section = getSection(y);
        if (section == null || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return 0;
        return palette.get(section.getBlockIndex(x, y & 15, z));
    }
    
    public ChunkPos getPosition() {
        return position;
    }
    
    public boolean needsMeshUpdate() {
        return dirtyCounter.get() != lastMeshCounter;
    }
    
    public void setNeedsMeshUpdate(boolean needsUpdate) {
        if (needsUpdate) dirtyCounter.incrementAndGet();
    }
    
    public void setMeshUpdated(long version) {
        this.lastMeshCounter = version;
    }

    public long getDirtyCounter() {
        return dirtyCounter.get();
    }

    public void setDirtyCounter(long dirtyCounterVal) {
        this.dirtyCounter.set(dirtyCounterVal);
    }

    public record DataSnapshot(ChunkPos position, int[] blockData, byte[] lightData) {
        public int getRawBlockData(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return 0;
            return blockData[y * (CHUNK_SIZE * CHUNK_SIZE) + z * CHUNK_SIZE + x];
        }
        
        public int getSunlight(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return 15;
            return (lightData[y * (CHUNK_SIZE * CHUNK_SIZE) + z * CHUNK_SIZE + x] >> 4) & 0xF;
        }

        public int getBlockLight(int x, int y, int z) {
            if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) return 0;
            return lightData[y * (CHUNK_SIZE * CHUNK_SIZE) + z * CHUNK_SIZE + x] & 0xF;
        }
    }

    public int[] getBlockData() { 
        int[] data = new int[CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_SIZE];
        for (int y = 0; y < CHUNK_HEIGHT; y++) {
            ChunkSection sec = getSection(y);
            if (sec == null) continue;
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    data[y * 256 + z * 16 + x] = palette.get(sec.getBlockIndex(x, y & 15, z));
                }
            }
        }
        return data; 
    }
    
    public byte[] getLightData() { 
        byte[] data = new byte[CHUNK_SIZE * CHUNK_HEIGHT * CHUNK_SIZE];
        for (int i = 0; i < NUM_SECTIONS; i++) {
            System.arraycopy(sections[i].getLightData(), 0, data, i * ChunkSection.SECTION_VOLUME, ChunkSection.SECTION_VOLUME);
        }
        return data; 
    }

    public synchronized DataSnapshot getSnapshot(int[] outBlockData, byte[] outLightData) {
        for (int y = 0; y < CHUNK_HEIGHT; y++) {
            ChunkSection sec = getSection(y);
            if (sec == null) continue;
            short[] indices = sec.getBlockIndices();
            int baseIdx = y * 256;
            int localY = y & 15;
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    int palIdx = indices[localY * 256 + z * 16 + x] & 0xFFFF;
                    if (palIdx < palette.size()) {
                        outBlockData[baseIdx + z * 16 + x] = palette.get(palIdx);
                    } else {
                        com.za.zenith.utils.Logger.error("Palette corruption in chunk %s at local [%d, %d, %d]: Index %d out of bounds for palette size %d. Resetting to Air.", 
                            position, x, y, z, palIdx, palette.size());
                        outBlockData[baseIdx + z * 16 + x] = 0;
                        // Attempt to repair the index in the section
                        sec.setBlockIndex(x, localY, z, (short)0, false, true);
                    }
                }
            }
        }
        for (int i = 0; i < NUM_SECTIONS; i++) {
            System.arraycopy(sections[i].getLightData(), 0, outLightData, i * ChunkSection.SECTION_VOLUME, ChunkSection.SECTION_VOLUME);
        }
        return new DataSnapshot(position, outBlockData, outLightData);
    }
}
