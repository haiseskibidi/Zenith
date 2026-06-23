package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshPool;

public record RawChunkMeshResult(RawMeshData[] opaque, RawMeshData[] translucent, RawMeshData[] water, long version, float firstSpawnTime, long[] visibilityMasks) {
    public ChunkMeshResult upload(MeshPool pool) {
        Mesh[] opaqueMeshes = new Mesh[Chunk.NUM_SECTIONS];
        Mesh[] translucentMeshes = new Mesh[Chunk.NUM_SECTIONS];
        Mesh[] waterMeshes = new Mesh[Chunk.NUM_SECTIONS];
        for (int i = 0; i < Chunk.NUM_SECTIONS; i++) {
            if (opaque != null && opaque[i] != null) opaqueMeshes[i] = opaque[i].createMesh(pool);
            if (translucent != null && translucent[i] != null) translucentMeshes[i] = translucent[i].createMesh(pool);
            if (water != null && water[i] != null) waterMeshes[i] = water[i].createMesh(pool);
        }
        return new ChunkMeshResult(version, opaqueMeshes, translucentMeshes, waterMeshes, firstSpawnTime);
    }

    public void cleanup() {
        if (opaque != null) for (RawMeshData r : opaque) if (r != null) r.cleanup();
        if (translucent != null) for (RawMeshData r : translucent) if (r != null) r.cleanup();
        if (water != null) for (RawMeshData r : water) if (r != null) r.cleanup();
    }
}
