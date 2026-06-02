package com.za.zenith.world.generation.pipeline.steps;

import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.caves.CaveCarver;
import com.za.zenith.world.generation.pipeline.GenerationStep;

public class CaveStep implements GenerationStep {
    private final long seed;

    public CaveStep(long seed) {
        this.seed = seed;
    }

    @Override
    public void generateTerrain(Chunk chunk) {
        CaveCarver.carve(chunk, seed);
    }
}
