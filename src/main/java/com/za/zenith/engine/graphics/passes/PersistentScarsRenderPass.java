package com.za.zenith.engine.graphics.passes;

import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ShaderStateManager;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.graphics.SceneState;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.OverlayRenderSystem;
import com.za.zenith.engine.graphics.RenderContext;
import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkMeshGenerator;
import org.joml.Matrix4f;

public class PersistentScarsRenderPass implements RenderPass {

    @Override
    public void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system) {
        World world = state.getWorld();
        if (world.getBlockDamageMap().isEmpty()) return;

        // Clean stale items in system's caches
        system.getPersistentHoleCache().keySet().removeIf(pos -> !world.getBlockDamageMap().containsKey(World.packBlockPos(pos.x(), pos.y(), pos.z())));
        system.getPersistentProxyCache().keySet().removeIf(pos -> !world.getBlockDamageMap().containsKey(World.packBlockPos(pos.x(), pos.y(), pos.z())));
        system.getProxyBlockTypeMap().keySet().removeIf(pos -> !world.getBlockDamageMap().containsKey(World.packBlockPos(pos.x(), pos.y(), pos.z())));

        for (var entry : world.getBlockDamageMap().entrySet()) {
            long packed = entry.getKey();
            int bx = World.unpackBlockX(packed), by = World.unpackBlockY(packed), bz = World.unpackBlockZ(packed);
            com.za.zenith.world.BlockPos pos = new com.za.zenith.world.BlockPos(bx, by, bz);
            
            // Skip current active breaking block
            if (system.getBreakingPos() != null && pos.equals(system.getBreakingPos())) continue;

            World.BlockDamageInstance info = entry.getValue();
            Block block = info.getBlock();
            if (block == null || block.isAir()) continue;

            var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
            if (def.getBreakingPattern() == 0) continue;

            // Invalidate caches if the block type changed (e.g. log -> felling stage)
            Integer cachedType = system.getProxyBlockTypeMap().get(pos);
            if (cachedType != null && cachedType != block.getType()) {
                Mesh oldMesh = system.getPersistentProxyCache().remove(pos);
                if (oldMesh != null) oldMesh.cleanup();
                Mesh oldHole = system.getPersistentHoleCache().remove(pos);
                if (oldHole != null) oldHole.cleanup();
            }
            system.getProxyBlockTypeMap().put(pos, block.getType());

            // 1. Hole (for adjacent faces)
            Mesh hole = system.getPersistentHoleCache().computeIfAbsent(pos, p -> ChunkMeshGenerator.generateHoleMesh(p, world, atlas));
            if (hole != null) {
                stateManager.setBoolean("uIsProxy", false);
                Matrix4f model = RenderContext.getMatrix();
                model.translate(pos.x(), pos.y(), pos.z());
                stateManager.setMatrix4f("model", model);
                
                Chunk c = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
                stateManager.setFloat("uChunkSpawnTime", c != null ? c.getFirstSpawnTime() : -100.0f);
                hole.render(shader);
            }

            // 2. Proxy (the static damaged block)
            Mesh mesh = system.getPersistentProxyCache().computeIfAbsent(pos, p -> ChunkMeshGenerator.generateSingleBlockMesh(block, atlas, world, p));
            if (mesh != null) {
                stateManager.setBoolean("uIsProxy", true);
                stateManager.setFloat("uBreakingProgress", info.getDamage() / (def.getHardness() * 10.0f));
                stateManager.setInt("uBreakingPattern", def.getBreakingPattern());
                
                // Static proxy properties (no wobble)
                stateManager.setVector3f("uWobbleScale", 1.0f, 1.0f, 1.0f);
                stateManager.setVector3f("uWobbleOffset", 0.0f, 0.0f, 0.0f);
                stateManager.setFloat("uWobbleShake", 0.0f);
                stateManager.setVector3f("uWeakSpotPos", 0.0f, -100.0f, 0.0f);
                stateManager.setVector3f("uWeakSpotColor", 1.0f, 1.0f, 1.0f);
                
                int hc = Math.min(16, info.getHitHistory().size());
                stateManager.setInt("uHitCount", hc);
                for (int i = 0; i < hc; i++) {
                    stateManager.setVector4f("uHitHistory[" + i + "]", info.getHitHistory().get(i));
                }

                Matrix4f model = RenderContext.getMatrix();
                model.translate(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f);
                stateManager.setMatrix4f("model", model);
                mesh.render(shader);
            }
        }
        stateManager.setBoolean("uIsProxy", false);
    }
}
