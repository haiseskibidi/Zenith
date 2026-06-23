package com.za.zenith.engine.graphics;

import com.za.zenith.entities.ItemEntity;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.FallingBlockEntity;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.physics.AABB;
import org.joml.Vector3f;

import java.util.List;

/**
 * EntityRenderSystem handles rendering of all dynamic entities,
 * including players, item entities, and delegates the first-person viewmodel render.
 */
public class EntityRenderSystem {
    Mesh playerMesh;
    private final ViewmodelRenderPass viewmodelRenderPass = new ViewmodelRenderPass();
    
    // L1 Entity Light Cache (Zero Alloc)
    private com.za.zenith.world.chunks.Chunk lastEntityChunk;
    private long lastEntityChunkPacked = Long.MIN_VALUE;

    public EntityRenderSystem() {
        createPlayerMesh();
    }

    public void updateHeat(float hand, float item) {
        viewmodelRenderPass.updateHeat(hand, item);
    }

    void setEntityLight(World world, Vector3f pos, Shader shader) {
        setEntityLight(world, pos, shader, false);
    }

    void setEntityLight(World world, Vector3f pos, Shader shader, boolean offsetLightForLanded) {
        int x = (int) Math.floor(pos.x), y = (int) Math.floor(pos.y), z = (int) Math.floor(pos.z);
        if (offsetLightForLanded) {
            y += 1;
        }
        long packed = com.za.zenith.world.chunks.ChunkPos.pack(x >> 4, z >> 4);

        if (packed != lastEntityChunkPacked) {
            lastEntityChunk = world.getChunkInternal(x >> 4, z >> 4);
            lastEntityChunkPacked = packed;
        }

        if (lastEntityChunk != null) {
            int lx = x & 15;
            int lz = z & 15;
            int clampY = Math.max(0, Math.min(com.za.zenith.world.chunks.Chunk.CHUNK_HEIGHT - 1, y));
            float aoVal = offsetLightForLanded ? 0.9f : 1.0f;

            Vector3f light = RenderContext.getVector().set(
                lastEntityChunk.getSunlight(lx, clampY, lz),
                lastEntityChunk.getBlockLight(lx, clampY, lz),
                aoVal
            );
            shader.setVector3f("uOverrideLight", light);
            shader.setFloat("uChunkSpawnTime", lastEntityChunk.getFirstSpawnTime());
        } else {
            shader.setVector3f("uOverrideLight", 15, 15, 1.0f);
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
                if (entity.isGroundEntity()) continue;

                Vector3f p = entity.getInterpolatedPosition(state.getAlpha(), RenderContext.getVector());
                AABB localBox = entity.getLocalBoundingBox();
                if (!state.getFrustum().testAab(
                    localBox.minX() + p.x, localBox.minY() + p.y, localBox.minZ() + p.z,
                    localBox.maxX() + p.x, localBox.maxY() + p.y, localBox.maxZ() + p.z
                )) continue;
                
                if (entity instanceof FallingBlockEntity fallingBlock) {
                    setEntityLight(world, p, blockShader, fallingBlock.isLanded());
                } else {
                    setEntityLight(world, p, blockShader, false);
                }
                blockShader.setInt("highlightPass", 0);
                
                if (entity instanceof com.za.zenith.entities.ScoutEntity scout) {
                    EntityRenderHelper.renderScoutEntity(scout, p, entity.getRotation().y, blockShader, playerMesh);
                } else if (entity instanceof com.za.zenith.entities.DecorationEntity decoration) {
                    EntityRenderHelper.renderDecorationEntity(decoration, p, entity.getRotation().y, blockShader, atlas);
                } else if (entity instanceof FallingBlockEntity fallingBlock) {
                    EntityRenderHelper.renderFallingBlockEntity(fallingBlock, p, blockShader, atlas, world);
                } else {
                    EntityRenderHelper.renderGeneralEntity(p, entity.getRotation().y, blockShader, playerMesh);
                }
            }
        }

        // 2. Render Ground Entities (Spatial Partitioning: Items, Resources)
        Player localPlayer = world.getPlayer();
        if (localPlayer != null) {
            int px = (int)Math.floor(localPlayer.getPosition().x / 16.0);
            int pz = (int)Math.floor(localPlayer.getPosition().z / 16.0);
            int r = 4; 
            for (int cx = px - r; cx <= px + r; cx++) {
                for (int cz = pz - r; cz <= pz + r; cz++) {
                    com.za.zenith.world.chunks.ChunkPos cp = new com.za.zenith.world.chunks.ChunkPos(cx, cz);
                    List<com.za.zenith.entities.Entity> groundEntities = world.getGroundEntitiesInChunk(cp);
                    if (groundEntities.isEmpty()) continue;

                    synchronized(groundEntities) {
                        for (int i = 0; i < groundEntities.size(); i++) {
                            com.za.zenith.entities.Entity entity = groundEntities.get(i);
                            Vector3f p = entity.getInterpolatedPosition(state.getAlpha(), RenderContext.getVector());
                            
                            AABB localBox = entity.getLocalBoundingBox();
                            if (!state.getFrustum().testAab(
                                localBox.minX() + p.x, localBox.minY() + p.y, localBox.minZ() + p.z,
                                localBox.maxX() + p.x, localBox.maxY() + p.y, localBox.maxZ() + p.z
                            )) continue;
                            
                            if (p.distanceSquared(localPlayer.getPosition()) > 2304.0f) continue;

                            setEntityLight(world, p, blockShader);
                            blockShader.setInt("highlightPass", 0);

                            if (entity instanceof ItemEntity itemEntity) {
                                EntityRenderHelper.renderItemEntity(itemEntity, p, state.getAlpha(), blockShader, atlas, world);
                            } else if (entity instanceof com.za.zenith.entities.ResourceEntity resource) {
                                EntityRenderHelper.renderResourceEntity(resource, p, state.getAlpha(), blockShader, atlas);
                            }
                        }
                    }
                }
            }
        }

        // 3. Render Remote Players
        EntityRenderHelper.renderRemotePlayers(world, networkClient, blockShader, playerMesh, this);
    }

    public void renderViewmodel(SceneState state, DynamicTextureAtlas atlas) {
        viewmodelRenderPass.render(state, atlas, this);
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
        viewmodelRenderPass.cleanup();
    }
}
