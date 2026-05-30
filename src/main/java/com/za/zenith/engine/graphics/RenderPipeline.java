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
    private final Framebuffer sunShaftsFramebuffer;
    private final PostProcessor postProcessor;
    private final UIRenderer uiRenderer;
    private final SkyRenderer skyRenderer = new SkyRenderer();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private final com.za.zenith.world.particles.AmbientParticleManager ambientParticleManager;
    
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
        this.sunShaftsFramebuffer = new Framebuffer(width, height, 1);
        this.postProcessor = new PostProcessor();
        this.uiRenderer = new UIRenderer();
        this.ambientParticleManager = new com.za.zenith.world.particles.AmbientParticleManager();
        
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
        ambientParticleManager.init();

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
        
        // 1.5. Update Ambient Particles (Dust Motes) - Disabled for realism
        com.za.zenith.world.generation.BiomeDefinition biome = world.getBiomeManager().getBiome((int)camera.getPosition().x, (int)camera.getPosition().z);
        // ambientParticleManager.update(deltaTime, camera, world, biome);
        
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
        
        // Ambient Particles (Dust Motes) - Disabled for realism
        // ambientParticleManager.render(camera, biome);
        
        // Viewmodel
        entitySystem.renderViewmodel(sceneState, atlas);

        msaaFramebuffer.unbind();
        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        glDisable(GL_MULTISAMPLE);

        // 3. Post-Processing & UI
        msaaFramebuffer.resolveTo(resolveFramebuffer);
        
        boolean sunShaftsEnabled = false;
        com.za.zenith.world.generation.AtmosphereSettings.SunShaftsSettings sunShaftsSettings = null;
        
        if (biome != null) {
            sunShaftsSettings = biome.getAtmosphere().getSunShafts();
            sunShaftsEnabled = sunShaftsSettings.isEnabled();
        }
        
        int finalInputColorTexture = resolveFramebuffer.getColorTextureId();
        
        if (sunShaftsEnabled && sunShaftsSettings != null) {
            sunShaftsFramebuffer.resize(window.getWidth(), window.getHeight());
            
            // NEGATE lightDirection to point from camera to sun in world space
            Vector3f sunDir = new Vector3f(sceneState.getLightDirection()).negate().normalize();
            org.joml.Matrix4f viewMatrix = camera.getViewMatrix(alpha);
            Vector3f viewDir = new Vector3f();
            viewMatrix.transformDirection(sunDir, viewDir);
            viewDir.normalize();
            
            // Calculate Height Attenuation (piecewise function for day/sunset crepuscular rays)
            float heightFactor = 0.0f;
            float sunHeight = sunDir.y;
            if (sunHeight >= 0.0f) {
                heightFactor = (float) Math.pow(1.0f - sunHeight, 1.3f);
            } else {
                // Crepuscular twilight rays after sunset, fading smoothly until sun is -0.25 height
                heightFactor = Math.max(0.0f, 1.0f + sunHeight / 0.25f);
            }

            boolean sunVisible = heightFactor > 0.0f;
            org.joml.Vector2f sunScreenPos = new org.joml.Vector2f(0.5f, 0.5f);
            
            if (sunVisible) {
                // Sun must be in front of the camera (viewDir.z < 0.0f)
                if (viewDir.z < 0.0f) {
                    org.joml.Vector4f sunViewPos = new org.joml.Vector4f(viewDir.x * 100.0f, viewDir.y * 100.0f, viewDir.z * 100.0f, 1.0f);
                    org.joml.Vector4f sunClipPos = new org.joml.Vector4f();
                    camera.getProjectionMatrix().transform(sunViewPos, sunClipPos);
                    
                    if (sunClipPos.w > 0.0f) {
                        float ndcX = sunClipPos.x / sunClipPos.w;
                        float ndcY = sunClipPos.y / sunClipPos.w;
                        sunScreenPos.set(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f);
                        
                        // Soft fade-out bounds: only enable if close to viewport
                        if (sunScreenPos.x < -0.7f || sunScreenPos.x > 1.7f || sunScreenPos.y < -0.7f || sunScreenPos.y > 1.7f) {
                            sunVisible = false;
                        }
                    } else {
                        sunVisible = false;
                    }
                } else {
                    sunVisible = false;
                }
            }
            
            if (sunVisible) {
                // Calculate Dynamic Blinding (Eye Adaptation / Glare)
                Vector3f cameraDir = camera.getDirection();
                float cosAngle = cameraDir.dot(sunDir);
                // Highly directional glare factor (only when looking straight at the sun)
                float glareFactor = (float) Math.pow(Math.max(0.0f, cosAngle), 16.0f);
                float exposureMultiplier = 1.0f + glareFactor * 0.9f; // Max 1.9x glare flare
                float weightMultiplier = 1.0f + glareFactor * 0.6f; // Max 1.6x ray density

                // Combine for realistic exposure and weight
                float customExposure = sunShaftsSettings.getExposure() * (0.15f + 0.6f * heightFactor) * exposureMultiplier;
                float customWeight = sunShaftsSettings.getWeight() * (0.25f + 0.5f * heightFactor) * weightMultiplier;

                sunShaftsFramebuffer.bind();
                org.joml.Matrix4f invProjection = new org.joml.Matrix4f(camera.getProjectionMatrix()).invert();
                postProcessor.processSunShafts(
                    resolveFramebuffer.getColorTextureId(), 
                    resolveFramebuffer.getDepthTextureId(), 
                    window.getWidth(), 
                    window.getHeight(), 
                    sunScreenPos, 
                    sunVisible, 
                    invProjection, 
                    viewDir, 
                    sunShaftsSettings,
                    customExposure,
                    customWeight
                );
                sunShaftsFramebuffer.unbind();
                finalInputColorTexture = sunShaftsFramebuffer.getColorTextureId();
            }
        }
        
        if (fxaaEnabled) postProcessor.processFXAA(finalInputColorTexture, resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight());
        else postProcessor.processPassthrough(finalInputColorTexture, resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight());

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
        
        float cosVal = (float)Math.cos(angle);
        float sunIntensity = Math.max(0.0f, cosVal);
        float moonIntensity = Math.max(0.0f, -cosVal);
        
        Vector3f finalLightDir = RenderContext.getVector().set(lightDir);
        if (moonIntensity > sunIntensity) finalLightDir.negate();
        
        // Define key colors
        Vector3f daySunColor = RenderContext.getVector().set(settings.sunLightColor[0], settings.sunLightColor[1], settings.sunLightColor[2]);
        Vector3f goldenSunColor = RenderContext.getVector().set(1.0f, 0.58f, 0.12f);
        Vector3f moonLightCol = RenderContext.getVector().set(settings.moonLightColor[0], settings.moonLightColor[1], settings.moonLightColor[2]);
        
        // Interpolate Sun Light Color during Golden Hour
        Vector3f currentSunColor = RenderContext.getVector().set(daySunColor);
        if (cosVal >= 0.0f && cosVal < 0.25f) {
            float t = cosVal / 0.25f;
            float goldenWeight = 1.0f - t; // stronger at the horizon (cos = 0)
            daySunColor.lerp(goldenSunColor, goldenWeight * 0.95f);
            currentSunColor.set(daySunColor);
        } else if (cosVal < 0.0f) {
            currentSunColor.set(0.0f, 0.0f, 0.0f); // sun is down
        }
        
        // Interpolate Ambient Color to cold blue-violet during Blue Hour
        Vector3f baseAmbient = RenderContext.getVector().set(settings.ambientColor[0], settings.ambientColor[1], settings.ambientColor[2]);
        Vector3f blueHourAmbient = RenderContext.getVector().set(0.08f, 0.10f, 0.24f);
        Vector3f currentAmbient = RenderContext.getVector().set(baseAmbient);
        
        if (cosVal < 0.0f && cosVal >= -0.15f) {
            // Blue Hour Ambient
            float t = (cosVal - (-0.15f)) / 0.15f; // [0, 1]
            blueHourAmbient.lerp(baseAmbient, t);
            currentAmbient.set(blueHourAmbient).mul(0.35f);
        } else {
            currentAmbient.mul(0.2f + 0.8f * sunIntensity + 0.3f * moonIntensity);
        }
        
        state.updateLights(finalLightDir, currentAmbient);

        // Update Dynamic Light Manager
        com.za.zenith.world.lighting.LightManager.update(state.getWorld(), state.getWorld().getPlayer());
        
        // Add Sun/Moon as a directional light (Zero Alloc)
        Vector3f lightColorResult = RenderContext.getVector();
        if (sunIntensity > 0.0f) {
            lightColorResult.set(currentSunColor).mul(sunIntensity);
        } else {
            lightColorResult.set(moonLightCol).mul(moonIntensity * 0.5f);
        }
        
        sunSource.data.color.set(lightColorResult);
        sunSource.direction.set(finalLightDir);
        com.za.zenith.world.lighting.LightManager.addDirectionalLight(sunSource);
    }

    private Vector3f getSkyColor(SceneState state) {
        float cos = (float) Math.cos(((state.getWorld().getWorldTime() / com.za.zenith.world.WorldSettings.getInstance().dayLength) - 0.25f) * Math.PI * 2.0);
        
        Vector3f nightColor = RenderContext.getVector().set(0.012f, 0.012f, 0.024f);
        Vector3f blueHourColor = RenderContext.getVector().set(0.04f, 0.06f, 0.22f);
        Vector3f goldenHourColor = RenderContext.getVector().set(0.85f, 0.52f, 0.25f);
        Vector3f dayColor = RenderContext.getVector().set(0.50f, 0.73f, 0.98f);
        
        Vector3f result = RenderContext.getVector();
        
        if (cos < -0.15f) {
            // Deep Night
            result.set(nightColor);
        } else if (cos < 0.0f) {
            // Blue Hour (interpolating from Night to Blue Hour)
            float t = (cos - (-0.15f)) / 0.15f; // [0, 1]
            nightColor.lerp(blueHourColor, t);
            result.set(nightColor);
        } else if (cos < 0.25f) {
            // Golden Hour (interpolating from Blue Hour/Dawn to Day)
            float t = cos / 0.25f; // [0, 1]
            // We want a gorgeous golden peak around cos = 0.05 - 0.12, so we shape it with smoothstep
            float goldenWeight = (float) Math.sin(t * Math.PI); // peak at 0.5 (cos = 0.125)
            
            Vector3f baseMix = RenderContext.getVector().set(blueHourColor).lerp(dayColor, t);
            baseMix.lerp(goldenHourColor, goldenWeight * 0.85f);
            result.set(baseMix);
        } else {
            // Daytime
            result.set(dayColor);
        }
        
        return result;
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
        sunShaftsFramebuffer.cleanup();
        ambientParticleManager.cleanup();
        blockShader.cleanup();
        RenderContext.cleanup();
    }

    // Getters for external access (UI, etc.)
    public DynamicTextureAtlas getAtlas() { return atlas; }
    public UIRenderer getUIRenderer() { return uiRenderer; }
    public void toggleFXAA() { fxaaEnabled = !fxaaEnabled; }
}
