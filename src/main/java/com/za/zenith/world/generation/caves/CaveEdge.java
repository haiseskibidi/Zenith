package com.za.zenith.world.generation.caves;

import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.generation.BiomeGenerator;
import com.za.zenith.world.generation.density.NoiseRouter;
import com.za.zenith.world.generation.density.DensityContextImpl;
import java.util.Random;

public class CaveEdge {
    public final CaveNode from;
    public final CaveNode to;
    public boolean isMineshaft = false;

    public CaveEdge(CaveNode from, CaveNode to) {
        this.from = from;
        this.to = to;
    }

    private boolean checkMountainRoof(NoiseRouter noiseRouter, BiomeGenerator biomeGen, int steps, double dx, double dy, double dz) {
        double progress1_5 = 1.5 / steps;
        double tx1_5 = from.x + dx * progress1_5;
        double ty1_5 = from.y + dy * progress1_5;
        double tz1_5 = from.z + dz * progress1_5;
        double ceilingY1_5 = from.y + (dy * progress1_5) + 2.0;

        double progress3 = 3.0 / steps;
        double tx3 = from.x + dx * progress3;
        double ty3 = from.y + dy * progress3;
        double tz3 = from.z + dz * progress3;
        double ceilingY3 = from.y + (dy * progress3) + 2.0;

        // Check if terrain rises and roof is solid using the exact density router
        boolean roofIsSolid1_5 = isTerrainSolid((int) Math.floor(tx1_5), (int) Math.floor(ceilingY1_5 + 2.0), (int) Math.floor(tz1_5), noiseRouter, biomeGen);
        boolean roofIsSolid3 = isTerrainSolid((int) Math.floor(tx3), (int) Math.floor(ceilingY3 + 3.0), (int) Math.floor(tz3), noiseRouter, biomeGen);

        return roofIsSolid1_5 && roofIsSolid3;
    }

    public void carveTunnels(Chunk chunk, boolean[] carvableMask, int airId, NoiseRouter noiseRouter, BiomeGenerator biomeGen, int minX, int maxX, int minZ, int maxZ) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0) return;

        int steps = (int) (distance / 1.5);
        if (steps <= 0) steps = 1;

        long edgeSeed = ((long) Double.doubleToLongBits(from.x) ^ (long) Double.doubleToLongBits(to.z));
        Random edgeRand = new Random(edgeSeed);

        boolean isSurfaceEntrance = from.depth == 0;
        boolean hasMountainRoof = false;

        if (isSurfaceEntrance) {
            hasMountainRoof = checkMountainRoof(noiseRouter, biomeGen, steps, dx, dy, dz);
        }

        // Loop over the steps of the tunnel
        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            double targetX = from.x + dx * progress;
            double targetY = from.y + dy * progress;
            double targetZ = from.z + dz * progress;

            int wx = (int) Math.floor(targetX);
            int wy = (int) Math.floor(targetY);
            int wz = (int) Math.floor(targetZ);

            boolean runsZ = Math.abs(dz) > Math.abs(dx);
            boolean isPortalStep = isSurfaceEntrance && hasMountainRoof && step <= 4;

            if (isPortalStep || isMineshaft) {
                // Mineshaft or Portal Step: straight 3x3 box
                carveBox(chunk, wx, wy, wz, runsZ, carvableMask, airId, minX, maxX, minZ, maxZ);
            } else {
                // Natural Cave: Izvilisty (wiggling) tunnel using spheres
                double stretchY = 1.15;

                // Maximum wiggle in the middle of the tunnel, fading out at the nodes
                double wiggleAmp = 3.0 * Math.sin(progress * Math.PI);
                double curX = targetX + (edgeRand.nextDouble() - 0.5) * wiggleAmp;
                double curY = targetY + (edgeRand.nextDouble() - 0.5) * (wiggleAmp * 0.4);
                double curZ = targetZ + (edgeRand.nextDouble() - 0.5) * wiggleAmp;

                // Interpolate radius and apply height-based scaling
                float baseRadius = from.radius + (to.radius - from.radius) * (float) progress;
                
                // Taper at the node connections to merge smoothly
                float taper = 1.0f;
                if (progress < 0.15) taper = (float) (progress / 0.15);
                else if (progress > 0.85) taper = (float) ((1.0 - progress) / 0.15);
                float radius = baseRadius * (0.7f + 0.3f * taper);

                // Fast boundary check before carving sphere
                if (curX + radius >= minX && curX - radius <= maxX && curZ + radius >= minZ && curZ - radius <= maxZ) {
                    carveSphere(chunk, curX, curY, curZ, radius, stretchY, carvableMask, airId, minX, maxX, minZ, maxZ);
                }
            }
        }
    }

    public void decorateTunnels(Chunk chunk, CaveSettings settings, int airId, NoiseRouter noiseRouter, BiomeGenerator biomeGen, java.util.List<CaveEdge> activeEdges, long worldSeed, int minX, int maxX, int minZ, int maxZ) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0) return;

        int steps = (int) (distance / 1.5);
        if (steps <= 0) steps = 1;

        long edgeSeed = ((long) Double.doubleToLongBits(from.x) ^ (long) Double.doubleToLongBits(to.z));
        Random edgeRand = new Random(edgeSeed);

        int logId = Blocks.OAK_LOG != null ? Blocks.OAK_LOG.getId() : 14;
        java.util.List<CaveSettings.CompiledOreSettings> compiledOres = settings.getCompiledOres();

        boolean isSurfaceEntrance = from.depth == 0;
        boolean hasMountainRoof = false;

        if (isSurfaceEntrance) {
            hasMountainRoof = checkMountainRoof(noiseRouter, biomeGen, steps, dx, dy, dz);
        }

        // Loop over the steps to place supports and ores
        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            double targetX = from.x + dx * progress;
            double targetY = from.y + dy * progress;
            double targetZ = from.z + dz * progress;

            int wx = (int) Math.floor(targetX);
            int wy = (int) Math.floor(targetY);
            int wz = (int) Math.floor(targetZ);

            boolean runsZ = Math.abs(dz) > Math.abs(dx);
            boolean isPortalStep = isSurfaceEntrance && hasMountainRoof && step <= 4;

            if (isPortalStep) {
                // Build portal structure (cliff cut, canopy, cobblestone retaining wall, logs)
                int cobbleId = Blocks.COBBLESTONE != null ? Blocks.COBBLESTONE.getId() : 35;
                int plankId = Blocks.OAK_PLANKS != null ? Blocks.OAK_PLANKS.getId() : 34;
                int stepDirX = dx > 0 ? 1 : -1;
                int stepDirZ = dz > 0 ? 1 : -1;

                buildEntrancePortal(chunk, wx, wy, wz, runsZ, stepDirX, stepDirZ, step, logId, cobbleId, plankId, airId, edgeRand, activeEdges, worldSeed, noiseRouter, biomeGen, minX, maxX, minZ, maxZ);
            } else if (isMineshaft) {
                // Place support wooden arches every 5 steps
                if (step % 5 == 0) {
                    placeSupportArch(chunk, wx, wy, wz, runsZ, logId, activeEdges, worldSeed, noiseRouter, biomeGen, minX, maxX, minZ, maxZ);
                }
            }

            // Ore Vein Spawning (5% chance in mineshaft, 6% in cave)
            double oreChance = isMineshaft ? 0.05 : 0.06;
            if (edgeRand.nextDouble() < oreChance) {
                double vx = wx;
                double vy = wy + 1.0;
                double vz = wz;

                if (!isMineshaft && !isPortalStep) {
                    // Caves spawn ore on boundaries
                    double progressWiggle = 3.0 * Math.sin(progress * Math.PI);
                    double cx = targetX + (edgeRand.nextDouble() - 0.5) * progressWiggle;
                    double cy = targetY + (edgeRand.nextDouble() - 0.5) * (progressWiggle * 0.4);
                    double cz = targetZ + (edgeRand.nextDouble() - 0.5) * progressWiggle;
                    float baseRadius = from.radius + (to.radius - from.radius) * (float) progress;
                    float taper = 1.0f;
                    if (progress < 0.15) taper = (float) (progress / 0.15);
                    else if (progress > 0.85) taper = (float) ((1.0 - progress) / 0.15);
                    float radius = baseRadius * (0.7f + 0.3f * taper);

                    double angle1 = edgeRand.nextDouble() * 2.0 * Math.PI;
                    double angle2 = edgeRand.nextDouble() * Math.PI;
                    vx = cx + radius * Math.cos(angle1) * Math.sin(angle2);
                    vy = cy + radius * Math.sin(angle1) * Math.sin(angle2) * 1.15;
                    vz = cz + radius * Math.cos(angle2);
                } else {
                    // Mineshafts and Portal Step (rectangular) spawn ore on walls/floor/ceiling
                    int side = edgeRand.nextInt(6);
                    switch (side) {
                        case 0 -> vx -= 1.5; // Left wall
                        case 1 -> vx += 1.5; // Right wall
                        case 2 -> vz -= 1.5; // Back wall
                        case 3 -> vz += 1.5; // Front wall
                        case 4 -> vy -= 1.0; // Floor
                        case 5 -> vy += 1.5; // Ceiling
                    }
                }

                double logicalY = vy - Chunk.LOGICAL_OFFSET_Y;
                int oreId = selectOreType(logicalY, edgeRand, compiledOres);
                if (oreId > 0) {
                    int size = getOreVeinSize(oreId, edgeRand, compiledOres);
                    generateOreVein(chunk, vx, vy, vz, oreId, size, edgeRand, minX, maxX, minZ, maxZ);
                }
            }
        }
    }

    private void carveBox(Chunk chunk, int wx, int wy, int wz, boolean runsZ, boolean[] carvableMask, int airId, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        // Use a 3x3 footprint (radius of 1 on both axes) to ensure overlapping between steps,
        // preventing thin stone/dirt walls from blocking the tunnel.
        int boxMinX = wx - 1;
        int boxMaxX = wx + 1;
        int boxMinZ = wz - 1;
        int boxMaxZ = wz + 1;
        
        int minY = Math.max(4, wy);
        int maxY = Math.min(Chunk.CHUNK_HEIGHT - 4, wy + 2); // 3 blocks high

        // Clamp to chunk boundaries
        int startX = Math.max(0, boxMinX - chunkMinX);
        int endX = Math.min(Chunk.CHUNK_SIZE - 1, boxMaxX - chunkMinX);
        int startZ = Math.max(0, boxMinZ - chunkMinZ);
        int endZ = Math.min(Chunk.CHUNK_SIZE - 1, boxMaxZ - chunkMinZ);

        for (int lx = startX; lx <= endX; lx++) {
            for (int lz = startZ; lz <= endZ; lz++) {
                for (int ly = minY; ly <= maxY; ly++) {
                    int currentBlock = chunk.getBlockType(lx, ly, lz);
                    if (currentBlock > 0 && currentBlock < carvableMask.length && carvableMask[currentBlock]) {
                        chunk.setBlock(lx, ly, lz, airId, 0);
                    }
                }
            }
        }
    }

    private void placeSupportArch(Chunk chunk, int wx, int wy, int wz, boolean runsZ, int logId, java.util.List<CaveEdge> activeEdges, long worldSeed, NoiseRouter noiseRouter, BiomeGenerator biomeGen, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        int leftX = runsZ ? wx - 1 : wx;
        int rightX = runsZ ? wx + 1 : wx;
        int leftZ = runsZ ? wz : wz - 1;
        int rightZ = runsZ ? wz : wz + 1;

        int ceilingY = wy + 2;

        // Ensure there is a solid ceiling directly above the arch's crossbar to prevent
        // arches from floating in open cave voids or sticking out onto the surface.
        boolean roofCenterSolid = isBlockSolidDeterministic(chunk, wx, ceilingY + 1, wz, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        boolean roofLeftSolid = isBlockSolidDeterministic(chunk, leftX, ceilingY + 1, leftZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        boolean roofRightSolid = isBlockSolidDeterministic(chunk, rightX, ceilingY + 1, rightZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);

        if (!roofCenterSolid || !roofLeftSolid || !roofRightSolid) {
            return; // Skip placing this arch since it has no solid roof above it
        }

        // Scan down to see if left pillar can reach solid ground within 8 blocks
        int leftY = ceilingY - 1;
        int leftLimit = 0;
        boolean leftSolidFound = false;
        while (leftY > 4 && leftLimit < 8) {
            if (isBlockSolidDeterministic(chunk, leftX, leftY, leftZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
                leftSolidFound = true;
                break;
            }
            leftY--;
            leftLimit++;
        }

        // Scan down to see if right pillar can reach solid ground within 8 blocks
        int rightY = ceilingY - 1;
        int rightLimit = 0;
        boolean rightSolidFound = false;
        while (rightY > 4 && rightLimit < 8) {
            if (isBlockSolidDeterministic(chunk, rightX, rightY, rightZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
                rightSolidFound = true;
                break;
            }
            rightY--;
            rightLimit++;
        }

        // Skip this arch if pillars cannot reach solid ground (e.g. they would hover in mid-air inside a deep cave)
        if (!leftSolidFound || !rightSolidFound) {
            return;
        }

        // Build left pillar down to solid ground
        for (int y = ceilingY - 1; y >= leftY; y--) {
            setBlockInChunk(chunk, leftX, y, leftZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        }

        // Build right pillar down to solid ground
        for (int y = ceilingY - 1; y >= rightY; y--) {
            setBlockInChunk(chunk, rightX, y, rightZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        }

        // Crossbar
        setBlockInChunk(chunk, leftX, ceilingY, leftZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        setBlockInChunk(chunk, wx, ceilingY, wz, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
        setBlockInChunk(chunk, rightX, ceilingY, rightZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
    }

    private int getBlockTypeWorld(Chunk chunk, int wx, int wy, int wz, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        if (wx >= chunkMinX && wx <= chunkMaxX && wz >= chunkMinZ && wz <= chunkMaxZ) {
            int lx = wx - chunkMinX;
            int lz = wz - chunkMinZ;
            return chunk.getBlockType(lx, wy, lz);
        }
        return 13; // Return Stone (solid) as fallback for out-of-bounds coordinates
    }

    private boolean isSolidId(int id) {
        if (id == 0) return false; // Air
        if (Blocks.WATER != null && id == Blocks.WATER.getId()) return false;
        return true;
    }

    private void setBlockInChunk(Chunk chunk, int wx, int wy, int wz, int typeId, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        if (wx >= chunkMinX && wx <= chunkMaxX && wz >= chunkMinZ && wz <= chunkMaxZ) {
            int lx = wx - chunkMinX;
            int lz = wz - chunkMinZ;
            chunk.setBlock(lx, wy, lz, typeId, 0);
        }
    }

    private void carveSphere(Chunk chunk, double cx, double cy, double cz, float radius, double stretchY, boolean[] carvableMask, int airId, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        double radiusSq = radius * radius;

        int minLocalX = Math.max(0, (int) Math.floor(cx - radius - 1) - chunkMinX);
        int maxLocalX = Math.min(Chunk.CHUNK_SIZE - 1, (int) Math.floor(cx + radius + 1) - chunkMinX);
        int minLocalZ = Math.max(0, (int) Math.floor(cz - radius - 1) - chunkMinZ);
        int maxLocalZ = Math.min(Chunk.CHUNK_SIZE - 1, (int) Math.floor(cz + radius + 1) - chunkMinZ);

        int minLocalY = Math.max(4, (int) Math.floor(cy - (radius + 1) * stretchY));
        int maxLocalY = Math.min(Chunk.CHUNK_HEIGHT - 4, (int) Math.floor(cy + (radius + 1) * stretchY));

        for (int lx = minLocalX; lx <= maxLocalX; lx++) {
            double dx = (chunkMinX + lx) - cx;
            double dxSq = dx * dx;
            
            for (int lz = minLocalZ; lz <= maxLocalZ; lz++) {
                double dz = (chunkMinZ + lz) - cz;
                double dzSq = dz * dz;
                
                if (dxSq + dzSq >= radiusSq) {
                    continue;
                }

                for (int ly = minLocalY; ly <= maxLocalY; ly++) {
                    double dy = (ly - cy) / stretchY;
                    double dySq = dy * dy;
                    double distSq = dxSq + dySq + dzSq;

                    if (distSq < radiusSq) {
                        int currentBlock = chunk.getBlockType(lx, ly, lz);
                        if (currentBlock > 0 && currentBlock < carvableMask.length && carvableMask[currentBlock]) {
                            chunk.setBlock(lx, ly, lz, airId, 0);
                        }
                    }
                }
            }
        }
    }

    private int selectOreType(double logicalY, Random rand, java.util.List<CaveSettings.CompiledOreSettings> compiledOres) {
        if (compiledOres == null || compiledOres.isEmpty()) return 0;

        java.util.List<CaveSettings.CompiledOreSettings> validOres = new java.util.ArrayList<>();
        int totalWeight = 0;

        for (CaveSettings.CompiledOreSettings ore : compiledOres) {
            if (logicalY >= ore.minY && logicalY <= ore.maxY) {
                validOres.add(ore);
                totalWeight += ore.weight;
            }
        }

        if (validOres.isEmpty() || totalWeight <= 0) {
            return 0; 
        }

        int roll = rand.nextInt(totalWeight);
        int sum = 0;
        for (CaveSettings.CompiledOreSettings ore : validOres) {
            sum += ore.weight;
            if (roll < sum) {
                return ore.blockId;
            }
        }
        return validOres.get(0).blockId;
    }

    private int getOreVeinSize(int oreId, Random rand, java.util.List<CaveSettings.CompiledOreSettings> compiledOres) {
        for (CaveSettings.CompiledOreSettings ore : compiledOres) {
            if (ore.blockId == oreId) {
                if (ore.maxSize <= ore.minSize) return ore.minSize;
                return ore.minSize + rand.nextInt(ore.maxSize - ore.minSize + 1);
            }
        }
        return 4;
    }

    private void generateOreVein(Chunk chunk, double vx, double vy, double vz, int oreId, int size, Random rand, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        int stoneId = Blocks.STONE != null ? Blocks.STONE.getId() : 13;
        int dirtId = Blocks.DIRT != null ? Blocks.DIRT.getId() : 2;
        int gravelId = Blocks.GRAVEL != null ? Blocks.GRAVEL.getId() : 38;
        int sandId = Blocks.SAND != null ? Blocks.SAND.getId() : 37;

        double cx = vx;
        double cy = vy;
        double cz = vz;

        int maxAttempts = (int) (size * 1.8);
        int placed = 0;

        for (int attempt = 0; attempt < maxAttempts && placed < size; attempt++) {
            int bx = (int) Math.round(cx);
            int by = (int) Math.round(cy);
            int bz = (int) Math.round(cz);

            if (by >= 4 && by < Chunk.CHUNK_HEIGHT - 4) {
                if (bx >= chunkMinX && bx <= chunkMaxX && bz >= chunkMinZ && bz <= chunkMaxZ) {
                    int lx = bx - chunkMinX;
                    int lz = bz - chunkMinZ;
                    int currentBlock = chunk.getBlockType(lx, by, lz);

                    if (isReplaceableWithOre(currentBlock, stoneId, dirtId, gravelId, sandId)) {
                        chunk.setBlock(lx, by, lz, oreId, 0);
                        placed++;
                    }
                }
            }

            // Random walk step
            cx += (rand.nextDouble() - 0.5) * 1.5;
            cy += (rand.nextDouble() - 0.5) * 1.2; // Slightly flatter veins
            cz += (rand.nextDouble() - 0.5) * 1.5;
        }
    }

    private boolean isReplaceableWithOre(int id, int stoneId, int dirtId, int gravelId, int sandId) {
        if (id == 0) return false;
        if (Blocks.WATER != null && id == Blocks.WATER.getId()) return false;
        return id == stoneId || id == dirtId || id == gravelId || id == sandId;
    }

    private void buildEntrancePortal(Chunk chunk, int wx, int wy, int wz, boolean runsZ, int stepDirX, int stepDirZ, int step, int logId, int cobbleId, int plankId, int airId, Random rand, java.util.List<CaveEdge> activeEdges, long worldSeed, NoiseRouter noiseRouter, BiomeGenerator biomeGen, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        // Step 0: Cliff cut on the outside (1 block outside the entrance Node)
        if (step == 0) {
            int outsideX = runsZ ? wx : wx - stepDirX;
            int outsideZ = runsZ ? wz - stepDirZ : wz;

            // Cut a 5-wide, 5-high slice of air to make a flat rock facade
            int startK = runsZ ? -2 : 0;
            int endK = runsZ ? 2 : 0;
            int startM = runsZ ? 0 : -2;
            int endM = runsZ ? 0 : 2;

            for (int k = startK; k <= endK; k++) {
                for (int m = startM; m <= endM; m++) {
                    int cx = outsideX + k;
                    int cz = outsideZ + m;
                    for (int cy = wy; cy <= wy + 4; cy++) {
                        setBlockSafe(chunk, cx, cy, cz, airId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                    }
                }
            }
        }

        // Retaining Walls (Cobblestone) on the sides (width 5, side pillars at offset 2)
        int leftX = runsZ ? wx - 2 : wx;
        int rightX = runsZ ? wx + 2 : wx;
        int leftZ = runsZ ? wz : wz - 2;
        int rightZ = runsZ ? wz : wz + 2;

        for (int cy = wy; cy <= wy + 2; cy++) {
            // Replace stone/dirt with cobblestone for retaining walls
            int leftBlock = getBlockSafe(chunk, leftX, cy, leftZ, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            if (leftBlock != airId) {
                setBlockSafe(chunk, leftX, cy, leftZ, cobbleId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }
            int rightBlock = getBlockSafe(chunk, rightX, cy, rightZ, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            if (rightBlock != airId) {
                setBlockSafe(chunk, rightX, cy, rightZ, cobbleId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }
        }

        // Shed Canopy & Support Arch (on step 0 and 1)
        if (step == 0 || step == 1) {
            int archLeftX = runsZ ? wx - 1 : wx;
            int archRightX = runsZ ? wx + 1 : wx;
            int archLeftZ = runsZ ? wz : wz - 1;
            int archRightZ = runsZ ? wz : wz + 1;

            int ceilingY = wy + 2;

            // Ensure we have solid floor under the bases of the pillars
            boolean solidUnderLeft = isBlockSolidDeterministic(chunk, archLeftX, wy - 1, archLeftZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            boolean solidUnderRight = isBlockSolidDeterministic(chunk, archRightX, wy - 1, archRightZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);

            if (!solidUnderLeft && !solidUnderRight) {
                return; // Skip placing if both pillars hover in mid-air
            }

            // For step 1, ensure there is a solid ceiling directly above the wood roof to anchor it
            if (step == 1) {
                boolean roofCenterSolid = isBlockSolidDeterministic(chunk, wx, ceilingY + 2, wz, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                boolean roofLeftSolid = isBlockSolidDeterministic(chunk, archLeftX, ceilingY + 2, archLeftZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                boolean roofRightSolid = isBlockSolidDeterministic(chunk, archRightX, ceilingY + 2, archRightZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);

                if (!roofCenterSolid || !roofLeftSolid || !roofRightSolid) {
                    return; // Skip step 1 if the roof is exposed to open air
                }
            }

            // Scan left pillar down to see if it can reach solid ground within 8 blocks
            int leftY = ceilingY - 1;
            int leftLimit = 0;
            boolean leftSolidFound = false;
            while (leftY > 4 && leftLimit < 8) {
                if (isBlockSolidDeterministic(chunk, archLeftX, leftY, archLeftZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
                    leftSolidFound = true;
                    break;
                }
                leftY--;
                leftLimit++;
            }

            // Scan right pillar down to see if it can reach solid ground within 8 blocks
            int rightY = ceilingY - 1;
            int rightLimit = 0;
            boolean rightSolidFound = false;
            while (rightY > 4 && rightLimit < 8) {
                if (isBlockSolidDeterministic(chunk, archRightX, rightY, archRightZ, activeEdges, worldSeed, noiseRouter, biomeGen, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ)) {
                    rightSolidFound = true;
                    break;
                }
                rightY--;
                rightLimit++;
            }

            // Skip placing this portal frame if pillars cannot reach solid ground (prevent floating structures)
            if (!leftSolidFound || !rightSolidFound) {
                return;
            }

            // Build left pillar
            for (int y = ceilingY - 1; y >= leftY; y--) {
                setBlockSafe(chunk, archLeftX, y, archLeftZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }

            // Build right pillar
            for (int y = ceilingY - 1; y >= rightY; y--) {
                setBlockSafe(chunk, archRightX, y, archRightZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }

            // Crossbars (Oak log)
            setBlockSafe(chunk, archLeftX, ceilingY, archLeftZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            setBlockSafe(chunk, wx, ceilingY, wz, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            setBlockSafe(chunk, archRightX, ceilingY, archRightZ, logId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);

            // Plank roof (Oak Planks at ceilingY + 1)
            setBlockSafe(chunk, archLeftX, ceilingY + 1, archLeftZ, plankId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            setBlockSafe(chunk, wx, ceilingY + 1, wz, plankId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            setBlockSafe(chunk, archRightX, ceilingY + 1, archRightZ, plankId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);

            // Clear everything above the roof to prevent burial
            for (int cy = ceilingY + 2; cy <= ceilingY + 6; cy++) {
                setBlockSafe(chunk, archLeftX, cy, archLeftZ, airId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                setBlockSafe(chunk, wx, cy, wz, airId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
                setBlockSafe(chunk, archRightX, cy, archRightZ, airId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }

            // Hang light source on the very first arch (step 0)
            if (step == 0) {
                int lampId = Blocks.ELECTRIC_LAMP != null ? Blocks.ELECTRIC_LAMP.getId() : 64;
                int campfireId = Blocks.CAMPFIRE != null ? Blocks.CAMPFIRE.getId() : 61;
                int lightBlockId = rand.nextFloat() < 0.5f ? campfireId : lampId;
                setBlockSafe(chunk, wx, ceilingY - 1, wz, lightBlockId, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ);
            }
        }
    }

    private void setBlockSafe(Chunk chunk, int wx, int wy, int wz, int typeId, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        if (wx >= chunkMinX && wx <= chunkMaxX && wz >= chunkMinZ && wz <= chunkMaxZ) {
            int lx = wx - chunkMinX;
            int lz = wz - chunkMinZ;
            if (wy >= 0 && wy < Chunk.CHUNK_HEIGHT) {
                chunk.setBlock(lx, wy, lz, typeId, 0);
            }
        }
    }

    private int getBlockSafe(Chunk chunk, int wx, int wy, int wz, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        if (wx >= chunkMinX && wx <= chunkMaxX && wz >= chunkMinZ && wz <= chunkMaxZ) {
            int lx = wx - chunkMinX;
            int lz = wz - chunkMinZ;
            if (wy >= 0 && wy < Chunk.CHUNK_HEIGHT) {
                return chunk.getBlockType(lx, wy, lz);
            }
        }
        return 0; // Air as fallback
    }

    public boolean intersectsPoint(int wx, int wy, int wz, NoiseRouter noiseRouter, BiomeGenerator biomeGen) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0) return false;

        // Bounding Box check: fast-fail if the point is too far horizontally or vertically
        double minX = Math.min(from.x, to.x) - 15.0;
        double maxX = Math.max(from.x, to.x) + 15.0;
        double minZ = Math.min(from.z, to.z) - 15.0;
        double maxZ = Math.max(from.z, to.z) + 15.0;
        double minY = Math.min(from.y, to.y) - 15.0;
        double maxY = Math.max(from.y, to.y) + 15.0;

        if (wx < minX || wx > maxX || wz < minZ || wz > maxZ || wy < minY || wy > maxY) {
            return false;
        }

        int steps = (int) (distance / 1.5);
        if (steps <= 0) steps = 1;

        long edgeSeed = ((long) Double.doubleToLongBits(from.x) ^ (long) Double.doubleToLongBits(to.z));
        Random edgeRand = new Random(edgeSeed);

        boolean isSurfaceEntrance = from.depth == 0;
        boolean hasMountainRoof = false;

        if (isSurfaceEntrance) {
            hasMountainRoof = checkMountainRoof(noiseRouter, biomeGen, steps, dx, dy, dz);
        }

        for (int step = 0; step <= steps; step++) {
            double progress = (double) step / steps;
            double targetX = from.x + dx * progress;
            double targetY = from.y + dy * progress;
            double targetZ = from.z + dz * progress;

            boolean isPortalStep = isSurfaceEntrance && hasMountainRoof && step <= 4;

            if (isPortalStep || isMineshaft) {
                int bx = (int) Math.floor(targetX);
                int by = (int) Math.floor(targetY);
                int bz = (int) Math.floor(targetZ);
                
                int bMinY = Math.max(4, by);
                int bMaxY = Math.min(Chunk.CHUNK_HEIGHT - 4, by + 2);

                if (wx >= bx - 1 && wx <= bx + 1 && wz >= bz - 1 && wz <= bz + 1 && wy >= bMinY && wy <= bMaxY) {
                    return true;
                }
            } else {
                double wiggleAmp = 3.0 * Math.sin(progress * Math.PI);
                double curX = targetX + (edgeRand.nextDouble() - 0.5) * wiggleAmp;
                double curY = targetY + (edgeRand.nextDouble() - 0.5) * (wiggleAmp * 0.4);
                double curZ = targetZ + (edgeRand.nextDouble() - 0.5) * wiggleAmp;

                float baseRadius = from.radius + (to.radius - from.radius) * (float) progress;
                float taper = 1.0f;
                if (progress < 0.15) taper = (float) (progress / 0.15);
                else if (progress > 0.85) taper = (float) ((1.0 - progress) / 0.15);
                float radius = baseRadius * (0.7f + 0.3f * taper);

                double dxSphere = wx - curX;
                double dzSphere = wz - curZ;
                double radiusSq = radius * radius;

                if (dxSphere * dxSphere + dzSphere * dzSphere < radiusSq) {
                    double stretchY = 1.15;
                    double dySphere = (wy - curY) / stretchY;
                    if (dxSphere * dxSphere + dySphere * dySphere + dzSphere * dzSphere < radiusSq) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockSolidDeterministic(Chunk chunk, int wx, int wy, int wz, java.util.List<CaveEdge> activeEdges, long worldSeed, NoiseRouter noiseRouter, BiomeGenerator biomeGen, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ) {
        if (wy < 4 || wy >= Chunk.CHUNK_HEIGHT - 4) {
            return false;
        }

        // 1. If within current chunk boundaries, read the actual block directly.
        if (wx >= chunkMinX && wx <= chunkMaxX && wz >= chunkMinZ && wz <= chunkMaxZ) {
            int lx = wx - chunkMinX;
            int lz = wz - chunkMinZ;
            int blockType = chunk.getBlockType(lx, wy, lz);
            return isSolidId(blockType);
        }

        // 2. If outside the chunk, determine the block state using the exact noise router:
        if (!isTerrainSolid(wx, wy, wz, noiseRouter, biomeGen)) {
            return false;
        }

        // Check if it was carved by a cave/mineshaft in our active networks
        for (CaveEdge edge : activeEdges) {
            if (edge.intersectsPoint(wx, wy, wz, noiseRouter, biomeGen)) {
                return false;
            }
        }

        return true;
    }

    private boolean isTerrainSolid(int wx, int wy, int wz, NoiseRouter noiseRouter, BiomeGenerator biomeGen) {
        if (wy < 4) return true;
        if (wy >= Chunk.CHUNK_HEIGHT - 4) return false;
        float[] climate = biomeGen.getClimateParams(wx, wz);
        int logicalY = wy - Chunk.LOGICAL_OFFSET_Y;
        DensityContextImpl ctx = new DensityContextImpl(wx, logicalY, wz, climate[2], climate[3], climate[4], climate[0], climate[1]);
        return noiseRouter.getDensity(ctx) > 0.0;
    }
}
