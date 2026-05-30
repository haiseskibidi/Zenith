package com.za.zenith.engine.graphics;

import com.za.zenith.entities.ItemEntity;
import com.za.zenith.entities.Player;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.ChunkMeshGenerator;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.engine.graphics.model.Viewmodel;
import com.za.zenith.engine.graphics.model.ViewmodelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * EntityRenderSystem handles rendering of all dynamic entities,
 * including players, item entities, and the first-person viewmodel.
 */
public class EntityRenderSystem {
    private Mesh playerMesh;
    private final ViewmodelRenderer viewmodelRenderer = new ViewmodelRenderer();
    private final Shader viewmodelShader;
    
    // VFX Heat
    private float handHeat;
    private float itemHeat;

    // L1 Entity Light Cache (Zero Alloc)
    private com.za.zenith.world.chunks.Chunk lastEntityChunk;
    private long lastEntityChunkPacked = Long.MIN_VALUE;

    // Zero Alloc Viewmodel Lights
    private final List<com.za.zenith.world.lighting.LightSource> viewLights = new ArrayList<>();
    private final com.za.zenith.world.lighting.LightSource[] viewLightPool = new com.za.zenith.world.lighting.LightSource[com.za.zenith.world.lighting.LightManager.MAX_DYNAMIC_LIGHTS];

    public EntityRenderSystem() {
        this.viewmodelShader = new Shader("src/main/resources/shaders/viewmodel_vertex.glsl", "src/main/resources/shaders/viewmodel_fragment.glsl");
        createPlayerMesh();
        for (int i = 0; i < viewLightPool.length; i++) {
            viewLightPool[i] = new com.za.zenith.world.lighting.LightSource(new com.za.zenith.world.lighting.LightData());
        }
    }

    public void updateHeat(float hand, float item) {
        this.handHeat = hand;
        this.itemHeat = item;
    }

    private void setEntityLight(World world, Vector3f pos, Shader shader) {
        int x = (int) Math.floor(pos.x), y = (int) Math.floor(pos.y), z = (int) Math.floor(pos.z);
        long packed = com.za.zenith.world.chunks.ChunkPos.pack(x >> 4, z >> 4);

        if (packed != lastEntityChunkPacked) {
            // Use getChunkInternal to see chunks in STAGING phase (pre-lighting/pre-mesh)
            lastEntityChunk = world.getChunkInternal(x >> 4, z >> 4);
            lastEntityChunkPacked = packed;
        }

        if (lastEntityChunk != null) {
            int lx = x & 15;
            int lz = z & 15;
            // Clamp Y for safety during generation spikes
            int clampY = Math.max(0, Math.min(com.za.zenith.world.chunks.Chunk.CHUNK_HEIGHT - 1, y));

            Vector3f light = RenderContext.getVector().set(
                lastEntityChunk.getSunlight(lx, clampY, lz),
                lastEntityChunk.getBlockLight(lx, clampY, lz),
                1.0f
            );
            shader.setVector3f("uOverrideLight", light);
            shader.setFloat("uChunkSpawnTime", lastEntityChunk.getFirstSpawnTime());
        } else {
            // Fallback for totally unloaded areas
            shader.setVector3f("uOverrideLight", 15, 15, 1);
            shader.setFloat("uChunkSpawnTime", (float)world.getWorldTime());
        }
    }
    public void render(SceneState state, Shader blockShader, DynamicTextureAtlas atlas, GameClient networkClient) {
        World world = state.getWorld();
        RenderContext.resetBlockShader(blockShader);
        
        // 1. Render World Entities (Dynamic: Players, Scouts, etc.)
        List<com.za.zenith.entities.Entity> entities = world.getEntities();
        synchronized(entities) {
            for (int i = 0; i < entities.size(); i++) {
                com.za.zenith.entities.Entity entity = entities.get(i);
                // Only process non-ground entities here (Ground entities handled via spatial map below)
                if (entity.isGroundEntity()) continue;

                Vector3f p = entity.getInterpolatedPosition(state.getAlpha());
                if (!state.getFrustum().testAab(entity.getBoundingBox().getMin(), entity.getBoundingBox().getMax())) continue;
                
                setEntityLight(world, p, blockShader);
                blockShader.setInt("highlightPass", 0);
                
                if (entity instanceof com.za.zenith.entities.ScoutEntity scout) {
                    renderScoutEntity(scout, p, entity.getRotation().y, blockShader);
                } else if (entity instanceof com.za.zenith.entities.DecorationEntity decoration) {
                    renderDecorationEntity(decoration, p, entity.getRotation().y, blockShader, atlas);
                } else {
                    renderGeneralEntity(p, entity.getRotation().y, blockShader);
                }
            }
        }

        // 2. Render Ground Entities (Spatial Partitioning: Items, Resources)
        // We only iterate loaded chunks within render distance for massive performance gain
        Player localPlayer = world.getPlayer();
        if (localPlayer != null) {
            int px = (int)Math.floor(localPlayer.getPosition().x / 16.0);
            int pz = (int)Math.floor(localPlayer.getPosition().z / 16.0);
            // Render entities within 4 chunks (64m) - much more than enough for small items
            int r = 4; 
            for (int cx = px - r; cx <= px + r; cx++) {
                for (int cz = pz - r; cz <= pz + r; cz++) {
                    com.za.zenith.world.chunks.ChunkPos cp = new com.za.zenith.world.chunks.ChunkPos(cx, cz);
                    List<com.za.zenith.entities.Entity> groundEntities = world.getGroundEntitiesInChunk(cp);
                    if (groundEntities.isEmpty()) continue;

                    synchronized(groundEntities) {
                        for (int i = 0; i < groundEntities.size(); i++) {
                            com.za.zenith.entities.Entity entity = groundEntities.get(i);
                            Vector3f p = entity.getInterpolatedPosition(state.getAlpha());
                            
                            // Frustum Culling
                            if (!state.getFrustum().testAab(entity.getBoundingBox().getMin(), entity.getBoundingBox().getMax())) continue;
                            
                            // Extra Distance Culling: small items don't need to render far
                            if (p.distanceSquared(localPlayer.getPosition()) > 2304.0f) continue; // 48m radius

                            setEntityLight(world, p, blockShader);
                            blockShader.setInt("highlightPass", 0);

                            if (entity instanceof ItemEntity itemEntity) {
                                renderItemEntity(itemEntity, p, state.getAlpha(), blockShader, atlas, world);
                            } else if (entity instanceof com.za.zenith.entities.ResourceEntity resource) {
                                renderResourceEntity(resource, p, state.getAlpha(), blockShader, atlas);
                            }
                        }
                    }
                }
            }
        }

        // 3. Render Remote Players
        renderRemotePlayers(world, networkClient, blockShader);
    }

    private void renderScoutEntity(com.za.zenith.entities.ScoutEntity scout, Vector3f pos, float rotY, Shader shader) {
        Matrix4f model = RenderContext.getMatrix();
        model.translate(pos.x, pos.y, pos.z).rotateY(rotY);
        shader.setMatrix4f("model", model);
        shader.setInt("highlightPass", 1);
        
        Vector3f color = RenderContext.getVector();
        switch (scout.getCurrentState()) {
            case CHASE -> color.set(1, 0, 0);
            case SEARCH -> color.set(1, 0.5f, 0);
            default -> color.set(0.5f, 0.5f, 0.5f);
        }
        shader.setVector3f("highlightColor", color);
        playerMesh.render(shader);
        shader.setInt("highlightPass", 0);
    }

    private void renderItemEntity(ItemEntity entity, Vector3f interpPos, float alpha, Shader shader, DynamicTextureAtlas atlas, World world) {
        var player = world.getPlayer();
        // Balanced distance: 64 blocks (4096.0f) to avoid rendering too many items
        if (player != null && interpPos.distanceSquared(player.getPosition()) > 4096.0f) return;

        var item = entity.getStack().getItem();
        Mesh mesh = MeshRegistry.getItemMesh(item, atlas);
        
        if (mesh != null) {
            float age = entity.getAge() + alpha * 0.016f;
            float scale = item.getDroppedScale() * (item.isBlock() ? 0.25f : 0.45f);
            float yOff = item.isBlock() ? 0 : scale * 0.5f;
            var rot = entity.getInterpolatedRotation(alpha);
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(interpPos.x, interpPos.y + (float)Math.sin(age * 2.5f) * 0.02f + yOff, interpPos.z())
                 .rotateX(rot.x)
                 .rotateY(rot.y)
                 .rotateZ(rot.z)
                 .scale(scale);
            
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 0);
            mesh.render(shader);
        }
    }

    private void renderResourceEntity(com.za.zenith.entities.ResourceEntity resource, Vector3f pos, float alpha, Shader shader, DynamicTextureAtlas atlas) {
        var item = resource.getStack().getItem();
        Mesh mesh = MeshRegistry.getItemMesh(item, atlas); 
        if (mesh != null) {
            float s = item.getDroppedScale();
            Matrix4f model = RenderContext.getMatrix();
            model.translate(pos.x, pos.y + 0.03125f * s, pos.z).rotateY(resource.getRotation().y).rotateX(1.5708f).scale(s);
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 0);
            mesh.render(shader);
        }
    }

    private void renderDecorationEntity(com.za.zenith.entities.DecorationEntity decoration, Vector3f pos, float rotY, Shader shader, DynamicTextureAtlas atlas) {
        var def = decoration.getDefinition();
        if (def == null) return;
        
        Mesh mesh = MeshRegistry.getEntityMesh(def, atlas);
        
        if (mesh != null) {
            var s = def.visualScale();
            Matrix4f model = RenderContext.getMatrix();
            model.translate(pos.x, pos.y, pos.z).rotateY(rotY).scale(s.x, s.y, s.z);
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 0);
            mesh.render(shader);
        }
    }

    private void renderGeneralEntity(Vector3f pos, float rotY, Shader shader) {
        Matrix4f model = RenderContext.getMatrix();
        model.translate(pos.x, pos.y, pos.z).rotateY(rotY);
        shader.setMatrix4f("model", model);
        shader.setInt("highlightPass", 0);
        playerMesh.render(shader);
    }

    private void renderRemotePlayers(World world, GameClient client, Shader shader) {
        if (client == null || !client.isConnected()) return;
        for (var p : client.getRemotePlayers().values()) {
            Vector3f pos = RenderContext.getVector().set(p.getX(), p.getY(), p.getZ());
            setEntityLight(world, pos, shader);
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(pos.x, pos.y, pos.z).scale(0.6f, 1.8f, 0.6f);
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 1);
            shader.setVector3f("highlightColor", RenderContext.getVector().set(0.3f, 0.6f, 1));
            playerMesh.render(shader);
        }
        shader.setInt("highlightPass", 0);
    }

    public void renderViewmodel(SceneState state, DynamicTextureAtlas atlas) {
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
        var worldLights = com.za.zenith.world.lighting.LightManager.getActiveLights();
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
        viewmodelShader.setVector3f("uCondition", RenderContext.getVector().set(player.getDirt(), player.getBlood(), player.getWetness()));
        
        Viewmodel vm = player.getViewmodel();
        if (vm != null) {
            if (!vm.root.children.isEmpty() && vm.root.children.get(0).mesh == null) vm.initMeshes(atlas);
            ItemStack mainHand = player.getInventory().getSelectedItemStack();
            ItemStack offHand = player.getInventory().getStack(com.za.zenith.entities.Inventory.SLOT_OFFHAND);
            viewmodelRenderer.render(vm, viewmodelShader, atlas, player, mainHand, offHand, handHeat, itemHeat);
        }
        
        glDepthRange(0.0, 1.0);
        glEnable(GL_CULL_FACE);
    }

    private void createPlayerMesh() {
        float[] p = {-0.5f,-1,0.5f, 0.5f,-1,0.5f, 0.5f,1,0.5f, -0.5f,1,0.5f, -0.5f,-1,-0.5f, -0.5f,1,-0.5f, 0.5f,1,-0.5f, 0.5f,-1,-0.5f, -0.5f,-1,-0.5f, -0.5f,-1,0.5f, -0.5f,1,0.5f, -0.5f,1,-0.5f, 0.5f,-1,0.5f, 0.5f,-1,-0.5f, 0.5f,1,-0.5f, 0.5f,1,0.5f, -0.5f,1,0.5f, 0.5f,1,0.5f, 0.5f,1,-0.5f, -0.5f,1,-0.5f, -0.5f,-1,-0.5f, 0.5f,-1,-0.5f, 0.5f,-1,0.5f, -0.5f,-1,0.5f};
        float[] t = {0,0, 1,0, 1,1, 0,1, 1,0, 1,1, 0,1, 0,0, 0,0, 1,0, 1,1, 0,1, 0,0, 1,0, 1,1, 0,1, 0,1, 1,1, 1,0, 0,0, 0,0, 1,0, 1,1, 0,1};
        float[] n = {0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1, -1,0,0, -1,0,0, -1,0,0, -1,0,0, 1,0,0, 1,0,0, 1,0,0, 1,0,0, 0,1,0, 0,1,0, 0,1,0, 0,1,0, 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0};
        int[] ind = {0,1,2, 2,3,0, 4,5,6, 6,7,4, 8,9,10, 10,11,8, 12,13,14, 14,15,12, 16,17,18, 18,19,16, 20,21,22, 22,23,20};
        playerMesh = new Mesh(p, t, n, ind);
    }

    public void cleanup() {
        if (playerMesh != null) playerMesh.cleanup();
        viewmodelShader.cleanup();
    }
}
