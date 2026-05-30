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
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.chunks.ChunkMeshGenerator;
import org.joml.Matrix4f;

public class BreakingRenderPass implements RenderPass {

    @Override
    public void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system) {
        if (system.getBreakingPos() == null) return;
        
        // Skip rendering overlay for blocks under cursor that haven't been hit yet
        if (system.getBreakingProgress() <= 0.0f && system.getWobbleTimer() >= 0.5f) {
            return;
        }
        
        stateManager.setBoolean("uIsBatch", false);
        stateManager.setFloat("uSwayOverride", -1.0f);
        
        World world = state.getWorld();
        // Match chunk spawn time for visual alignment
        Chunk c = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(system.getBreakingPos().x(), system.getBreakingPos().z()));
        float spawnTime = (c != null) ? c.getFirstSpawnTime() : -100.0f;
        stateManager.setFloat("uChunkSpawnTime", spawnTime);

        // Hole mesh (transparent cutout)
        if (system.getBreakingProgress() < 1.0f) {
            if (system.getHoleMesh() == null || !system.getBreakingPos().equals(system.getHolePos())) {
                if (system.getHoleMesh() != null) system.getHoleMesh().cleanup();
                Mesh newHole = ChunkMeshGenerator.generateHoleMesh(system.getBreakingPos(), world, atlas);
                system.setHoleMesh(newHole, system.getBreakingPos());
            }
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(system.getBreakingPos().x(), system.getBreakingPos().y(), system.getBreakingPos().z());
            stateManager.setMatrix4f("model", model);
            stateManager.setBoolean("uIsProxy", false);
            
            if (system.getHoleMesh() != null) system.getHoleMesh().render(shader);
        }

        // Breaking Proxy (the actual wobbly block)
        if (system.getBreakingMesh() != null && system.getCurrentBreakingBlock() != null) {
            var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(system.getCurrentBreakingBlock().getType());
            stateManager.setBoolean("uIsProxy", true);
            stateManager.setFloat("uBreakingProgress", system.getBreakingProgress());
            stateManager.setInt("uBreakingPattern", def.getBreakingPattern());
            stateManager.setVector3f("uBreakingHitPoint", system.getBreakingHitPoint());
            stateManager.setVector3f("uWeakSpotPos", system.getWeakSpotPos());
            stateManager.setVector3f("uWeakSpotColor", system.getWeakSpotColor());
            stateManager.setInt("uHitCount", system.getHitCount());
            
            // Animation data from registry
            String animName = (def.getWobbleAnimation() != null) ? def.getWobbleAnimation() : "block_wobble";
            var profile = com.za.zenith.entities.parkour.animation.AnimationRegistry.get(animName);
            float sx = 1.0f, sy = 1.0f, sz = 1.0f, ox = 0.0f, oy = 0.0f, oz = 0.0f, sh = 0.0f;
            if (profile != null) {
                float nt = system.getWobbleTimer() / Math.max(0.001f, profile.getDuration());
                sx = profile.evaluate("scale_x", nt, 1.0f); 
                sy = profile.evaluate("scale_y", nt, 1.0f); 
                sz = profile.evaluate("scale_z", nt, 1.0f);
                ox = profile.evaluate("offset_x", nt, 0.0f); 
                oy = profile.evaluate("offset_y", nt, 0.0f); 
                oz = profile.evaluate("offset_z", nt, 0.0f);
                sh = profile.evaluate("shake", nt, 0.0f);
            }
            stateManager.setVector3f("uWobbleScale", RenderContext.getVector().set(sx, sy, sz));
            stateManager.setVector3f("uWobbleOffset", RenderContext.getVector().set(ox, oy, oz));
            stateManager.setFloat("uWobbleShake", sh);
            stateManager.setFloat("uWobbleTime", system.getWobbleTimer());
            
            for (int i = 0; i < system.getHitCount(); i++) {
                stateManager.setVector4f("uHitHistory[" + i + "]", system.getHitHistory()[i]);
            }

            Matrix4f model = RenderContext.getMatrix();
            // Proxy must be centered for proper wobble/scaling
            model.translate(system.getBreakingPos().x() + 0.5f, system.getBreakingPos().y(), system.getBreakingPos().z() + 0.5f);
            stateManager.setMatrix4f("model", model);
            
            system.getBreakingMesh().render(shader);
            stateManager.setBoolean("uIsProxy", false);
        }
    }
}
