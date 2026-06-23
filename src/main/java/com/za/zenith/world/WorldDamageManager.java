package com.za.zenith.world;

import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.chunks.Chunk;
import com.za.zenith.world.World.BlockDamageInstance;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager responsible for block damage persistence, healing delay, and scar visual fading.
 * ponytail: isolated to keep World.java small and clean.
 */
public class WorldDamageManager {
    private final World world;
    private final Map<Long, BlockDamageInstance> blockDamageMap = new ConcurrentHashMap<>();

    public WorldDamageManager(World world) {
        this.world = world;
    }

    public Map<Long, BlockDamageInstance> getBlockDamageMap() {
        return blockDamageMap;
    }

    public float getBlockDamage(BlockPos pos) {
        return getBlockDamage(pos.x(), pos.y(), pos.z());
    }

    public float getBlockDamage(int x, int y, int z) {
        BlockDamageInstance info = blockDamageMap.get(World.packBlockPos(x, y, z));
        return (info != null) ? info.getDamage() : 0.0f;
    }

    public List<Vector4f> getBlockHitHistory(BlockPos pos) {
        BlockDamageInstance info = blockDamageMap.get(World.packBlockPos(pos.x(), pos.y(), pos.z()));
        return (info != null) ? info.getHitHistory() : new ArrayList<>();
    }

    public void setBlockDamage(BlockPos pos, float damage) {
        setBlockDamage(pos, damage, new ArrayList<>());
    }

    public void setBlockDamage(BlockPos pos, float damage, List<Vector4f> history) {
        long packed = World.packBlockPos(pos.x(), pos.y(), pos.z());
        if (damage <= 0.0f) {
            if (blockDamageMap.remove(packed) != null) {
                Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
                if (chunk != null) {
                    chunk.removeLocalBlockDamage(packed);
                }
            }
        } else {
            BlockDamageInstance info = blockDamageMap.get(packed);
            if (info != null) {
                info.setDamage(damage);
                info.resetLastHitTime(); // Refresh the delay on every hit

                // Update history smoothly: only add new ones that aren't already there
                List<Vector4f> targetHistory = info.getHitHistory();
                if (history.size() > targetHistory.size()) {
                    for (int i = targetHistory.size(); i < history.size(); i++) {
                        targetHistory.add(new Vector4f(history.get(i)));
                    }
                }

                // Cap at 16
                while (targetHistory.size() > 16) {
                    targetHistory.remove(0);
                }
            } else {
                blockDamageMap.put(packed, new BlockDamageInstance(damage, world.getBlock(pos).copy(), new ArrayList<>(history)));
                Chunk chunk = world.getChunk(com.za.zenith.world.chunks.ChunkPos.fromBlockPos(pos.x(), pos.z()));
                if (chunk != null) {
                    chunk.addLocalBlockDamage(packed);
                }
            }
        }
    }

    public void update(float deltaTime) {
        if (blockDamageMap.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        for (java.util.Map.Entry<Long, BlockDamageInstance> entry : blockDamageMap.entrySet()) {
            long packed = entry.getKey();
            BlockDamageInstance info = entry.getValue();

            // --- HEALING DELAY ---
            // Do not start healing if block was hit in the last 5 seconds
            if (currentTime - info.getLastHitTime() < 5000) continue;

            float damage = info.getDamage();
            int bx = World.unpackBlockX(packed);
            int by = World.unpackBlockY(packed);
            int bz = World.unpackBlockZ(packed);
            int blockType = world.getRawBlockData(bx, by, bz) >> 8;
            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(blockType);

            if (def.getHealingSpeed() > 0) {
                float maxHealth = def.getHardness() * 10.0f;
                float healAmount = def.getHealingSpeed() * maxHealth * deltaTime;
                float newDamage = damage - healAmount;

                if (newDamage <= 0) {
                    blockDamageMap.remove(packed);
                } else {
                    info.setDamage(newDamage);

                    // --- SMOOTH SCAR FADING ---
                    List<Vector4f> history = info.getHitHistory();
                    if (!history.isEmpty()) {
                        // Target total intensity across all scars based on health
                        float targetTotalIntensity = (newDamage / maxHealth) * 16.0f;

                        float currentTotalIntensity = 0;
                        for (int i = 0; i < history.size(); i++) {
                            currentTotalIntensity += history.get(i).w;
                        }

                        if (currentTotalIntensity > targetTotalIntensity) {
                            float toRemove = currentTotalIntensity - targetTotalIntensity;
                            while (toRemove > 0 && !history.isEmpty()) {
                                Vector4f oldest = history.get(0);
                                if (oldest.w <= toRemove) {
                                    toRemove -= oldest.w;
                                    history.remove(0);
                                } else {
                                    oldest.w -= toRemove;
                                    toRemove = 0;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
