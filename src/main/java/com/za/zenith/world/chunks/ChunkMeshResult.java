package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshPool;

public record ChunkMeshResult(long version, Mesh[] opaqueSections, Mesh[] translucentSections, Mesh[] waterSections, float spawnTime) {
    public void cleanup() {
        if (opaqueSections != null) for (Mesh m : opaqueSections) if (m != null) m.cleanup();
        if (translucentSections != null) for (Mesh m : translucentSections) if (m != null) m.cleanup();
        if (waterSections != null) for (Mesh m : waterSections) if (m != null) m.cleanup();
    }
}
