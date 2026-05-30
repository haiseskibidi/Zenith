package com.za.zenith.world.generation.pipeline.steps;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.ResourceEntity;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.generation.ScavengeSettings;
import com.za.zenith.world.generation.pipeline.GenerationStep;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.items.ItemRegistry;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Шаг генерации, рассыпающий 3D ресурсы (сущности) по поверхности мира.
 * Версия 2.0: Кластерный спавн, Data-Driven фильтры и оптимизированный цикл.
 */
public class ScavengeDecorationStep implements GenerationStep {

    public ScavengeDecorationStep(long seed) {
    }

    @Override
    public void generateTerrain(Chunk chunk) {
    }

    @Override
    public void generateStructures(World world, Chunk chunk) {
        int chunkX = chunk.getPosition().x() * Chunk.CHUNK_SIZE;
        int chunkZ = chunk.getPosition().z() * Chunk.CHUNK_SIZE;

        Random random = new Random(world.getSeed() + chunk.getPosition().hashCode() * 31L);

        for (ScavengeSettings.ScavengeDefinition def : ScavengeSettings.getDefinitions()) {
            // Aggressive optimization: only 2 attempts per chunk
            int attempts = 2;
            for (int attempt = 0; attempt < attempts; attempt++) {                if (random.nextFloat() < def.chance) {
                    int lx = random.nextInt(Chunk.CHUNK_SIZE);
                    int lz = random.nextInt(Chunk.CHUNK_SIZE);
                    int centerX = chunkX + lx;
                    int centerZ = chunkZ + lz;

                    int surfaceY = findSurfaceY(chunk, lx, lz);
                    if (surfaceY <= 0) continue;

                    // 1. Biome Check
                    if (!def.biomes.isEmpty()) {
                        var biome = world.getBiomeManager().getBiome(centerX, centerZ);
                        if (biome == null || !def.biomes.contains(biome.getId())) continue;
                    }

                    // 2. Ground Block Check
                    Block groundBlock = chunk.getBlock(lx, surfaceY, lz);
                    BlockDefinition groundDef = BlockRegistry.getBlock(groundBlock.getType());
                    if (!def.groundBlocks.isEmpty()) {
                        if (!def.groundBlocks.contains(groundDef.getIdentifier())) continue;
                    }

                    // 3. Cluster Spawn
                    int count = def.minGroup + (def.maxGroup > def.minGroup ? random.nextInt(def.maxGroup - def.minGroup + 1) : 0);
                    java.util.List<Vector3f> clusterPositions = new java.util.ArrayList<>();
                    
                    for (int i = 0; i < count; i++) {
                        boolean posValid = false;
                        float finalX = 0, finalZ = 0;
                        int worldX = 0, worldZ = 0;
                        Chunk targetChunk = null;
                        int itemY = 0;

                        // Rejection sampling: try to find a spot that doesn't overlap
                        for (int tries = 0; tries < 5; tries++) {
                            float dx = (random.nextFloat() * 2.4f) - 1.2f;
                            float dz = (random.nextFloat() * 2.4f) - 1.2f;

                            finalX = centerX + dx;
                            finalZ = centerZ + dz;

                            worldX = (int)Math.floor(finalX);
                            worldZ = (int)Math.floor(finalZ);

                            targetChunk = world.getChunkInternal(worldX >> 4, worldZ >> 4);
                            if (targetChunk == null) continue;

                            itemY = findSurfaceY(targetChunk, worldX & 15, worldZ & 15);
                            if (itemY <= 0) continue;

                            // Distance check (Min 0.7m squared = 0.49f)
                            float minDistanceSq = 0.49f;
                            posValid = true;

                            // Check against items already in this cluster
                            for (Vector3f other : clusterPositions) {
                                float tdx = finalX - other.x;
                                float tdz = finalZ - other.z;
                                if (tdx * tdx + tdz * tdz < minDistanceSq) {
                                    posValid = false;
                                    break;
                                }
                            }
                            if (!posValid) continue;

                            // Check against items already in the chunk (from previous clusters)
                            java.util.List<Entity> existing = world.getGroundEntitiesInChunk(targetChunk.getPosition());
                            if (!existing.isEmpty()) {
                                for (Entity e : existing) {
                                    float tdx = finalX - e.getPosition().x;
                                    float tdz = finalZ - e.getPosition().z;
                                    // Only check XZ overlap at similar height
                                    if (Math.abs(e.getPosition().y - (itemY + 1.0f)) < 0.5f) {
                                        if (tdx * tdx + tdz * tdz < minDistanceSq) {
                                            posValid = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (posValid) break;
                        }

                        if (!posValid) continue;

                        var itemDef = ItemRegistry.getItem(def.itemId);
                        if (itemDef == null) continue;

                        // Position: top of the block (itemY + 1)
                        Vector3f pos = new Vector3f(finalX, itemY + 1.0f, finalZ);
                        clusterPositions.add(pos);

                        float rotation = random.nextFloat() * (float)Math.PI * 2;
                        
                        // Use ResourceEntity for scavenge items (3D Foraging)
                        ResourceEntity resource = new ResourceEntity(pos, new ItemStack(itemDef, 1), rotation);
                        world.spawnEntity(resource);
                    }
                }
            }
        }
    }

    private int findSurfaceY(Chunk chunk, int lx, int lz) {
        for (int y = Chunk.CHUNK_HEIGHT - 1; y > 0; y--) {
            Block b = chunk.getBlock(lx, y, lz);
            if (!b.isAir()) {
                BlockDefinition def = BlockRegistry.getBlock(b.getType());
                // We need solid, non-transparent ground (not grass/flowers)
                if (def.isSolid() && !def.isTransparent()) {
                    return y;
                }
            }
        }
        return -1;
    }}
