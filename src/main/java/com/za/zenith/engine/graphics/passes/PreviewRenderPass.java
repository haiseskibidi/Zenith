package com.za.zenith.engine.graphics.passes;

import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ShaderStateManager;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.graphics.SceneState;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.OverlayRenderSystem;
import com.za.zenith.engine.graphics.RenderContext;
import com.za.zenith.engine.graphics.Mesh;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;

public class PreviewRenderPass implements RenderPass {

    @Override
    public void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system) {
        if (system.getPreviewPos() != null && system.getPreviewMesh() != null) {
            glDisable(GL_CULL_FACE);
            stateManager.setBoolean("previewPass", true);
            stateManager.setFloat("previewAlpha", 0.35f);
            stateManager.setFloat("uSwayOverride", 0.0f);
            stateManager.setFloat("uChunkSpawnTime", -100.0f);
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(system.getPreviewPos().x() + 0.5f, system.getPreviewPos().y(), system.getPreviewPos().z() + 0.5f);
            stateManager.setMatrix4f("model", model);
            
            system.getPreviewMesh().render(shader);
            
            stateManager.setBoolean("previewPass", false);
            glEnable(GL_CULL_FACE);
        }
    }
}
