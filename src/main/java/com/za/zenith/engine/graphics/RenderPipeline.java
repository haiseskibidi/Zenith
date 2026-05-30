package com.za.zenith.engine.graphics;

import com.za.zenith.engine.core.Window;
import com.za.zenith.entities.Player;
import com.za.zenith.engine.graphics.ui.UIRenderer;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * RenderPipeline is the main orchestrator of the rendering process.
 * It manages sub-systems and global rendering states.
 */
public class RenderPipeline {
    private final Shader blockShader;
    private final DynamicTextureAtlas atlas;
    private final MeshPool meshPool;
    
    // Sub-systems
    private final ChunkRenderSystem chunkSystem;
    private final EntityRenderSystem entitySystem;
    private final OverlayRenderSystem overlaySystem;
    private final com.za.zenith.engine.graphics.vfx.MiningVFXManager vfxManager;
    
    // Core Rendering
    private final Framebuffer msaaFramebuffer;
    private final Framebuffer resolveFramebuffer;
    private final PostProcessor postProcessor;
    private final UIRenderer uiRenderer;
    private final SkyRenderer skyRenderer = new SkyRenderer();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    
    private boolean fxaaEnabled = false;
    private long frameCounter = 0;

    // Persistent States (Zero Alloc)
    private final SceneState sceneState = new SceneState(null, null, 0, 0);
    private final com.za.zenith.world.lighting.LightSource sunSource = new com.za.zenith.world.lighting.LightSource(new com.za.zenith.world.lighting.LightData());

    public RenderPipeline(int width, int height) {
        RenderContext.init();
        this.blockShader = new Shader("src/main/resources/shaders/vertex.glsl", "src/main/resources/shaders/fragment.glsl");
        this.atlas = new DynamicTextureAtlas(16);
        this.meshPool = new MeshPool();
        
        this.chunkSystem = new ChunkRenderSystem(meshPool);
        this.entitySystem = new EntityRenderSystem();
        this.overlaySystem = new OverlayRenderSystem();
        this.vfxManager = new com.za.zenith.engine.graphics.vfx.MiningVFXManager();
        
        this.msaaFramebuffer = new Framebuffer(width, height, 4);
        this.resolveFramebuffer = new Framebuffer(width, height, 1);
        this.postProcessor = new PostProcessor();
        this.uiRenderer = new UIRenderer();
        
        sunSource.data.type = com.za.zenith.world.lighting.LightData.Type.DIRECTIONAL;
        sunSource.data.intensity = 1.0f;

        initResources();
    }

    private void initResources() {
        // 1. Blocks
        for (var def : com.za.zenith.world.blocks.BlockRegistry.getRegistry().values()) {
            if (def.getTextures() != null) {
                for (int f = 0; f < 7; f++) {
                    String k = def.getTextures().getTextureForFace(f);
                    if (k != null) atlas.add(k, "src/main/resources/" + k);
                }
            }
            if (def.getUpperTexture() != null) atlas.add(def.getUpperTexture(), "src/main/resources/" + def.getUpperTexture());
        }

        // 2. Items
        for (var item : com.za.zenith.world.items.ItemRegistry.getAllItems().values()) {
            String tex = item.getTexturePath();
            if (tex != null && !tex.isEmpty()) atlas.add(tex, "src/main/resources/" + tex);
        }

        // 3. Viewmodels
        for (var vmDef : com.za.zenith.engine.graphics.model.ModelRegistry.getAllViewmodels()) {
            if (vmDef.texture != null) atlas.add(vmDef.texture, "src/main/resources/" + vmDef.texture);
        }

        // 4. Entities
        for (var def : com.za.zenith.entities.EntityRegistry.getAll().values()) {
            if ("item".equals(def.modelType())) {
                String tex = def.texture();
                if (tex != null && !tex.isEmpty()) atlas.add(tex, "src/main/resources/" + tex);
            }
        }

        atlas.build();

        postProcessor.init();
        uiRenderer.init();
        skyRenderer.init();
        particleRenderer.init();

        blockShader.use();
        blockShader.setInt("textureSampler", 0);

        float[] glassUV = atlas.uvFor("zenith/textures/block/glass.png");
        if (glassUV != null) blockShader.setFloat("glassLayer", glassUV[2]);
    }


    public void render(Window window, Camera camera, World world, RaycastResult highlightedBlock, GameClient networkClient, float alpha, float deltaTime, Renderer wrapper, com.za.zenith.engine.input.InputManager inputManager) {
        // 1. Prepare Scene State (Zero Alloc reuse)
        sceneState.update(camera, world, alpha, deltaTime);
        sceneState.setFrameCounter(frameCounter++);
        
        // Update VFX
        if (inputManager != null) {
            vfxManager.update(deltaTime, world.getPlayer(), inputManager.getMiningController());
            entitySystem.updateHeat(vfxManager.getHandHeat(), vfxManager.getItemHeat());
        }

        // Update global lighting/environment
        updateEnvironment(sceneState);
        
        // Update UBO and Context
        RenderContext.update(world, camera, alpha, sceneState.getLightDirection(), sceneState.getAmbientLight());

        // 2. Main Rendering Pass (MSAA)
        msaaFramebuffer.resize(window.getWidth(), window.getHeight());
        resolveFramebuffer.resize(window.getWidth(), window.getHeight());
        
        msaaFramebuffer.bind();
        glEnable(GL_MULTISAMPLE);
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        
        Vector3f skyColor = getSkyColor(sceneState);
        glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Sky
        skyRenderer.render(camera, sceneState.getLightDirection());
        
        // World
        renderWorld(sceneState, networkClient, highlightedBlock, wrapper);
        
        // Particles
        particleRenderer.render(camera, com.za.zenith.world.particles.ParticleManager.getInstance().getActiveParticles(), atlas, alpha, sceneState.getAmbientLight());
        
        // Viewmodel
        entitySystem.renderViewmodel(sceneState, atlas);

        msaaFramebuffer.unbind();
        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glDisable(GL_MULTISAMPLE);

        // 3. Post-Processing & UI
        msaaFramebuffer.resolveTo(resolveFramebuffer);
        
        if (fxaaEnabled) postProcessor.processFXAA(resolveFramebuffer.getColorTextureId(), resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight());
        else postProcessor.processPassthrough(resolveFramebuffer.getColorTextureId(), resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight());

        uiRenderer.renderCrosshair(window.getWidth(), window.getHeight());
        uiRenderer.renderHotbar(window.getWidth(), window.getHeight(), atlas);
        uiRenderer.renderHUDOverlay(window.getWidth(), window.getHeight());
        uiRenderer.renderLootboxOpening(window.getWidth(), window.getHeight());
    }

    private void renderWorld(SceneState state, GameClient networkClient, RaycastResult highlightedBlock, Renderer wrapper) {
        blockShader.use();
        atlas.bind();
        
        // --- Restore Critical Global State (Baseline) ---
        blockShader.setBoolean("useMask", false);
        blockShader.setBoolean("previewPass", false);
        blockShader.setBoolean("isHand", false);
        blockShader.setFloat("brightnessMultiplier", 1.0f);
        blockShader.setInt("highlightPass", 0);
        blockShader.setBoolean("uIsProxy", false);
        blockShader.setInt("uHitCount", 0);
        blockShader.setFloat("uBreakingProgress", 0.0f);
        blockShader.setInt("uBreakingPattern", 0);
        blockShader.setFloat("uSwayOverride", -1.0f);
        blockShader.setVector3f("uOverrideLight", -1.0f, -1.0f, -1.0f);
        blockShader.setFloat("uChunkSpawnTime", -100.0f);

        // Pass dynamic lights to shader
        blockShader.setLights("uLights", com.za.zenith.world.lighting.LightManager.getActiveLights());
        
        // Systems Update
        chunkSystem.updateVisibility(state);
        chunkSystem.updateMeshes(state, atlas);
        
        // --- Pass Hidden Positions ---
        int hiddenCount = 0;
        com.za.zenith.world.BlockPos bPos = overlaySystem.getBreakingPos();
        if (bPos != null) {
            if (overlaySystem.getBreakingProgress() > 0.0f || overlaySystem.getWobbleTimer() < 0.5f) {
                blockShader.setVector3f("uHiddenPositions[" + hiddenCount + "]", bPos.x(), bPos.y(), bPos.z());
                hiddenCount++;
            }
        }
        for (var entry : state.getWorld().getBlockDamageMap().entrySet()) {
            if (hiddenCount >= 16) break;
            long packed = entry.getKey();
            int bx = com.za.zenith.world.World.unpackBlockX(packed);
            int by = com.za.zenith.world.World.unpackBlockY(packed);
            int bz = com.za.zenith.world.World.unpackBlockZ(packed);
            if (bPos != null && bx == bPos.x() && by == bPos.y() && bz == bPos.z()) continue;
            blockShader.setVector3f("uHiddenPositions[" + hiddenCount + "]", bx, by, bz);
            hiddenCount++;
        }
        blockShader.setInt("uHiddenCount", hiddenCount);

        // 1. Opaque Chunks
        blockShader.setBoolean("uIsCompressed", true);
        blockShader.setBoolean("uIsBatch", true);
        chunkSystem.render(state, blockShader, true);
        
        // Disable hiding for Entities and Overlays to prevent artifacts
        blockShader.setInt("uHiddenCount", 0);

        // 2. Entities
        entitySystem.render(state, blockShader, atlas, networkClient);

        // 3. Overlays
        overlaySystem.render(state, blockShader, atlas, highlightedBlock, wrapper);

        // 4. Translucent Chunks
        blockShader.setInt("uHiddenCount", hiddenCount); // Restore for translucent blocks
        blockShader.setBoolean("uIsCompressed", true);
        blockShader.setBoolean("uIsBatch", true);
        glDepthMask(false);
        chunkSystem.render(state, blockShader, false);
        glDepthMask(true);

        // Reset state for safety
        blockShader.setBoolean("uIsBatch", false);
        blockShader.setBoolean("uIsCompressed", false);
        blockShader.setInt("uHiddenCount", 0);
    }

    private void updateEnvironment(SceneState state) {
        com.za.zenith.world.WorldSettings settings = com.za.zenith.world.WorldSettings.getInstance();
        float timeRatio = state.getWorld().getWorldTime() / settings.dayLength;
        float angle = (timeRatio - 0.25f) * (float)Math.PI * 2.0f;
        
        Vector3f lightDir = RenderContext.getVector();
        lightDir.set(0.2f, -(float)Math.cos(angle), (float)Math.sin(angle)).normalize();
        
        float sunIntensity = Math.max(0.0f, (float)Math.cos(angle));
        float moonIntensity = Math.max(0.0f, -(float)Math.cos(angle));
        
        Vector3f finalLightDir = RenderContext.getVector().set(lightDir);
        if (moonIntensity > sunIntensity) finalLightDir.negate();
        
        Vector3f ambCol = RenderContext.getVector().set(settings.ambientColor[0], settings.ambientColor[1], settings.ambientColor[2]);
        Vector3f currentAmbient = ambCol.mul(0.2f + 0.8f * sunIntensity + 0.3f * moonIntensity);
        
        state.updateLights(finalLightDir, currentAmbient);

        // Update Dynamic Light Manager
        com.za.zenith.world.lighting.LightManager.update(state.getWorld(), state.getWorld().getPlayer());
        
        // Add Sun/Moon as a directional light (Zero Alloc)
        sunSource.data.color.set(settings.sunLightColor[0], settings.sunLightColor[1], settings.sunLightColor[2])
                .mul(sunIntensity)
                .add(RenderContext.getVector().set(settings.moonLightColor[0], settings.moonLightColor[1], settings.moonLightColor[2]).mul(moonIntensity * 0.5f));
        sunSource.direction.set(finalLightDir);
        com.za.zenith.world.lighting.LightManager.addDirectionalLight(sunSource);
    }

    private Vector3f getSkyColor(SceneState state) {
        float cos = (float) Math.cos(((state.getWorld().getWorldTime() / com.za.zenith.world.WorldSettings.getInstance().dayLength) - 0.25f) * Math.PI * 2.0);
        float sunInt = Math.max(0.0f, cos);
        return RenderContext.getVector().set(0.02f, 0.02f, 0.05f).lerp(RenderContext.getVector().set(0.6f, 0.8f, 1.0f), sunInt);
    }

    public void setBreakingBlock(com.za.zenith.world.BlockPos pos, Block block, float progress, float timer, Vector3f localHitPoint, Vector3f localWeakSpot, Vector3f color, java.util.List<org.joml.Vector4f> history, World world) {
        overlaySystem.setBreakingBlock(pos, block, progress, timer, localHitPoint, localWeakSpot, color, history, world, atlas);
    }

    public void setPreviewBlock(com.za.zenith.world.BlockPos pos, Block block) {
        overlaySystem.setPreviewBlock(pos, block, atlas);
    }

    public void onChunkUnload(Chunk chunk) {
        chunkSystem.onChunkUnload(chunk);
    }

    public void rebuildMeshes() {
        MeshRegistry.rebuild();
    }

    public void cleanup() {
        chunkSystem.cleanup();
        entitySystem.cleanup();
        overlaySystem.cleanup();
        msaaFramebuffer.cleanup();
        resolveFramebuffer.cleanup();
        blockShader.cleanup();
        RenderContext.cleanup();
    }

    // Getters for external access (UI, etc.)
    public DynamicTextureAtlas getAtlas() { return atlas; }
    public UIRenderer getUIRenderer() { return uiRenderer; }
    public void toggleFXAA() { fxaaEnabled = !fxaaEnabled; }
}
