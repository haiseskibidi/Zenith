package com.za.zenith.engine.graphics;

import com.za.zenith.entities.ItemEntity;
import com.za.zenith.entities.FallingBlockEntity;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.chunks.Chunk;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class EntityRenderHelper {

    public static void renderScoutEntity(com.za.zenith.entities.ScoutEntity scout, Vector3f pos, float rotY, Shader shader, Mesh playerMesh) {
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

    public static void renderItemEntity(ItemEntity entity, Vector3f interpPos, float alpha, Shader shader, DynamicTextureAtlas atlas, World world) {
        var player = world.getPlayer();
        if (player != null && interpPos.distanceSquared(player.getPosition()) > 4096.0f) return;
        ItemEntityStacker.render(entity, interpPos, alpha, shader, atlas);
    }

    public static void renderResourceEntity(com.za.zenith.entities.ResourceEntity resource, Vector3f pos, float alpha, Shader shader, DynamicTextureAtlas atlas) {
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

    public static void renderFallingBlockEntity(FallingBlockEntity entity, Vector3f pos, Shader shader, DynamicTextureAtlas atlas, World world) {
        if (entity.isLanded()) {
            int cx = (int) Math.floor(entity.getPosition().x) >> 4;
            int cz = (int) Math.floor(entity.getPosition().z) >> 4;
            Chunk chunk = world.getChunk(cx, cz);
            if (chunk == null || !chunk.needsMeshUpdate()) {
                return;
            }
        }
        Mesh mesh = MeshRegistry.getBlockMesh(entity.getBlockType(), atlas);
        if (mesh != null) {
            Matrix4f model = RenderContext.getMatrix();
            model.translate(pos.x, pos.y, pos.z);
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 0);
            mesh.render(shader);
        }
    }

    public static void renderDecorationEntity(com.za.zenith.entities.DecorationEntity decoration, Vector3f pos, float rotY, Shader shader, DynamicTextureAtlas atlas) {
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

    public static void renderGeneralEntity(Vector3f pos, float rotY, Shader shader, Mesh playerMesh) {
        Matrix4f model = RenderContext.getMatrix();
        model.translate(pos.x, pos.y, pos.z).rotateY(rotY);
        shader.setMatrix4f("model", model);
        shader.setInt("highlightPass", 0);
        playerMesh.render(shader);
    }

    public static void renderRemotePlayers(World world, GameClient client, Shader shader, Mesh playerMesh, EntityRenderSystem parent) {
        if (client == null || !client.isConnected()) return;
        for (var p : client.getRemotePlayers().values()) {
            Vector3f pos = RenderContext.getVector().set(p.getX(), p.getY(), p.getZ());
            parent.setEntityLight(world, pos, shader);
            
            Matrix4f model = RenderContext.getMatrix();
            model.translate(pos.x, pos.y, pos.z).scale(0.6f, 1.8f, 0.6f);
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 1);
            shader.setVector3f("highlightColor", RenderContext.getVector().set(0.3f, 0.6f, 1));
            playerMesh.render(shader);
        }
        shader.setInt("highlightPass", 0);
    }
}
