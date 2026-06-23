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
import java.util.Map;

/**
 * Main facade class representing the Zenith game world.
 * Coordinate translation, block management, and subsystem orchestration.
 * ponytail: refactored to keep size manageable and delegates tasks to specific managers.
 */
public class World {
    private final Map<Long, Chunk> chunks;
    private final Map<Long, Chunk> stagingChunks = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities;
    private final List<ITickable> tickableBlockEntities;
    private final LightEngine lightEngine;
    private float worldTime; // Stored as float for smooth interpolation

    // Subsystems
    private final com.za.zenith.world.weather.CloudSimulationSystem cloudSystem;
    private final WorldDamageManager damageManager;
    private final WorldEntityManager entityManager;
    private final com.za.zenith.world.chunks.WorldChunkManager chunkManager;

    private final com.za.zenith.world.fluid.FluidSimulator fluidSimulator = new com.za.zenith.world.fluid.FluidSimulator(this);
    private Player player;
    private final TerrainGenerator terrainGenerator;
    private final com.za.zenith.world.generation.BiomeGenerator biomeGenerator;
    private final long seed;
    private boolean generating = false;

    private final com.za.zenith.world.weather.WeatherManager weatherManager = new com.za.zenith.world.weather.WeatherManager();

    // Packing Block Pos helper methods
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

    private static class WorldCache {
        Chunk lastChunk;
        long lastPackedPos = Long.MIN_VALUE;
    }
    private final ThreadLocal<WorldCache> threadCache = ThreadLocal.withInitial(WorldCache::new);

    public World() {
        this(System.currentTimeMillis());
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

        // Initialize managers before generation starts
        this.cloudSystem = new com.za.zenith.world.weather.CloudSimulationSystem();
        this.damageManager = new WorldDamageManager(this);
        this.entityManager = new WorldEntityManager(this);
        this.chunkManager = new com.za.zenith.world.chunks.WorldChunkManager(this);

        generating = true;
        generateWorld();
        generating = false;
    }

    private void generateWorld() {
        int renderDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().initialRenderDistance;

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

    // Accessors for subsystems & components
    public Map<Long, Chunk> getChunksMap() { return chunks; }
    public Map<Long, Chunk> getStagingChunks() { return stagingChunks; }
    public TerrainGenerator getTerrainGenerator() { return terrainGenerator; }
    public LightEngine getLightEngine() { return lightEngine; }
    public com.za.zenith.world.weather.CloudSimulationSystem getCloudSystem() { return cloudSystem; }
    public WorldDamageManager getDamageManager() { return damageManager; }
    public WorldEntityManager getEntityManager() { return entityManager; }
    public Map<com.za.zenith.world.chunks.ChunkPos, List<Entity>> getGroundEntityMap() { return entityManager.getGroundEntityMap(); }
    public com.za.zenith.world.chunks.WorldChunkManager getChunkManager() { return chunkManager; }
    public List<ITickable> getTickableBlockEntities() { return tickableBlockEntities; }
    public com.za.zenith.world.weather.WeatherManager getWeatherManager() { return weatherManager; }
    public com.za.zenith.world.generation.BiomeGenerator getBiomeManager() { return biomeGenerator; }
    public java.util.Map<BlockPos, com.za.zenith.world.blocks.entity.BlockEntity> getBlockEntities() { return blockEntities; }
    public Player getPlayer() { return player; }
    public void setPlayer(Player player) { this.player = player; }
    public long getSeed() { return seed; }
    public boolean isGenerating() { return generating; }
    public float getWorldTime() { return worldTime; }

    // API Compatibility Delegates
    public float getWindTime() { return cloudSystem.getWindTime(); }
    public java.util.List<com.za.zenith.world.World.CloudInstance> getActiveClouds() { return cloudSystem.getActiveClouds(); }
    public void registerChunkClouds(Chunk chunk) {}
    public void unregisterChunkClouds(Chunk chunk) {}
    public int getRenderDistance() { return com.za.zenith.world.generation.GenerationSettings.getInstance().activeRenderDistance; }
    public com.za.zenith.utils.PriorityExecutorService getLightExecutor() { return chunkManager.getLightExecutor(); }
    public void addUnloadListener(java.util.function.Consumer<Chunk> listener) { chunkManager.addUnloadListener(listener); }
    public Map<Long, BlockDamageInstance> getBlockDamageMap() { return damageManager.getBlockDamageMap(); }
    public float getBlockDamage(BlockPos pos) { return damageManager.getBlockDamage(pos); }
    public float getBlockDamage(int x, int y, int z) { return damageManager.getBlockDamage(x, y, z); }
    public List<Vector4f> getBlockHitHistory(BlockPos pos) { return damageManager.getBlockHitHistory(pos); }
    public void setBlockDamage(BlockPos pos, float damage) { damageManager.setBlockDamage(pos, damage); }
    public void setBlockDamage(BlockPos pos, float damage, List<Vector4f> history) { damageManager.setBlockDamage(pos, damage, history); }
    public void spawnEntity(Entity entity) { entityManager.spawnEntity(entity); }
    public void spawnItem(ItemStack stack, float x, float y, float z) { entityManager.spawnItem(stack, x, y, z); }
    public void updateItemSpatial(com.za.zenith.entities.ItemEntity item, com.za.zenith.world.chunks.ChunkPos oldPos, com.za.zenith.world.chunks.ChunkPos newPos) { entityManager.updateItemSpatial(item, oldPos, newPos); }
    public List<Entity> getGroundEntitiesInChunk(com.za.zenith.world.chunks.ChunkPos pos) { return entityManager.getGroundEntitiesInChunk(pos); }
    public List<Entity> getEntities() { return entityManager.getEntities(); }

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

    public void update(float deltaTime) {
        chunkManager.update(player);
        weatherManager.update(deltaTime);
        cloudSystem.update(deltaTime, this, player, weatherManager);
        
        fluidSimulator.tick(deltaTime);
        
        entityManager.update(deltaTime, player);

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

        damageManager.update(deltaTime);

        // Day/night cycle progression
        worldTime += deltaTime * WorldSettings.getInstance().dayCycleSpeed * 20.0f; 
        if (worldTime >= WorldSettings.getInstance().dayLength) {
            worldTime -= WorldSettings.getInstance().dayLength;
        }
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
        if (type == 0) return 0xFF000000;

        if (!com.za.zenith.engine.graphics.ui.renderers.MinimapRegistry.isSolid(type) && type != com.za.zenith.world.blocks.Blocks.WATER.getId()) return 0xFF000000;

        int color = com.za.zenith.engine.graphics.ui.renderers.MinimapRegistry.getColor(type);

        float brightness = 0.7f + (y / (float)Chunk.CHUNK_HEIGHT) * 0.6f;
        int r = (int) ((color & 0xFF) * brightness);
        int g = (int) (((color >> 8) & 0xFF) * brightness);
        int b = (int) (((color >> 16) & 0xFF) * brightness);

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

    public void registerTickable(ITickable tickable) {
        if (!tickableBlockEntities.contains(tickable)) {
            tickableBlockEntities.add(tickable);
        }
    }

    public void unregisterTickable(ITickable tickable) {
        tickableBlockEntities.remove(tickable);
    }

    public Vector3f getInterpolatedFluidFlowVector(float px, float py, float pz, int fluidId) {
        return com.za.zenith.world.physics.FluidFlowCalculator.getInterpolatedFluidFlowVector(this, px, py, pz, fluidId);
    }

    public Vector3f getFluidFlowVector(int x, int y, int z, int fluidId, int currentLevel) {
        return com.za.zenith.world.physics.FluidFlowCalculator.getFluidFlowVector(this, x, y, z, fluidId, currentLevel);
    }

    public Block getBlock(BlockPos pos) {
        return getBlock(pos.x(), pos.y(), pos.z());
    }

    public Block getBlock(int x, int y, int z) {
        if (!damageManager.getBlockDamageMap().isEmpty()) {
            BlockDamageInstance damageInstance = damageManager.getBlockDamageMap().get(packBlockPos(x, y, z));
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
                chunk.addLocalBlockEntity(pos);
            }
        }
    }

    public void setBlock(int x, int y, int z, Block block) {
        setBlock(x, y, z, block, true);
    }

    public void setBlock(int x, int y, int z, Block block, boolean notifyAndLight) {
        long packed = ChunkPos.pack(x >> 4, z >> 4);
        Chunk chunk = chunks.get(packed);

        if (chunk != null) {
            Block oldBlock = chunk.getBlock(x & 15, y, z & 15);
            if (oldBlock.getType() == block.getType()) {
                if (oldBlock.getMetadata() == block.getMetadata()) return;

                chunk.setBlock(x & 15, y, z & 15, block);
                chunk.setNeedsMeshUpdate(true);

                com.za.zenith.world.blocks.BlockDefinition newDef = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                if (newDef.isFluid() && !generating) {
                    scheduleFluidTick(x, y, z);
                    for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                        int nx = x + dir.getDx();
                        int ny = y + dir.getDy();
                        int nz = z + dir.getDz();
                        Block neighbor = getBlock(nx, ny, nz);
                        if (com.za.zenith.world.blocks.BlockRegistry.getBlock(neighbor.getType()).isFluid()) {
                            scheduleFluidTick(nx, ny, nz);
                        }
                    }
                }
                return;
            }
        }

        BlockPos pos = new BlockPos(x, y, z);

        removeBlockEntity(pos);

        long packedPos = packBlockPos(x, y, z);
        if (damageManager.getBlockDamageMap().remove(packedPos) != null) {
            if (chunk != null) {
                chunk.removeLocalBlockDamage(packedPos);
            }
        }

        if (chunk != null) {
            chunk.setBlock(x & 15, y, z & 15, block);
            chunk.setNeedsMeshUpdate(true);

            if (!generating) {
                com.za.zenith.world.blocks.BlockDefinition newDef = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                if (newDef.isFluid()) {
                    scheduleFluidTick(x, y, z);
                }
                newDef.onBlockAdded(this, pos);
                for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
                    BlockPos neighborPos = dir.offset(pos);
                    Block neighborBlock = getBlock(neighborPos);
                    if (com.za.zenith.world.blocks.BlockRegistry.getBlock(neighborBlock.getType()).isFluid()) {
                        scheduleFluidTick(neighborPos.x(), neighborPos.y(), neighborPos.z());
                    }
                }
            }

            if (!generating) {
                int cx = x >> 4;
                int cz = z >> 4;
                List<Entity> list = entityManager.getGroundEntityMap().get(new com.za.zenith.world.chunks.ChunkPos(cx, cz));
                if (list != null) {
                    Entity[] itemsToWake = null;
                    synchronized (list) {
                        if (!list.isEmpty()) {
                            itemsToWake = list.toArray(new Entity[0]);
                        }
                    }
                    if (itemsToWake != null) {
                        for (Entity e : itemsToWake) {
                            if (e instanceof com.za.zenith.entities.ItemEntity item) {
                                item.wakeUp();
                            }
                        }
                    }
                }
            }

            com.za.zenith.world.lighting.LightManager.onBlockChange(this, pos, block.getType());

            if (notifyAndLight && !generating) {
                lightEngine.enqueueLightUpdate(pos, false);
            }

            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
            BlockEntity be = def.createBlockEntity(pos);
            if (be != null) {
                setBlockEntity(be);
            }

            int lx = x & 15;
            int lz = z & 15;
            int cx = x >> 4;
            int cz = z >> 4;

            if (lx == 0) notifyChunkUpdate(cx - 1, cz);
            if (lx == Chunk.CHUNK_SIZE - 1) notifyChunkUpdate(cx + 1, cz);
            if (lz == 0) notifyChunkUpdate(cx, cz - 1);
            if (lz == Chunk.CHUNK_SIZE - 1) notifyChunkUpdate(cx, cz + 1);

            if (notifyAndLight && !generating) {
                notifyNeighbors(pos);
            }
        }
    }

    public void notifyNeighbors(BlockPos pos) {
        Block centerBlock = getBlock(pos);
        for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
            BlockPos neighborPos = dir.offset(pos);
            Block neighborBlock = getBlock(neighborPos);

            if (neighborBlock.isAir()) continue;

            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(neighborBlock.getType());
            def.onNeighborChange(this, neighborPos, centerBlock, dir.getOpposite());
        }
    }

    public boolean onBlockBreak(BlockPos pos, Player player) {
        Block block = getBlock(pos);
        if (block.isAir()) return true;

        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
        return def.onBlockBreak(this, pos, block, player);
    }

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
        removeBlockEntity(pos);

        entity.setWorld(this);
        blockEntities.put(pos, entity);
        if (entity instanceof ITickable) {
            tickableBlockEntities.add((ITickable) entity);
        }

        com.za.zenith.world.chunks.Chunk chunk = getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
        if (chunk != null) {
            chunk.addLocalBlockEntity(pos);
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
            if (entity instanceof ITickable) {
                tickableBlockEntities.remove(entity);
            }
            com.za.zenith.world.chunks.Chunk chunk = getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
            if (chunk != null) {
                chunk.removeLocalBlockEntity(pos);
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

    public int getSunlight(BlockPos pos) {
        return getSunlight(pos.x(), pos.y(), pos.z());
    }

    public int getSunlight(int x, int y, int z) {
        Chunk chunk = getChunkInternal(x >> 4, z >> 4);
        if (chunk == null) return 0;
        return chunk.getSunlight(x & 15, y, z & 15);
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

    public float getNoiseLevelAt(Vector3f pos) {
        float totalNoise = 0.0f;

        if (player != null) {
            float dist = pos.distance(player.getPosition());
            float playerNoise = player.getNoiseLevel();
            if (dist < 32.0f) {
                totalNoise = Math.max(totalNoise, playerNoise * (1.0f - dist / 32.0f));
            }
        }

        for (BlockEntity be : blockEntities.values()) {
            if (be instanceof com.za.zenith.world.blocks.entity.GeneratorBlockEntity generator && generator.isRunning()) {
                float dist = pos.distance(new Vector3f(be.getPos().x() + 0.5f, be.getPos().y() + 0.5f, be.getPos().z() + 0.5f));
                if (dist < 20.0f) {
                    float genNoise = 0.5f * (1.0f - dist / 20.0f);
                    totalNoise = Math.max(totalNoise, genNoise);
                }
            }
        }

        return totalNoise;
    }

    public void scheduleFluidTick(int x, int y, int z) {
        if (!generating && y >= 0 && y < 256) {
            fluidSimulator.scheduleTick(x, y, z);
        }
    }

    public void cleanup() {
        chunkManager.cleanup();
    }

    // Static nested class left for type compatibility in graphic renderer modules
    public static class CloudInstance {
        private static int nextId = 0;
        public final int id;
        public float x, y, z;
        public float scale;
        public float seed;
        private boolean collected = false;
        public float currentAlpha = 1.0f;
        
        public CloudInstance(float x, float y, float z, float scale, float seed) {
            this.id = nextId++;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.seed = seed;
        }
        
        public void resetPosition(float x, float y, float z, float scale, float seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.seed = seed;
            this.collected = false;
            this.currentAlpha = 1.0f;
        }
        
        public boolean isCollected() { 
            return collected && currentAlpha <= 0.001f; 
        }
        
        public boolean isMarkedCollected() {
            return collected;
        }
        
        public void collect() { 
            this.collected = true; 
        }
        
        public float getAlpha() { 
            return currentAlpha; 
        }
        
        public void update(float deltaTime) {
            if (collected) {
                currentAlpha = Math.max(0.0f, currentAlpha - deltaTime * 0.8f);
            } else {
                currentAlpha = Math.min(1.0f, currentAlpha + deltaTime * 0.4f);
            }
        }
    }

    // Static nested class left for type compatibility in PersistentScarsRenderPass
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
}
