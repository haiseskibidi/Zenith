package com.za.zenith.engine.graphics;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import com.za.zenith.utils.Logger;

/**
 * MeshPool manages large shared buffers to avoid frequent state changes.
 */
public class MeshPool {
    public static final int VERTEX_BUFFER_SIZE = 512 * 1024 * 1024; // 512 MB per buffer
    public static final int INDEX_BUFFER_SIZE = 256 * 1024 * 1024;   // 256 MB per buffer
    public static final int STRIDE = 7 * Float.BYTES;
    
    private final int[] vboIds = new int[2];
    private final int[] eboIds = new int[2];
    
    private int vertexOffset = 0;
    private int indexOffset = 0;
    private int version = 0;

    public MeshPool() {
        for (int i = 0; i < 2; i++) {
            vboIds[i] = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboIds[i]);
            glBufferData(GL_ARRAY_BUFFER, VERTEX_BUFFER_SIZE, GL_DYNAMIC_DRAW);

            eboIds[i] = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboIds[i]);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, INDEX_BUFFER_SIZE, GL_DYNAMIC_DRAW);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        Logger.info("MeshPool: Initialized with Double Buffering. 2x 512MB VBO, 2x 256MB EBO (1.5GB total)");
    }

    public synchronized Allocation allocate(FloatBuffer vertices, IntBuffer indices) {
        int vSize = vertices.remaining() * Float.BYTES;
        int iSize = indices.remaining() * Integer.BYTES;

        int activeIndex = version % 2;

        if (vertexOffset + vSize > VERTEX_BUFFER_SIZE || indexOffset + iSize > INDEX_BUFFER_SIZE) {
            version++;
            activeIndex = version % 2;
            vertexOffset = 0;
            indexOffset = 0;
            String msg = "MeshPool: Buffer wrap-around! Switching active buffer to index " + activeIndex + 
                         ". Version incremented to " + version + ". Initiating seamless chunk updates...";
            Logger.warn(msg);
            try (java.io.FileWriter fw = new java.io.FileWriter("mesh_pool_logs.txt", true);
                 java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.println(new java.util.Date() + " - " + msg);
            } catch (Exception e) {}
        }

        int currentVOffset = vertexOffset;
        int currentIOffset = indexOffset;

        glBindBuffer(GL_ARRAY_BUFFER, vboIds[activeIndex]);
        glBufferSubData(GL_ARRAY_BUFFER, currentVOffset, vertices);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboIds[activeIndex]);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, currentIOffset, indices);

        vertexOffset += vSize;
        indexOffset += iSize;

        return new Allocation(currentVOffset / STRIDE, currentIOffset / Integer.BYTES, indices.remaining(), version);
    }

    public int getVboId(int index) { return vboIds[index]; }
    public int getEboId(int index) { return eboIds[index]; }
    public int getActiveVboId() { return vboIds[version % 2]; }
    public int getActiveEboId() { return eboIds[version % 2]; }

    public int getVboId() { return getActiveVboId(); }
    public int getEboId() { return getActiveEboId(); }
    public int getVersion() { return version; }

    public record Allocation(int baseVertex, int firstIndex, int indexCount, int poolVersion) {}
    
    public void cleanup() {
        for (int i = 0; i < 2; i++) {
            glDeleteBuffers(vboIds[i]);
            glDeleteBuffers(eboIds[i]);
        }
    }
}
