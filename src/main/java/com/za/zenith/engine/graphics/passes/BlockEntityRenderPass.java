package com.za.zenith.engine.graphics.passes;

import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ShaderStateManager;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.graphics.SceneState;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.OverlayRenderSystem;
import com.za.zenith.engine.graphics.RenderContext;
import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshRegistry;
import com.za.zenith.world.World;
import com.za.zenith.world.chunks.Chunk;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class BlockEntityRenderPass implements RenderPass {

    @Override
    public void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system) {
        World world = state.getWorld();
        if (world.getBlockEntities().isEmpty()) return;
        
        for (var be : world.getBlockEntities().values()) {
            system.getCarvingRenderer().render(be, atlas, shader, RenderContext.getMatrix(), wrapper, system.getBreakingPos(), system.getWobbleTimer());
            
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
                sampleLightAt(world, p.x(), p.y() + 1, p.z(), stateManager);
                
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
                        stateManager.setMatrix4f("model", model);
                        stateManager.setInt("highlightPass", 0);
                        mesh.render(shader);
                    }
                }
                stateManager.setVector3f("uOverrideLight", -1.0f, -1.0f, -1.0f);
            }
        }
    }

    private void sampleLightAt(World world, int x, int y, int z, ShaderStateManager stateManager) {
        Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(x, z));
        if (chunk != null) {
            float sun = chunk.getSunlight(x & 15, y, z & 15);
            float block = chunk.getBlockLight(x & 15, y, z & 15);
            stateManager.setVector3f("uOverrideLight", sun, block, 1.0f);
        } else {
            stateManager.setVector3f("uOverrideLight", 15.0f, 0.0f, 1.0f);
        }
    }
}
