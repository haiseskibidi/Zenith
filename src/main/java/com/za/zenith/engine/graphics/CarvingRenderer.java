package com.za.zenith.engine.graphics;

import com.za.zenith.world.BlockPos;
import org.joml.Matrix4f;

import com.za.zenith.world.blocks.entity.BlockEntity;
import com.za.zenith.world.blocks.entity.ModularBlockEntity;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.Block;

/**
 * Универсальный рендерер динамических блоков (обтёсывание).
 */
public class CarvingRenderer {
    private Mesh fullFaceMesh;

    public void render(BlockEntity be, DynamicTextureAtlas atlas, Shader shader, Matrix4f modelMatrix, Renderer renderer, BlockPos breakingPos, float wobbleTimer) {
        if (be instanceof ModularBlockEntity modular) {
            renderModular(modular, atlas, shader, modelMatrix, breakingPos, wobbleTimer);
        }
    }

    private void renderModular(ModularBlockEntity modular, DynamicTextureAtlas atlas, Shader shader, Matrix4f modelMatrix, BlockPos breakingPos, float wobbleTimer) {
        int mask = (int) modular.getFloat(ModularBlockEntity.PROP_CARVE_MASK, -1);
        if (mask < 0) return; // Нет маски для отрисовки
        
        BlockPos pos = modular.getPos();
        com.za.zenith.world.World world = modular.getWorld();
        if (world == null) return;
        
        int blockType = world.getBlock(pos).getType();
        com.za.zenith.world.blocks.BlockTextures textures = com.za.zenith.world.blocks.BlockRegistry.getTextures(blockType);
        if (textures == null || textures.getTop() == null) return;
        
        String textureName = textures.getTop();
        if (textureName != null && textureName.contains("stripped_")) {
            textureName = textureName.replace("stripped_", "");
        }
        float[] uv = atlas.uvFor(textureName);
        if (uv == null) return;

        if (fullFaceMesh == null) {
            createFullFaceMesh();
        }

        // 1. Устанавливаем освещение из чанка для оверлея
        int lx = pos.x(), ly = pos.y() + 1, lz = pos.z();
        com.za.zenith.world.chunks.Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(lx, lz));
        if (chunk != null) {
            float sun = chunk.getSunlight(lx & 15, ly, lz & 15);
            float block = chunk.getBlockLight(lx & 15, ly, lz & 15);
            shader.setVector3f("uOverrideLight", sun, block, 1.0f);
        } else {
            shader.setVector3f("uOverrideLight", 15.0f, 0.0f, 1.0f);
        }

        shader.setBoolean("useMask", true);
        shader.setInt("faceMask", mask);
        shader.setBoolean("previewPass", false);
        shader.setFloat("overlayLayer", uv[2]);
        shader.setFloat("brightnessMultiplier", 1.1f);

        boolean isProxy = pos.equals(breakingPos);
        if (isProxy) {
            shader.setBoolean("uIsProxy", true);
            
            // Загружаем wobble анимацию для блока
            var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(blockType);
            String animName = (def != null && def.getWobbleAnimation() != null) ? def.getWobbleAnimation() : "block_wobble";
            var profile = com.za.zenith.entities.parkour.animation.AnimationRegistry.get(animName);
            
            float sx = 1.0f, sy = 1.0f, sz = 1.0f, ox = 0.0f, oy = 0.0f, oz = 0.0f, sh = 0.0f;
            if (profile != null) {
                float nt = wobbleTimer / Math.max(0.001f, profile.getDuration());
                sx = profile.evaluate("scale_x", nt, 1.0f); 
                sy = profile.evaluate("scale_y", nt, 1.0f); 
                sz = profile.evaluate("scale_z", nt, 1.0f);
                ox = profile.evaluate("offset_x", nt, 0.0f); 
                oy = profile.evaluate("offset_y", nt, 0.0f); 
                oz = profile.evaluate("offset_z", nt, 0.0f);
                sh = profile.evaluate("shake", nt, 0.0f);
            }
            shader.setVector3f("uWobbleScale", RenderContext.getVector().set(sx, sy, sz));
            shader.setVector3f("uWobbleOffset", RenderContext.getVector().set(ox, oy, oz));
            shader.setFloat("uWobbleShake", sh);
            shader.setFloat("uWobbleTime", wobbleTimer);
        }

        // Use the exact same model matrix setup as the breaking proxy block
        // Centered at XZ=0, base at Y=0
        modelMatrix.identity().translate(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f);
        shader.setMatrix4f("model", modelMatrix);
        
        fullFaceMesh.render();

        if (isProxy) {
            shader.setBoolean("uIsProxy", false);
        }

        shader.setBoolean("useMask", false);
        shader.setFloat("brightnessMultiplier", 1.0f);
        shader.setVector3f("uOverrideLight", -1.0f, -1.0f, -1.0f); // Сбрасываем освещение
    }

    private void createFullFaceMesh() {
        // Defined at Y=1.001 relative to block base (Y=0)
        // Center is at XZ=0
        float[] positions = { 
            -0.5f, 1.001f,  0.5f, // Bottom-Left
             0.5f, 1.001f,  0.5f, // Bottom-Right
             0.5f, 1.001f, -0.5f, // Top-Right
            -0.5f, 1.001f, -0.5f  // Top-Left
        };
        float[] normals = { 0, 1, 0,  0, 1, 0,  0, 1, 0,  0, 1, 0 };
        float[] texCoords = {
            0, 1, 0, -1.0f, // BL
            1, 1, 0, -1.0f, // BR
            1, 0, 0, -1.0f, // TR
            0, 0, 0, -1.0f  // TL
        };        float[] blockTypes = { 150, 150, 150, 150 }; 
        float[] neighborData = { 0, 0, 0, 0 };
        int[] indices = { 0, 1, 2, 2, 3, 0 };

        fullFaceMesh = new Mesh(positions, texCoords, normals, blockTypes, neighborData, indices);
    }

    public void cleanup() {
        if (fullFaceMesh != null) {
            fullFaceMesh.cleanup();
            fullFaceMesh = null;
        }
    }
}


