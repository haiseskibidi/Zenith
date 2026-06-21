package com.za.zenith.engine.graphics;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkMeshGenerator;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * OverlayRenderSystem handles visual overlays like block highlighting,
 * block damage indicators, and placement previews.
 */
public class OverlayRenderSystem {
    private final BlockHighlightRenderer highlightRenderer = new BlockHighlightRenderer();
    private final CarvingRenderer carvingRenderer = new CarvingRenderer();
    private final Map<com.za.zenith.world.items.Item, Mesh> itemMeshCache = new java.util.HashMap<>();
    
    // Breaking state
    private com.za.zenith.world.BlockPos breakingPos;
    private Block currentBreakingBlock;
    private Mesh breakingMesh;
    private Mesh holeMesh;
    private com.za.zenith.world.BlockPos holePos;
    private float breakingProgress;
    private float wobbleTimer;
    private final Vector3f breakingHitPoint = new Vector3f();
    private final Vector3f weakSpotPos = new Vector3f();
    private final Vector3f weakSpotColor = new Vector3f(1.0f, 1.0f, 1.0f);
    private final Vector4f[] hitHistory = new Vector4f[16];
    private int hitCount = 0;

    // Preview state
    private com.za.zenith.world.BlockPos previewPos;
    private Block currentPreviewBlock;
    private Mesh previewMesh;

    // Persistent scars cache
    private final Map<com.za.zenith.world.BlockPos, Mesh> persistentHoleCache = new java.util.HashMap<>();
    private final Map<com.za.zenith.world.BlockPos, Mesh> persistentProxyCache = new java.util.HashMap<>();
    private final Map<com.za.zenith.world.BlockPos, Integer> proxyBlockTypeMap = new java.util.HashMap<>();

    // Recently broken holes (prevents X-Ray holes during asynchronous chunk meshing)
    public static class RecentlyBrokenHole {
        public Mesh mesh;
        public float remainingTime;
        public RecentlyBrokenHole(Mesh mesh, float time) {
            this.mesh = mesh;
            this.remainingTime = time;
        }
    }
    private final Map<com.za.zenith.world.BlockPos, RecentlyBrokenHole> recentlyBrokenHoles = new java.util.HashMap<>();

    // Render pipeline/passes
    private final ShaderStateManager stateManager = new ShaderStateManager();
    private final com.za.zenith.engine.graphics.passes.RenderPass[] passes = new com.za.zenith.engine.graphics.passes.RenderPass[] {
        new com.za.zenith.engine.graphics.passes.BlockEntityRenderPass(),
        new com.za.zenith.engine.graphics.passes.PersistentScarsRenderPass(),
        new com.za.zenith.engine.graphics.passes.HighlightRenderPass(),
        new com.za.zenith.engine.graphics.passes.PreviewRenderPass(),
        new com.za.zenith.engine.graphics.passes.BreakingRenderPass()
    };
    private RaycastResult currentHighlightedBlock;

    private World lastWorld;

    public void setBreakingBlock(com.za.zenith.world.BlockPos pos, Block block, float progress, float timer, Vector3f localHitPoint, Vector3f localWeakSpot, Vector3f color, List<Vector4f> history, World world, DynamicTextureAtlas atlas) {
        if (world != null) {
            this.lastWorld = world;
        }
        if (block == null) {
            if (this.breakingPos != null && this.breakingProgress >= 0.99f) {
                if (lastWorld != null && atlas != null) {
                    // Update all existing hole meshes since their neighbors might have changed
                    for (Map.Entry<com.za.zenith.world.BlockPos, RecentlyBrokenHole> entry : recentlyBrokenHoles.entrySet()) {
                        if (entry.getValue().mesh != null) entry.getValue().mesh.cleanup();
                        entry.getValue().mesh = ChunkMeshGenerator.generateHoleMesh(entry.getKey(), lastWorld, atlas);
                    }
                    
                    if (this.holeMesh != null) this.holeMesh.cleanup();
                    this.holeMesh = ChunkMeshGenerator.generateHoleMesh(this.breakingPos, lastWorld, atlas);
                }
                if (this.holeMesh != null) {
                    // Move hole mesh to recently broken cache to prevent X-Ray while chunk builds
                    recentlyBrokenHoles.put(this.breakingPos, new RecentlyBrokenHole(this.holeMesh, 0.35f));
                    this.holeMesh = null;
                    this.holePos = null;
                }
            }
            this.breakingPos = null;
            this.currentBreakingBlock = null;
            return;
        }
        if (currentBreakingBlock == null || !pos.equals(this.breakingPos) || currentBreakingBlock.getType() != block.getType()) {
            if (breakingMesh != null) breakingMesh.cleanup();
            breakingMesh = ChunkMeshGenerator.generateSingleBlockMesh(block, atlas, world, pos);
            currentBreakingBlock = block;
        }
        this.breakingPos = pos;
        this.breakingProgress = progress;
        this.wobbleTimer = timer;
        if (localHitPoint != null) this.breakingHitPoint.set(localHitPoint);
        if (localWeakSpot != null) {
            this.weakSpotPos.set(localWeakSpot);
        } else {
            this.weakSpotPos.set(0.0f, -100.0f, 0.0f); // ponytail: hide weak spot
        }
        if (color != null) {
            this.weakSpotColor.set(color);
        } else {
            this.weakSpotColor.set(1.0f, 1.0f, 1.0f);
        }
        this.hitCount = history != null ? Math.min(16, history.size()) : 0;
        if (history != null) {
            for (int i = 0; i < hitCount; i++) {
                if (hitHistory[i] == null) hitHistory[i] = new Vector4f();
                hitHistory[i].set(history.get(i));
            }
        }
    }

    public void setPreviewBlock(com.za.zenith.world.BlockPos pos, Block block, DynamicTextureAtlas atlas) {
        if (block == null) {
            this.previewPos = null;
            this.currentPreviewBlock = null;
            return;
        }
        if (currentPreviewBlock == null || currentPreviewBlock.getType() != block.getType()) {
            if (previewMesh != null) previewMesh.cleanup();
            previewMesh = ChunkMeshGenerator.generateSingleBlockMesh(block, atlas, null, null);
            currentPreviewBlock = block;
        }
        this.previewPos = pos;
    }

    public void updateRecentlyBrokenHoles(float deltaTime, World world) {
        if (recentlyBrokenHoles.isEmpty()) return;
        var it = recentlyBrokenHoles.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var bPos = entry.getKey();
            
            // Check if the chunk containing this block has completed its mesh update
            boolean meshIsUpToDate = false;
            if (world != null) {
                com.za.zenith.world.chunks.Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(bPos.x(), bPos.z()));
                if (chunk != null && !chunk.needsMeshUpdate() && chunk.getCurrentMeshResult() != null) {
                    meshIsUpToDate = true;
                }
            }
            
            entry.getValue().remainingTime -= deltaTime;
            if (entry.getValue().remainingTime <= 0.0f || meshIsUpToDate) {
                if (entry.getValue().mesh != null) {
                    entry.getValue().mesh.cleanup();
                }
                it.remove();
            }
        }
    }

    public void render(SceneState state, Shader shader, DynamicTextureAtlas atlas, RaycastResult highlightedBlock, Renderer wrapper) {
        this.currentHighlightedBlock = highlightedBlock;
        stateManager.bind(shader);
        
        // Update recently broken holes (fade-out Z-fighting guard)
        updateRecentlyBrokenHoles(state.getDeltaTime(), state.getWorld());
        
        for (com.za.zenith.engine.graphics.passes.RenderPass pass : passes) {
            stateManager.resetToBaseline();
            pass.render(state, shader, stateManager, atlas, wrapper, this);
        }
        
        stateManager.resetToBaseline();
    }

    public void rebuildMeshes() {
        itemMeshCache.values().forEach(Mesh::cleanup);
        itemMeshCache.clear();
        cleanupPersistentCache();
        for (RecentlyBrokenHole rbh : recentlyBrokenHoles.values()) {
            if (rbh.mesh != null) rbh.mesh.cleanup();
        }
        recentlyBrokenHoles.clear();
    }

    private void cleanupPersistentCache() {
        persistentHoleCache.values().forEach(Mesh::cleanup);
        persistentHoleCache.clear();
        persistentProxyCache.values().forEach(Mesh::cleanup);
        persistentProxyCache.clear();
        proxyBlockTypeMap.clear();
    }

    public void cleanup() {
        if (breakingMesh != null) breakingMesh.cleanup();
        if (holeMesh != null) holeMesh.cleanup();
        if (previewMesh != null) previewMesh.cleanup();
        cleanupPersistentCache();
        for (Mesh m : itemMeshCache.values()) m.cleanup();
        for (RecentlyBrokenHole rbh : recentlyBrokenHoles.values()) {
            if (rbh.mesh != null) rbh.mesh.cleanup();
        }
        recentlyBrokenHoles.clear();
        carvingRenderer.cleanup();
    }

    public void addTemporaryHiddenBlock(com.za.zenith.world.BlockPos pos) {
        recentlyBrokenHoles.put(pos, new RecentlyBrokenHole(null, 1.5f));
    }

    // Getters and Setters
    public Map<com.za.zenith.world.BlockPos, RecentlyBrokenHole> getRecentlyBrokenHoles() { return recentlyBrokenHoles; }
    public BlockHighlightRenderer getHighlightRenderer() { return highlightRenderer; }
    public CarvingRenderer getCarvingRenderer() { return carvingRenderer; }
    public com.za.zenith.world.BlockPos getBreakingPos() { return breakingPos; }
    public Block getCurrentBreakingBlock() { return currentBreakingBlock; }
    public Mesh getBreakingMesh() { return breakingMesh; }
    public Mesh getHoleMesh() { return holeMesh; }
    public com.za.zenith.world.BlockPos getHolePos() { return holePos; }
    public void setHoleMesh(Mesh holeMesh, com.za.zenith.world.BlockPos holePos) {
        this.holeMesh = holeMesh;
        this.holePos = holePos;
    }
    public float getBreakingProgress() { return breakingProgress; }
    public float getWobbleTimer() { return wobbleTimer; }
    public Vector3f getBreakingHitPoint() { return breakingHitPoint; }
    public Vector3f getWeakSpotPos() { return weakSpotPos; }
    public Vector3f getWeakSpotColor() { return weakSpotColor; }
    public Vector4f[] getHitHistory() { return hitHistory; }
    public int getHitCount() { return hitCount; }
    public com.za.zenith.world.BlockPos getPreviewPos() { return previewPos; }
    public Mesh getPreviewMesh() { return previewMesh; }
    public Map<com.za.zenith.world.BlockPos, Mesh> getPersistentHoleCache() { return persistentHoleCache; }
    public Map<com.za.zenith.world.BlockPos, Mesh> getPersistentProxyCache() { return persistentProxyCache; }
    public Map<com.za.zenith.world.BlockPos, Integer> getProxyBlockTypeMap() { return proxyBlockTypeMap; }
    public RaycastResult getHighlightedBlock() { return currentHighlightedBlock; }
}
