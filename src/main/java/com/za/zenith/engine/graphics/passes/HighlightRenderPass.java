package com.za.zenith.engine.graphics.passes;

import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.engine.graphics.ShaderStateManager;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.engine.graphics.SceneState;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.engine.graphics.OverlayRenderSystem;
import com.za.zenith.engine.graphics.RenderContext;
import com.za.zenith.world.World;
import com.za.zenith.world.physics.RaycastResult;

public class HighlightRenderPass implements RenderPass {

    @Override
    public void render(SceneState state, Shader shader, ShaderStateManager stateManager, DynamicTextureAtlas atlas, Renderer wrapper, OverlayRenderSystem system) {
        RaycastResult highlightedBlock = system.getHighlightedBlock();
        if (highlightedBlock != null && highlightedBlock.isHit()) {
            World world = state.getWorld();
            system.getHighlightRenderer().render(
                state.getCamera(),
                world,
                highlightedBlock,
                shader,
                RenderContext.getMatrix(),
                state.getAlpha(),
                system.getBreakingPos(),
                system.getCurrentBreakingBlock(),
                system.getWobbleTimer()
            );
        }
    }
}
