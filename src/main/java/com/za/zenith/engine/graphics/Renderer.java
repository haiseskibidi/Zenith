package com.za.zenith.engine.graphics;

import com.za.zenith.engine.core.Window;
import com.za.zenith.engine.graphics.ui.UIRenderer;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Renderer is now a high-level wrapper around RenderPipeline.
 * It maintains the original API for compatibility while delegating
 * all actual work to specialized systems.
 */
public class Renderer {
    private RenderPipeline pipeline;

    public Renderer() {
        // Pipeline initialization is deferred to init()
    }

    public void init(int width, int height) {
        this.pipeline = new RenderPipeline(width, height);
    }

    public void render(Window window, Camera camera, World world, RaycastResult highlightedBlock, GameClient networkClient, float alpha, float deltaTime, com.za.zenith.engine.input.InputManager inputManager) {
        pipeline.render(window, camera, world, highlightedBlock, networkClient, alpha, deltaTime, this, inputManager);
    }

    public void onChunkUnload(Chunk chunk) {
        pipeline.onChunkUnload(chunk);
    }

    public void setBreakingBlock(com.za.zenith.world.BlockPos pos, Block block, float progress, float timer, Vector3f localHitPoint, Vector3f localWeakSpot, Vector3f color, List<Vector4f> history, World world) {
        pipeline.setBreakingBlock(pos, block, progress, timer, localHitPoint, localWeakSpot, color, history, world);
    }

    public void setPreviewBlock(com.za.zenith.world.BlockPos pos, Block block) {
        pipeline.setPreviewBlock(pos, block);
    }

    public void addTemporaryHiddenBlock(com.za.zenith.world.BlockPos pos) {
        pipeline.addTemporaryHiddenBlock(pos);
    }

    public void cleanup() {
        if (pipeline != null) pipeline.cleanup();
    }

    public DynamicTextureAtlas getAtlas() {
        return pipeline.getAtlas();
    }

    public UIRenderer getUIRenderer() {
        return pipeline.getUIRenderer();
    }

    public void setHotbar(com.za.zenith.engine.graphics.ui.Hotbar h) {
        pipeline.getUIRenderer().setHotbar(h);
    }

    public void toggleFXAA() {
        pipeline.toggleFXAA();
    }

    // Legacy/Stub methods for compatibility
    public void renderDebug(float fps, int w, int h) { /* Handled elsewhere or via UIRenderer */ }
    public void rebuildAllChunks() {
        pipeline.rebuildMeshes();
    }
    public int getVisibleSectionsCount() { return 0; /* Could be tracked in ChunkSystem */ }
    public int getDrawCallCount() { return 0; /* Could be tracked in RenderPipeline */ }
}
