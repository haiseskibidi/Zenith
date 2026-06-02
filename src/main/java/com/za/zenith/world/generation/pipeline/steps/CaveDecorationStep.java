package com.za.zenith.world.generation.pipeline.steps;

import com.za.zenith.world.World;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.pipeline.GenerationStep;

/**
 * Obsolete step. Portal and decoration generation are now handled directly
 * during the CaveStep via CaveEdge.carve() for unified generation.
 */
public class CaveDecorationStep implements GenerationStep {
    public CaveDecorationStep(long seed) {}

    @Override
    public void generateTerrain(Chunk chunk) {}

    @Override
    public void generateStructures(World world, Chunk chunk) {}
}
