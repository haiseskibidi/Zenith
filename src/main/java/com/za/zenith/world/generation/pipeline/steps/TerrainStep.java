package com.za.zenith.world.generation.pipeline.steps;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.BiomeDefinition;
import com.za.zenith.world.generation.BiomeGenerator;
import com.za.zenith.world.generation.BiomeRegistry;
import com.za.zenith.world.generation.density.DensityContextImpl;
import com.za.zenith.world.generation.density.NoiseRouter;
import com.za.zenith.world.generation.pipeline.GenerationStep;
import com.za.zenith.utils.ArrayPool;

public class TerrainStep implements GenerationStep {
    private final NoiseRouter noiseRouter;
    private final BiomeGenerator biomeGenerator;
    
    private static final int HORIZ_STEP = 4;
    private static final int VERT_STEP = 4;
    private static final int GRID_X = Chunk.CHUNK_SIZE / HORIZ_STEP + 1;
    private static final int GRID_Z = Chunk.CHUNK_SIZE / HORIZ_STEP + 1;
    private static final int GRID_Y = Chunk.CHUNK_HEIGHT / VERT_STEP + 1;

    public TerrainStep(long seed) {
        this.biomeGenerator = new BiomeGenerator(seed);
        this.noiseRouter = new NoiseRouter(seed, biomeGenerator);
    }

    @Override
    public void generateTerrain(Chunk chunk) {
        int startX = chunk.getPosition().x() * Chunk.CHUNK_SIZE;
        int startZ = chunk.getPosition().z() * Chunk.CHUNK_SIZE;

        double[] densityGrid = ArrayPool.rentDensityGrid();

        for (int i = 0; i < GRID_X; i++) {
            for (int j = 0; j < GRID_Z; j++) {
                int worldX = startX + i * HORIZ_STEP;
                int worldZ = startZ + j * HORIZ_STEP;
                
                float[] climate = biomeGenerator.getClimateParams(worldX, worldZ);
                int baseIdx = (i * GRID_Z + j) * GRID_Y;

                for (int k = 0; k < GRID_Y; k++) {
                    int internalY = k * VERT_STEP;
                    // Pass logical Y (with 128 offset) to density function
                    int logicalY = internalY - Chunk.LOGICAL_OFFSET_Y;
                    DensityContextImpl ctx = new DensityContextImpl(worldX, logicalY, worldZ, climate[2], climate[3], climate[4], climate[0], climate[1]);
                    densityGrid[baseIdx + k] = noiseRouter.getDensity(ctx);
                }
            }
        }

        int stoneId = Blocks.STONE != null ? Blocks.STONE.getId() : 1;
        int waterId = Blocks.WATER != null ? Blocks.WATER.getId() : 0;
        int bedrockId = Blocks.BEDROCK != null ? Blocks.BEDROCK.getId() : 7;

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int worldX = startX + x;
                int worldZ = startZ + z;
                
                BiomeDefinition biome = biomeGenerator.getBiome(worldX, worldZ);
                if (biome == null) biome = BiomeRegistry.getAll().iterator().next();

                int surfaceId = BlockRegistry.getRegistry().getId(biome.getSurfaceBlock());
                int undergroundId = BlockRegistry.getRegistry().getId(biome.getUndergroundBlock());
                float noiseVal = biomeGenerator.getClimateParams(worldX, worldZ)[0];

                int currentSurfaceY = -1;

                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    double density = sampleInterpolatedFlat(densityGrid, x, y, z);

                    if (density > 0.0) {
                        if (currentSurfaceY == -1) currentSurfaceY = y;
                        int depth = currentSurfaceY - y;

                        if (y == 0) {
                            chunk.setBlock(x, y, z, bedrockId, 0);
                        } else {
                            boolean ruleMatched = false;
                            if (biome.getSurfaceRules() != null && !biome.getSurfaceRules().isEmpty()) {
                                for (com.za.zenith.world.generation.rules.SurfaceRule rule : biome.getSurfaceRules()) {
                                    if (rule.evaluate(worldX, y - Chunk.LOGICAL_OFFSET_Y, worldZ, noiseVal, depth)) {
                                        var blockDef = rule.getBlock();
                                        if (blockDef != null) {
                                            chunk.setBlock(x, y, z, blockDef.getId(), 0);
                                            ruleMatched = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (!ruleMatched) {
                                if (y == currentSurfaceY) {
                                    chunk.setBlock(x, y, z, surfaceId, 0);
                                } else if (y > currentSurfaceY - 4) {
                                    chunk.setBlock(x, y, z, undergroundId, 0);
                                } else {
                                    chunk.setBlock(x, y, z, stoneId, 0);
                                }
                            }
                        }
                    } else {
                        currentSurfaceY = -1;
                        // Logical Sea Level 62 = Internal 128 + 62 = 190
                        if (y < 190 && y >= 128) {
                            chunk.setBlock(x, y, z, waterId, 0);
                        }
                    }
                }
            }
        }
        
        ArrayPool.returnDensityGrid(densityGrid);
    }

    private double sampleInterpolatedFlat(double[] grid, int x, int y, int z) {
        int gx = x / HORIZ_STEP; int gz = z / HORIZ_STEP; int gy = y / VERT_STEP;
        double tx = (x % HORIZ_STEP) / (double) HORIZ_STEP;
        double tz = (z % HORIZ_STEP) / (double) HORIZ_STEP;
        double ty = (y % VERT_STEP) / (double) VERT_STEP;

        int b00 = (gx * GRID_Z + gz) * GRID_Y;
        int b10 = ((gx + 1) * GRID_Z + gz) * GRID_Y;
        int b01 = (gx * GRID_Z + (gz + 1)) * GRID_Y;
        int b11 = ((gx + 1) * GRID_Z + (gz + 1)) * GRID_Y;

        double d000 = grid[b00 + gy];
        double d100 = grid[b10 + gy];
        double d010 = grid[b01 + gy];
        double d110 = grid[b11 + gy];
        double d001 = grid[b00 + gy + 1];
        double d101 = grid[b10 + gy + 1];
        double d011 = grid[b01 + gy + 1];
        double d111 = grid[b11 + gy + 1];

        double dx00 = d000 * (1 - tx) + d100 * tx;
        double dx10 = d010 * (1 - tx) + d110 * tx;
        double dx01 = d001 * (1 - tx) + d101 * tx;
        double dx11 = d011 * (1 - tx) + d111 * tx;
        double dxy0 = dx00 * (1 - tz) + dx10 * tz;
        double dxy1 = dx01 * (1 - tz) + dx11 * tz;
        return dxy0 * (1 - ty) + dxy1 * ty;
    }

    @Override
    public void generateStructures(World world, Chunk chunk) {
    }
}
