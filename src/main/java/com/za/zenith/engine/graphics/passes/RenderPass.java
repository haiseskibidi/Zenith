package com.za.zenith.engine.graphics.passes;

import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ShaderStateManager;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.graphics.SceneState;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.OverlayRenderSystem;

public interface RenderPass {
    void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system);
    default void cleanup() {}
}
