package com.za.zenith.world.generation.caves;

import com.za.zenith.utils.Identifier;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.BiomeGenerator;
import com.za.zenith.world.generation.density.NoiseRouter;
import java.util.Random;

public class CaveCarver {
    private static boolean[] carvableBlocksMask = null;
    private static int airId = 0;

    private static void initializeMask(CaveSettings settings) {
        if (carvableBlocksMask != null) return;
        
        carvableBlocksMask = new boolean[1024];
        airId = Blocks.AIR != null ? Blocks.AIR.getId() : 0;
        
        for (String blockName : settings.carvableBlocks) {
            try {
                Identifier id = Identifier.of(blockName);
                BlockDefinition def = BlockRegistry.getBlock(id);
                if (def != null && def.getId() > 0 && def.getId() < carvableBlocksMask.length) {
                    carvableBlocksMask[def.getId()] = true;
                }
            } catch (Exception e) {
                Logger.error("CaveCarver: Failed to register carvable block: " + blockName + " - " + e.getMessage());
            }
        }

        // Auto-register all ores as carvable to avoid floating ore blocks when tunnels intersect
        BlockDefinition[] oresToCarve = {
            Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.LAPIS_ORE,
            Blocks.REDSTONE_ORE, Blocks.COPPER_ORE
        };
        for (BlockDefinition oreDef : oresToCarve) {
            if (oreDef != null && oreDef.getId() > 0 && oreDef.getId() < carvableBlocksMask.length) {
                carvableBlocksMask[oreDef.getId()] = true;
            }
        }

        // Auto-register logs and planks to avoid floating wooden arches when caves intersect mineshafts
        BlockDefinition[] woodToCarve = {
            Blocks.OAK_LOG, Blocks.OAK_PLANKS
        };
        for (BlockDefinition woodDef : woodToCarve) {
            if (woodDef != null && woodDef.getId() > 0 && woodDef.getId() < carvableBlocksMask.length) {
                carvableBlocksMask[woodDef.getId()] = true;
            }
        }
    }

    public static void carve(Chunk chunk, long worldSeed) {
        CaveSettings settings = CaveSettings.getInstance();
        if (!settings.enabled) return;

        initializeMask(settings);

        BiomeGenerator biomeGen = new BiomeGenerator(worldSeed);
        NoiseRouter noiseRouter = new NoiseRouter(worldSeed, biomeGen);

        int cx = chunk.getPosition().x();
        int cz = chunk.getPosition().z();
        int searchRadius = settings.searchRadius;

        int chunkMinX = cx * Chunk.CHUNK_SIZE;
        int chunkMaxX = chunkMinX + Chunk.CHUNK_SIZE - 1;
        int chunkMinZ = cz * Chunk.CHUNK_SIZE;
        int chunkMaxZ = chunkMinZ + Chunk.CHUNK_SIZE - 1;

        // Collect all active edges in the search radius once to optimize and support boundary checks
        java.util.List<CaveEdge> activeEdges = new java.util.ArrayList<>();
        for (int nx = cx - searchRadius; nx <= cx + searchRadius; nx++) {
            for (int nz = cz - searchRadius; nz <= cz + searchRadius; nz++) {
                long chunkSeed = worldSeed ^ (nx * 341873128712L + nz * 132897987541L);
                Random random = new Random(chunkSeed);

                CliffPoint cliff = findCliffSlope(nx, nz, biomeGen);
                double chance = (cliff != null) ? 0.30 : settings.chance;

                if (random.nextDouble() < chance) {
                    CaveNetwork network = new CaveNetwork(nx, nz, worldSeed, settings, biomeGen, cliff);
                    activeEdges.addAll(network.edges);
                }
            }
        }

        // PHASE 1: Carve all tunnel voids first
        for (CaveEdge edge : activeEdges) {
            edge.carveTunnels(chunk, carvableBlocksMask, airId, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        }

        // PHASE 2: Place all decorations and supports once all voids are carved
        for (CaveEdge edge : activeEdges) {
            edge.decorateTunnels(chunk, settings, airId, noiseRouter, biomeGen, activeEdges, worldSeed, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        }
    }

    public static class CliffPoint {
        public int x;
        public int z;
        public int y;
        public int slope;
    }

    static CliffPoint findCliffSlope(int chunkX, int chunkZ, BiomeGenerator biomeGen) {
        int startX = chunkX * Chunk.CHUNK_SIZE;
        int startZ = chunkZ * Chunk.CHUNK_SIZE;

        // We check a 4x4 grid inside the chunk
        for (int dx = 2; dx < Chunk.CHUNK_SIZE; dx += 4) {
            for (int dz = 2; dz < Chunk.CHUNK_SIZE; dz += 4) {
                int wx = startX + dx;
                int wz = startZ + dz;
                
                int slope = getApproximateSlope(wx, wz, biomeGen);
                if (slope >= 8) { // Distinct steep wall/cliff
                    int height = getApproximateSurfaceHeight(wx, wz, biomeGen);
                    
                    // Check if nearby (offset by 6 blocks in 4 directions) there is a flat plain/valley
                    int[] ox = {-6, 6, 0, 0};
                    int[] oz = {0, 0, -6, 6};
                    
                    for (int i = 0; i < 4; i++) {
                        int checkX = wx + ox[i];
                        int checkZ = wz + oz[i];
                        int nearbySlope = getApproximateSlope(checkX, checkZ, biomeGen);
                        int nearbyHeight = getApproximateSurfaceHeight(checkX, checkZ, biomeGen);
                        
                        // It's a cliff foot if height drops by at least 4 blocks and nearby terrain is flatter
                        if (nearbyHeight < height - 4 && nearbySlope < 5) {
                            CliffPoint cp = new CliffPoint();
                            cp.x = wx;
                            cp.z = wz;
                            cp.y = height;
                            cp.slope = slope;
                            return cp; // Found a valid cliff transition point!
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- Fast, deterministic terrain height calculation based on BiomeGenerator continentalness & weirdness ---
    public static int getApproximateSurfaceHeight(int wx, int wz, BiomeGenerator biomeGen) {
        float[] climate = biomeGen.getClimateParams(wx, wz);
        float cont = climate[2];
        float weird = climate[4];
        
        // Match the spline logic configured in final_density.json
        double baseHeight;
        if (cont < 0.2f) {
            baseHeight = 20.0 + (cont / 0.2) * 35.0; // 20 to 55
        } else if (cont < 0.35f) {
            baseHeight = 55.0 + ((cont - 0.2) / 0.15) * 8.0; // 55 to 63
        } else if (cont < 0.5f) {
            baseHeight = 63.0 + ((cont - 0.35) / 0.15) * 2.0; // 63 to 65
        } else if (cont < 0.8f) {
            baseHeight = 65.0 + ((cont - 0.5) / 0.3) * 5.0; // 65 to 70
        } else {
            baseHeight = 70.0 + ((cont - 0.8) / 0.2) * 15.0; // 70 to 85
        }
        
        double weirdHeight = 0.0;
        if (weird >= 0.6f) {
            if (weird < 0.75f) {
                weirdHeight = ((weird - 0.6) / 0.15) * 25.0; // 0 to 25
            } else if (weird < 0.9f) {
                weirdHeight = 25.0 + ((weird - 0.75) / 0.15) * 65.0; // 25 to 90
            } else {
                weirdHeight = 90.0 + ((weird - 0.9) / 0.1) * 70.0; // 90 to 160
            }
        }
        
        double logicalY = baseHeight + weirdHeight;
        
        // Add vertical logical offset to convert to internal Y space
        return (int) Math.round(logicalY + Chunk.LOGICAL_OFFSET_Y);
    }

    // --- Fast, deterministic slope calculation around world coordinates ---
    public static int getApproximateSlope(int wx, int wz, BiomeGenerator biomeGen) {
        int hCenter = getApproximateSurfaceHeight(wx, wz, biomeGen);
        int hLeft = getApproximateSurfaceHeight(wx - 4, wz, biomeGen);
        int hRight = getApproximateSurfaceHeight(wx + 4, wz, biomeGen);
        int hForward = getApproximateSurfaceHeight(wx, wz - 4, biomeGen);
        int hBackward = getApproximateSurfaceHeight(wx, wz + 4, biomeGen);

        return Math.max(
            Math.max(Math.abs(hCenter - hLeft), Math.abs(hCenter - hRight)),
            Math.max(Math.abs(hCenter - hForward), Math.abs(hCenter - hBackward))
        );
    }
}
