package com.za.zenith.engine.graphics;

import com.za.zenith.utils.Logger;
import com.za.zenith.world.World;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkMeshGenerator;
import com.za.zenith.world.chunks.ChunkSection;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import static org.lwjgl.opengl.GL11.*;

/**
 * ChunkRenderSystem handles the visibility determination (BFS),
 * asynchronous meshing, and batching of world chunks.
 */
public class ChunkRenderSystem {
    private final MeshPool meshPool;
    private final MultiDrawBatch[] opaqueBatches = new MultiDrawBatch[2];
    private final MultiDrawBatch[] translucentBatches = new MultiDrawBatch[2];
    
    private final Map<Chunk, Future<ChunkMeshGenerator.RawChunkMeshResult>> pendingUpdates = new ConcurrentHashMap<>();
    private final com.za.zenith.utils.PriorityExecutorService meshExecutor;
    
    private Chunk[] visibleChunks = new Chunk[1024];
    private int[] visibleSectionIndices = new int[1024];
    private int visibleSectionsCount = 0;

    private void ensureVisibleSectionsCapacity() {
        if (visibleSectionsCount >= visibleChunks.length) {
            int newLength = visibleChunks.length * 2;
            Chunk[] newChunks = new Chunk[newLength];
            System.arraycopy(visibleChunks, 0, newChunks, 0, visibleChunks.length);
            visibleChunks = newChunks;

            int[] newIndices = new int[newLength];
            System.arraycopy(visibleSectionIndices, 0, newIndices, 0, visibleSectionIndices.length);
            visibleSectionIndices = newIndices;

            float[] newDist = new float[newLength];
            System.arraycopy(visibleDistSq, 0, newDist, 0, visibleDistSq.length);
            visibleDistSq = newDist;

            Integer[] newSort = new Integer[newLength];
            System.arraycopy(sortIndices, 0, newSort, 0, sortIndices.length);
            sortIndices = newSort;
        }
    }
    
    // Zero-Allocation BFS structures
    private static final int MAX_QUEUE_SIZE = 131072;
    private final int[] bfsQueueX = new int[MAX_QUEUE_SIZE];
    private final int[] bfsQueueZ = new int[MAX_QUEUE_SIZE];
    private final int[] bfsQueueY = new int[MAX_QUEUE_SIZE];
    private final byte[] bfsQueueEntry = new byte[MAX_QUEUE_SIZE];
    
    // Hyper-fast versioned visited mask (avoids Arrays.fill overhead)
    private final short[] visitedFast = new short[262144];
    private short currentVisitId = 0;
    private Chunk[] localChunkCache = new Chunk[4096];
    
    // Data structures for sorting opaque chunks (Front-to-Back for Early-Z)
    private float[] visibleDistSq = new float[1024];
    private Integer[] sortIndices = new Integer[1024];

    // Performance optimization: Avoid re-sorting if camera hasn't moved much
    private final org.joml.Vector3f lastSortPos = new org.joml.Vector3f(Float.MAX_VALUE);

    private final org.joml.Matrix4f lastFrustumMatrix = new org.joml.Matrix4f();
    private int lastCamSecX = Integer.MAX_VALUE;
    private int lastCamSecY = Integer.MAX_VALUE;
    private int lastCamSecZ = Integer.MAX_VALUE;
    private int lastPoolVersion = 0;
    private static final com.za.zenith.utils.Direction[] DIRS = com.za.zenith.utils.Direction.values();

    public ChunkRenderSystem(MeshPool meshPool) {
        this.meshPool = meshPool;
        this.opaqueBatches[0] = new MultiDrawBatch(meshPool, 0);
        this.opaqueBatches[1] = new MultiDrawBatch(meshPool, 1);
        this.translucentBatches[0] = new MultiDrawBatch(meshPool, 0);
        this.translucentBatches[1] = new MultiDrawBatch(meshPool, 1);
        
        this.meshExecutor = new com.za.zenith.utils.PriorityExecutorService(
            Math.min(2, Math.max(1, Runtime.getRuntime().availableProcessors() / 2)),
            r -> {
                Thread t = new Thread(r, "MeshGenerator");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        );
    }

    public void onChunkUnload(Chunk chunk) {
        if (chunk == null) return;
        Future<?> future = pendingUpdates.remove(chunk);
        if (future != null) future.cancel(true);
        ChunkMeshGenerator.ChunkMeshResult result = chunk.getCurrentMeshResult();
        if (result != null) {
            result.cleanup();
            chunk.setCurrentMeshResult(null);
        }
    }
    
    private int getVisitedIndex(int cx, int cz, int cy, int camCx, int camCz, int renderDist) {
        int side = renderDist * 2 + 1;
        int rx = cx - camCx + renderDist;
        int rz = cz - camCz + renderDist;
        if (rx < 0 || rx >= side || rz < 0 || rz >= side || cy < 0 || cy >= Chunk.NUM_SECTIONS) return -1;
        return (rx * side + rz) * Chunk.NUM_SECTIONS + cy;
    }

    public void updateVisibility(SceneState state) {
        World world = state.getWorld();
        Vector3f camPos = state.getCameraPos();
        int camChunkX = (int) Math.floor(camPos.x / Chunk.CHUNK_SIZE);
        int camChunkZ = (int) Math.floor(camPos.z / Chunk.CHUNK_SIZE);
        int camSecY = (int) Math.floor(camPos.y / ChunkSection.SECTION_SIZE);
        // Robustness: Handle camera being way above the sky limit
        int rootSecY = Math.max(0, Math.min(Chunk.NUM_SECTIONS - 1, camSecY));
        int renderDist = world.getRenderDistance();

        // Check for pool wrap-around
        if (meshPool.getVersion() != lastPoolVersion) {
            int oldVersion = lastPoolVersion;
            lastPoolVersion = meshPool.getVersion();
            Logger.warn("ChunkRenderSystem: MeshPool wrapped! Initiating seamless transition from version " + oldVersion + " to " + lastPoolVersion);
            for (Chunk c : world.getLoadedChunks()) {
                c.setMeshUpdated(-1);
            }
        }

        boolean movedSection = camChunkX != lastCamSecX || camSecY != lastCamSecY || camChunkZ != lastCamSecZ;
        org.joml.Matrix4f currentFrustum = state.getFrustumMatrix();
        boolean frustumChanged = !currentFrustum.equals(lastFrustumMatrix);

        if (movedSection || frustumChanged || visibleSectionsCount == 0) {
            lastFrustumMatrix.set(currentFrustum);
            lastCamSecX = camChunkX; 
            lastCamSecY = camSecY; 
            lastCamSecZ = camChunkZ;
            
            visibleSectionsCount = 0;
            int poolVer = meshPool.getVersion();
            org.joml.FrustumIntersection frustum = state.getFrustum();
            
            int side = renderDist * 2 + 1;
            
            // Hyper-fast chunk cache for the current frame
            if (localChunkCache.length < side * side) {
                localChunkCache = new Chunk[side * side * 2];
            }
            for (int rX = 0; rX < side; rX++) {
                for (int rZ = 0; rZ < side; rZ++) {
                    localChunkCache[rX * side + rZ] = world.getChunk(camChunkX + rX - renderDist, camChunkZ + rZ - renderDist);
                }
            }

            // O(1) clear of visited mask
            currentVisitId++;
            if (currentVisitId == Short.MAX_VALUE) {
                Arrays.fill(visitedFast, (short)0);
                currentVisitId = 1;
            }

            int head = 0;
            int tail = 0;
            
            // Push root
            bfsQueueX[tail] = camChunkX;
            bfsQueueZ[tail] = camChunkZ;
            bfsQueueY[tail] = rootSecY;
            bfsQueueEntry[tail] = -1; // -1 means origin (can go anywhere)
            
            int rootIdx = getVisitedIndex(camChunkX, camChunkZ, rootSecY, camChunkX, camChunkZ, renderDist);
            if (rootIdx >= 0) visitedFast[rootIdx] = currentVisitId;
            tail++;

            while (head < tail && tail < MAX_QUEUE_SIZE - 6) {
                int cx = bfsQueueX[head];
                int cz = bfsQueueZ[head];
                int cy = bfsQueueY[head];
                int entryFace = bfsQueueEntry[head];
                head++;

                float worldX = cx * 16.0f;
                float worldY = cy * 16.0f;
                float worldZ = cz * 16.0f;

                // CRITICAL: Frustum Check BEFORE expanding (prevents 360-degree occlusion leaking out of tunnels)
                if (!frustum.testAab(worldX, worldY, worldZ, worldX + 16.0f, worldY + 16.0f, worldZ + 16.0f)) {
                    continue;
                }

                int localRx = cx - camChunkX + renderDist;
                int localRz = cz - camChunkZ + renderDist;
                Chunk chunk = localChunkCache[localRx * side + localRz];
                
                // Stop BFS wave if chunk is unloaded! 
                if (chunk == null || !chunk.isReady()) {
                    continue;
                }

                ChunkSection section = chunk.getSections()[cy];
                ChunkMeshGenerator.ChunkMeshResult result = chunk.getCurrentMeshResult();
                
                if (result != null && section != null && !section.isEmpty()) {
                    if (isSectionMeshValid(result, cy, poolVer)) {
                        ensureVisibleSectionsCapacity();
                        visibleChunks[visibleSectionsCount] = chunk;
                        visibleSectionIndices[visibleSectionsCount] = cy;
                        // Calculate distance squared for Early-Z sorting
                        float dx = worldX + 8.0f - camPos.x;
                        float dy = worldY + 8.0f - camPos.y;
                        float dz = worldZ + 8.0f - camPos.z;
                        visibleDistSq[visibleSectionsCount] = dx*dx + dy*dy + dz*dz;
                        sortIndices[visibleSectionsCount] = visibleSectionsCount;
                        visibleSectionsCount++;
                    }
                }

                // Queue neighbors
                for (int i = 0; i < 6; i++) {
                    com.za.zenith.utils.Direction outDir = DIRS[i];
                    
                    // PERFORMANCE CRITICAL: Strict Monotonicity Rule
                    // Prevents "portal leakage" back to ground from sky
                    int odx = outDir.getDx();
                    int ody = outDir.getDy();
                    int odz = outDir.getDz();
                    if (odx > 0 && cx < camChunkX) continue;
                    if (odx < 0 && cx > camChunkX) continue;
                    if (ody > 0 && cy < rootSecY) continue;
                    if (ody < 0 && cy > rootSecY) continue;
                    if (odz > 0 && cz < camChunkZ) continue;
                    if (odz < 0 && cz > camChunkZ) continue;

                    // Occlusion Check: Can we see *through* the current section to the neighbor?
                    if (entryFace != -1 && section != null && !section.isEmpty()) {
                        com.za.zenith.utils.Direction inDir = DIRS[entryFace];
                        if (!section.canSeeThrough(inDir, outDir)) {
                            continue; // Blocked by this section's geometry!
                        }
                    }

                    int ncx = cx + odx;
                    int ncz = cz + odz;
                    int ncy = cy + ody;

                    int nIdx = getVisitedIndex(ncx, ncz, ncy, camChunkX, camChunkZ, renderDist);
                    // Out of bounds or already visited
                    if (nIdx < 0 || visitedFast[nIdx] == currentVisitId) continue;

                    visitedFast[nIdx] = currentVisitId;
                    
                    bfsQueueX[tail] = ncx;
                    bfsQueueZ[tail] = ncz;
                    bfsQueueY[tail] = ncy;
                    bfsQueueEntry[tail] = (byte) outDir.getOpposite().ordinal();
                    tail++;
                }
            }
            
            // OPAQUE SORT: Front-to-Back sorting to maximize Early-Z discard on iGPU
            if (visibleSectionsCount > 0) {
                Arrays.sort(sortIndices, 0, visibleSectionsCount, (a, b) -> Float.compare(visibleDistSq[a], visibleDistSq[b]));
            }
        }
    }

    private boolean isSectionMeshValid(ChunkMeshGenerator.ChunkMeshResult result, int secIdx, int poolVer) {
        Mesh mO = result.opaqueSections()[secIdx];
        Mesh mT = result.translucentSections()[secIdx];
        if (mO != null || mT != null) {
            Mesh valid = (mO != null) ? mO : mT;
            if (valid.getPool() == null) return true;
            int meshVer = valid.getPoolVersion();
            return meshVer == poolVer || meshVer == poolVer - 1;
        }
        return false;
    }

    public void updateMeshes(SceneState state, DynamicTextureAtlas atlas) {
        World world = state.getWorld();
        Vector3f camPos = state.getCameraPos();
        int camChunkX = (int) Math.floor(camPos.x / Chunk.CHUNK_SIZE);
        int camChunkZ = (int) Math.floor(camPos.z / Chunk.CHUNK_SIZE);
        int renderDist = world.getRenderDistance();

        // 1. Process finished uploads (prioritize closer chunks to prevent visual lag)
        long uploadStart = System.nanoTime();
        List<ChunkUploadNode> readyUploads = new ArrayList<>();
        
        for (Map.Entry<Chunk, Future<ChunkMeshGenerator.RawChunkMeshResult>> entry : pendingUpdates.entrySet()) {
            if (entry.getValue().isDone()) {
                Chunk chunk = entry.getKey();
                float distSq = camPos.distanceSquared(chunk.getPosition().x() * 16 + 8, camPos.y, chunk.getPosition().z() * 16 + 8);
                readyUploads.add(new ChunkUploadNode(chunk, entry.getValue(), distSq));
            }
        }
        
        if (!readyUploads.isEmpty()) {
            // Sort closer chunks first
            readyUploads.sort(Comparator.comparingDouble(n -> n.distSq));
            
            int priorityUploads = 0;
            
            for (ChunkUploadNode node : readyUploads) {
                try {
                    ChunkMeshGenerator.RawChunkMeshResult raw = node.future.get();
                    Chunk chunk = node.chunk;
                    if (world.getChunk(chunk.getPosition()) == chunk) {
                        ChunkMeshGenerator.ChunkMeshResult res = raw.upload(meshPool);
                        
                        // Update visibility masks on the main thread chunks
                        if (raw.visibilityMasks() != null) {
                            for (int i = 0; i < Chunk.NUM_SECTIONS; i++) {
                                ChunkSection sec = chunk.getSections()[i];
                                if (sec != null) {
                                    sec.setVisibilityMask(raw.visibilityMasks()[i]);
                                }
                            }
                        }
                        
                        raw.cleanup();
                        ChunkMeshGenerator.ChunkMeshResult old = chunk.getCurrentMeshResult();
                        if (old != null) old.cleanup();
                        chunk.setCurrentMeshResult(res);
                        chunk.setMeshUpdated(res.version());
                    } else {
                        raw.cleanup();
                    }
                    pendingUpdates.remove(chunk);
                    
                    // Time budget for chunk uploads to prevent main thread stutters
                    if (node.distSq > 24 * 24) {
                        // Far chunks get a strict 1.5ms budget
                        if (System.nanoTime() - uploadStart > 1_500_000) break;
                    } else {
                        // Priority chunks (close to player) can upload up to 2 per frame even if over budget
                        priorityUploads++;
                        if (priorityUploads >= 2 && System.nanoTime() - uploadStart > 2_500_000) break;
                    }
                } catch (Exception e) {
                    pendingUpdates.remove(node.chunk);
                }
            }
        }

        // 2. Schedule new meshes using SPIRAL search for better prioritization
        int activeTasks = pendingUpdates.size();
        int poolSize = meshExecutor.getCorePoolSize();
        // Dynamically scale maxSchedule to fully utilize idle executor threads (up to poolSize * 2)
        int maxSchedule = Math.max(4, poolSize * 2 - activeTasks); 
        int scheduled = 0;
        int scheduledLowPriority = 0;
        int x = 0, z = 0, dx = 0, dz = -1, checkRadius = renderDist + 1;
        int iterations = (checkRadius * 2 + 1) * (checkRadius * 2 + 1);
        
        for (int i = 0; i < iterations; i++) {
            if (scheduled >= maxSchedule) break;
            if (Math.abs(x) <= checkRadius && Math.abs(z) <= checkRadius) {
                Chunk chunk = world.getChunk(camChunkX + x, camChunkZ + z);
                if (chunk != null && chunk.isReady() && !pendingUpdates.containsKey(chunk)) {
                    if (chunk.needsMeshUpdate() || chunk.getCurrentMeshResult() == null) {
                        boolean isLowPriority = chunk.getCurrentMeshResult() != null && chunk.getLastMeshCounter() == -1;
                        if (isLowPriority && scheduledLowPriority >= 1) {
                            // Skip this low-priority transition mesh in this frame to avoid CPU spikes on the main thread
                            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                                int t = dx; dx = -dz; dz = t;
                            }
                            x += dx; z += dz;
                            continue;
                        }

                        if (isLowPriority) {
                            scheduledLowPriority++;
                        }

                        scheduleChunkMesh(chunk, world, atlas, camPos);
                        scheduled++;
                    }
                }
            }
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int t = dx; dx = -dz; dz = t;
            }
            x += dx; z += dz;
        }
    }

    private void scheduleChunkMesh(Chunk chunk, World world, DynamicTextureAtlas atlas, Vector3f camPos) {
        Chunk.DataSnapshot snapshot = chunk.getSnapshot();
        long version = chunk.getDirtyCounter();
        float distSq = camPos.distanceSquared(snapshot.position().x() * 16 + 8, camPos.y, snapshot.position().z() * 16 + 8);
        float spawnTime = chunk.getFirstSpawnTime();

        pendingUpdates.put(chunk, meshExecutor.submit(new com.za.zenith.utils.PriorityExecutorService.PrioritizedCallable<>() {
            @Override public int getPriority() { return (int)distSq; }
            @Override public ChunkMeshGenerator.RawChunkMeshResult call() throws Exception {
                Chunk temp = new Chunk(snapshot);
                temp.setDirtyCounter(version);
                temp.setFirstSpawnTime(spawnTime);
                return ChunkMeshGenerator.generateRawMesh(temp, world, atlas);
            }
        }));
    }

    public void render(SceneState state, Shader shader, boolean opaque) {
        MultiDrawBatch[] batches = opaque ? opaqueBatches : translucentBatches;
        batches[0].reset();
        batches[1].reset();
        
        int count = visibleSectionsCount;
        if (!opaque) {
            // BACK-TO-FRONT for transparency (Reverse sorted)
            for (int i = count - 1; i >= 0; i--) {
                int idx = sortIndices[i];
                Chunk chunk = visibleChunks[idx];
                int secIdx = visibleSectionIndices[idx];
                ChunkMeshGenerator.ChunkMeshResult res = chunk.getCurrentMeshResult();
                if (res == null) continue;
                Mesh m = res.translucentSections()[secIdx];
                if (m == null) continue;
                
                int bIdx = m.getPoolVersion() % 2;
                addSectionToSpecificBatch(chunk, secIdx, m, batches[bIdx], shader, res.spawnTime());
            }
        } else {
            // FRONT-TO-BACK for Early-Z discard (Sorted)
            for (int i = 0; i < count; i++) {
                int idx = sortIndices[i];
                Chunk chunk = visibleChunks[idx];
                int secIdx = visibleSectionIndices[idx];
                ChunkMeshGenerator.ChunkMeshResult res = chunk.getCurrentMeshResult();
                if (res == null) continue;
                Mesh m = res.opaqueSections()[secIdx];
                if (m == null) continue;
                
                int bIdx = m.getPoolVersion() % 2;
                addSectionToSpecificBatch(chunk, secIdx, m, batches[bIdx], shader, res.spawnTime());
            }
        }
        
        batches[0].render();
        batches[1].render();
    }

    private void addSectionToSpecificBatch(Chunk chunk, int secIdx, Mesh m, MultiDrawBatch batch, Shader shader, float spawnTime) {
        if (m.getPool() != null) {
            batch.addMesh(m, chunk.getPosition().x() * 16, 0, chunk.getPosition().z() * 16, spawnTime);
        } else {
            // Fallback for non-pooled meshes (legacy support)
            shader.setBoolean("uIsBatch", false);
            org.joml.Matrix4f model = RenderContext.getMatrix();
            model.translate(chunk.getPosition().x() * 16, 0, chunk.getPosition().z() * 16);
            shader.setMatrix4f("model", model);
            shader.setFloat("uChunkSpawnTime", spawnTime);
            m.render(shader);
            shader.setBoolean("uIsBatch", true);
        }
    }

    private long packSectionPos(int cx, int cz, int secIdx) {
        return (((long)cx & 0xFFFFFFL) << 32) | (((long)cz & 0xFFFFFFL) << 8) | (secIdx & 0xFF);
    }

    public void cleanup() {
        meshExecutor.shutdown();
        try {
            if (!meshExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                meshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            meshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        opaqueBatches[0].cleanup();
        opaqueBatches[1].cleanup();
        translucentBatches[0].cleanup();
        translucentBatches[1].cleanup();
        com.za.zenith.utils.NioBufferPool.clearPools();
    }

    private static class ChunkUploadNode {
        final Chunk chunk;
        final Future<ChunkMeshGenerator.RawChunkMeshResult> future;
        final float distSq;

        ChunkUploadNode(Chunk chunk, Future<ChunkMeshGenerator.RawChunkMeshResult> future, float distSq) {
            this.chunk = chunk;
            this.future = future;
            this.distSq = distSq;
        }
    }
}
