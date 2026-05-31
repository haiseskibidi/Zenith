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
    private final CloudRenderSystem cloudRenderSystem = new CloudRenderSystem();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private final com.za.zenith.engine.graphics.vfx.RainRenderer rainRenderer = new com.za.zenith.engine.graphics.vfx.RainRenderer();
    private final com.za.zenith.world.particles.AmbientParticleManager ambientParticleManager;
    
    private boolean fxaaEnabled = false;
    private long frameCounter = 0;
    private float smoothSunVisibility = 1.0f;

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
        cloudRenderSystem.init();
        particleRenderer.init();
        ambientParticleManager.init();

        blockShader.use();
        blockShader.setInt("textureSampler", 0);
        blockShader.setInt("uHeightmap", 1); // Slot 1 for heightmap to avoid conflict with slot 0

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
        
        // Determine day/night state and moon phase for sky rendering and moon shafts
        com.za.zenith.world.WorldSettings ws = com.za.zenith.world.WorldSettings.getInstance();
        float timeRatio = world.getWorldTime() / ws.dayLength;
        float angle = (timeRatio - 0.25f) * (float)Math.PI * 2.0f;
        float cosVal = (float)Math.cos(angle);

        // Smooth isNight factor (0.0 = day, 1.0 = night). Transition from -0.15 to 0.15.
        float nightFactor = Math.clamp((-cosVal + 0.15f) / 0.30f, 0.0f, 1.0f);
        boolean isNight = nightFactor > 0.5f;

        long currentDay = (long) (world.getWorldTime() / ws.dayLength);
        float phaseProgress = (currentDay % 8) / 8.0f;
        float moonPhase = (float) Math.cos(phaseProgress * Math.PI * 2.0);

        // 1.5. Update Ambient Particles (Dust Motes) - Disabled for realism
        com.za.zenith.world.generation.BiomeDefinition biome = world.getBiomeManager().getBiome((int)camera.getPosition().x, (int)camera.getPosition().z);
        // ambientParticleManager.update(deltaTime, camera, world, biome);

        // Update UBO and Context (pass raw sun direction and night factor for smooth transition)
        RenderContext.update(world, camera, alpha, sceneState.getLightDirection(), sceneState.getSunDirection(), sceneState.getAmbientLight(), nightFactor);

        // 2. Main Rendering Pass (MSAA)
        msaaFramebuffer.resize(window.getWidth(), window.getHeight());
        resolveFramebuffer.resize(window.getWidth(), window.getHeight());
        
        // Update heightmap before world rendering for synchronized occlusion
        rainRenderer.update(camera, world);
        
        msaaFramebuffer.bind();
        glEnable(GL_MULTISAMPLE);
        glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE); // Disable during sky rendering to prevent MSAA stipple noise on sky/Moon gradients
        
        Vector3f skyColor = getSkyColor(sceneState);
        glClearColor(skyColor.x, skyColor.y, skyColor.z, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Sky (uses SceneState for absolute sun direction)
        skyRenderer.render(camera, sceneState, moonPhase, alpha);
        
        // Enable Alpha-to-Coverage now for smooth voxel foliage and transparent blocks
        glEnable(GL_SAMPLE_ALPHA_TO_COVERAGE);
        
        // World
        renderWorld(sceneState, networkClient, highlightedBlock, wrapper);
        
        // Clouds (Cel-shaded Borderlands style)
        cloudRenderSystem.render(camera, world, alpha);
        
        // Particles
        particleRenderer.render(camera, com.za.zenith.world.particles.ParticleManager.getInstance().getActiveParticles(), atlas, alpha, sceneState.getAmbientLight());
        
        // Rain Streaks
        rainRenderer.render(camera, world, alpha, deltaTime);
        
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
                // Perform fast CPU-side raycast to determine smooth visibility through foliage or blocks
                float currentVisibility = 1.0f;
                float startDist = 0.5f;
                float maxDist = 48.0f;
                float step = 0.4f;
                Vector3f eyePos = camera.getPosition();
                
                for (float d = startDist; d < maxDist; d += step) {
                    float px = eyePos.x + sunDir.x * d;
                    float py = eyePos.y + sunDir.y * d;
                    float pz = eyePos.z + sunDir.z * d;
                    
                    int ix = (int) Math.floor(px);
                    int iy = (int) Math.floor(py);
                    int iz = (int) Math.floor(pz);
                    
                    if (iy < 0 || iy >= 256) {
                        if (iy >= 256) break; // Sky is clear above 256
                        if (iy < 0) {
                            currentVisibility = 0.0f;
                            break;
                        }
                    }
                    
                    Block block = world.getBlock(ix, iy, iz);
                    if (!block.isAir()) {
                        com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                        if (def != null) {
                            if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_LEAVES)) {
                                // Leaf block: lets sunlight pass through, filters it gently
                                currentVisibility *= 0.85f;
                            } else if (def.isTransparent() || def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_TRANSLUCENT)) {
                                currentVisibility *= 0.90f;
                            } else if (def.is(com.za.zenith.world.blocks.BlockDefinition.FLAG_SOLID)) {
                                // Solid block: fully blocks the sun
                                currentVisibility = 0.0f;
                                break;
                            }
                        }
                    }
                }
                
                // Exponential moving average for frame-rate independent smooth adaptation
                float blendFactor = 1.0f - (float) Math.exp(-deltaTime * 7.5f);
                smoothSunVisibility = smoothSunVisibility + (currentVisibility - smoothSunVisibility) * blendFactor;
            } else {
                float blendFactor = 1.0f - (float) Math.exp(-deltaTime * 7.5f);
                smoothSunVisibility = smoothSunVisibility + (0.0f - smoothSunVisibility) * blendFactor;
            }
            
            if (sunVisible) {
                // Calculate Dynamic Blinding (Eye Adaptation / Glare)
                Vector3f cameraDir = camera.getDirection();
                float cosAngle = cameraDir.dot(sunDir);
                // Highly directional glare factor (only when looking straight at the sun)
                float glareFactor = (float) Math.pow(Math.max(0.0f, cosAngle), 16.0f);
                float exposureMultiplier = 1.0f + glareFactor * 0.3f; // Max 1.3x exposure
                float weightMultiplier = 1.0f + glareFactor * 0.2f; // Max 1.2x weight

                // Combine for realistic exposure and weight
                float hazeMultiplier = AtmosphereManager.getInstance().getHazeMultiplier();
                float customExposure = sunShaftsSettings.getExposure() * (0.15f + 0.6f * heightFactor) * exposureMultiplier * hazeMultiplier;
                float customWeight = sunShaftsSettings.getWeight() * (0.25f + 0.5f * heightFactor) * weightMultiplier * hazeMultiplier;

                // Decouple shaft color and scale down for dynamic Moon Shafts at night
                org.joml.Vector3f shaftColor = new org.joml.Vector3f(
                    sunShaftsSettings.getShaftColor()[0],
                    sunShaftsSettings.getShaftColor()[1],
                    sunShaftsSettings.getShaftColor()[2]
                );
                
                if (isNight) {
                    customExposure *= 0.40f; // Moon rays are softer
                    customWeight *= 0.45f;
                    shaftColor.set(ws.moonLightColor[0], ws.moonLightColor[1], ws.moonLightColor[2]);
                }

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
                    shaftColor,
                    customExposure,
                    customWeight,
                    smoothSunVisibility
                );
                sunShaftsFramebuffer.unbind();
                finalInputColorTexture = sunShaftsFramebuffer.getColorTextureId();
            }
        }
        
        Vector3f currentHorizonColor = AtmosphereManager.getInstance().getHorizonColor();
        float hazeDensity = 0.012f * AtmosphereManager.getInstance().getHazeMultiplier();
        if (fxaaEnabled) postProcessor.processFXAA(finalInputColorTexture, resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight(), currentHorizonColor, hazeDensity);
        else postProcessor.processPassthrough(finalInputColorTexture, resolveFramebuffer.getDepthTextureId(), window.getWidth(), window.getHeight(), currentHorizonColor, hazeDensity);

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
        
        // Pass rain intensity for wetness effects
        if (state.getWorld().getWeatherManager() != null) {
            float rainIntensity = state.getWorld().getWeatherManager().getRainIntensity();
            blockShader.setFloat("uRainIntensity", rainIntensity);
            
            // Pass heightmap data for synchronized occlusion
            if (rainIntensity > 0.05f) {
                blockShader.setInt("uHeightmap", 1);
                blockShader.setVector2f("uGridStart", new org.joml.Vector2f(rainRenderer.getStartX(), rainRenderer.getStartZ()));
                blockShader.setVector2f("uGridSize", new org.joml.Vector2f(rainRenderer.getGridSize(), rainRenderer.getGridSize()));
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, rainRenderer.getHeightmapTexId());
                glActiveTexture(GL_TEXTURE0); // Reset for other textures
            }
        } else {
            blockShader.setFloat("uRainIntensity", 0.0f);
        }

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
        
        // Hide recently broken blocks (holes) on GPU until their chunk mesh is fully rebuilt
        for (com.za.zenith.world.BlockPos rPos : overlaySystem.getRecentlyBrokenHoles().keySet()) {
            if (hiddenCount >= 16) break;
            if (bPos != null && rPos.equals(bPos)) continue;
            blockShader.setVector3f("uHiddenPositions[" + hiddenCount + "]", rPos.x(), rPos.y(), rPos.z());
            hiddenCount++;
        }
        
        for (var entry : state.getWorld().getBlockDamageMap().entrySet()) {
            if (hiddenCount >= 16) break;
            long packed = entry.getKey();
            int bx = com.za.zenith.world.World.unpackBlockX(packed);
            int by = com.za.zenith.world.World.unpackBlockY(packed);
            int bz = com.za.zenith.world.World.unpackBlockZ(packed);
            if (bPos != null && bx == bPos.x() && by == bPos.y() && bz == bPos.z()) continue;
            
            // Avoid duplicate GPU hiding if already hidden by recently broken holes
            boolean alreadyHidden = false;
            for (com.za.zenith.world.BlockPos rPos : overlaySystem.getRecentlyBrokenHoles().keySet()) {
                if (rPos.x() == bx && rPos.y() == by && rPos.z() == bz) {
                    alreadyHidden = true;
                    break;
                }
            }
            if (alreadyHidden) continue;
            
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
        // Update atmosphere first
        AtmosphereManager atmosphere = AtmosphereManager.getInstance();
        atmosphere.update(state.getWorld(), state.getDeltaTime());

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
        
        // Save the raw Sun direction (opposite of lightDir if lightDir points from sun to origin)
        // lightDir.y is -cosVal, so -lightDir.y is cosVal. Sun is up if cosVal > 0.
        Vector3f rawSunDir = RenderContext.getVector().set(lightDir).negate();
        state.updateSunDirection(rawSunDir);
        
        // Get dynamic colors from AtmosphereManager
        Vector3f currentSunColor = atmosphere.getSunColor();
        Vector3f currentAmbient = atmosphere.getAmbientColor();
        
        state.updateLights(finalLightDir, currentAmbient);

        // Update Dynamic Light Manager
        com.za.zenith.world.lighting.LightManager.update(state.getWorld(), state.getWorld().getPlayer());
        
        // Add Sun/Moon as a directional light (Zero Alloc)
        Vector3f lightColorResult = RenderContext.getVector();
        if (sunIntensity > 0.0f) {
            lightColorResult.set(currentSunColor).mul(sunIntensity);
        } else {
            Vector3f moonLightCol = RenderContext.getVector().set(settings.moonLightColor[0], settings.moonLightColor[1], settings.moonLightColor[2]);
            lightColorResult.set(moonLightCol).mul(moonIntensity * 0.5f);
        }
        
        sunSource.data.color.set(lightColorResult);
        sunSource.direction.set(finalLightDir);
        com.za.zenith.world.lighting.LightManager.addDirectionalLight(sunSource);
    }

    private Vector3f getSkyColor(SceneState state) {
        return AtmosphereManager.getInstance().getSkyColor();
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
        rainRenderer.cleanup();
        msaaFramebuffer.cleanup();
        resolveFramebuffer.cleanup();
        sunShaftsFramebuffer.cleanup();
        ambientParticleManager.cleanup();
        blockShader.cleanup();
        cloudRenderSystem.cleanup();
        RenderContext.cleanup();
    }

    // Getters for external access (UI, etc.)
    public DynamicTextureAtlas getAtlas() { return atlas; }
    public UIRenderer getUIRenderer() { return uiRenderer; }
    public void toggleFXAA() { fxaaEnabled = !fxaaEnabled; }
}
