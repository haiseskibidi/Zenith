package com.za.zenith.world;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.Player;
import com.za.zenith.world.chunks.ChunkPos;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager responsible for entity lifecycle, chunk-based spatial indexing for item entities,
 * culling far entities, and player pickup attraction logic.
 * ponytail: isolated to keep World.java small and clean.
 */
public class WorldEntityManager {
    private final World world;
    private final List<Entity> entities = Collections.synchronizedList(new ArrayList<>());
    private final Map<ChunkPos, List<Entity>> groundEntityMap = new ConcurrentHashMap<>();

    public WorldEntityManager(World world) {
        this.world = world;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public Map<ChunkPos, List<Entity>> getGroundEntityMap() {
        return groundEntityMap;
    }

    public List<Entity> getGroundEntitiesInChunk(ChunkPos pos) {
        return groundEntityMap.getOrDefault(pos, Collections.emptyList());
    }

    public void spawnEntity(Entity entity) {
        if (entity == null) return;
        entities.add(entity);
        if (entity.isGroundEntity()) {
            ChunkPos cp = ChunkPos.fromBlockPos((int) entity.getPosition().x, (int) entity.getPosition().z);
            groundEntityMap.computeIfAbsent(cp, k -> Collections.synchronizedList(new ArrayList<>())).add(entity);
        }
    }

    public void spawnItem(ItemStack stack, float x, float y, float z) {
        com.za.zenith.entities.ItemEntity entity = new com.za.zenith.entities.ItemEntity(new Vector3f(x, y, z), stack);
        spawnEntity(entity);
    }

    public void updateItemSpatial(com.za.zenith.entities.ItemEntity item, ChunkPos oldPos, ChunkPos newPos) {
        if (oldPos != null) {
            List<Entity> list = groundEntityMap.get(oldPos);
            if (list != null) list.remove(item);
        }
        if (newPos != null) {
            groundEntityMap.computeIfAbsent(newPos, k -> Collections.synchronizedList(new ArrayList<>())).add(item);
        }
    }

    public void update(float deltaTime, Player player) {
        float px = 0;
        float py = 0;
        float pz = 0;
        boolean inventoryFull = false;
        if (player != null) {
            px = player.getPosition().x;
            py = player.getPosition().y + player.getHeight() * 0.5f;
            pz = player.getPosition().z;
            inventoryFull = player.getInventory().isFull();
        }

        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);

            if (entity.isRemoved()) {
                if (entity instanceof com.za.zenith.entities.ItemEntity item) {
                    ChunkPos cp = item.getLastChunkPos();
                    if (cp != null) {
                        List<Entity> list = groundEntityMap.get(cp);
                        if (list != null) list.remove(item);
                    }
                } else if (entity instanceof com.za.zenith.entities.ResourceEntity) {
                    ChunkPos cp = ChunkPos.fromBlockPos((int) entity.getPosition().x, (int) entity.getPosition().z);
                    List<Entity> list = groundEntityMap.get(cp);
                    if (list != null) list.remove(entity);
                }
                entities.remove(i);
                continue;
            }

            // SIMULATION DISTANCE GUARD: Remove distant entities
            if (player != null && entity != player) {
                float edx = px - entity.getPosition().x;
                float edz = pz - entity.getPosition().z;
                float distSq = edx * edx + edz * edz;

                // Aggressive culling for static resources: 128m radius (8 chunks)
                if (entity instanceof com.za.zenith.entities.ResourceEntity) {
                    if (distSq > 128 * 128) {
                        entity.setRemoved();
                        continue;
                    }
                } else {
                    // Standard culling for dynamic entities: 320m radius
                    if (distSq > 320 * 320) {
                        entity.setRemoved();
                        continue;
                    }
                }
            }

            entity.update(deltaTime, world);

            // Item Pickup logic - Optimized zero-allocation
            if (player != null && entity instanceof com.za.zenith.entities.ItemEntity itemEntity) {
                if (itemEntity.canBePickedUp()) {
                    Vector3f itemPos = itemEntity.getPosition();
                    float dx = px - itemPos.x;
                    float dy = py - itemPos.y;
                    float dz = pz - itemPos.z;
                    float distSq = dx * dx + dy * dy + dz * dz;

                    float pickupRadius = com.za.zenith.world.physics.PhysicsSettings.getInstance().itemPickupRadius;
                    com.za.zenith.world.items.component.MagneticComponent magnet = player.getInventory().getActiveComponent(com.za.zenith.world.items.component.MagneticComponent.class);
                    if (magnet != null) {
                        pickupRadius = magnet.pickupRadius;
                    }

                    boolean isMagnetic = itemEntity.isBeingAttracted();
                    float effectiveRadius = isMagnetic ? pickupRadius * 1.5f : pickupRadius;

                    if (distSq < effectiveRadius * effectiveRadius || player.getBoundingBox().intersects(itemEntity.getBoundingBox())) {
                        if (itemEntity.isRemoved()) continue;

                        if (inventoryFull) {
                            com.za.zenith.engine.graphics.ui.NotificationTriggers.getInstance().onInventoryFull();
                        } else if (player.getInventory().addItem(itemEntity.getStack(), true)) {
                            itemEntity.setRemoved();
                            com.za.zenith.utils.Logger.info("Picked up item: %s", itemEntity.getStack().getItem().getName());
                            inventoryFull = player.getInventory().isFull();

                            // Remove from spatial map safely using actual registered chunk position
                            ChunkPos cp = itemEntity.getLastChunkPos();
                            if (cp != null) {
                                List<Entity> list = groundEntityMap.get(cp);
                                if (list != null) list.remove(itemEntity);
                            }

                            continue;
                        } else {
                            inventoryFull = true;
                        }
                    }
                }
            }

            // Remove dead entities (if they are LivingEntity)
            if (entity instanceof com.za.zenith.entities.LivingEntity living) {
                if (living.isDead()) {
                    entities.remove(i);
                }
            }
        }
    }
}
