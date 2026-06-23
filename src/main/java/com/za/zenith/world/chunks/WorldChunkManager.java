package com.za.zenith.world.chunks;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.entity.BlockEntity;
import com.za.zenith.world.blocks.entity.ITickable;
import com.za.zenith.utils.PriorityExecutorService;
import com.za.zenith.utils.Logger;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manager responsible for background chunk generation, lighting calculation, chunk loading queues,
 * spiral chunk discovery around the player, and lazy unload logic.
 * ponytail: isolated to keep World.java small and clean.
 */
public class WorldChunkManager {
    private final World world;
    
    private final PriorityExecutorService chunkGenExecutor = new PriorityExecutorService(
        Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 2)),
        r -> {
            Thread t = new Thread(r, "ChunkGenerator");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    );
    private final PriorityExecutorService lightExecutor = new PriorityExecutorService(
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
    private final LinkedHashSet<Long> pendingChunkQueue = new LinkedHashSet<>();
    private final List<Consumer<Chunk>> unloadListeners = new CopyOnWriteArrayList<>();

    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;
    private long lastSpiralCheckTime = 0;
    private long lastUnloadCheckTime = 0;

    public WorldChunkManager(World world) {
        this.world = world;
    }

    public PriorityExecutorService getLightExecutor() {
        return lightExecutor;
    }

    public PriorityExecutorService getChunkGenExecutor() {
        return chunkGenExecutor;
    }

    public Map<Long, Future<?>> getGeneratingChunks() {
        return generatingChunks;
    }

    public Map<Long, Future<?>> getLightingChunks() {
        return lightingChunks;
    }

    public LinkedHashSet<Long> getPendingChunkQueue() {
        return pendingChunkQueue;
    }

    public List<Consumer<Chunk>> getUnloadListeners() {
        return unloadListeners;
    }

    public void addUnloadListener(Consumer<Chunk> listener) {
        unloadListeners.add(listener);
    }

    public void update(Player player) {
        if (player == null) return;

        int currentChunkX = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
        int currentChunkZ = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);

        int renderDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().activeRenderDistance;
        int unloadDistance = com.za.zenith.world.generation.GenerationSettings.getInstance().unloadDistance;

        long now = System.currentTimeMillis();
        boolean forceCheck = now - lastSpiralCheckTime > 1500; // Check every 1.5 seconds
        boolean shouldUnload = now - lastUnloadCheckTime > 1000; // Unload every 1.0 second (highly responsive!)

        // 1. SPIRAL DISCOVERY: If player moved or timer fired, rebuild queue in strict spiral order from center
        if (currentChunkX != lastPlayerChunkX || currentChunkZ != lastPlayerChunkZ || (forceCheck && pendingChunkQueue.isEmpty())) {
            lastPlayerChunkX = currentChunkX;
            lastPlayerChunkZ = currentChunkZ;
            lastSpiralCheckTime = now;

            pendingChunkQueue.clear();

            int x = 0, z = 0, dx = 0, dz = -1;
            int maxChunks = (renderDistance * 2 + 1) * (renderDistance * 2 + 1);
            for (int i = 0; i < maxChunks; i++) {
                if (-renderDistance <= x && x <= renderDistance && -renderDistance <= z && z <= renderDistance) {
                    long packed = ChunkPos.pack(currentChunkX + x, currentChunkZ + z);
                    if (!world.getChunksMap().containsKey(packed) && !generatingChunks.containsKey(packed) && !lightingChunks.containsKey(packed)) {
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
        }

        // 2. LAZY UNLOAD: Clean up far chunks once every 5 seconds to eliminate CPU lock contention and garbage sweeps
        if (shouldUnload) {
            lastUnloadCheckTime = now;

            world.getChunksMap().entrySet().removeIf(entry -> {
                long packed = entry.getKey();
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                boolean remove = Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance;
                if (remove) {
                    Chunk chunk = entry.getValue();
                    com.za.zenith.world.lighting.LightManager.onChunkUnload(chunk);
                    
                    // Ultra-fast chunk-local O(1) clear for block damage
                    for (long packedPos : chunk.getLocalBlockDamage()) {
                        world.getBlockDamageMap().remove(packedPos);
                    }
                    chunk.getLocalBlockDamage().clear();

                    // Ultra-fast chunk-local O(1) clear for block entities
                    for (BlockPos pos : chunk.getLocalBlockEntities()) {
                        BlockEntity be = world.getBlockEntities().remove(pos);
                        if (be != null) {
                            be.setRemoved();
                            if (be instanceof ITickable) {
                                world.getTickableBlockEntities().remove(be);
                            }
                        }
                    }
                    chunk.getLocalBlockEntities().clear();

                    // CRITICAL FIX: Properly remove the entire chunk from groundEntityMap
                    world.getGroundEntityMap().remove(new ChunkPos(cx, cz));

                    for (Consumer<Chunk> listener : unloadListeners) {
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

            world.getStagingChunks().keySet().removeIf(packed -> {
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                return Math.abs(cx - currentChunkX) > unloadDistance || Math.abs(cz - currentChunkZ) > unloadDistance;
            });

            world.getGroundEntityMap().keySet().removeIf(pos -> {
                return Math.abs(pos.x() - currentChunkX) > unloadDistance || Math.abs(pos.z() - currentChunkZ) > unloadDistance;
            });

            pendingChunkQueue.removeIf(packed -> {
                int cx = ChunkPos.unpackX(packed);
                int cz = ChunkPos.unpackZ(packed);
                return Math.abs(cx - currentChunkX) > renderDistance || Math.abs(cz - currentChunkZ) > renderDistance;
            });
        }

        if (!pendingChunkQueue.isEmpty()) {
            int submittedThisTick = 0;
            int canSubmitGen = Math.min(4, Math.max(0, (chunkGenExecutor.getCorePoolSize() * 2) - generatingChunks.size()));
            
            Iterator<Long> it = pendingChunkQueue.iterator();
            while (it.hasNext() && submittedThisTick < canSubmitGen) {
                long packedPos = it.next();
                it.remove();
                
                if (!world.getChunksMap().containsKey(packedPos) && !generatingChunks.containsKey(packedPos) && !lightingChunks.containsKey(packedPos)) {
                    int cx = ChunkPos.unpackX(packedPos);
                    int cz = ChunkPos.unpackZ(packedPos);
                    
                    Future<?> future = chunkGenExecutor.submit(new PriorityExecutorService.PrioritizedRunnable() {
                        @Override public int getPriority() { 
                            int px = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
                            int pz = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);
                            return (cx - px)*(cx - px) + (cz - pz)*(cz - pz);
                        }

                        @Override public void run() {
                            try {
                                Chunk chunk = world.getStagingChunks().get(packedPos);
                                if (chunk == null) chunk = new Chunk(new ChunkPos(cx, cz));
                                world.getTerrainGenerator().generateTerrain(chunk);
                                world.getStagingChunks().put(packedPos, chunk);
                                
                                submitLightingTask(packedPos, chunk, player);
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

    private void submitLightingTask(long packedPos, Chunk chunk, Player player) {
        int cx = chunk.getPosition().x();
        int cz = chunk.getPosition().z();
        
        Future<?> future = lightExecutor.submit(new PriorityExecutorService.PrioritizedRunnable() {
            @Override public int getPriority() {
                int px = (int) Math.floor(player.getPosition().x / Chunk.CHUNK_SIZE);
                int pz = (int) Math.floor(player.getPosition().z / Chunk.CHUNK_SIZE);
                return (cx - px)*(cx - px) + (cz - pz)*(cz - pz);
            }

            @Override public void run() {
                try {
                    world.getTerrainGenerator().generateStructures(world, chunk);
                    world.getLightEngine().generateInitialSunlight(chunk);
                    chunk.setReady(true);
                    chunk.setNeedsMeshUpdate(true);
                    world.getLightEngine().onChunkReady(chunk);
                    com.za.zenith.world.lighting.LightManager.onChunkLoad(chunk);
                    world.getChunksMap().put(packedPos, chunk);
                } catch (Exception e) {
                    String errorMsg = "Lighting error in chunk " + chunk.getPosition() + ": " + e.getMessage();
                    Logger.error(errorMsg, e);
                    try (FileWriter fw = new FileWriter("chunk_errors.txt", true);
                         PrintWriter pw = new PrintWriter(fw)) {
                        pw.println(new Date() + " - " + errorMsg);
                        e.printStackTrace(pw);
                    } catch (Exception ex) {}
                } finally {
                    world.getStagingChunks().remove(packedPos);
                    generatingChunks.remove(packedPos);
                    lightingChunks.remove(packedPos);
                }
            }
        });
        lightingChunks.put(packedPos, future);
    }

    public void cleanup() {
        chunkGenExecutor.shutdown();
        try {
            if (!chunkGenExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                chunkGenExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkGenExecutor.shutdownNow();
        }
    }
}
