package com.za.zenith.engine.graphics;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.GL40.*;
import static org.lwjgl.opengl.GL43.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import com.za.zenith.utils.Logger;

/**
 * MultiDrawBatch aggregates draw commands to be executed in a single MDI call.
 */
public class MultiDrawBatch {
    private static final int MAX_COMMANDS = 4096;
    private static final int COMMAND_SIZE = 5 * 4; // count, instanceCount, firstIndex, baseVertex, baseInstance
    
    private final int vaoId;
    private final int indirectBufferId;
    private final int instanceBufferId; // For per-chunk data (vec4: pos.x, pos.y, pos.z, spawnTime)
    
    private final ByteBuffer commandData;
    private final ByteBuffer instanceData;
    
    private int commandCount = 0;
    private final MeshPool pool;
    private final int bufferIndex;

    public MultiDrawBatch(MeshPool pool) {
        this(pool, 0);
    }

    public MultiDrawBatch(MeshPool pool, int bufferIndex) {
        this.pool = pool;
        this.bufferIndex = bufferIndex;
        
        this.vaoId = glGenVertexArrays();
        glBindVertexArray(this.vaoId);
        
        glBindBuffer(GL_ARRAY_BUFFER, pool.getVboId(bufferIndex));
        int stride = MeshPool.STRIDE;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(4);
        
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, pool.getEboId(bufferIndex));
        
        this.indirectBufferId = glGenBuffers();
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBufferId);
        glBufferData(GL_DRAW_INDIRECT_BUFFER, (long) MAX_COMMANDS * COMMAND_SIZE, GL_DYNAMIC_DRAW);
        
        this.instanceBufferId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceBufferId);
        glBufferData(GL_ARRAY_BUFFER, (long) MAX_COMMANDS * 4 * Float.BYTES, GL_DYNAMIC_DRAW);
        
        // Attribute 5: Per-instance data
        glVertexAttribPointer(5, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(5);
        glVertexAttribDivisor(5, 1); 
        
        glBindVertexArray(0);
        
        this.commandData = BufferUtils.createByteBuffer(MAX_COMMANDS * COMMAND_SIZE);
        this.instanceData = BufferUtils.createByteBuffer(MAX_COMMANDS * 4 * Float.BYTES);
    }
    
    public void reset() {
        commandCount = 0;
        commandData.clear();
        instanceData.clear();
    }
    
    public void addMesh(Mesh mesh, float x, float y, float z, float spawnTime) {
        if (commandCount >= MAX_COMMANDS || mesh == null || mesh.getPool() != pool) return;
        if ((mesh.getPoolVersion() % 2) != bufferIndex) return;
        
        // count, instanceCount, firstIndex, baseVertex, baseInstance
        commandData.putInt(mesh.getVertexCount());
        commandData.putInt(1); // instanceCount = 1
        commandData.putInt(mesh.getFirstIndex());
        commandData.putInt(mesh.getBaseVertex());
        commandData.putInt(commandCount); // baseInstance (used as index for instanceData)
        
        instanceData.putFloat(x);
        instanceData.putFloat(y);
        instanceData.putFloat(z);
        instanceData.putFloat(spawnTime);
        
        commandCount++;
    }
    
    public void render() {
        if (commandCount == 0) return;
        
        glBindVertexArray(vaoId);
        
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBufferId);
        commandData.flip();
        glBufferSubData(GL_DRAW_INDIRECT_BUFFER, 0, commandData);
        
        glBindBuffer(GL_ARRAY_BUFFER, instanceBufferId);
        instanceData.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceData);
        
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0, commandCount, 0);
        
        glBindVertexArray(0);
    }
    
    public void cleanup() {
        glDeleteBuffers(indirectBufferId);
        glDeleteBuffers(instanceBufferId);
        glDeleteVertexArrays(vaoId);
    }
}
