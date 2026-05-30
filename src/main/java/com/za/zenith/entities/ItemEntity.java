package com.za.zenith.entities;

import com.za.zenith.world.World;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.entities.inventory.Slot;
import com.za.zenith.entities.inventory.SlotGroup;
import org.joml.Vector3f;
import java.util.List;

/**
 * Entity representing an item dropped in the world.
 */
public class ItemEntity extends Entity {
    private final ItemStack stack;
    private float age;
    private float pickupDelay = 1.0f; // 1 second before it can be picked up
    private final Vector3f angularVelocity = new Vector3f();
    private boolean isBeingAttracted = false;
    private boolean isLockedOnPlayer = false; // "Мертвая хватка"
    private boolean isSleeping = false;
    private float sleepTimer = 0;
    private float mergeTimer = 0;
    
    private com.za.zenith.world.chunks.ChunkPos lastChunkPos;
    private final Vector3f vPool1 = new Vector3f();

    public ItemEntity(Vector3f position, ItemStack stack) {
        super(position, 0.25f, 0.25f);
        this.stack = stack;
        this.age = 0;
        this.lastChunkPos = com.za.zenith.world.chunks.ChunkPos.fromBlockPos((int)position.x, (int)position.z);
    }
    
    public void setAngularVelocity(Vector3f angVel) {
        this.angularVelocity.set(angVel);
    }
    
    @Override
    protected void onUpdate(float deltaTime, World world) {
        age += deltaTime;
        if (age > com.za.zenith.world.physics.PhysicsSettings.getInstance().itemDespawnTime) {
            setRemoved();
            return;
        }

        if (isSleeping) {
            processSleeping(deltaTime, world);
            return;
        }

        // --- CHUNK BOUNDARY CHECK ---
        int cx = (int) Math.floor(position.x / 16.0);
        int cz = (int) Math.floor(position.z / 16.0);
        if (cx != lastChunkPos.x() || cz != lastChunkPos.z()) {
            com.za.zenith.world.chunks.ChunkPos newPos = new com.za.zenith.world.chunks.ChunkPos(cx, cz);
            world.updateItemSpatial(this, lastChunkPos, newPos);
            lastChunkPos = newPos;
        }

        if (pickupDelay > 0) pickupDelay -= deltaTime;

        // 0. ITEM MERGING (Every frame while moving, less when sleeping)
        mergeTimer += deltaTime;
        if (mergeTimer >= com.za.zenith.world.physics.PhysicsSettings.getInstance().itemMergeInterval) {
            mergeTimer = 0;
            tryMerge(world);
        }

        Player player = world.getPlayer();
        
        // 1. MAGNETIC OPTIMIZATION (Zero allocations)
        if (canBePickedUp() && player != null && !player.getInventory().isFull()) {
            float px = player.getPosition().x;
            float py = player.getPosition().y + player.getHeight() * 0.5f;
            float pz = player.getPosition().z;
            
            float dx = px - position.x;
            float dy = py - position.y;
            float dz = pz - position.z;
            float distSq = dx*dx + dy*dy + dz*dz;

            if (isLockedOnPlayer || distSq < 100.0f) { 
                com.za.zenith.world.items.component.MagneticComponent magnet = player.getInventory().getActiveComponent(com.za.zenith.world.items.component.MagneticComponent.class);
                
                if (magnet != null && (distSq < magnet.attractionRadius * magnet.attractionRadius || isLockedOnPlayer)) {
                    isBeingAttracted = true;
                    isLockedOnPlayer = true;
                    isSleeping = false; 
                    
                    float distance = (float)Math.sqrt(distSq);
                    vPool1.set(dx, dy, dz).normalize();
                    float approachSpeed = 12.0f + (1.0f - Math.min(1.0f, distance / 4.0f)) * (magnet.attractionForce * 0.2f);
                    
                    velocity.set(player.getVelocity());
                    velocity.add(vPool1.mul(approachSpeed));
                    onGround = false; 
                } else {
                    isBeingAttracted = false;
                    isLockedOnPlayer = false;
                }
            }
        }

        // 2. PHYSICS SLEEPING LOGIC
        if (onGround && velocity.lengthSquared() < 0.001f && !isBeingAttracted) {
            sleepTimer += deltaTime;
            if (sleepTimer > 2.0f) {
                isSleeping = true;
                velocity.set(0, 0, 0);
                return;
            }
        } else {
            sleepTimer = 0;
        }

        float gravityMultiplier = stack.getItem().getWeight();
        if (!flying && !isBeingAttracted) {
            velocity.y = Math.max(velocity.y + GRAVITY * gravityMultiplier * deltaTime, TERMINAL_VELOCITY);
        }
        
        move(world, velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);

        float friction = isBeingAttracted ? 1.0f : (onGround ? 0.8f : 0.98f);
        velocity.mul(friction);
        
        if (onGround && !isBeingAttracted) {
            rotation.x = lerpAngle(rotation.x, 0, deltaTime * 5.0f);
            rotation.z = lerpAngle(rotation.z, 0, deltaTime * 5.0f);
            angularVelocity.mul(0.8f);
        } else if (isBeingAttracted) {
            angularVelocity.y += deltaTime * 20.0f;
            angularVelocity.x += deltaTime * 5.0f;
        }
        
        rotation.add(angularVelocity.x * deltaTime, angularVelocity.y * deltaTime, angularVelocity.z * deltaTime);
    }

    private void processSleeping(float deltaTime, World world) {
        mergeTimer += deltaTime;
        if (mergeTimer >= 1.0f) {
            mergeTimer = 0;
            tryMerge(world);
        }

        // 1. MAGNET CHECK (Optimization-friendly)
        com.za.zenith.entities.Player player = world.getPlayer();
        if (player != null && !player.getInventory().isFull()) {
            float dx = player.getPosition().x - position.x;
            float dy = (player.getPosition().y + player.getHeight() * 0.5f) - position.y;
            float dz = player.getPosition().z - position.z;
            float distSq = dx*dx + dy*dy + dz*dz;

            if (distSq < 100.0f) { // Only check detailed magnet component if player is within 10 blocks
                com.za.zenith.world.items.component.MagneticComponent magnet = player.getInventory().getActiveComponent(com.za.zenith.world.items.component.MagneticComponent.class);
                if (magnet != null && distSq < magnet.attractionRadius * magnet.attractionRadius) {
                    isSleeping = false;
                    isBeingAttracted = true;
                    isLockedOnPlayer = true;
                    return; 
                }
            }
        }

        if (world.getWorldTime() % 1.5f < deltaTime) {             if (world.getBlock((int)Math.floor(position.x), (int)Math.floor(position.y - 0.05f), (int)Math.floor(position.z)).isAir()) {
                isSleeping = false;
                onGround = false;
                sleepTimer = 0;
             }
        }
    }

    public void wakeUp() {
        this.isSleeping = false;
        this.sleepTimer = 0;
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff < -Math.PI) diff += Math.PI * 2;
        while (diff > Math.PI) diff -= Math.PI * 2;
        return start + diff * t;
    }
    
    public com.za.zenith.world.chunks.ChunkPos getLastChunkPos() {
        return lastChunkPos;
    }

    public ItemStack getStack() {
        return stack;
    }
    
    public boolean canBePickedUp() {
        return pickupDelay <= 0;
    }
    
    public float getAge() {
        return age;
    }

    public boolean isBeingAttracted() {
        return isBeingAttracted;
    }

    private void tryMerge(World world) {
        if (this.isRemoved() || stack.isFull()) return;

        float radius = com.za.zenith.world.physics.PhysicsSettings.getInstance().itemMergeRadius;
        float radiusSq = radius * radius;

        // SPATIAL MERGING: Only check ground entities in the same chunk
        List<com.za.zenith.entities.Entity> entities = world.getGroundEntitiesInChunk(lastChunkPos);
        for (com.za.zenith.entities.Entity e : entities) {
            if (e instanceof ItemEntity other && other != this && !other.isRemoved()) {
                if (other.stack.getItem().equals(this.stack.getItem())) {
                    float distSq = position.distanceSquared(other.position);
                    if (distSq < radiusSq) {
                        if (this.age < other.age) continue;

                        int canAccept = stack.getItem().getMaxStackSize() - stack.getCount();
                        if (canAccept > 0) {
                            int toTake = Math.min(canAccept, other.stack.getCount());
                            this.stack.setCount(this.stack.getCount() + toTake);
                            other.stack.setCount(other.stack.getCount() - toTake);

                            if (other.stack.getCount() <= 0) {
                                other.setRemoved();
                            }
                            this.isSleeping = false; 
                            if (stack.isFull()) break;
                        }
                    }
                }
            }
        }
    }
}


