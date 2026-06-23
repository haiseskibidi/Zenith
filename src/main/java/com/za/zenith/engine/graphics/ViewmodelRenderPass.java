package com.za.zenith.engine.graphics;

import com.za.zenith.entities.Inventory;
import com.za.zenith.entities.Player;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.graphics.model.Viewmodel;
import com.za.zenith.engine.graphics.model.ViewmodelRenderer;
import com.za.zenith.world.lighting.LightManager;
import com.za.zenith.world.lighting.LightSource;
import com.za.zenith.world.lighting.LightData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class ViewmodelRenderPass {
    private final ViewmodelRenderer viewmodelRenderer = new ViewmodelRenderer();
    private final Shader viewmodelShader;
    
    private float handHeat;
    private float itemHeat;

    private final List<LightSource> viewLights = new ArrayList<>();
    private final LightSource[] viewLightPool = new LightSource[LightManager.MAX_DYNAMIC_LIGHTS];

    public ViewmodelRenderPass() {
        this.viewmodelShader = new Shader("src/main/resources/shaders/viewmodel_vertex.glsl", "src/main/resources/shaders/viewmodel_fragment.glsl");
        for (int i = 0; i < viewLightPool.length; i++) {
            viewLightPool[i] = new LightSource(new LightData());
        }
    }

    public void updateHeat(float hand, float item) {
        this.handHeat = hand;
        this.itemHeat = item;
    }

    public void render(SceneState state, DynamicTextureAtlas atlas, EntityRenderSystem parent) {
        Player player = state.getWorld().getPlayer();
        if (player == null) return;
        
        glDisable(GL_CULL_FACE);
        glDepthRange(0.0, 0.05);
        
        viewmodelShader.use();
        atlas.bind();
        
        // UI Projection for viewmodel
        Matrix4f vmProj = RenderContext.getMatrix().setPerspective((float)Math.toRadians(70.0f), state.getCamera().getAspectRatio(), 0.01f, 1000.0f);
        viewmodelShader.setMatrix4f("projection", vmProj);
        viewmodelShader.setMatrix4f("view", RenderContext.getMatrix()); // Identity view
        
        // Lighting for viewmodel (transformed to view space - Zero Alloc)
        Matrix4f vMat = state.getViewMatrix();
        var worldLights = LightManager.getActiveLights();
        viewLights.clear();
        for (int i = 0; i < Math.min(worldLights.size(), viewLightPool.length); i++) {
            var src = worldLights.get(i);
            var dst = viewLightPool[i];
            dst.data = src.data; // Shallow copy data ref
            vMat.transformPosition(src.position, dst.position);
            vMat.transformDirection(src.direction, dst.direction);
            viewLights.add(dst);
        }
        viewmodelShader.setLights("uLights", viewLights);
        
        // Pass light from player's eyes level to viewmodel (Zero Alloc)
        Vector3f eyePos = RenderContext.getVector().set(player.getPosition()).add(0, player.getEyeHeight(), 0);
        parent.setEntityLight(state.getWorld(), eyePos, viewmodelShader);
        
        viewmodelShader.setVector3f("uCondition", RenderContext.getVector().set(player.getDirt(), player.getBlood(), player.getWetness()));
        
        Viewmodel vm = player.getViewmodel();
        if (vm != null) {
            if (!vm.root.children.isEmpty() && vm.root.children.get(0).mesh == null) vm.initMeshes(atlas);
            ItemStack mainHand = player.getInventory().getSelectedItemStack();
            ItemStack offHand = player.getInventory().getStack(Inventory.SLOT_OFFHAND);
            viewmodelRenderer.render(vm, viewmodelShader, atlas, player, mainHand, offHand, handHeat, itemHeat);
        }
        
        glDepthRange(0.0, 1.0);
        glEnable(GL_CULL_FACE);
    }

    public void cleanup() {
        viewmodelShader.cleanup();
    }
}
