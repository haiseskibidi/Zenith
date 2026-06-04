package com.za.zenith.engine.graphics;

import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.physics.AABB;
import com.za.zenith.world.physics.RaycastResult;
import com.za.zenith.world.physics.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;

public class BlockHighlightRenderer {
    private final Map<VoxelShape, Mesh> highlightMeshes = new ConcurrentHashMap<>();

    public void render(Camera camera, World world, RaycastResult highlightedBlock, Shader blockShader, Matrix4f modelMatrix, float alpha, BlockPos breakingPos, Block currentBreakingBlock, float wobbleTimer) {
        BlockPos pos = highlightedBlock.getBlockPos();
        Block block = world.getBlock(pos);
        VoxelShape shape = block.getShape();

        if (shape == null || shape.getBoxes().isEmpty()) return;

        Mesh mesh = highlightMeshes.computeIfAbsent(shape, this::createMeshForShape);

        glDepthMask(false);
        glEnable(GL_CULL_FACE); // Fix RGB frame when inside block
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        glLineWidth(3.0f);
        
        blockShader.use();
        blockShader.setMatrix4f("projection", camera.getProjectionMatrix());
        blockShader.setMatrix4f("view", camera.getViewMatrix(alpha));
        blockShader.setBoolean("uIsCompressed", false);
        blockShader.setFloat("uChunkSpawnTime", -100.0f); // Disable reveal animation
        
        setNeighborLight(world, pos, highlightedBlock.getSide(), blockShader);
        
        com.za.zenith.world.blocks.BlockDefinition blockDef = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
        blockShader.setFloat("uSwayOverride", (blockDef != null && blockDef.isSway()) ? 1.0f : 0.0f);

        boolean isProxy = false;
        float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
        float offsetX = 0.0f, offsetY = 0.0f, offsetZ = 0.0f;
        float shake = 0.0f;

        if (pos.equals(breakingPos) && currentBreakingBlock != null) {
            isProxy = true;
            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(currentBreakingBlock.getType());
            String animName = (def != null && def.getWobbleAnimation() != null) ? def.getWobbleAnimation() : "block_wobble";

            com.za.zenith.entities.parkour.animation.AnimationProfile profile = com.za.zenith.entities.parkour.animation.AnimationRegistry.get(animName);

            if (profile != null) {
                float normTimer = wobbleTimer / Math.max(0.001f, profile.getDuration());
                scaleX = profile.evaluate("scale_x", normTimer, 1.0f);
                scaleY = profile.evaluate("scale_y", normTimer, 1.0f);
                scaleZ = profile.evaluate("scale_z", normTimer, 1.0f);
                offsetX = profile.evaluate("offset_x", normTimer, 0.0f);
                offsetY = profile.evaluate("offset_y", normTimer, 0.0f);
                offsetZ = profile.evaluate("offset_z", normTimer, 0.0f);
                shake = profile.evaluate("shake", normTimer, 0.0f);
            }
        }

        blockShader.setVector3f("uWobbleScale", new Vector3f(scaleX, scaleY, scaleZ));
        blockShader.setVector3f("uWobbleOffset", new Vector3f(offsetX, offsetY, offsetZ));
        blockShader.setFloat("uWobbleShake", shake);
        blockShader.setFloat("uWobbleTime", wobbleTimer);
        blockShader.setBoolean("uIsProxy", isProxy);
        
        // Vertices are now shifted by -0.5 on X and Z, so we center at pos.x + 0.5 and pos.z + 0.5.
        modelMatrix.identity()
            .translate(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f)
            .scale(1.002f);
            
        blockShader.setMatrix4f("model", modelMatrix);
        mesh.render(GL_LINES, blockShader);
        
        blockShader.setVector3f("uOverrideLight", new Vector3f(-1.0f, -1.0f, -1.0f));
        blockShader.setBoolean("uIsProxy", false);
        blockShader.setFloat("uSwayOverride", -1.0f);
        blockShader.setInt("highlightPass", 0);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glEnable(GL_CULL_FACE);
        glDepthMask(true);
    }

    private void setNeighborLight(World world, BlockPos pos, com.za.zenith.utils.Direction face, Shader blockShader) {
        int nx = pos.x() + face.getDx();
        int ny = pos.y() + face.getDy();
        int nz = pos.z() + face.getDz();
        
        com.za.zenith.world.chunks.ChunkPos cp = com.za.zenith.world.chunks.ChunkPos.fromBlockPos(nx, nz);
        com.za.zenith.world.chunks.Chunk chunk = world.getChunkInternal(cp.x(), cp.z());
        
        if (chunk != null && ny >= 0 && ny < com.za.zenith.world.chunks.Chunk.CHUNK_HEIGHT) {
            float sun = chunk.getSunlight(nx & 15, ny, nz & 15);
            float block = chunk.getBlockLight(nx & 15, ny, nz & 15);
            blockShader.setVector3f("uOverrideLight", new Vector3f(sun, block, 1.0f));
        } else {
            blockShader.setVector3f("uOverrideLight", new Vector3f(15.0f, 0.0f, 1.0f));
        }
    }

    private Mesh createMeshForShape(VoxelShape shape) {
        if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
            return createRampMesh(shape);
        }

        List<Float> positionsList = new ArrayList<>();
        List<Integer> indicesList = new ArrayList<>();
        int vertexOffset = 0;

        java.util.Set<Float> xSet = new java.util.TreeSet<>();
        java.util.Set<Float> ySet = new java.util.TreeSet<>();
        java.util.Set<Float> zSet = new java.util.TreeSet<>();

        for (AABB box : shape.getBoxes()) {
            xSet.add(box.getMin().x); xSet.add(box.getMax().x);
            ySet.add(box.getMin().y); ySet.add(box.getMax().y);
            zSet.add(box.getMin().z); zSet.add(box.getMax().z);
        }

        Float[] X = xSet.toArray(new Float[0]);
        Float[] Y = ySet.toArray(new Float[0]);
        Float[] Z = zSet.toArray(new Float[0]);
        
        float eps = 0.001f;

        // X-axis segments
        for (int i = 0; i < X.length - 1; i++) {
            float mx = (X[i] + X[i+1]) / 2.0f;
            for (float y : Y) {
                for (float z : Z) {
                    int count = 0;
                    if (contains(shape, mx, y + eps, z + eps)) count++;
                    if (contains(shape, mx, y + eps, z - eps)) count++;
                    if (contains(shape, mx, y - eps, z + eps)) count++;
                    if (contains(shape, mx, y - eps, z - eps)) count++;
                    if (count == 1 || count == 3) {
                        positionsList.add(X[i]); positionsList.add(y); positionsList.add(z);
                        positionsList.add(X[i+1]); positionsList.add(y); positionsList.add(z);
                        indicesList.add(vertexOffset++); indicesList.add(vertexOffset++);
                    }
                }
            }
        }

        // Y-axis segments
        for (int j = 0; j < Y.length - 1; j++) {
            float my = (Y[j] + Y[j+1]) / 2.0f;
            for (float x : X) {
                for (float z : Z) {
                    int count = 0;
                    if (contains(shape, x + eps, my, z + eps)) count++;
                    if (contains(shape, x + eps, my, z - eps)) count++;
                    if (contains(shape, x - eps, my, z + eps)) count++;
                    if (contains(shape, x - eps, my, z - eps)) count++;
                    if (count == 1 || count == 3) {
                        positionsList.add(x); positionsList.add(Y[j]); positionsList.add(z);
                        positionsList.add(x); positionsList.add(Y[j+1]); positionsList.add(z);
                        indicesList.add(vertexOffset++); indicesList.add(vertexOffset++);
                    }
                }
            }
        }

        // Z-axis segments
        for (int k = 0; k < Z.length - 1; k++) {
            float mz = (Z[k] + Z[k+1]) / 2.0f;
            for (float x : X) {
                for (float y : Y) {
                    int count = 0;
                    if (contains(shape, x + eps, y + eps, mz)) count++;
                    if (contains(shape, x + eps, y - eps, mz)) count++;
                    if (contains(shape, x - eps, y + eps, mz)) count++;
                    if (contains(shape, x - eps, y - eps, mz)) count++;
                    if (count == 1 || count == 3) {
                        positionsList.add(x); positionsList.add(y); positionsList.add(Z[k]);
                        positionsList.add(x); positionsList.add(y); positionsList.add(Z[k+1]);
                        indicesList.add(vertexOffset++); indicesList.add(vertexOffset++);
                    }
                }
            }
        }

        float[] posArray = new float[positionsList.size()];
        for (int i = 0; i < positionsList.size(); i += 3) {
            posArray[i] = positionsList.get(i) - 0.5f;
            posArray[i+1] = positionsList.get(i+1);
            posArray[i+2] = positionsList.get(i+2) - 0.5f;
        }

        int[] indArray = new int[indicesList.size()];
        for (int i = 0; i < indicesList.size(); i++) indArray[i] = indicesList.get(i);

        int numVerts = vertexOffset; 
        float[] tcArray = new float[numVerts * 4];
        float[] wArray = new float[numVerts];
        
        // Calculate maxY for the entire shape to determine weights
        float shapeMaxY = -1e9f;
        for (AABB box : shape.getBoxes()) shapeMaxY = Math.max(shapeMaxY, box.getMax().y);

        for (int i = 0; i < numVerts; i++) {
            tcArray[i * 4 + 0] = 0.0f;
            tcArray[i * 4 + 1] = 0.0f;
            tcArray[i * 4 + 2] = 0.0f;
            tcArray[i * 4 + 3] = 1.0f; // Enable swaying in attribute, control by uniform
            
            // Weight is 1.0 if the vertex is at the top of the block
            float vy = posArray[i * 3 + 1];
            wArray[i] = (vy > 0.5f) ? 1.0f : 0.0f;
        }
        float[] nArray = new float[numVerts * 3];
        float[] btArray = new float[numVerts];

        return new Mesh(posArray, tcArray, nArray, btArray, new float[numVerts], wArray, indArray);
    }

    private boolean contains(VoxelShape shape, float x, float y, float z) {
        for (AABB box : shape.getBoxes()) {
            if (x >= box.getMin().x && x <= box.getMax().x &&
                y >= box.getMin().y && y <= box.getMax().y &&
                z >= box.getMin().z && z <= box.getMax().z) {
                return true;
            }
        }
        return false;
    }

    private Mesh createRampMesh(VoxelShape shape) {
        List<Float> positionsList = new ArrayList<>();
        List<Integer> indicesList = new ArrayList<>();
        
        byte dir = (byte)(shape.getMetadata() & 0x0F);
        float H = 1.0f; // Height of the ramp
        
        // Base edges (Y = 0)
        addEdge(positionsList, indicesList, 0, 0, 0,  1, 0, 0);
        addEdge(positionsList, indicesList, 1, 0, 0,  1, 0, 1);
        addEdge(positionsList, indicesList, 1, 0, 1,  0, 0, 1);
        addEdge(positionsList, indicesList, 0, 0, 1,  0, 0, 0);
        
        switch (dir) {
            case Block.DIR_EAST:
                addEdge(positionsList, indicesList, 1, 0, 0,  1, H, 0);
                addEdge(positionsList, indicesList, 1, 0, 1,  1, H, 1);
                addEdge(positionsList, indicesList, 0, 0, 0,  1, H, 0);
                addEdge(positionsList, indicesList, 0, 0, 1,  1, H, 1);
                addEdge(positionsList, indicesList, 1, H, 0,  1, H, 1);
                break;
            case Block.DIR_WEST:
                addEdge(positionsList, indicesList, 0, 0, 0,  0, H, 0);
                addEdge(positionsList, indicesList, 0, 0, 1,  0, H, 1);
                addEdge(positionsList, indicesList, 1, 0, 0,  0, H, 0);
                addEdge(positionsList, indicesList, 1, 0, 1,  0, H, 1);
                addEdge(positionsList, indicesList, 0, H, 0,  0, H, 1);
                break;
            case Block.DIR_SOUTH:
                addEdge(positionsList, indicesList, 0, 0, 1,  0, H, 1);
                addEdge(positionsList, indicesList, 1, 0, 1,  1, H, 1);
                addEdge(positionsList, indicesList, 0, 0, 0,  0, H, 1);
                addEdge(positionsList, indicesList, 1, 0, 0,  1, H, 1);
                addEdge(positionsList, indicesList, 0, H, 1,  1, H, 1);
                break;
            case Block.DIR_NORTH:
            default:
                addEdge(positionsList, indicesList, 0, 0, 0,  0, H, 0);
                addEdge(positionsList, indicesList, 1, 0, 0,  1, H, 0);
                addEdge(positionsList, indicesList, 0, 0, 1,  0, H, 0);
                addEdge(positionsList, indicesList, 1, 0, 1,  1, H, 0);
                addEdge(positionsList, indicesList, 0, H, 0,  1, H, 0);
                break;
        }

        int numVerts = positionsList.size() / 3;
        float[] posArray = new float[positionsList.size()];
        for (int i = 0; i < positionsList.size(); i += 3) {
            posArray[i] = positionsList.get(i) - 0.5f;
            posArray[i+1] = positionsList.get(i+1);
            posArray[i+2] = positionsList.get(i+2) - 0.5f;
        }

        int[] indArray = new int[indicesList.size()];
        for (int i = 0; i < indicesList.size(); i++) indArray[i] = indicesList.get(i);

        float[] tcArray = new float[numVerts * 4];
        float[] wArray = new float[numVerts];
        for (int i = 0; i < numVerts; i++) {
            tcArray[i * 4 + 0] = 0.0f;
            tcArray[i * 4 + 1] = 0.0f;
            tcArray[i * 4 + 2] = 0.0f;
            tcArray[i * 4 + 3] = 1.0f;
            wArray[i] = (posArray[i * 3 + 1] > 0.5f) ? 1.0f : 0.0f;
        }
        float[] nArray = new float[numVerts * 3];
        float[] btArray = new float[numVerts];

        return new Mesh(posArray, tcArray, nArray, btArray, new float[numVerts], wArray, indArray);
    }

    private void addEdge(List<Float> positionsList, List<Integer> indicesList, 
                         float x1, float y1, float z1, float x2, float y2, float z2) {
        int startIdx = positionsList.size() / 3;
        positionsList.add(x1); positionsList.add(y1); positionsList.add(z1);
        positionsList.add(x2); positionsList.add(y2); positionsList.add(z2);
        indicesList.add(startIdx);
        indicesList.add(startIdx + 1);
    }

    public void cleanup() {
        for (Mesh mesh : highlightMeshes.values()) {
            mesh.cleanup();
        }
        highlightMeshes.clear();
    }
}


