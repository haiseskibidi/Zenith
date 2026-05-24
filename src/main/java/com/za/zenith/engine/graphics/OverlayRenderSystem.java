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

    public void setBreakingBlock(com.za.zenith.world.BlockPos pos, Block block, float progress, float timer, Vector3f localHitPoint, Vector3f localWeakSpot, Vector3f color, List<Vector4f> history, World world, DynamicTextureAtlas atlas) {
        if (block == null) {
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
        if (localWeakSpot != null) this.weakSpotPos.set(localWeakSpot);
        if (color != null) this.weakSpotColor.set(color);
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

    public com.za.zenith.world.BlockPos getBreakingPos() {
        return breakingPos;
    }

    public void render(SceneState state, Shader shader, DynamicTextureAtlas atlas, RaycastResult highlightedBlock, Renderer wrapper) {
        World world = state.getWorld();
        RenderContext.resetBlockShader(shader);
        
        // 1. Block Entities (Carving, ICraftingSurface)
        renderBlockEntities(state, shader, atlas, wrapper);
        
        // 2. Persistent Scars (on previously hit blocks)
        renderPersistentScars(state, shader, atlas);
        
        // 3. Highlight
        if (highlightedBlock != null && highlightedBlock.isHit()) {
            highlightRenderer.render(state.getCamera(), world, highlightedBlock, shader, RenderContext.getMatrix(), state.getAlpha(), breakingPos, currentBreakingBlock, wobbleTimer);
        }

        // 4. Preview
        if (previewPos != null && previewMesh != null) {
            glDisable(GL_CULL_FACE);
            shader.setBoolean("previewPass", true);
            shader.setFloat("previewAlpha", 0.35f);
            shader.setFloat("uSwayOverride", 0.0f);
            shader.setFloat("uChunkSpawnTime", -100.0f);
            Matrix4f model = RenderContext.getMatrix();
            model.translate(previewPos.x() + 0.5f, previewPos.y(), previewPos.z() + 0.5f);
            shader.setMatrix4f("model", model);
            previewMesh.render(shader);
            shader.setBoolean("previewPass", false);
            glEnable(GL_CULL_FACE);
        }

        // 5. Breaking Proxy & Holes (Current target)
        glDisable(GL_CULL_FACE);
        renderBreakingEffects(state, shader, world, atlas);
        glEnable(GL_CULL_FACE);
    }

    private void renderBlockEntities(SceneState state, Shader shader, DynamicTextureAtlas atlas, Renderer wrapper) {
        World world = state.getWorld();
        if (world.getBlockEntities().isEmpty()) return;
        
        for (var be : world.getBlockEntities().values()) {
            carvingRenderer.render(be, atlas, shader, RenderContext.getMatrix(), wrapper, breakingPos, wobbleTimer);
            
            if (be instanceof com.za.zenith.world.blocks.entity.ModularBlockEntity modular) {
                var block = world.getBlock(be.getPos());
                var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                if (def == null) continue;

                var surface = def.getComponent(com.za.zenith.world.blocks.component.CraftingSurfaceComponent.class);

                // Отрисовываем только если это "поверхность для крафта"
                if (surface == null) continue;

                int count = 0;
                for (int i = 0; i < modular.size(); i++) {
                    if (modular.getStack(i) != null) count++;
                }
                if (count == 0) continue;
                
                var p = be.getPos();
                sampleLightAt(world, p.x(), p.y() + 1, p.z(), shader);
                
                int gridSize = surface.getGridSize();
                
                for (int i = 0; i < modular.size(); i++) {
                    var stack = modular.getStack(i);
                    if (stack == null) continue;
                    var item = stack.getItem();
                    Mesh mesh = MeshRegistry.getItemMesh(item, atlas);
                    
                    if (mesh != null) {
                        var t = com.za.zenith.world.blocks.CraftingLayoutEngine.getSlotTransform(i, count, gridSize);
                        float s = (item.isBlock() ? 0.4f : item.getDroppedScale() * 0.6f) * t.y;
                        Matrix4f model = RenderContext.getMatrix();
                        model.translate(p.x() + 0.5f + t.x, p.y() + 1.02f, p.z() + 0.5f + t.z);
                        if (item.isBlock()) model.scale(s); else model.rotateX(1.5708f).scale(s);
                        shader.setMatrix4f("model", model);
                        shader.setInt("highlightPass", 0);
                        mesh.render(shader);
                    }
                }
                shader.setVector3f("uOverrideLight", -1, -1, -1);
            }
        }
    }

    private void renderPersistentScars(SceneState state, Shader shader, DynamicTextureAtlas atlas) {
        World world = state.getWorld();
        if (world.getBlockDamageMap().isEmpty()) {
            return;
        }

        // Remove stale cache entries
        persistentHoleCache.keySet().removeIf(pos -> !world.getBlockDamageMap().containsKey(World.packBlockPos(pos.x(), pos.y(), pos.z())));
        persistentProxyCache.keySet().removeIf(pos -> !world.getBlockDamageMap().containsKey(World.packBlockPos(pos.x(), pos.y(), pos.z())));

        for (var entry : world.getBlockDamageMap().entrySet()) {
            long packed = entry.getKey();
            int bx = World.unpackBlockX(packed), by = World.unpackBlockY(packed), bz = World.unpackBlockZ(packed);
            com.za.zenith.world.BlockPos pos = new com.za.zenith.world.BlockPos(bx, by, bz);
            
            // Skip current breaking block (handled by renderBreakingEffects)
            if (breakingPos != null && pos.equals(breakingPos)) continue;

            World.BlockDamageInstance info = entry.getValue();
            Block block = info.getBlock();
            if (block == null || block.isAir()) continue;

            // 1. Hole (for adjacent faces)
            Mesh hole = persistentHoleCache.computeIfAbsent(pos, p -> ChunkMeshGenerator.generateHoleMesh(p, world, atlas));
            if (hole != null) {
                shader.setBoolean("uIsProxy", false);
                Matrix4f model = RenderContext.getMatrix();
                model.translate(pos.x(), pos.y(), pos.z());
                shader.setMatrix4f("model", model);
                
                Chunk c = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
                shader.setFloat("uChunkSpawnTime", c != null ? c.getFirstSpawnTime() : -100.0f);
                hole.render(shader);
            }

            // 2. Proxy (the static damaged block)
            Mesh mesh = persistentProxyCache.computeIfAbsent(pos, p -> ChunkMeshGenerator.generateSingleBlockMesh(block, atlas, world, p));
            if (mesh != null) {
                var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                shader.setBoolean("uIsProxy", true);
                shader.setFloat("uBreakingProgress", info.getDamage() / (def.getHardness() * 10.0f));
                shader.setInt("uBreakingPattern", def.getBreakingPattern());
                
                // Static proxy properties (no wobble)
                shader.setVector3f("uWobbleScale", 1.0f, 1.0f, 1.0f);
                shader.setVector3f("uWobbleOffset", 0.0f, 0.0f, 0.0f);
                shader.setFloat("uWobbleShake", 0.0f);
                shader.setVector3f("uWeakSpotPos", 0, -100, 0); // Hide marker
                shader.setVector3f("uWeakSpotColor", 1, 1, 1);
                
                int hc = Math.min(16, info.getHitHistory().size());
                shader.setInt("uHitCount", hc);
                for (int i = 0; i < hc; i++) {
                    shader.setVector4f("uHitHistory[" + i + "]", info.getHitHistory().get(i));
                }

                Matrix4f model = RenderContext.getMatrix();
                model.translate(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f);
                shader.setMatrix4f("model", model);
                mesh.render(shader);
            }
        }
        shader.setBoolean("uIsProxy", false);
    }

    private void renderBreakingEffects(SceneState state, Shader shader, World world, DynamicTextureAtlas atlas) {
        if (breakingPos == null) return;
        
        shader.use();
        shader.setBoolean("uIsBatch", false);
        shader.setFloat("uSwayOverride", -1.0f);
        
        // Match chunk spawn time for visual alignment
        Chunk c = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(breakingPos.x(), breakingPos.z()));
        float spawnTime = (c != null) ? c.getFirstSpawnTime() : -100.0f;
        shader.setFloat("uChunkSpawnTime", spawnTime);

        // Hole mesh (transparent cutout)
        if (breakingProgress < 1.0f) {
            if (holeMesh == null || !breakingPos.equals(holePos)) {
                if (holeMesh != null) holeMesh.cleanup();
                holeMesh = ChunkMeshGenerator.generateHoleMesh(breakingPos, world, atlas);
                holePos = breakingPos;
            }
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(breakingPos.x(), breakingPos.y(), breakingPos.z());
            shader.setMatrix4f("model", model);
            shader.setBoolean("uIsProxy", false);
            if (holeMesh != null) holeMesh.render(shader);
        }

        // Breaking Proxy (the actual wobbly block)
        if (breakingMesh != null && currentBreakingBlock != null) {
            var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(currentBreakingBlock.getType());
            shader.setBoolean("uIsProxy", true);
            shader.setFloat("uBreakingProgress", breakingProgress);
            shader.setInt("uBreakingPattern", def.getBreakingPattern());
            shader.setVector3f("uBreakingHitPoint", breakingHitPoint);
            shader.setVector3f("uWeakSpotPos", weakSpotPos);
            shader.setVector3f("uWeakSpotColor", weakSpotColor);
            shader.setInt("uHitCount", hitCount);
            
            // Animation data from registry
            String animName = (def.getWobbleAnimation() != null) ? def.getWobbleAnimation() : "block_wobble";
            var profile = com.za.zenith.entities.parkour.animation.AnimationRegistry.get(animName);
            float sx = 1, sy = 1, sz = 1, ox = 0, oy = 0, oz = 0, sh = 0;
            if (profile != null) {
                float nt = wobbleTimer / Math.max(0.001f, profile.getDuration());
                sx = profile.evaluate("scale_x", nt, 1); sy = profile.evaluate("scale_y", nt, 1); sz = profile.evaluate("scale_z", nt, 1);
                ox = profile.evaluate("offset_x", nt, 0); oy = profile.evaluate("offset_y", nt, 0); oz = profile.evaluate("offset_z", nt, 0);
                sh = profile.evaluate("shake", nt, 0);
            }
            shader.setVector3f("uWobbleScale", RenderContext.getVector().set(sx, sy, sz));
            shader.setVector3f("uWobbleOffset", RenderContext.getVector().set(ox, oy, oz));
            shader.setFloat("uWobbleShake", sh);
            shader.setFloat("uWobbleTime", wobbleTimer);
            
            for (int i = 0; i < hitCount; i++) {
                shader.setVector4f("uHitHistory[" + i + "]", hitHistory[i]);
            }

            Matrix4f model = RenderContext.getMatrix();
            // Proxy must be centered for proper wobble/scaling
            model.translate(breakingPos.x() + 0.5f, breakingPos.y(), breakingPos.z() + 0.5f);
            shader.setMatrix4f("model", model);
            
            breakingMesh.render(shader);
            shader.setBoolean("uIsProxy", false);
        }
    }

    private void sampleLightAt(World world, int x, int y, int z, Shader shader) {
        Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(x, z));
        if (chunk != null) {
            float sun = chunk.getSunlight(x & 15, y, z & 15);
            float block = chunk.getBlockLight(x & 15, y, z & 15);
            shader.setVector3f("uOverrideLight", sun, block, 1.0f);
        } else {
            shader.setVector3f("uOverrideLight", 15, 0, 1.0f);
        }
    }

    private void cleanupPersistentCache() {
        persistentHoleCache.values().forEach(Mesh::cleanup);
        persistentHoleCache.clear();
        persistentProxyCache.values().forEach(Mesh::cleanup);
        persistentProxyCache.clear();
    }

    public void rebuildMeshes() {
        itemMeshCache.values().forEach(Mesh::cleanup);
        itemMeshCache.clear();
        cleanupPersistentCache();
    }

    public void cleanup() {
        if (breakingMesh != null) breakingMesh.cleanup();
        if (holeMesh != null) holeMesh.cleanup();
        if (previewMesh != null) previewMesh.cleanup();
        cleanupPersistentCache();
        for (Mesh m : itemMeshCache.values()) m.cleanup();
        carvingRenderer.cleanup();
    }
}
