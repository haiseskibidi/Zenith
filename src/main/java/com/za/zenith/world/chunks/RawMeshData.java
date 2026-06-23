package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshPool;

public record RawMeshData(java.nio.FloatBuffer dataBuffer, int dataLen, java.nio.IntBuffer indicesBuffer, int idxLen, org.joml.Vector3f min, org.joml.Vector3f max) {
    public Mesh createMesh(MeshPool pool) {
        if (pool != null) {
            return new Mesh(pool, dataBuffer, indicesBuffer, min, max);
        }
        return new Mesh(dataBuffer, dataLen, indicesBuffer, idxLen, min, max, Mesh.VertexFormat.COMPRESSED_CHUNK);
    }

    public void cleanup() {
        com.za.zenith.utils.NioBufferPool.returnFloat(dataBuffer);
        com.za.zenith.utils.NioBufferPool.returnInt(indicesBuffer);
    }
}
