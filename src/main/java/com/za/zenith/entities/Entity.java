package com.za.zenith.entities;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.physics.AABB;
import com.za.zenith.world.physics.VoxelShape;
import org.joml.Vector3f;

/**
 * Base class for all entities in the game (Players, Mobs, Items, etc.).
 * Handles physics, movement, and collisions.
 */
public abstract class Entity {
    protected Vector3f position;
    protected Vector3f prevPosition;
    protected final Vector3f velocity;
    protected final Vector3f rotation;
    protected final Vector3f prevRotation;
    protected com.za.zenith.world.physics.AABB boundingBox;
    
    protected boolean onGround = false;
    protected boolean flying = false;
    protected boolean removed = false;
    
    protected static final float GRAVITY = -28.0f;
    protected static final float TERMINAL_VELOCITY = -50.0f;

    private final java.util.Map<Class<? extends com.za.zenith.entities.components.EntityComponent>, com.za.zenith.entities.components.EntityComponent> components = new java.util.HashMap<>();

    public <T extends com.za.zenith.entities.components.EntityComponent> T getComponent(Class<T> type) {
        return type.cast(components.get(type));
    }

    public <T extends com.za.zenith.entities.components.EntityComponent> boolean hasComponent(Class<T> type) {
        return components.containsKey(type);
    }

    public void addComponent(com.za.zenith.entities.components.EntityComponent component) {
        components.put(component.getClass(), component);
    }

    public Entity(Vector3f position, float width, float height) {
        this.position = new Vector3f(position);
        this.prevPosition = new Vector3f(position);
        this.velocity = new Vector3f();
        this.rotation = new Vector3f();
        this.prevRotation = new Vector3f();
        setBoundingBox(width, height);
    }

    protected void setBoundingBox(float width, float height) {
        this.boundingBox = new AABB(
            -width / 2, 0, -width / 2,
            width / 2, height, width / 2
        );
    }

    public void setRemoved() {
        this.removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    public final void update(float deltaTime, World world) {
        // Гарантированно сохраняем состояние начала тика для интерполяции
        prevPosition.set(position);
        prevRotation.set(rotation);
        
        onUpdate(deltaTime, world);

        // Обновляем компоненты
        for (com.za.zenith.entities.components.EntityComponent component : components.values()) {
            component.update(this, deltaTime, world);
        }

        // Snap very small residual velocities to zero
        if (Math.abs(velocity.x) < 0.005f) velocity.x = 0f;
        if (Math.abs(velocity.z) < 0.005f) velocity.z = 0f;
        if (onGround && Math.abs(velocity.y) < 0.005f) velocity.y = 0f;
    }

    /**
     * Реализация логики конкретной сущности.
     */
    protected abstract void onUpdate(float deltaTime, World world);

    protected void applyGravity(float deltaTime) {
        if (!flying) {
            velocity.y = Math.max(velocity.y + GRAVITY * deltaTime, TERMINAL_VELOCITY);
        }
    }

    public Vector3f getInterpolatedPosition(float alpha) {
        return new Vector3f(prevPosition).lerp(position, alpha);
    }

    public Vector3f getInterpolatedRotation(float alpha) {
        Vector3f result = new Vector3f();
        result.x = lerpAngle(prevRotation.x, rotation.x, alpha);
        result.y = lerpAngle(prevRotation.y, rotation.y, alpha);
        result.z = lerpAngle(prevRotation.z, rotation.z, alpha);
        return result;
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff < -Math.PI) diff += Math.PI * 2;
        while (diff > Math.PI) diff -= Math.PI * 2;
        return start + diff * t;
    }

    protected void move(World world, float dx, float dy, float dz) {
        float originalDx = dx;
        float originalDy = dy;
        float originalDz = dz;
        
        // 1. UNSTUCK: Если сущность уже внутри блока (например, при спавне), 
        // пытаемся мягко вытолкнуть её вверх
        AABB currentBox = boundingBox.offset(position);
        if (isCollidingAt(world, currentBox)) {
            for (int i = 0; i < 10; i++) { // Проверяем 10 ступеней по 0.1 блока
                float lift = 0.1f * (i + 1);
                if (!isCollidingAt(world, boundingBox.offset(position.x, position.y + lift, position.z))) {
                    position.y += lift;
                    currentBox = boundingBox.offset(position);
                    break;
                }
            }
        }

        // 2. VERTICAL COLLISION
        if (dy != 0) {
            if (dy > 0) onGround = false;

            int minX = (int) Math.floor(currentBox.minX());
            int maxX = (int) Math.floor(currentBox.maxX());
            int minZ = (int) Math.floor(currentBox.minZ());
            int maxZ = (int) Math.floor(currentBox.maxZ());
            int minY = (int) Math.floor(currentBox.minY() + Math.min(0, dy));
            int maxY = (int) Math.floor(currentBox.maxY() + Math.max(0, dy));

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    AABB blockBox = box.offset(x, y, z);
                                    if (currentBox.offset(0, dy, 0).intersects(blockBox)) {
                                        if (dy > 0) dy = blockBox.minY() - currentBox.maxY() - 0.001f;
                                        else {
                                            dy = blockBox.maxY() - currentBox.minY() + 0.001f;
                                            onGround = true;
                                        }
                                        velocity.y = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.y += dy;
            currentBox = boundingBox.offset(position);
        }

        // Если мы падали, но не коснулись земли (dy остался равен originalDy), значит мы в воздухе
        if (originalDy < -0.001f && onGround && Math.abs(dy - originalDy) < 0.0001f) {
            onGround = false;
        }

        // 3. HORIZONTAL COLLISION (X)
        if (dx != 0) {
            int minX = (int) Math.floor(currentBox.minX() + Math.min(0, dx));
            int maxX = (int) Math.floor(currentBox.maxX() + Math.max(0, dx));
            int minZ = (int) Math.floor(currentBox.minZ());
            int maxZ = (int) Math.floor(currentBox.maxZ());
            int minY = (int) Math.floor(currentBox.minY());
            int maxY = (int) Math.floor(currentBox.maxY());

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    AABB blockBox = box.offset(x, y, z);
                                    if (currentBox.offset(dx, 0, 0).intersects(blockBox)) {
                                        if (dx > 0) dx = blockBox.minX() - currentBox.maxX() - 0.001f;
                                        else dx = blockBox.maxX() - currentBox.minX() + 0.001f;
                                        velocity.x = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.x += dx;
            currentBox = boundingBox.offset(position);
        }

        // 4. HORIZONTAL COLLISION (Z)
        if (dz != 0) {
            int minX = (int) Math.floor(currentBox.minX());
            int maxX = (int) Math.floor(currentBox.maxX());
            int minZ = (int) Math.floor(currentBox.minZ() + Math.min(0, dz));
            int maxZ = (int) Math.floor(currentBox.maxZ() + Math.max(0, dz));
            int minY = (int) Math.floor(currentBox.minY());
            int maxY = (int) Math.floor(currentBox.maxY());

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    AABB blockBox = box.offset(x, y, z);
                                    if (currentBox.offset(0, 0, dz).intersects(blockBox)) {
                                        if (dz > 0) dz = blockBox.minZ() - currentBox.maxZ() - 0.001f;
                                        else dz = blockBox.maxZ() - currentBox.minZ() + 0.001f;
                                        velocity.z = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            position.z += dz;
        }
    }

    private boolean isCollidingAt(World world, AABB box) {
        int minX = (int) Math.floor(box.minX());
        int maxX = (int) Math.floor(box.maxX());
        int minY = (int) Math.floor(box.minY());
        int maxY = (int) Math.floor(box.maxY());
        int minZ = (int) Math.floor(box.minZ());
        int maxZ = (int) Math.floor(box.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlock(x, y, z);
                    if (!block.isAir() && block.isSolid()) {
                        VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                        if (shape != null) {
                            for (AABB bBox : shape.getBoxes()) {
                                if (box.intersects(bBox.offset(x, y, z))) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public Vector3f getPosition() { return position; }
    public Vector3f getVelocity() { return velocity; }
    public Vector3f getRotation() { return rotation; }
    public AABB getBoundingBox() { return boundingBox.offset(position); }
    public boolean isOnGround() { return onGround; }
    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    public void setPosition(float x, float y, float z) { position.set(x, y, z); }
}


