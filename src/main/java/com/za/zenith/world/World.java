package com.za.zenith.world;

import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.blocks.entity.BlockEntity;
import com.za.zenith.world.blocks.entity.ITickable;
import com.za.zenith.world.lighting.LightEngine;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkPos;
import com.za.zenith.world.generation.TerrainGenerator;
import com.za.zenith.world.items.ItemStack;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.ScoutEntity;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.Map;

public class World {
    private final Map<Long, Chunk> chunks;
    private final Map<Long, Chunk> stagingChunks = new ConcurrentHashMap<>();
    private final List<Entity> entities = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<com.za.zenith.world.chunks.ChunkPos, List<Entity>> groundEntityMap = new ConcurrentHashMap<>();
    private final Vector3f vPool1 = new Vector3f();
    private final Vector3f vPool2 = new Vector3f();
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<ITickable> tickableBlockEntities;
    private final LightEngine lightEngine;
    private float worldTime; // Stored as float for smooth interpolation

    private final com.za.zenith.utils.PriorityExecutorService chunkGenExecutor = new com.za.zenith.utils.PriorityExecutorService(
        Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 2)),
        r -> {
            Thread t = new Thread(r, "ChunkGenerator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    );
    private final com.za.zenith.utils.PriorityExecutorService lightExecutor = new com.za.zenith.utils.PriorityExecutorService(
        Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 4)),
        r -> {
            Thread t = new Thread(r, "LightGenerator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    );
    private final Map<Long, Future<?>> generatingChunks = new ConcurrentHashMap<>();
    private final Map<Long, Future<?>> lightingChunks = new ConcurrentHashMap<>();
    private final List<java.util.function.Consumer<Chunk>> unloadListeners = new CopyOnWriteArrayList<>();

    public void addUnloadListener(java.util.function.Consumer<Chunk> listener) {
        unloadListeners.add(listener);
    }

    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;

    private static class WorldCache {
        Chunk lastChunk;
        long lastPackedPos = Long.MIN_VALUE;
    }
    private final ThreadLocal<WorldCache> threadCache = ThreadLocal.withInitial(WorldCache::new);

    public Chunk getChunk(int chunkX, int chunkZ) {
        WorldCache cache = threadCache.get();
        long packed = ChunkPos.pack(chunkX, chunkZ);
        if (packed == cache.lastPackedPos) return cache.lastChunk;

        cache.lastChunk = chunks.get(packed);
        cache.lastPackedPos = packed;
        return cache.lastChunk;
    }

    public Chunk getChunk(ChunkPos pos) {
        if (pos == null) return null;
        return getChunk(pos.x(), pos.z());
    }

    public Chunk getChunkInternal(int chunkX, int chunkZ) {
        long packed = ChunkPos.pack(chunkX, chunkZ);
        Chunk c = chunks.get(packed);
        if (c == null) {
            c = stagingChunks.get(packed);
            // Double-check chunks in case it was moved from staging to main map between the two calls
            if (c == null) c = chunks.get(packed);
        }
        return c;
    }

    public Chunk getChunkInternal(ChunkPos pos) {
        if (pos == null) return null;
        return getChunkInternal(pos.x(), pos.z());
    }

    public int getHighestBlock(int x, int z) {
        Chunk chunk = getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getHighestBlock(x & 15, z & 15);
    }

    public int getRawBlockData(int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
        Chunk chunk = getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getRawBlockData(x & 15, y, z & 15);
    }

    public static class BlockDamageInstance {
        private float damage;
        private final Block block;
        private final List<Vector4f> hitHistory;
        private long lastHitTime;

        public BlockDamageInstance(float damage, Block block, List<Vector4f> hitHistory) {
            this.damage = damage;
            this.block = block;
            this.hitHistory = new ArrayList<>(hitHistory);
            this.lastHitTime = System.currentTimeMillis();
        }

        public Block getBlock() { return block; }
        public float getDamage() { return damage; }
        public void setDamage(float damage) { this.damage = damage; }
        public List<Vector4f> getHitHistory() { return hitHistory; }
        public long getLastHitTime() { return lastHitTime; }
        public void resetLastHitTime() { this.lastHitTime = System.currentTimeMillis(); }
    }

    private final Map<Long, BlockDamageInstance> blockDamageMap = new ConcurrentHashMap<>();

    public Map<Long, BlockDamageInstance> getBlockDamageMap() {
        return blockDamageMap;
    }

    public static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0x3FFL) << 28 | ((long) z & 0x3FFFFFFL);
    }

    public static int unpackBlockX(long packed) {
        long x = (packed >> 38) & 0x3FFFFFFL;
        if ((x & 0x2000000L) != 0) x |= 0xFFFFFFFFFF000000L;
        return (int) x;
    }

    public static int unpackBlockY(long packed) {
        return (int) ((packed >> 28) & 0x3FFL);
    }

    public static int unpackBlockZ(long packed) {
        long z = packed & 0x3FFFFFFL;
        if ((z & 0x2000000L) != 0) z |= 0xFFFFFFFFFF000000L;
        return (int) z;
    }
    
    private Player player;
    private final TerrainGenerator terrainGenerator;
    private final com.za.zenith.world.generation.BiomeGenerator biomeGenerator;
    private final long seed;
    private boolean generating = false;

    public World() {
        this.chunks = new ConcurrentHashMap<>();
        this.blockEntities = new ConcurrentHashMap<>();
        this.tickableBlockEntities = java.util.Collections.synchronizedList(new ArrayList<>());
        this.seed = System.currentTimeMillis(); // Random seed each time
        com.za.zenith.utils.Logger.info("Generating new world with seed: %d", seed);
        this.biomeGenerator = new com.za.zenith.world.generation.BiomeGenerator(seed);
        this.terrainGenerator = new TerrainGenerator(seed);
        this.lightEngine = new com.za.zenith.world.lighting.LightEngine(this);
        this.worldTime = WorldSettings.getInstance().initialTime;

        generating = true;
        generateWorld();
        generating = false;
    }

    public World(long seed) {
        this.chunks = new ConcurrentHashMap<>();
        this.blockEntities = new ConcurrentHashMap<>();
        this.tickableBlockEntities = java.util.Collections.synchronizedList(new ArrayList<>());
        this.seed = seed;
        com.za.zenith.utils.Logger.info("Generating new world with seed: %d", seed);
        this.biomeGenerator = new com.za.zenith.world.generation.BiomeGenerator(seed);
        this.terrainGenerator = new TerrainGenerator(seed);
        this.lightEngine = new com.za.zenith.world.lighting.LightEngine(this);
        this.worldTime = WorldSettings.getInstance().initialTime;

        generating = true;
        generateWorld();
        generating = false;
    }

    private void generateWorld() {
        int renderDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().initialRenderDistance; // Generate small radius initially

        // First pass: generate terrain
        for (int chunkX = -renderDistance; chunkX <= renderDistance; chunkX++) {
            for (int chunkZ = -renderDistance; chunkZ <= renderDistance; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                Chunk chunk = new Chunk(pos);

                terrainGenerator.generateTerrain(chunk);
                chunks.put(pos.pack(), chunk);
            }
        }

        // Second pass: generate structures (trees, etc.) and sunlight
        com.za.zenith.utils.Logger.info("Generating structures and sunlight for %d chunks...", (renderDistance * 2 + 1) * (renderDistance * 2 + 1));
        for (int chunkX = -renderDistance; chunkX <= renderDistance; chunkX++) {
            for (int chunkZ = -renderDistance; chunkZ <= renderDistance; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                Chunk chunk = chunks.get(pos.pack());
                if (chunk != null) {
                    terrainGenerator.generateStructures(this, chunk);
                    lightEngine.generateInitialSunlight(chunk);
                    chunk.setReady(true);
                    lightEngine.onChunkReady(chunk);
                    com.za.zenith.world.lighting.LightManager.onChunkLoad(chunk);
                }
            }
        }

        // Spawn initial scouts on surface
        for (int i = 0; i < 15; i++) {
            int rx = (int) ((Math.random() - 0.5) * 160);
            int rz = (int) ((Math.random() - 0.5) * 160);
            int ry = getSurfaceHeight(rx, rz);
            if (ry > 0) {
                spawnEntity(new ScoutEntity(new Vector3f(rx + 0.5f, ry + 1.0f, rz + 0.5f)));
            }
        }

        // Spawn initial resources on surface
        for (int i = 0; i < 40; i++) {
            int rx = (int) ((Math.random() - 0.5) * 200);
            int rz = (int) ((Math.random() - 0.5) * 200);
            int ry = getSurfaceHeight(rx, rz);
            if (ry > 0) {
                com.za.zenith.world.items.Item item = Math.random() > 0.5 ?
                    com.za.zenith.world.items.Items.ROCK :
                    (Math.random() > 0.5 ? com.za.zenith.world.items.Items.STICK : com.za.zenith.world.items.Items.FLINT);
                float randomRot = (float) (Math.random() * Math.PI * 2);
                spawnEntity(new com.za.zenith.entities.ResourceEntity(
                    new Vector3f(rx + 0.5f, ry + 1.0f, rz + 0.5f),
                    new com.za.zenith.world.items.ItemStack(item),
                    randomRot
                ));
            }
        }

        com.za.zenith.utils.Logger.info("World generation completed!");
        this.worldTime = WorldSettings.getInstance().initialTime;
    }

    private final java.util.LinkedHashSet<Long> pendingChunkQueue = new java.util.LinkedHashSet<>();

    public int getRenderDistance() {
        return com.za.zenith.world.generation.GenerationSettings.getInstance().activeRenderDistance;
    }

    private final org.joml.Vector3f lastSortPos = new org.joml.Vector3f(Float.MAX_VALUE);

    private void updateChunks() {
        if (player == null) return;

        int currentChunkX = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
        int currentChunkZ = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);

        int renderDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().activeRenderDistance;
        int unloadDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().unloadDistance;

        if (currentChunkX != lastPlayerChunkX || currentChunkZ != lastPlayerChunkZ) {
            lastPlayerChunkX = currentChunkX;
            lastPlayerChunkZ = currentChunkZ;

            // SPIRAL LOADING: Build a list of chunks in a radial spiral from player
            int x = 0, z = 0, dx = 0, dz = -1;
            int maxChunks = (renderDistance * 2 + 1) * (renderDistance * 2 + 1);
            int addedThisTick = 0;
            for (int i = 0; i < maxChunks; i++) {
                if (-renderDistance <= x && x <= renderDistance && -renderDistance <= z && z <= renderDistance) {
                    long packed = ChunkPos.pack(currentChunkX + x, currentChunkZ + z);
                    if (!chunks.containsKey(packed) && !generatingChunks.containsKey(packed) && !lightingChunks.containsKey(packed)) {
                        pendingChunkQueue.add(packed);
                    }
                }
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx;
                    dx = -dz;
                    dz = temp;
                }
                x += dx;
                z += dz;
            }

            // Unload chunks outside unloadDistance and cancel their generation tasks
            chunks.entrySet().removeIf(entry -> {
                long packed = entry.getKey();
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                boolean remove = Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance;
                if (remove) {
                    Chunk chunk = entry.getValue();
                    com.za.zenith.world.lighting.LightManager.onChunkUnload(chunk);
                    
                    // Clear damage info for this chunk using packed coordinates
                    blockDamageMap.keySet().removeIf(packedPos -> {
                        int bx = unpackBlockX(packedPos);
                        int bz = unpackBlockZ(packedPos);
                        return (bx >> 4) == cx && (bz >> 4) == cz;
                    });

                    // Clear block entities for this chunk
                    blockEntities.keySet().removeIf(pos -> {
                        if ((pos.x() >> 4) == cx && (pos.z() >> 4) == cz) {
                            BlockEntity be = blockEntities.get(pos);
                            if (be != null) {
                                be.setRemoved();
                                if (be instanceof ITickable) tickableBlockEntities.remove(be);
                            }
                            return true;
                        }
                        return false;
                    });

                    // CRITICAL FIX: Properly remove the entire chunk from groundEntityMap
                    // This prevents thousands of "zombie" entity references from staying in memory
                    groundEntityMap.remove(new com.za.zenith.world.chunks.ChunkPos(cx, cz));

                    for (java.util.function.Consumer<Chunk> listener : unloadListeners) {
                        listener.accept(chunk);
                    }
                }
                return remove;
            });

            generatingChunks.entrySet().removeIf(entry -> {
                long packed = entry.getKey();
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                if (Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance) {
                    entry.getValue().cancel(true);
                    return true;
                }
                return false;
            });

            lightingChunks.entrySet().removeIf(entry -> {
                long packed = entry.getKey();
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                if (Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance) {
                    entry.getValue().cancel(true);
                    return true;
                }
                return false;
            });

            stagingChunks.keySet().removeIf(packed -> {
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                return Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance;
            });

            groundEntityMap.keySet().removeIf(pos -> {
                return Math.abs(pos.x() - currentChunkX) > unloadDistance || Math.abs(pos.z() - currentChunkZ) > unloadDistance;
            });

            pendingChunkQueue.removeIf(packed -> {
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                return Math.abs(cx - currentChunkX) > renderDistance || Math.abs(cz - currentChunkZ) > renderDistance;
            });
        }

        if (!pendingChunkQueue.isEmpty()) {
            // Lazy sorting: only if player moved > 2.0 units
            if (player.getPosition().distanceSquared(lastSortPos) > 4.0f) {
                List<Long> sortedPending = new ArrayList<>(pendingChunkQueue);
                sortedPending.sort((p1, p2) -> {
                    int x1 = ChunkPos.unpackX(p1), z1 = ChunkPos.unpackZ(p1);
                    int x2 = ChunkPos.unpackX(p2), z2 = ChunkPos.unpackZ(p2);
                    int d1 = (x1 - currentChunkX) * (x1 - currentChunkX) + (z1 - currentChunkZ) * (z1 - currentChunkZ);
                    int d2 = (x2 - currentChunkX) * (x2 - currentChunkX) + (z2 - currentChunkZ) * (z2 - currentChunkZ);
                    return Integer.compare(d1, d2);
                });
                pendingChunkQueue.clear();
                // Limit queue size to prevent RAM explosion during ultra-fast flight
                for (int i = 0; i < Math.min(sortedPending.size(), 512); i++) {
                    pendingChunkQueue.add(sortedPending.get(i));
                }
                lastSortPos.set(player.getPosition());
            }

            int submittedThisTick = 0;
            // Backpressure for generation (limit discovery to 4 per tick to avoid boundary spikes)
            int canSubmitGen = Math.min(4, Math.max(0, (chunkGenExecutor.getCorePoolSize() * 2) - generatingChunks.size()));
            
            java.util.Iterator<Long> it = pendingChunkQueue.iterator();
            while (it.hasNext() && submittedThisTick < canSubmitGen) {
                long packedPos = it.next();
                it.remove();
                
                if (!chunks.containsKey(packedPos) && !generatingChunks.containsKey(packedPos) && !lightingChunks.containsKey(packedPos)) {
                    int cx = ChunkPos.unpackX(packedPos);
                    int cz = ChunkPos.unpackZ(packedPos);
                    
                    Future<?> future = chunkGenExecutor.submit(new com.za.zenith.utils.PriorityExecutorService.PrioritizedRunnable() {
                        @Override public int getPriority() { 
                            if (player == null) return 0;
                            int px = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
                            int pz = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);
                            return (cx - px)*(cx - px) + (cz - pz)*(cz - pz);
                        }

                        @Override public void run() {
                            try {
                                Chunk chunk = stagingChunks.get(packedPos);
                                if (chunk == null) chunk = new Chunk(new ChunkPos(cx, cz));
                                terrainGenerator.generateTerrain(chunk);
                                stagingChunks.put(packedPos, chunk);
                                
                                // Chain to lighting
                                submitLightingTask(packedPos, chunk);
                            } catch (Exception e) {
                                generatingChunks.remove(packedPos);
                            }
                        }
                    });
                    generatingChunks.put(packedPos, future);
                    submittedThisTick++;
                }
            }
        }
    }

    private void submitLightingTask(long packedPos, Chunk chunk) {
        int cx = chunk.getPosition().x();
        int cz = chunk.getPosition().z();
        
        Future<?> future = lightExecutor.submit(new com.za.zenith.utils.PriorityExecutorService.PrioritizedRunnable() {
            @Override public int getPriority() {
                if (player == null) return 0;
                int px = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
                int pz = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);
                return (cx - px)*(cx - px) + (cz - pz)*(cz - pz);
            }

            @Override public void run() {
                try {
                    terrainGenerator.generateStructures(World.this, chunk);
                    lightEngine.generateInitialSunlight(chunk);
                    chunk.setReady(true);
                    chunk.setNeedsMeshUpdate(true);
                    lightEngine.onChunkReady(chunk);
                    com.za.zenith.world.lighting.LightManager.onChunkLoad(chunk);
                    chunks.put(packedPos, chunk);
                } catch (Exception e) {
                    com.za.zenith.utils.Logger.error("Lighting error: " + e.getMessage());
                } finally {
                    stagingChunks.remove(packedPos);
                    generatingChunks.remove(packedPos);
                    lightingChunks.remove(packedPos);
                }
            }
        });
        lightingChunks.put(packedPos, future);
    }

    public int getFastSurfaceColor(int x, int z) {
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk == null || !chunk.isReady()) return 0xFF000000;

        int lx = x & 15;
        int lz = z & 15;

        int y = chunk.getHighestBlock(lx, lz);
        if (y <= 0) return 0xFF000000;

        int data = chunk.getRawBlockData(lx, y, lz);
        int type = data >> 8;
        if (type == 0) return 0xFF000000; // Air

        if (!com.za.zenith.engine.graphics.ui.renderers.MinimapRegistry.isSolid(type) && type != com.za.zenith.world.blocks.Blocks.WATER.getId()) return 0xFF000000;

        int color = com.za.zenith.engine.graphics.ui.renderers.MinimapRegistry.getColor(type);

        // Apply height-based shading for volume effect
        float brightness = 0.7f + (y / (float)Chunk.CHUNK_HEIGHT) * 0.6f;
        int r = (int) ((color & 0xFF) * brightness);
        int g = (int) (((color >> 8) & 0xFF) * brightness);
        int b = (int) (((color >> 16) & 0xFF) * brightness);

        // Store Y height in Alpha channel for toon-shading outlines in shader
        return (y << 24) | (Math.min(255, b) << 16) | (Math.min(255, g) << 8) | Math.min(255, r);
    }

    private int getSurfaceHeight(int x, int z) {
        for (int y = 255; y > 0; y--) {
            Block b = getBlock(x, y, z);
            if (!b.isAir() && com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType()).isSolid()) {
                return y;
            }
        }
        return -1;
    }

    public void update(float deltaTime) {
        updateChunks();

        // Advance time
        worldTime += deltaTime * WorldSettings.getInstance().dayCycleSpeed * 20.0f; 
        if (worldTime >= WorldSettings.getInstance().dayLength) {
            worldTime -= WorldSettings.getInstance().dayLength;
        }

        // Cache player values for zero-allocation access
        float px = 0, py = 0, pz = 0;
        boolean inventoryFull = false;
        if (player != null) {
            px = player.getPosition().x;
            py = player.getPosition().y + player.getHeight() * 0.5f;
            pz = player.getPosition().z;
            inventoryFull = player.getInventory().isFull();
        }

        // Update all entities
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);

            if (entity.isRemoved()) {
                if (entity instanceof com.za.zenith.entities.ItemEntity || entity instanceof com.za.zenith.entities.ResourceEntity) {
                    com.za.zenith.world.chunks.ChunkPos cp = com.za.zenith.world.chunks.ChunkPos.fromBlockPos((int)entity.getPosition().x, (int)entity.getPosition().z);
                    List<Entity> list = groundEntityMap.get(cp);
                    if (list != null) list.remove(entity);
                }
                entities.remove(i);
                continue;
            }

            // SIMULATION DISTANCE GUARD: Remove distant entities
            if (player != null && entity != player) {
                float edx = px - entity.getPosition().x;
                float edz = pz - entity.getPosition().z;
                float distSq = edx * edx + edz * edz;
                
                // Aggressive culling for static resources: 128m radius (8 chunks)
                if (entity instanceof com.za.zenith.entities.ResourceEntity) {
                    if (distSq > 128 * 128) {
                        entity.setRemoved();
                        continue;
                    }
                } else {
                    // Standard culling for dynamic entities: 320m radius
                    if (distSq > 320 * 320) {
                        entity.setRemoved();
                        continue;
                    }
                }
            }

            entity.update(deltaTime, this);

            // Item Pickup logic - Optimized zero-allocation
            if (player != null && entity instanceof com.za.zenith.entities.ItemEntity itemEntity) {
                if (itemEntity.canBePickedUp()) {
                    Vector3f itemPos = itemEntity.getPosition();
                    float dx = px - itemPos.x;
                    float dy = py - itemPos.y;
                    float dz = pz - itemPos.z;
                    float distSq = dx*dx + dy*dy + dz*dz;

                    float pickupRadius = com.za.zenith.world.physics.PhysicsSettings.getInstance().itemPickupRadius;
                    boolean isMagnetic = itemEntity.isBeingAttracted();
                    float effectiveRadius = isMagnetic ? pickupRadius * 1.5f : pickupRadius;

                    if (distSq < effectiveRadius * effectiveRadius || player.getBoundingBox().intersects(itemEntity.getBoundingBox())) {
                        if (itemEntity.isRemoved()) continue; 

                        if (inventoryFull) {
                             com.za.zenith.engine.graphics.ui.NotificationTriggers.getInstance().onInventoryFull();
                        } else if (player.getInventory().addItem(itemEntity.getStack(), true)) {
                            itemEntity.setRemoved();
                            com.za.zenith.utils.Logger.info("Picked up item: %s", itemEntity.getStack().getItem().getName());
                            inventoryFull = player.getInventory().isFull();
                            
                            // Remove from spatial map
                            com.za.zenith.world.chunks.ChunkPos cp = com.za.zenith.world.chunks.ChunkPos.fromBlockPos((int)itemEntity.getPosition().x, (int)itemEntity.getPosition().z);
                            List<Entity> list = groundEntityMap.get(cp);
                            if (list != null) list.remove(itemEntity);
                            
                            continue;
                        } else {
                            inventoryFull = true;
                        }
                    }
                }
            }

            // Remove dead entities (if they are LivingEntity)
            if (entity instanceof com.za.zenith.entities.LivingEntity living) {
                if (living.isDead()) {
                    entities.remove(i);
                }
            }
        }

        // Update tickable block entities
        for (int i = tickableBlockEntities.size() - 1; i >= 0; i--) {
            ITickable tickable = tickableBlockEntities.get(i);
            if (tickable instanceof BlockEntity be && be.isRemoved()) {
                tickableBlockEntities.remove(i);
                continue;
            }
            if (tickable.shouldTick()) {
                tickable.update(deltaTime);
            }
        }

        if (player != null) {
            player.update(deltaTime, this);
        }

        // Heal blocks over time and fade scars
        if (!blockDamageMap.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            for (java.util.Map.Entry<Long, BlockDamageInstance> entry : blockDamageMap.entrySet()) {
                long packed = entry.getKey();
                BlockDamageInstance info = entry.getValue();

                // --- HEALING DELAY ---
                // Do not start healing if block was hit in the last 5 seconds
                if (currentTime - info.getLastHitTime() < 5000) continue;

                float damage = info.getDamage();
                int bx = unpackBlockX(packed);
                int by = unpackBlockY(packed);
                int bz = unpackBlockZ(packed);
                int blockType = getRawBlockData(bx, by, bz) >> 8;
                com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(blockType);

                if (def.getHealingSpeed() > 0) {
                    float maxHealth = def.getHardness() * 10.0f;
                    float healAmount = def.getHealingSpeed() * maxHealth * deltaTime;
                    float newDamage = damage - healAmount;

                    if (newDamage <= 0) {
                        blockDamageMap.remove(packed);
                    } else {
                        info.setDamage(newDamage);

                        // --- SMOOTH SCAR FADING ---
                        List<Vector4f> history = info.getHitHistory();
                        if (!history.isEmpty()) {
                            // Target total intensity across all scars based on health
                            float targetTotalIntensity = (newDamage / maxHealth) * 16.0f;

                            float currentTotalIntensity = 0;
                            for (Vector4f hit : history) currentTotalIntensity += hit.w;

                            if (currentTotalIntensity > targetTotalIntensity) {
                                float toRemove = currentTotalIntensity - targetTotalIntensity;
                                while (toRemove > 0 && !history.isEmpty()) {
                                    Vector4f oldest = history.get(0);
                                    if (oldest.w <= toRemove) {
                                        toRemove -= oldest.w;
                                        history.remove(0);
                                    } else {
                                        oldest.w -= toRemove;
                                        toRemove = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void registerTickable(ITickable tickable) {
        if (!tickableBlockEntities.contains(tickable)) {
            tickableBlockEntities.add(tickable);
        }
    }

    public void unregisterTickable(ITickable tickable) {
        tickableBlockEntities.remove(tickable);
    }

    public float getBlockDamage(BlockPos pos) {
        return getBlockDamage(pos.x(), pos.y(), pos.z());
    }

    public float getBlockDamage(int x, int y, int z) {
        BlockDamageInstance info = blockDamageMap.get(packBlockPos(x, y, z));
        return (info != null) ? info.getDamage() : 0.0f;
    }

    public List<Vector4f> getBlockHitHistory(BlockPos pos) {
        BlockDamageInstance info = blockDamageMap.get(packBlockPos(pos.x(), pos.y(), pos.z()));
        return (info != null) ? info.getHitHistory() : new ArrayList<>();
    }

    public void setBlockDamage(BlockPos pos, float damage) {
        setBlockDamage(pos, damage, new ArrayList<>());
    }

    public void setBlockDamage(BlockPos pos, float damage, List<Vector4f> history) {
        long packed = packBlockPos(pos.x(), pos.y(), pos.z());
        if (damage <= 0.0f) {
            blockDamageMap.remove(packed);
        } else {
            BlockDamageInstance info = blockDamageMap.get(packed);
            if (info != null) {
                info.setDamage(damage);
                info.resetLastHitTime(); // Refresh the delay on every hit

                // Update history smoothly: only add new ones that aren't already there
                List<Vector4f> targetHistory = info.getHitHistory();
                if (history.size() > targetHistory.size()) {
                    for (int i = targetHistory.size(); i < history.size(); i++) {
                        targetHistory.add(new Vector4f(history.get(i)));
                    }
                }

                // Cap at 16
                while (targetHistory.size() > 16) {
                    targetHistory.remove(0);
                }
            } else {
                blockDamageMap.put(packed, new BlockDamageInstance(damage, getBlock(pos).copy(), new ArrayList<>(history)));
            }
        }
    }

    public void spawnEntity(Entity entity) {
        if (entity == null) return;
        entities.add(entity);
        if (entity.isGroundEntity()) {
            com.za.zenith.world.chunks.ChunkPos cp = com.za.zenith.world.chunks.ChunkPos.fromBlockPos((int)entity.getPosition().x, (int)entity.getPosition().z);
            groundEntityMap.computeIfAbsent(cp, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(entity);
        }
    }

    public void updateItemSpatial(com.za.zenith.entities.ItemEntity item, com.za.zenith.world.chunks.ChunkPos oldPos, com.za.zenith.world.chunks.ChunkPos newPos) {
        if (oldPos != null) {
            List<Entity> list = groundEntityMap.get(oldPos);
            if (list != null) list.remove(item);
        }
        if (newPos != null) {
            groundEntityMap.computeIfAbsent(newPos, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(item);
        }
    }

    public List<Entity> getGroundEntitiesInChunk(com.za.zenith.world.chunks.ChunkPos pos) {
        return groundEntityMap.getOrDefault(pos, java.util.Collections.emptyList());
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public com.za.zenith.world.generation.BiomeGenerator getBiomeManager() {
        return biomeGenerator;
    }

    public Player getPlayer() {
        return player;
    }

    public java.util.Map<BlockPos, com.za.zenith.world.blocks.entity.BlockEntity> getBlockEntities() {
        return blockEntities;
    }

    public Block getBlock(BlockPos pos) {
        return getBlock(pos.x(), pos.y(), pos.z());
    }

    public Block getBlock(int x, int y, int z) {
        if (!blockDamageMap.isEmpty()) {
            BlockDamageInstance damageInstance = blockDamageMap.get(packBlockPos(x, y, z));
            if (damageInstance != null) {
                return damageInstance.getBlock();
            }
        }

        long packed = ChunkPos.pack(x >> 4, z >> 4);
        Chunk chunk = chunks.get(packed);
        if (chunk == null) chunk = stagingChunks.get(packed);

        if (chunk == null) {
            return new Block(com.za.zenith.world.blocks.Blocks.AIR.getId());
        }

        return chunk.getBlock(x & 15, y, z & 15);
    }

    public void setBlock(BlockPos pos, Block block) {
        setBlock(pos.x(), pos.y(), pos.z(), block, true);
    }

    public void setBlockQuietly(BlockPos pos, Block block) {
        setBlock(pos.x(), pos.y(), pos.z(), block, false);
    }

    public void setBlockQuietly(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, false);
    }

    public void setBlockDuringGen(int x, int y, int z, Block block) {
        long packed = ChunkPos.pack(x >> 4, z >> 4);
        Chunk chunk = chunks.get(packed);
        if (chunk == null) chunk = stagingChunks.get(packed);
        
        // Neighbor creation: ensure neighbor chunk exists to receive blocks from current generation
        if (chunk == null) {
            chunk = new Chunk(new ChunkPos(x >> 4, z >> 4));
            stagingChunks.put(packed, chunk);
        }

        chunk.setBlock(x & 15, y, z & 15, block);

        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
        if (def != null && def.hasBlockEntity()) {
            BlockPos pos = new BlockPos(x, y, z);
            com.za.zenith.world.blocks.entity.BlockEntity be = def.createBlockEntity(pos);
            if (be != null) {
                be.setWorld(this);
                blockEntities.put(pos, be);
                if (be instanceof ITickable) tickableBlockEntities.add((ITickable) be);
            }
        }
    }

    public boolean isGenerating() {
        return generating;
    }

    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, true);
    }

    public com.za.zenith.utils.PriorityExecutorService getLightExecutor() {
        return lightExecutor;
    }

    public void setBlock(int x, int y, int z, Block block, boolean notifyAndLight) {
        BlockPos pos = new BlockPos(x, y, z);

        // Remove old block entity if it exists
        removeBlockEntity(pos);

        // IMPORTANT: Clear any damage/proxy data at this position immediately
        blockDamageMap.remove(packBlockPos(x, y, z));

        long packed = ChunkPos.pack(x >> 4, z >> 4);
        Chunk chunk = chunks.get(packed);

        if (chunk != null) {
            chunk.setBlock(x & 15, y, z & 15, block);
            chunk.setNeedsMeshUpdate(true);

            // Event-driven lighting registration
            com.za.zenith.world.lighting.LightManager.onBlockChange(this, pos, block.getType());

            // Update lighting asynchronously (skip during world generation for performance)
            if (notifyAndLight && !generating) {
                lightEngine.enqueueLightUpdate(pos);
            }

            // Handle block entities
            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
            // Automatically create block entity if defined
            BlockEntity be = def.createBlockEntity(pos);
            if (be != null) {
                setBlockEntity(be);
            }

            // Notify neighbors if block is on the edge
            int lx = x & 15;
            int lz = z & 15;
            int cx = x >> 4;
            int cz = z >> 4;

            if (lx == 0) notifyChunkUpdate(cx - 1, cz);
            if (lx == Chunk.CHUNK_SIZE - 1) notifyChunkUpdate(cx + 1, cz);
            if (lz == 0) notifyChunkUpdate(cx, cz - 1);
            if (lz == Chunk.CHUNK_SIZE - 1) notifyChunkUpdate(cx, cz + 1);

            // Notify all 6 neighbors about the block change for survival/logic updates
            if (notifyAndLight && !generating) {
                notifyNeighbors(pos);
            }
        }
    }

    /**
     * Notify 6 neighbor blocks about a change at the current position.
     * This triggers survival logic (requiresSupport) and other updates.
     */
    public void notifyNeighbors(BlockPos pos) {
        Block centerBlock = getBlock(pos);
        for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
            BlockPos neighborPos = dir.offset(pos);
            Block neighborBlock = getBlock(neighborPos);

            // Air doesn't handle updates
            if (neighborBlock.isAir()) continue;

            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(neighborBlock.getType());
            // Pass the direction from the neighbor to the CHANGED block
            def.onNeighborChange(this, neighborPos, centerBlock, dir.getOpposite());
        }
    }

    /**
     * Called by the player upon completion of block breaking progress.
     * @return true if the block should be removed.
     */
    public boolean onBlockBreak(BlockPos pos, Player player) {
        Block block = getBlock(pos);
        if (block.isAir()) return true;

        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
        return def.onBlockBreak(this, pos, block, player);
    }

    /**
     * Destroys a block by a player. Calls the onDestroyed hook.
     */
    public void destroyBlock(BlockPos pos, Player player) {
        Block block = getBlock(pos);
        if (block.isAir()) return;

        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
        def.spawnDrops(this, pos, block, player);
        def.onDestroyed(this, pos, block, player);

        com.za.zenith.world.particles.ParticleManager.getInstance().spawnShatter(pos, block);

        setBlock(pos, new Block(Blocks.AIR.getId()));
    }

    public void setBlockEntity(BlockEntity entity) {
        BlockPos pos = entity.getPos();
        removeBlockEntity(pos); // Clean up any existing at this position

        entity.setWorld(this);
        blockEntities.put(pos, entity);
        if (entity instanceof ITickable) {
            tickableBlockEntities.add((ITickable) entity);
        }

        // Trigger mesh update for the chunk
        com.za.zenith.world.chunks.Chunk chunk = getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
        if (chunk != null) {
            chunk.setNeedsMeshUpdate(true);
        }
    }

    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    public void removeBlockEntity(BlockPos pos) {
        BlockEntity entity = blockEntities.remove(pos);
        if (entity != null) {
            entity.setRemoved();
            // Trigger mesh update for the chunk
            com.za.zenith.world.chunks.Chunk chunk = getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
            if (chunk != null) {
                chunk.setNeedsMeshUpdate(true);
            }
        }
    }


    private void notifyChunkUpdate(int cx, int cz) {
        Chunk neighbor = chunks.get(ChunkPos.pack(cx, cz));
        if (neighbor != null) {
            neighbor.setNeedsMeshUpdate(true);
        }
    }

    public Iterable<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    public float getWorldTime() {
        return worldTime;
    }

    public int getSunlight(BlockPos pos) {
        return getSunlight(pos.x(), pos.y(), pos.z());
    }

    public int getSunlight(int x, int y, int z) {
        Chunk chunk = getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getSunlight(x & 15, y, z & 15);
    }

    public LightEngine getLightEngine() {
        return lightEngine;
    }

    public long getSeed() {
        return seed;
    }

    public int getBlockLight(BlockPos pos) {
        return getBlockLight(pos.x(), pos.y(), pos.z());
    }

    public int getBlockLight(int x, int y, int z) {
        Chunk chunk = getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getBlockLight(x & 15, y, z & 15);
    }

    public void setBlockLight(BlockPos pos, int level) {
        setBlockLight(pos.x(), pos.y(), pos.z(), level);
    }

    public void setBlockLight(int x, int y, int z, int level) {
        Chunk chunk = getChunk(x >> 4, z >> 4);
        if (chunk != null) {
            chunk.setBlockLight(x & 15, y, z & 15, level);
        }
    }

    public void setBlock(int x, int y, int z, int blockType) {
        setBlock(x, y, z, new Block(blockType));
    }

    public void spawnItem(ItemStack stack, float x, float y, float z) {
        com.za.zenith.entities.ItemEntity entity = new com.za.zenith.entities.ItemEntity(new Vector3f(x, y, z), stack);
        spawnEntity(entity);
    }

    /**
     * Calculates the noise level at a specific point in the world.
     * Takes into account player noise and working machines (BlockEntities).
     */
    public float getNoiseLevelAt(Vector3f pos) {
        float totalNoise = 0.0f;

        // Player noise
        if (player != null) {
            float dist = pos.distance(player.getPosition());
            float playerNoise = player.getNoiseLevel();
            // Player noise attenuation (radius 32 blocks)
            if (dist < 32.0f) {
                totalNoise = Math.max(totalNoise, playerNoise * (1.0f - dist / 32.0f));
            }
        }

        // Active block entity noise
        for (BlockEntity be : blockEntities.values()) {
            if (be instanceof com.za.zenith.world.blocks.entity.GeneratorBlockEntity generator && generator.isRunning()) {
                float dist = pos.distance(new Vector3f(be.getPos().x() + 0.5f, be.getPos().y() + 0.5f, be.getPos().z() + 0.5f));
                // Generator noise (radius 20 blocks, base noise 0.5)
                if (dist < 20.0f) {
                    float genNoise = 0.5f * (1.0f - dist / 20.0f);
                    totalNoise = Math.max(totalNoise, genNoise);
                }
            }
        }

        return totalNoise;
    }

    public void cleanup() {
        chunkGenExecutor.shutdown();
        try {
            if (!chunkGenExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                chunkGenExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkGenExecutor.shutdownNow();
        }
    }
}
