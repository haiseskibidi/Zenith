package com.za.zenith.world.generation.caves;

import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.BiomeGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CaveNetwork {
    public final List<CaveNode> nodes = new ArrayList<>();
    public final List<CaveEdge> edges = new ArrayList<>();

    public CaveNetwork(int chunkX, int chunkZ, long worldSeed, CaveSettings settings, BiomeGenerator biomeGen, CaveCarver.CliffPoint cliff) {
        long chunkSeed = worldSeed ^ (chunkX * 341873128712L + chunkZ * 132897987541L);
        Random random = new Random(chunkSeed);

        CaveNode root;
        boolean forceEntranceConnection = false;

        if (cliff != null) {
            // Predictable entrance exactly at the cliff base transition
            root = new CaveNode(cliff.x, cliff.y - 2.0, cliff.z, 2.5f, 0);
            
            // Build entrance edge leading from this cliff root to a deep root deterministic node
            // to make sure it immediately goes inside the mountain
            double length = random.nextDouble() * 30.0 + 20.0;
            double yaw = random.nextDouble() * 2.0 * Math.PI;

            // Decides if this entrance is a mineshaft
            boolean entranceIsMineshaft = random.nextFloat() < 0.50f;
            
            // Mineshafts must go strictly horizontally into the cliff, natural caves go downwards
            double pitch = 0.0;
            if (!entranceIsMineshaft) {
                pitch = -random.nextDouble() * (Math.PI / 6.0) - (Math.PI / 12.0); // downwards
            }

            double bx = root.x + Math.cos(pitch) * Math.sin(yaw) * length;
            double by = root.y + Math.sin(pitch) * length;
            double bz = root.z + Math.cos(pitch) * Math.cos(yaw) * length;

            CaveNode nextNode = new CaveNode(bx, by, bz, 2.3f, 1);
            nodes.add(root);
            nodes.add(nextNode);

            CaveEdge entranceEdge = new CaveEdge(root, nextNode);
            entranceEdge.isMineshaft = entranceIsMineshaft;
            edges.add(entranceEdge);

            // Now make generateBranches go from nextNode downwards
            int maxDepth = 3;
            generateBranches(nextNode, 1, maxDepth, random, settings, biomeGen);
            return;
        }

        // Deterministically find a root node (legacy fallback)
        int sx = (chunkX * Chunk.CHUNK_SIZE) + random.nextInt(Chunk.CHUNK_SIZE);
        int sz = (chunkZ * Chunk.CHUNK_SIZE) + random.nextInt(Chunk.CHUNK_SIZE);
        
        int approxSurfaceY = CaveCarver.getApproximateSurfaceHeight(sx, sz, biomeGen);
        int slope = CaveCarver.getApproximateSlope(sx, sz, biomeGen);

        if (slope >= 6) {
            // Slope entrance: root node starts directly at the surface slope
            root = new CaveNode(sx, approxSurfaceY - 2.0, sz, 2.2f, 0);
        } else {
            // Flat area: start deep underground
            double startY = random.nextInt(60) + 30; // Logical height under plains
            double internalY = startY + Chunk.LOGICAL_OFFSET_Y;
            root = new CaveNode(sx, internalY, sz, 2.5f, 0);
            
            // We want to force a connection to a surface entrance on a nearby slope
            forceEntranceConnection = true;
        }

        nodes.add(root);

        // If forced, find a slope coordinate in a 2-chunk radius and connect
        if (forceEntranceConnection) {
            double entranceX = sx;
            double entranceZ = sz;
            double entranceY = approxSurfaceY;
            boolean foundSlope = false;

            // Search spirals to find a slope
            for (int r = 1; r <= 2 && !foundSlope; r++) {
                for (int dx = -r; dx <= r && !foundSlope; dx++) {
                    for (int dz = -r; dz <= r && !foundSlope; dz++) {
                        int tx = sx + dx * Chunk.CHUNK_SIZE;
                        int tz = sz + dz * Chunk.CHUNK_SIZE;
                        int tSlope = CaveCarver.getApproximateSlope(tx, tz, biomeGen);
                        if (tSlope >= 6) {
                            entranceX = tx;
                            entranceZ = tz;
                            entranceY = CaveCarver.getApproximateSurfaceHeight(tx, tz, biomeGen) - 2.0;
                            foundSlope = true;
                        }
                    }
                }
            }

            // Create entrance node and link it to the deep root
            CaveNode entranceNode = new CaveNode(entranceX, entranceY, entranceZ, 2.0f, 0);
            nodes.add(entranceNode);
            
            CaveEdge entranceEdge = new CaveEdge(entranceNode, root);
            // Slanted connection edges from plains to deep roots are always natural caves
            entranceEdge.isMineshaft = false;
            edges.add(entranceEdge);
        }

        // Recursively build the cave branching graph downwards
        int maxDepth = 3;
        generateBranches(root, 0, maxDepth, random, settings, biomeGen);
    }

    private int totalBranchesCreated = 0;

    private void generateBranches(CaveNode parent, int depth, int maxDepth, Random random, CaveSettings settings, BiomeGenerator biomeGen) {
        int maxBranches = 4;
        double baseBranchChance = 0.10;
        if (settings != null && settings.tunnel != null) {
            maxBranches = settings.tunnel.maxBranches;
            baseBranchChance = settings.tunnel.branchChance;
        }

        if (depth >= maxDepth || totalBranchesCreated >= maxBranches) return;

        // Determine how many branches to spawn from this node
        int numBranches = 1;
        if (depth == 0) {
            // At root level, we want to split into 2 branches with high weight to create a network
            numBranches = (random.nextDouble() < baseBranchChance * 3.5) ? 2 : 1;
        } else {
            // Further branches have smaller chances, especially if we are close to the limit
            double branchChance = baseBranchChance;
            if (totalBranchesCreated >= maxBranches - 1) {
                branchChance *= 0.10; // strictly throttle branching close to maximum limit
            } else if (totalBranchesCreated >= maxBranches - 2) {
                branchChance *= 0.35; // reduce chance near limit
            }

            if (random.nextDouble() < branchChance) {
                numBranches = 2;
            } else {
                // If we don't branch, there's a chance to stop this tunnel completely
                // End chance increases with depth (e.g. 15% at depth 1, 35% at depth 2)
                double endChance = 0.15 + (depth * 0.20);
                if (random.nextDouble() < endChance) {
                    return; // End this branch entirely
                }
                numBranches = 1;
            }
        }

        if (numBranches > 1) {
            totalBranchesCreated++;
        }

        double logicalParentY = parent.y - Chunk.LOGICAL_OFFSET_Y;

        for (int i = 0; i < numBranches; i++) {
            // Decides if this edge should be generated as a straight reinforced Mineshaft (25% chance in medium depths)
            boolean nextIsMineshaft = false;
            if (logicalParentY > 0.0 && logicalParentY < 120.0 && random.nextFloat() < 0.25f) {
                nextIsMineshaft = true;
            }

            // Generate offset
            double length = random.nextDouble() * 40.0 + 25.0; // 25 to 65 blocks length
            double yaw = random.nextDouble() * 2.0 * Math.PI;
            
            // Mineshafts must go strictly horizontally, natural caves go downwards
            double pitch = 0.0;
            if (!nextIsMineshaft) {
                // Phi directed downwards to dive into the earth
                pitch = (random.nextDouble() - 0.75) * (Math.PI / 4.0); // angle directed downwards
            }

            double bx = parent.x + Math.cos(pitch) * Math.sin(yaw) * length;
            double by = parent.y + Math.sin(pitch) * length;
            double bz = parent.z + Math.cos(pitch) * Math.cos(yaw) * length;

            // Limit by height boundaries (keep within bedrock to logical limit Y)
            int approxSurf = CaveCarver.getApproximateSurfaceHeight((int) bx, (int) bz, biomeGen);
            double maxAllowedY = approxSurf - 12;
            if (by > maxAllowedY) {
                by = maxAllowedY;
            }
            if (by < Chunk.LOGICAL_OFFSET_Y - 110) {
                by = Chunk.LOGICAL_OFFSET_Y - 110;
            }

            // Radius scales based on height: larger in deep bottom, smaller near top
            float heightWeight = 1.0f;
            double logicalY = by - Chunk.LOGICAL_OFFSET_Y;
            if (logicalY > 120.0) {
                float factor = (float) (logicalY - 120.0) / 130.0f;
                heightWeight = Math.max(0.35f, 1.0f - factor * 0.65f);
            } else if (logicalY < 0.0) {
                float factor = (float) (-logicalY) / 110.0f;
                heightWeight = 1.0f + factor * 0.50f; // Expand up to 150% in deep levels
            }

            float radius = (random.nextFloat() * (settings.tunnel.maxRadius - settings.tunnel.minRadius) + settings.tunnel.minRadius) * heightWeight;
            
            // 15% chance to make it a giant room node
            if (random.nextFloat() < 0.15f && logicalY < 50.0) {
                radius *= 2.0f;
            }

            CaveNode child = new CaveNode(bx, by, bz, radius, depth + 1);
            nodes.add(child);

            CaveEdge edge = new CaveEdge(parent, child);
            edge.isMineshaft = nextIsMineshaft;
            edges.add(edge);

            generateBranches(child, depth + 1, maxDepth, random, settings, biomeGen);
        }
    }
}
