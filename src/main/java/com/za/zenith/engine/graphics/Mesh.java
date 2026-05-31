package com.za.zenith.engine.graphics;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {
    public enum VertexFormat {
        STANDARD, // 16 floats (64 bytes)
        COMPRESSED_CHUNK // 7 floats (28 bytes)
    }

    private final int vaoId;
    private final int vboId;
    private final int eboId;
    private final int vertexCount;
    private final int baseVertex;
    private final int firstIndex;
    private final int poolVersion;
    private final MeshPool pool;
    private float minX, minY, minZ;
    private float maxX, maxY, maxZ;
    private float gripWidth = -1.0f;
    private org.joml.Vector3f graspOffset = new org.joml.Vector3f(0);
    private VertexFormat format = VertexFormat.STANDARD;

    public int getBaseVertex() { return baseVertex; }
    public int getFirstIndex() { return firstIndex; }
    public int getVertexCount() { return vertexCount; }
    public int getPoolVersion() { return poolVersion; }
    public MeshPool getPool() { return pool; }
    public int getVaoId() { return vaoId; }
    
    public void setGraspOffset(org.joml.Vector3f offset) { this.graspOffset = offset; }
    public org.joml.Vector3f getGraspOffset() { return graspOffset; }
    public boolean isCompressed() { return format == VertexFormat.COMPRESSED_CHUNK; }

    /**
     * Pooling constructor for COMPRESSED_CHUNK format.
     */
    public Mesh(MeshPool pool, FloatBuffer dataBuffer, IntBuffer indicesBuffer, org.joml.Vector3f min, org.joml.Vector3f max) {
        this.format = VertexFormat.COMPRESSED_CHUNK;
        this.pool = pool;
        
        MeshPool.Allocation alloc = pool.allocate(dataBuffer, indicesBuffer);
        this.vertexCount = alloc.indexCount();
        this.baseVertex = alloc.baseVertex();
        this.firstIndex = alloc.firstIndex();
        this.poolVersion = alloc.poolVersion();
        
        this.vaoId = -1;
        this.vboId = -1;
        this.eboId = -1;
        
        this.minX = min.x; this.minY = min.y; this.minZ = min.z;
        this.maxX = max.x; this.maxY = max.y; this.maxZ = max.z;
    }
    
    public Mesh(FloatBuffer dataBuffer, int dataLen, IntBuffer indicesBuffer, int idxLen, org.joml.Vector3f min, org.joml.Vector3f max, VertexFormat format) {
        this.format = format;
        this.pool = null;
        this.baseVertex = 0;
        this.firstIndex = 0;
        this.poolVersion = -1;
        if (idxLen == 0 || dataLen == 0) {
            this.vertexCount = 0;
            this.vaoId = -1;
            this.vboId = -1;
            this.eboId = -1;
            return;
        }
        this.vertexCount = idxLen;
        this.minX = min.x; this.minY = min.y; this.minZ = min.z;
        this.maxX = max.x; this.maxY = max.y; this.maxZ = max.z;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, dataBuffer, GL_STATIC_DRAW);
        
        setupAttributes();
        
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        
        glBindVertexArray(0);
    }
    
    public Mesh(FloatBuffer dataBuffer, int dataLen, IntBuffer indicesBuffer, int idxLen, org.joml.Vector3f min, org.joml.Vector3f max) {
        this(dataBuffer, dataLen, indicesBuffer, idxLen, min, max, VertexFormat.STANDARD);
    }

    public Mesh(FloatBuffer dataBuffer, int dataLen, IntBuffer indicesBuffer, int idxLen) {
        this.pool = null;
        this.baseVertex = 0;
        this.firstIndex = 0;
        this.poolVersion = -1;
        if (idxLen == 0 || dataLen == 0) {
            this.vertexCount = 0;
            this.vaoId = -1;
            this.vboId = -1;
            this.eboId = -1;
            return;
        }
        this.vertexCount = idxLen;
        
        calculateAABB(dataBuffer, dataLen);

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, dataBuffer, GL_STATIC_DRAW);
        
        setupAttributes();
        
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        
        glBindVertexArray(0);
    }

    private void setupAttributes() {
        if (format == VertexFormat.COMPRESSED_CHUNK) {
            int stride = 7 * Float.BYTES;
            // location = 0: position (vec3)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            // location = 1: packedTex (float)
            glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            // location = 2: packedLayers (float)
            glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 4 * Float.BYTES);
            glEnableVertexAttribArray(2);
            // location = 3: packedBlock (float)
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
            glEnableVertexAttribArray(3);
            // location = 4: packedLight (float)
            glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 6 * Float.BYTES);
            glEnableVertexAttribArray(4);
        } else {
            int stride = 16 * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 7 * Float.BYTES);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 10 * Float.BYTES);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 11 * Float.BYTES);
            glEnableVertexAttribArray(4);
            // location = 5 is reserved for aInstanceData in vertex.glsl
            glVertexAttribPointer(6, 1, GL_FLOAT, false, stride, 12 * Float.BYTES);
            glEnableVertexAttribArray(6);
            glVertexAttribPointer(7, 2, GL_FLOAT, false, stride, 13 * Float.BYTES);
            glEnableVertexAttribArray(7);
            glVertexAttribPointer(8, 1, GL_FLOAT, false, stride, 15 * Float.BYTES);
            glEnableVertexAttribArray(8);
        }
    }

    private void calculateAABB(FloatBuffer dataBuffer, int dataLen) {
        minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; minZ = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE; maxZ = -Float.MAX_VALUE;
        
        int step = (format == VertexFormat.COMPRESSED_CHUNK) ? 7 : 16;
        dataBuffer.mark();
        for (int i = 0; i < dataLen; i += step) {
            float px = dataBuffer.get(i);
            float py = dataBuffer.get(i+1);
            float pz = dataBuffer.get(i+2);
            minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
        }
        dataBuffer.reset();
    }

    public Mesh(float[] interleavedData, int dataLen, int[] indices, int idxLen, boolean isInterleaved) {
        this.pool = null;
        this.baseVertex = 0;
        this.firstIndex = 0;
        this.poolVersion = -1;
        if (idxLen == 0 || dataLen == 0) {
            this.vertexCount = 0;
            this.vaoId = -1;
            this.vboId = -1;
            this.eboId = -1;
            return;
        }
        this.vertexCount = idxLen;
        
        minX = Float.MAX_VALUE; minY = Float.MAX_VALUE; minZ = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE; maxY = -Float.MAX_VALUE; maxZ = -Float.MAX_VALUE;
        int step = (format == VertexFormat.COMPRESSED_CHUNK) ? 7 : 16;
        for (int i = 0; i < dataLen; i += step) {
            float px = interleavedData[i];
            float py = interleavedData[i+1];
            float pz = interleavedData[i+2];
            minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
        }

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        
        vboId = glGenBuffers();
        FloatBuffer dataBuffer = com.za.zenith.utils.NioBufferPool.rentFloat(dataLen);
        dataBuffer.put(interleavedData, 0, dataLen).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, dataBuffer, GL_STATIC_DRAW);
        
        setupAttributes();
        com.za.zenith.utils.NioBufferPool.returnFloat(dataBuffer);
        
        eboId = glGenBuffers();
        IntBuffer indicesBuffer = com.za.zenith.utils.NioBufferPool.rentInt(idxLen);
        indicesBuffer.put(indices, 0, idxLen).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        com.za.zenith.utils.NioBufferPool.returnInt(indicesBuffer);
        
        glBindVertexArray(0);
    }

    public Mesh(float[] positions, int posLen, float[] texCoords, int texLen, float[] normals, int normLen, float[] blockTypes, int btLen, float[] neighborData, int ndLen, float[] weights, int wLen, float[] lightData, int lLen, float[] aoData, int aoLen, int[] indices, int idxLen) {
        this(interleave(positions, posLen, texCoords, texLen, normals, normLen, blockTypes, btLen, neighborData, ndLen, weights, wLen, lightData, lLen, aoData, aoLen), (posLen / 3) * 16, indices, idxLen, true);
    }

    private static float[] interleave(float[] positions, int posLen, float[] texCoords, int texLen, float[] normals, int normLen, float[] blockTypes, int btLen, float[] neighborData, int ndLen, float[] weights, int wLen, float[] lightData, int lLen, float[] aoData, int aoLen) {
        int vertexCount = posLen / 3;
        int texComponents = texLen > 0 ? (texLen / vertexCount) : 0;
        float[] interleaved = new float[vertexCount * 16];
        
        for (int i = 0; i < vertexCount; i++) {
            int base = i * 16;
            interleaved[base] = positions[i*3];
            interleaved[base+1] = positions[i*3+1];
            interleaved[base+2] = positions[i*3+2];
            
            if (texComponents == 2) {
                interleaved[base+3] = texCoords[i*2];
                interleaved[base+4] = texCoords[i*2+1];
                interleaved[base+5] = 0;
                interleaved[base+6] = -1;
            } else if (texComponents == 3) {
                interleaved[base+3] = texCoords[i*3];
                interleaved[base+4] = texCoords[i*3+1];
                interleaved[base+5] = texCoords[i*3+2];
                interleaved[base+6] = -1;
            } else if (texComponents == 4) {
                interleaved[base+3] = texCoords[i*4];
                interleaved[base+4] = texCoords[i*4+1];
                interleaved[base+5] = texCoords[i*4+2];
                interleaved[base+6] = texCoords[i*4+3];
            }
            
            if (normLen > 0) {
                interleaved[base+7] = normals[i*3];
                interleaved[base+8] = normals[i*3+1];
                interleaved[base+9] = normals[i*3+2];
            }
            
            if (btLen > 0) interleaved[base+10] = blockTypes[i];
            if (ndLen > 0) interleaved[base+11] = neighborData[i];
            if (wLen > 0) interleaved[base+12] = weights[i];
            if (lLen > 0) {
                interleaved[base+13] = lightData[i*2];
                interleaved[base+14] = lightData[i*2+1];
            } else {
                interleaved[base+13] = 15;
                interleaved[base+14] = 15;
            }
            if (aoLen > 0) interleaved[base+15] = aoData[i];
            else interleaved[base+15] = 1.0f;
        }
        return interleaved;
    }

    public Mesh(float[] positions, float[] texCoords, float[] normals, int[] indices) {
        this(positions, positions.length, texCoords, texCoords.length, normals, normals.length, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3 * 2], positions.length / 3 * 2, new float[positions.length / 3], positions.length / 3, indices, indices.length);
    }
    
    public Mesh(float[] positions, float[] texCoords, float[] normals, float[] blockTypes, int[] indices) {
        this(positions, positions.length, texCoords, texCoords.length, normals, normals.length, blockTypes, blockTypes.length, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3 * 2], positions.length / 3 * 2, new float[positions.length / 3], positions.length / 3, indices, indices.length);
    }

    public Mesh(float[] positions, float[] texCoords, float[] normals, float[] blockTypes, float[] neighborData, int[] indices) {
        this(positions, positions.length, texCoords, texCoords.length, normals, normals.length, blockTypes, blockTypes.length, neighborData, neighborData.length, new float[positions.length / 3], positions.length / 3, new float[positions.length / 3 * 2], positions.length / 3 * 2, new float[positions.length / 3], positions.length / 3, indices, indices.length);
    }

    public Mesh(float[] positions, float[] texCoords, float[] normals, float[] blockTypes, float[] neighborData, float[] weights, int[] indices) {
        this(positions, positions.length, texCoords, texCoords.length, normals, normals.length, blockTypes, blockTypes.length, neighborData, neighborData.length, weights, weights.length, new float[positions.length / 3 * 2], positions.length / 3 * 2, new float[positions.length / 3], positions.length / 3, indices, indices.length);
    }

    public Mesh(float[] positions, float[] texCoords, float[] normals, float[] blockTypes, float[] neighborData, float[] weights, float[] lightData, float[] aoData, int[] indices) {
        this(positions, positions.length, texCoords, texCoords.length, normals, normals.length, blockTypes, blockTypes.length, neighborData, neighborData.length, weights, weights.length, lightData, lightData.length, aoData, aoData.length, indices, indices.length);
    }
    
    public void render() {
        if (vaoId == -1) return;
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }
    
    public void render(int glMode) {
        if (vaoId == -1) return;
        glBindVertexArray(vaoId);
        glDrawElements(glMode, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void renderInstanced(int instanceCount) {
        if (vaoId == -1) return;
        glBindVertexArray(vaoId);
        org.lwjgl.opengl.GL31.glDrawElementsInstanced(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0, instanceCount);
        glBindVertexArray(0);
    }

    /**
     * Renders the mesh and automatically manages the 'uIsCompressed' uniform in the shader.
     */
    public void render(Shader shader) {
        render(GL_TRIANGLES, shader);
    }

    /**
     * Renders the mesh with specific GL mode and automatically manages the 'uIsCompressed' uniform in the shader.
     */
    public void render(int glMode, Shader shader) {
        if (vaoId == -1) return;
        
        boolean compressed = isCompressed();
        shader.setBoolean("uIsCompressed", compressed);
        
        glBindVertexArray(vaoId);
        glDrawElements(glMode, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        
        // Reset state only if it was compressed, to avoid unnecessary uniform calls
        if (compressed) {
            shader.setBoolean("uIsCompressed", false);
        }
    }
    
    public org.joml.Vector3f getMin() {
        return new org.joml.Vector3f(minX, minY, minZ);
    }

    public org.joml.Vector3f getMax() {
        return new org.joml.Vector3f(maxX, maxY, maxZ);
    }

    public float getGripWidth(float gripY, float margin) {
        if (gripWidth >= 0) return gripWidth;
        return maxX - minX;
    }

    public void setGripWidth(float w) { this.gripWidth = w; }

    public org.joml.Vector3f getHorizontalCenterOffset() {
        return new org.joml.Vector3f(
            (minX + maxX) * 0.5f,
            0,
            (minZ + maxZ) * 0.5f
        );
    }

    public void cleanup() {
        if (vboId != -1) glDeleteBuffers(vboId);
        if (eboId != -1) glDeleteBuffers(eboId);
        if (vaoId != -1) glDeleteVertexArrays(vaoId);
    }
}
