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
    private final MultiDrawBatch opaqueBatch;
    private final MultiDrawBatch translucentBatch;
    
    private final Map<Chunk, Future<ChunkMeshGenerator.RawChunkMeshResult>> pendingUpdates = new ConcurrentHashMap<>();
    private final com.za.zenith.utils.PriorityExecutorService meshExecutor;
    
    private final List<SectionRenderNode> visibleSections = new ArrayList<>();
    private final Deque<BFSNode> bfsQueue = new ArrayDeque<>();
    private final Set<Long> visitedSections = new HashSet<>();
    
    // Performance optimization: Avoid re-sorting if camera hasn't moved much
    private final org.joml.Vector3f lastSortPos = new org.joml.Vector3f(Float.MAX_VALUE);

    private int lastCamSecX = Integer.MAX_VALUE;
    private int lastCamSecY = Integer.MAX_VALUE;
    private int lastCamSecZ = Integer.MAX_VALUE;
    private int lastPoolVersion = 0;

    private record SectionRenderNode(Chunk chunk, int sectionIdx) {}
    private record BFSNode(int cx, int cz, int secIdx, com.za.zenith.utils.Direction entryFace) {}

    public ChunkRenderSystem(MeshPool meshPool) {
        this.meshPool = meshPool;
        this.opaqueBatch = new MultiDrawBatch(meshPool);
        this.translucentBatch = new MultiDrawBatch(meshPool);
        
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

    public void updateVisibility(SceneState state) {
        World world = state.getWorld();
        Vector3f camPos = state.getCameraPos();
        int camChunkX = (int) Math.floor(camPos.x / Chunk.CHUNK_SIZE);
        int camChunkZ = (int) Math.floor(camPos.z / Chunk.CHUNK_SIZE);
        int camSecY = (int) Math.floor(camPos.y / ChunkSection.SECTION_SIZE);
        int renderDist = world.getRenderDistance();

        // Check for pool wrap-around
        if (meshPool.getVersion() != lastPoolVersion) {
            lastPoolVersion = meshPool.getVersion();
            Logger.warn("ChunkRenderSystem: MeshPool wrapped! Purging meshes.");
            for (Chunk c : world.getLoadedChunks()) {
                ChunkMeshGenerator.ChunkMeshResult result = c.getCurrentMeshResult();
                if (result != null) { result.cleanup(); c.setCurrentMeshResult(null); }
                c.setMeshUpdated(-1);
            }
            lastCamSecX = Integer.MAX_VALUE;
        }

        boolean movedSection = camChunkX != lastCamSecX || camSecY != lastCamSecY || camChunkZ != lastCamSecZ;
        if (movedSection || state.getFrameCounter() % 5 == 0) {
            visibleSections.clear();
            int poolVer = meshPool.getVersion();

            // Zero-Allocation monolithic spiral chunk scan
            int x = 0, z = 0, dx = 0, dz = -1;
            int maxChunks = (renderDist * 2 + 1) * (renderDist * 2 + 1);
            
            for (int i = 0; i < maxChunks; i++) {
                if (-renderDist <= x && x <= renderDist && -renderDist <= z && z <= renderDist) {
                    Chunk chunk = world.getChunk(camChunkX + x, camChunkZ + z);
                    if (chunk != null && chunk.isReady()) {
                        ChunkMeshGenerator.ChunkMeshResult result = chunk.getCurrentMeshResult();
                        if (result != null) {
                            float cx = (camChunkX + x) * 16;
                            float cz = (camChunkZ + z) * 16;
                            
                            for (int secIdx = 0; secIdx < Chunk.NUM_SECTIONS; secIdx++) {
                                ChunkSection section = chunk.getSections()[secIdx];
                                if (section == null || section.isEmpty()) continue;
                                
                                float sy = secIdx * 16;
                                // Frustum culling per section on CPU
                                if (state.getFrustum().testAab(cx, sy, cz, cx + 16, sy + 16, cz + 16)) {
                                    if (isSectionMeshValid(result, secIdx, poolVer)) {
                                        visibleSections.add(new SectionRenderNode(chunk, secIdx));
                                    }
                                }
                            }
                        }
                    }
                }
                if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                    int temp = dx; dx = -dz; dz = temp;
                }
                x += dx; z += dz;
            }
            
            lastCamSecX = camChunkX; lastCamSecY = camSecY; lastCamSecZ = camChunkZ;
        }
    }

    private boolean isSectionMeshValid(ChunkMeshGenerator.ChunkMeshResult result, int secIdx, int poolVer) {
        Mesh mO = result.opaqueSections()[secIdx];
        Mesh mT = result.translucentSections()[secIdx];
        if (mO != null || mT != null) {
            Mesh valid = (mO != null) ? mO : mT;
            return valid.getPool() == null || valid.getPoolVersion() == poolVer;
        }
        return false;
    }

    private void processEmptyNeighbor(BFSNode node, int camChunkX, int camChunkZ, int renderDist) {
        for (com.za.zenith.utils.Direction dir : com.za.zenith.utils.Direction.values()) {
            int ncx = node.cx+dir.getDx(), ncz = node.cz+dir.getDz(), nsec = node.secIdx+dir.getDy();
            if (nsec < 0 || nsec >= Chunk.NUM_SECTIONS) continue;
            if (Math.abs(ncx-camChunkX) > renderDist || Math.abs(ncz-camChunkZ) > renderDist) continue;
            if (visitedSections.add(packSectionPos(ncx, ncz, nsec))) bfsQueue.add(new BFSNode(ncx, ncz, nsec, dir.getOpposite()));
        }
    }

    public void updateMeshes(SceneState state, DynamicTextureAtlas atlas) {
        World world = state.getWorld();
        Vector3f camPos = state.getCameraPos();
        int camChunkX = (int) Math.floor(camPos.x / Chunk.CHUNK_SIZE);
        int camChunkZ = (int) Math.floor(camPos.z / Chunk.CHUNK_SIZE);
        int renderDist = world.getRenderDistance();

        // 1. Process finished uploads
        long uploadStart = System.nanoTime();
        Iterator<Map.Entry<Chunk, Future<ChunkMeshGenerator.RawChunkMeshResult>>> it = pendingUpdates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Chunk, Future<ChunkMeshGenerator.RawChunkMeshResult>> entry = it.next();
            if (entry.getValue().isDone()) {
                try {
                    ChunkMeshGenerator.RawChunkMeshResult raw = entry.getValue().get();
                    Chunk chunk = entry.getKey();
                    if (world.getChunk(chunk.getPosition()) == chunk) {
                        ChunkMeshGenerator.ChunkMeshResult res = raw.upload(meshPool);
                        raw.cleanup();
                        ChunkMeshGenerator.ChunkMeshResult old = chunk.getCurrentMeshResult();
                        if (old != null) old.cleanup();
                        chunk.setCurrentMeshResult(res);
                        chunk.setMeshUpdated(res.version());
                    } else {
                        raw.cleanup();
                    }
                    it.remove();
                    if (System.nanoTime() - uploadStart > 2_000_000) break;
                } catch (Exception e) { it.remove(); }
            }
        }

        // 2. Schedule new meshes using SPIRAL search for better prioritization
        int activeTasks = pendingUpdates.size();
        int poolSize = meshExecutor.getCorePoolSize();
        // Dynamically scale maxSchedule to fully utilize idle executor threads (up to poolSize * 2)
        int maxSchedule = Math.max(4, poolSize * 2 - activeTasks); 
        int scheduled = 0;
        int x = 0, z = 0, dx = 0, dz = -1, checkRadius = renderDist + 1;
        int iterations = (checkRadius * 2 + 1) * (checkRadius * 2 + 1);
        
        for (int i = 0; i < iterations; i++) {
            if (scheduled >= maxSchedule) break;
            if (Math.abs(x) <= checkRadius && Math.abs(z) <= checkRadius) {
                Chunk chunk = world.getChunk(camChunkX + x, camChunkZ + z);
                if (chunk != null && chunk.isReady() && !pendingUpdates.containsKey(chunk)) {
                    if (chunk.needsMeshUpdate() || chunk.getCurrentMeshResult() == null) {
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
        int[] bd = com.za.zenith.utils.ArrayPool.rentBlockDataArray();
        byte[] ld = com.za.zenith.utils.ArrayPool.rentLightDataArray();
        Chunk.DataSnapshot snapshot = chunk.getSnapshot(bd, ld);
        long version = chunk.getDirtyCounter();
        float distSq = camPos.distanceSquared(snapshot.position().x() * 16 + 8, camPos.y, snapshot.position().z() * 16 + 8);
        float spawnTime = chunk.getFirstSpawnTime();

        pendingUpdates.put(chunk, meshExecutor.submit(new com.za.zenith.utils.PriorityExecutorService.PrioritizedCallable<>() {
            @Override public int getPriority() { return (int)distSq; }
            @Override public ChunkMeshGenerator.RawChunkMeshResult call() throws Exception {
                try {
                    Chunk temp = new Chunk(snapshot.position(), snapshot.blockData(), snapshot.lightData());
                    temp.setDirtyCounter(version);
                    temp.setFirstSpawnTime(spawnTime);
                    return ChunkMeshGenerator.generateRawMesh(temp, world, atlas);
                } finally {
                    com.za.zenith.utils.ArrayPool.returnBlockDataArray(snapshot.blockData());
                    com.za.zenith.utils.ArrayPool.returnLightDataArray(snapshot.lightData());
                }
            }
        }));
    }

    public void render(SceneState state, Shader shader, boolean opaque) {
        MultiDrawBatch batch = opaque ? opaqueBatch : translucentBatch;
        batch.reset();
        
        // Sorting for transparency (already done in updateVisibility for distance)
        List<SectionRenderNode> list = visibleSections;
        if (!opaque) {
            // Reverse order for translucent
            for (int i = list.size() - 1; i >= 0; i--) {
                addSectionToBatch(list.get(i), batch, shader);
            }
        } else {
            for (SectionRenderNode node : list) {
                addSectionToBatch(node, batch, shader);
            }
        }
        
        batch.render();
    }

    private void addSectionToBatch(SectionRenderNode node, MultiDrawBatch batch, Shader shader) {
        ChunkMeshGenerator.ChunkMeshResult res = node.chunk.getCurrentMeshResult();
        if (res == null) return;
        
        Mesh m = (batch == opaqueBatch) ? res.opaqueSections()[node.sectionIdx] : res.translucentSections()[node.sectionIdx];
        if (m == null) return;

        if (m.getPool() != null) {
            batch.addMesh(m, node.chunk.getPosition().x() * 16, 0, node.chunk.getPosition().z() * 16, res.spawnTime());
        } else {
            // Fallback for non-pooled meshes (legacy support)
            shader.setBoolean("uIsBatch", false);
            org.joml.Matrix4f model = RenderContext.getMatrix();
            model.translate(node.chunk.getPosition().x() * 16, 0, node.chunk.getPosition().z() * 16);
            shader.setMatrix4f("model", model);
            shader.setFloat("uChunkSpawnTime", res.spawnTime());
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
        opaqueBatch.cleanup();
        translucentBatch.cleanup();
        com.za.zenith.utils.NioBufferPool.clearPools();
    }
}
