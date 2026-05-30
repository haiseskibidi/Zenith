package com.za.zenith.world.lighting;

import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkPos;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;

public class LightEngine {
    private final World world;
    
    private static final int QUEUE_SIZE = 1024 * 1024;
    private static final int QUEUE_MASK = QUEUE_SIZE - 1;
    
    private final ConcurrentLinkedQueue<BlockPos> updateQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public void enqueueLightUpdate(BlockPos pos) {
        // Queue Guard: prevent RAM explosion during ultra-fast terrain generation
        if (updateQueue.size() > 16384) {
            updateQueue.clear();
        }
        updateQueue.offer(pos);
        if (isProcessing.compareAndSet(false, true)) {
            submitProcessTask();
        }
    }

    private void submitProcessTask() {
        world.getLightExecutor().submit(new com.za.zenith.utils.PriorityExecutorService.PrioritizedRunnable() {
            @Override
            public int getPriority() { return 0; }

            @Override
            public void run() {
                processQueue();
            }
        });
    }

    private void processQueue() {
        try {
            List<BlockPos> batch = new ArrayList<>();
            BlockPos p;
            int count = 0;
            while ((p = updateQueue.poll()) != null && count < 256) {
                batch.add(p);
                count++;
            }
            
            if (!batch.isEmpty()) {
                onBlocksChanged(batch);
            }
        } finally {
            isProcessing.set(false);
            if (!updateQueue.isEmpty()) {
                if (isProcessing.compareAndSet(false, true)) {
                    submitProcessTask();
                }
            }
        }
    }

    public void onBlocksChanged(List<BlockPos> positions) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        
        // 1. Prepare Block Light
        for (BlockPos pos : positions) {
            prepareBlockLightUpdate(pos, ctx);
        }
        processLightRemoval(false, ctx);
        processLightFill(false, ctx);
        
        // 2. Prepare Sunlight
        for (BlockPos pos : positions) {
            prepareSunlightUpdate(pos, ctx);
        }
        processLightRemoval(true, ctx);
        processLightFill(true, ctx);
        
        commit(ctx);
    }

    private void prepareBlockLightUpdate(BlockPos pos, LightContext ctx) {
        int oldLevel = getBlockLight(pos.x(), pos.y(), pos.z(), ctx);
        int newLevel = calculateBlockLightSource(pos, ctx);

        if (newLevel > oldLevel) {
            ctx.blockChanges.put(pack(pos.x(), pos.y(), pos.z(), 0), (byte)newLevel);
            ctx.enqueueFill(pack(pos.x(), pos.y(), pos.z(), newLevel));
        } else if (newLevel < oldLevel) {
            ctx.blockChanges.put(pack(pos.x(), pos.y(), pos.z(), 0), (byte)0);
            ctx.enqueueRemoval(pack(pos.x(), pos.y(), pos.z(), oldLevel));
        }
    }

    private void prepareSunlightUpdate(BlockPos pos, LightContext ctx) {
        int x = pos.x();
        int z = pos.z();
        
        int ray = 15;
        byte[] newColumnLights = new byte[Chunk.CHUNK_HEIGHT];
        
        for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            Block b = world.getBlock(x, y, z);
            int opacity = getSunOpacity(b);
            
            if (ray == 15 && opacity == 0) {}
            else ray = Math.max(0, ray - Math.max(1, opacity));
            
            newColumnLights[y] = (byte) ray;
            int old = getSunlight(x, y, z, ctx);
            
            if (old != ray) {
                ctx.sunChanges.put(pack(x, y, z, 0), (byte)ray);
                if (ray > old) {
                    ctx.enqueueFill(pack(x, y, z, ray));
                } else {
                    ctx.enqueueRemoval(pack(x, y, z, old));
                    if (ray > 0) ctx.enqueueFill(pack(x, y, z, ray));
                }
            }
        }

        for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
            int currentVal = newColumnLights[y];
            if (currentVal < 15) {
                for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                    if (dir == com.za.zenith.utils.Direction.UP || dir == com.za.zenith.utils.Direction.DOWN) continue;
                    
                    int nx = x + dir.getDx();
                    int ny = y;
                    int nz = z + dir.getDz();
                    
                    int nl = getSunlight(nx, ny, nz, ctx);
                    if (nl > currentVal + 1) {
                        ctx.enqueueFill(pack(nx, ny, nz, nl));
                    }
                }
            }
        }
    }

    private static class PrimitiveLongByteMap {
        private static final int INITIAL_CAPACITY = 2048;
        private long[] keys = new long[INITIAL_CAPACITY];
        private byte[] values = new byte[INITIAL_CAPACITY];
        private boolean[] occupied = new boolean[INITIAL_CAPACITY];
        private int size = 0;
        private int mask = INITIAL_CAPACITY - 1;

        public void clear() {
            java.util.Arrays.fill(occupied, false);
            size = 0;
        }

        public void put(long key, byte value) {
            if (size >= keys.length * 0.7f) rehash();
            int h = hash(key) & mask;
            while (occupied[h] && keys[h] != key) {
                h = (h + 1) & mask;
            }
            if (!occupied[h]) {
                occupied[h] = true;
                keys[h] = key;
                size++;
            }
            values[h] = value;
        }

        public byte get(long key) {
            int h = hash(key) & mask;
            while (occupied[h]) {
                if (keys[h] == key) return values[h];
                h = (h + 1) & mask;
            }
            return -1;
        }

        public boolean isEmpty() { return size == 0; }
        public int size() { return size; }

        private int hash(long x) {
            x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
            x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
            x = x ^ (x >>> 33);
            return (int) x;
        }

        private void rehash() {
            long[] oldKeys = keys;
            byte[] oldValues = values;
            boolean[] oldOccupied = occupied;
            
            int newCap = oldKeys.length * 2;
            keys = new long[newCap];
            values = new byte[newCap];
            occupied = new boolean[newCap];
            mask = newCap - 1;
            size = 0;
            
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldOccupied[i]) put(oldKeys[i], oldValues[i]);
            }
        }
        
        public void forEach(java.util.function.BiConsumer<Long, Byte> action) {
            for (int i = 0; i < keys.length; i++) {
                if (occupied[i]) action.accept(keys[i], values[i]);
            }
        }

        public void forEachPrimitive(PrimitiveBiConsumer action) {
            for (int i = 0; i < keys.length; i++) {
                if (occupied[i]) action.accept(keys[i], values[i]);
            }
        }
    }

    private interface PrimitiveBiConsumer {
        void accept(long key, byte value);
    }

    private static class LightContext {
        final long[] fillQueue = new long[QUEUE_SIZE];
        final long[] removalQueue = new long[QUEUE_SIZE];
        int fillHead, fillTail;
        int removalHead, removalTail;
        
        final PrimitiveLongByteMap sunChanges = new PrimitiveLongByteMap();
        final PrimitiveLongByteMap blockChanges = new PrimitiveLongByteMap();

        void clear() {
            fillHead = fillTail = 0;
            removalHead = removalTail = 0;
            sunChanges.clear();
            blockChanges.clear();
        }

        void enqueueFill(long p) {
            fillQueue[fillTail] = p;
            fillTail = (fillTail + 1) & QUEUE_MASK;
        }

        long dequeueFill() {
            long p = fillQueue[fillHead];
            fillHead = (fillHead + 1) & QUEUE_MASK;
            return p;
        }

        void enqueueRemoval(long p) {
            removalQueue[removalTail] = p;
            removalTail = (removalTail + 1) & QUEUE_MASK;
        }

        long dequeueRemoval() {
            long p = removalQueue[removalHead];
            removalHead = (removalHead + 1) & QUEUE_MASK;
            return p;
        }
    }

    private final ThreadLocal<LightContext> threadContext = ThreadLocal.withInitial(LightContext::new);

    public LightEngine(World world) {
        this.world = world;
    }

    private long pack(int x, int y, int z, int val) {
        return ((long) (x & 0x1FFFFFF) << 39) |
               ((long) (y & 0x3FF) << 29) |
               ((long) (z & 0x1FFFFFF) << 4) |
               (long) (val & 0xF);
    }

    private int unpackX(long p) {
        int x = (int) (p >> 39);
        if ((x & 0x1000000) != 0) x |= 0xFE000000;
        return x;
    }

    private int unpackY(long p) {
        return (int) ((p >> 29) & 0x3FF);
    }

    private int unpackZ(long p) {
        int z = (int) ((p >> 4) & 0x1FFFFFF);
        if ((z & 0x1000000) != 0) z |= 0xFE000000;
        return z;
    }

    private int unpackVal(long p) {
        return (int) (p & 0xF);
    }

    public void onBlockChanged(BlockPos pos) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        updateBlockLightInternal(pos, ctx);
        updateSunlightInternal(pos, ctx);
        commit(ctx);
    }

    public void updateBlockLight(BlockPos pos) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        updateBlockLightInternal(pos, ctx);
        commit(ctx);
    }

    private void updateBlockLightInternal(BlockPos pos, LightContext ctx) {
        int oldLevel = getBlockLight(pos.x(), pos.y(), pos.z(), ctx);
        int newLevel = calculateBlockLightSource(pos, ctx);

        if (newLevel > oldLevel) {
            ctx.blockChanges.put(pack(pos.x(), pos.y(), pos.z(), 0), (byte)newLevel);
            ctx.enqueueFill(pack(pos.x(), pos.y(), pos.z(), newLevel));
        } else if (newLevel < oldLevel) {
            ctx.blockChanges.put(pack(pos.x(), pos.y(), pos.z(), 0), (byte)0);
            ctx.enqueueRemoval(pack(pos.x(), pos.y(), pos.z(), oldLevel));
        }

        processLightRemoval(false, ctx);
        processLightFill(false, ctx);
    }

    private int calculateBlockLightSource(BlockPos pos, LightContext ctx) {
        Block block = world.getBlock(pos);
        com.za.zenith.world.blocks.BlockDefinition def = BlockRegistry.getBlock(block.getType());
        if (def == null) return 0;
        int emission = def.getEmission();
        
        if (def.getIdentifier().toString().contains("lamp")) {
            com.za.zenith.world.blocks.entity.BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof com.za.zenith.world.blocks.entity.LampBlockEntity) {
                return world.getBlockLight(pos); 
            }
        }
        
        if (emission > 0) return emission;

        int maxNeighbor = 0;
        for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
            maxNeighbor = Math.max(maxNeighbor, getBlockLight(pos.x() + dir.getDx(), pos.y() + dir.getDy(), pos.z() + dir.getDz(), ctx) - 1);
        }
        return Math.max(0, maxNeighbor);
    }

    public void updateSunlight(BlockPos pos) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        updateSunlightInternal(pos, ctx);
        commit(ctx);
    }

    private void updateSunlightInternal(BlockPos pos, LightContext ctx) {
        int x = pos.x();
        int z = pos.z();
        
        // 1. Vertical Phase: Establish the "True Ray" from sky to bedrock
        // This clears any phantom lights in the current column before considering neighbors.
        int ray = 15;
        byte[] newColumnLights = new byte[Chunk.CHUNK_HEIGHT];
        
        for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            Block b = world.getBlock(x, y, z);
            int opacity = getSunOpacity(b);
            
            // The "Sky Ray" remains 15 if passing through pure AIR.
            // Otherwise it starts fading or gets blocked.
            if (ray == 15 && opacity == 0) {
                // remains 15
            } else {
                ray = Math.max(0, ray - Math.max(1, opacity));
            }
            
            newColumnLights[y] = (byte) ray;
            int old = getSunlight(x, y, z, ctx);
            
            if (old != ray) {
                ctx.sunChanges.put(pack(x, y, z, 0), (byte)ray);
                if (ray > old) {
                    ctx.enqueueFill(pack(x, y, z, ray));
                } else {
                    ctx.enqueueRemoval(pack(x, y, z, old));
                    // Re-fill with the new ray value if it's still > 0
                    if (ray > 0) ctx.enqueueFill(pack(x, y, z, ray));
                }
            }
        }

        // 2. Horizontal Sync: Check neighbors for horizontal light bleed
        // into our potentially dark column.
        for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
            int currentVal = newColumnLights[y];
            if (currentVal < 15) {
                for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                    if (dir == com.za.zenith.utils.Direction.UP || dir == com.za.zenith.utils.Direction.DOWN) continue;
                    
                    int nx = x + dir.getDx();
                    int ny = y;
                    int nz = z + dir.getDz();
                    
                    int nl = getSunlight(nx, ny, nz, ctx);
                    if (nl > currentVal + 1) {
                        ctx.enqueueFill(pack(nx, ny, nz, nl));
                    }
                }
            }
        }

        processLightRemoval(true, ctx);
        processLightFill(true, ctx);
    }

    public void onChunkReady(Chunk chunk) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        ChunkPos cp = chunk.getPosition();
        int sx = cp.x() * Chunk.CHUNK_SIZE;
        int sz = cp.z() * Chunk.CHUNK_SIZE;

        for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
            for (int i = 0; i < Chunk.CHUNK_SIZE; i++) {
                ctx.enqueueFill(pack(sx + i, y, sz, getSunlight(sx + i, y, sz, ctx)));
                ctx.enqueueFill(pack(sx + i, y, sz + 15, getSunlight(sx + i, y, sz + 15, ctx)));
                ctx.enqueueFill(pack(sx, y, sz + i, getSunlight(sx, y, sz + i, ctx)));
                ctx.enqueueFill(pack(sx + 15, y, sz + i, getSunlight(sx + 15, y, sz + i, ctx)));
            }
        }
        processLightFill(true, ctx);
        commit(ctx);
    }

    private void processLightFill(boolean sun, LightContext ctx) {
        while (ctx.fillHead != ctx.fillTail) {
            long p = ctx.dequeueFill();
            int x = unpackX(p);
            int y = unpackY(p);
            int z = unpackZ(p);
            int level = unpackVal(p);

            for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();

                if (ny < 0 || ny >= Chunk.CHUNK_HEIGHT) continue;
                
                Chunk nChunk = world.getChunkInternal(ChunkPos.fromBlockPos(nx, nz));
                if (nChunk == null) continue;

                if (isOpaque(nChunk, nx & 15, ny, nz & 15)) continue;

                int neighborLevel = sun ? getSunlight(nx, ny, nz, ctx) : getBlockLight(nx, ny, nz, ctx);
                
                int opacityLoss = 1;
                if (sun) {
                    Block b = nChunk.getBlock(nx & 15, ny, nz & 15);
                    opacityLoss = Math.max(1, getSunOpacity(b));
                }

                int nextLevel = (sun && dir == com.za.zenith.utils.Direction.DOWN && level == 15 && opacityLoss == 1) ? 15 : level - opacityLoss;
                if (nextLevel < 0) nextLevel = 0;

                if (neighborLevel < nextLevel) {
                    if (sun) ctx.sunChanges.put(pack(nx, ny, nz, 0), (byte)nextLevel);
                    else ctx.blockChanges.put(pack(nx, ny, nz, 0), (byte)nextLevel);
                    ctx.enqueueFill(pack(nx, ny, nz, nextLevel));
                }
            }
        }
    }

    private void processLightRemoval(boolean sun, LightContext ctx) {
        while (ctx.removalHead != ctx.removalTail) {
            long p = ctx.dequeueRemoval();
            int x = unpackX(p);
            int y = unpackY(p);
            int z = unpackZ(p);
            int level = unpackVal(p);

            for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();

                if (ny < 0 || ny >= Chunk.CHUNK_HEIGHT) continue;
                
                Chunk nChunk = world.getChunkInternal(ChunkPos.fromBlockPos(nx, nz));
                if (nChunk == null) continue;

                int neighborLevel = sun ? getSunlight(nx, ny, nz, ctx) : getBlockLight(nx, ny, nz, ctx);
                if (neighborLevel == 0) continue;

                int opacityLoss = 1;
                if (sun) {
                    Block b = nChunk.getBlock(nx & 15, ny, nz & 15);
                    opacityLoss = Math.max(1, getSunOpacity(b));
                }

                int expectedLevel = (sun && dir == com.za.zenith.utils.Direction.DOWN && level == 15 && opacityLoss == 1) ? 15 : level - opacityLoss;
                if (expectedLevel < 0) expectedLevel = 0;

                if (neighborLevel <= expectedLevel) {
                    // Sunlight 15 is ONLY protected if it comes from ABOVE.
                    if (sun && neighborLevel == 15 && dir == com.za.zenith.utils.Direction.UP) {
                        ctx.enqueueFill(pack(nx, ny, nz, 15));
                        continue;
                    }

                    if (sun) ctx.sunChanges.put(pack(nx, ny, nz, 0), (byte)0);
                    else ctx.blockChanges.put(pack(nx, ny, nz, 0), (byte)0);
                    ctx.enqueueRemoval(pack(nx, ny, nz, neighborLevel));
                } else {
                    ctx.enqueueFill(pack(nx, ny, nz, neighborLevel));
                }
            }
        }
    }

    private void commit(LightContext ctx) {
        if (ctx.sunChanges.isEmpty() && ctx.blockChanges.isEmpty()) return;

        java.util.Map<Chunk, java.util.List<LightEntry>> sunByChunk = new java.util.HashMap<>();
        java.util.Map<Chunk, java.util.List<LightEntry>> blockByChunk = new java.util.HashMap<>();

        ctx.sunChanges.forEachPrimitive((key, value) -> {
            Chunk c = world.getChunkInternal(unpackX(key) >> 4, unpackZ(key) >> 4);
            if (c != null) sunByChunk.computeIfAbsent(c, k -> new java.util.ArrayList<>()).add(new LightEntry(key, value));
        });
        
        ctx.blockChanges.forEachPrimitive((key, value) -> {
            Chunk c = world.getChunkInternal(unpackX(key) >> 4, unpackZ(key) >> 4);
            if (c != null) blockByChunk.computeIfAbsent(c, k -> new java.util.ArrayList<>()).add(new LightEntry(key, value));
        });

        java.util.Set<Chunk> affected = new java.util.HashSet<>(sunByChunk.keySet());
        affected.addAll(blockByChunk.keySet());
        java.util.Set<Chunk> chunksToUpdate = new java.util.HashSet<>();

        for (Chunk chunk : affected) {
            synchronized (chunk) {
                java.util.List<LightEntry> suns = sunByChunk.get(chunk);
                if (suns != null) {
                    for (LightEntry e : suns) {
                        long p = e.key;
                        int lx = unpackX(p) & 15;
                        int lz = unpackZ(p) & 15;
                        if (chunk.setSunlight(lx, unpackY(p), lz, e.value)) {
                            chunksToUpdate.add(chunk);
                            if (lx == 0) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x() - 1, chunk.getPosition().z());
                            if (lx == 15) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x() + 1, chunk.getPosition().z());
                            if (lz == 0) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x(), chunk.getPosition().z() - 1);
                            if (lz == 15) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x(), chunk.getPosition().z() + 1);
                        }
                    }
                }
                java.util.List<LightEntry> blocks = blockByChunk.get(chunk);
                if (blocks != null) {
                    for (LightEntry e : blocks) {
                        long p = e.key;
                        int lx = unpackX(p) & 15;
                        int lz = unpackZ(p) & 15;
                        if (chunk.setBlockLight(lx, unpackY(p), lz, e.value)) {
                            chunksToUpdate.add(chunk);
                            if (lx == 0) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x() - 1, chunk.getPosition().z());
                            if (lx == 15) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x() + 1, chunk.getPosition().z());
                            if (lz == 0) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x(), chunk.getPosition().z() - 1);
                            if (lz == 15) addChunkToUpdate(chunksToUpdate, chunk.getPosition().x(), chunk.getPosition().z() + 1);
                        }
                    }
                }
            }
        }
        
        for (Chunk c : chunksToUpdate) {
            if (c != null) c.setNeedsMeshUpdate(true);
        }
    }

    private void addChunkToUpdate(java.util.Set<Chunk> set, int cx, int cz) {
        Chunk c = world.getChunkInternal(cx, cz);
        if (c != null) set.add(c);
    }

    private record LightEntry(long key, byte value) {}

    private int getSunlight(int x, int y, int z, LightContext ctx) {
        long p = pack(x, y, z, 0);
        byte cached = ctx.sunChanges.get(p);
        if (cached != -1) return cached & 0xF;
        
        Chunk chunk = world.getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getSunlight(x & 15, y, z & 15);
    }

    private int getBlockLight(int x, int y, int z, LightContext ctx) {
        long p = pack(x, y, z, 0);
        byte cached = ctx.blockChanges.get(p);
        if (cached != -1) return cached & 0xF;

        Chunk chunk = world.getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getBlockLight(x & 15, y, z & 15);
    }

    public void generateInitialSunlight(Chunk chunk) {
        LightContext ctx = threadContext.get();
        ctx.clear();
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int light = 15;
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    Block b = chunk.getBlock(x, y, z);
                    int opacity = getSunOpacity(b);
                    if (light == 15 && opacity == 0) {}
                    else light = Math.max(0, light - Math.max(1, opacity));
                    chunk.setSunlight(x, y, z, light);
                    if (light > 0) {
                        int wx = chunk.getPosition().x() * Chunk.CHUNK_SIZE + x;
                        int wz = chunk.getPosition().z() * Chunk.CHUNK_SIZE + z;
                        ctx.enqueueFill(pack(wx, y, wz, light));
                    }
                }
            }
        }
        processLightFill(true, ctx);
        commit(ctx);
    }

    private boolean isOpaque(Chunk chunk, int lx, int ly, int lz) {
        if (chunk == null) return true;
        Block b = chunk.getBlock(lx, ly, lz);
        if (b.getType() == 0) return false;
        com.za.zenith.world.blocks.BlockDefinition def = BlockRegistry.getBlock(b.getType());
        if (def == null) return true;
        return def.isSolid() && !def.isTransparent();
    }

    private int getSunOpacity(Block b) {
        if (b.getType() == 0) return 0;
        com.za.zenith.world.blocks.BlockDefinition def = BlockRegistry.getBlock(b.getType());
        if (def == null) return 15;
        if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_LEAVES)) return 3;
        if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_SOLID) && !def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_TRANSPARENT)) return 15;
        return 0;
    }
}
