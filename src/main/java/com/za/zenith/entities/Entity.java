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

    public boolean horizontalCollision = false;

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

    /**
     * @return true if this entity should be tracked in the ground spatial map (items, rocks, etc.)
     */
    public boolean isGroundEntity() {
        return this instanceof ItemEntity || this instanceof ResourceEntity;
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
        float startX = position.x;
        float startZ = position.z;
        
        // 1. Initialize local ChunkCache covering the entire motion Broadphase bounding box
        AABB currentBox = boundingBox.offset(position);
        float minXF = Math.min(currentBox.minX(), currentBox.minX() + dx);
        float maxXF = Math.max(currentBox.maxX(), currentBox.maxX() + dx);
        float minYF = Math.min(currentBox.minY(), currentBox.minY() + dy) - 1.0f; // buffer for unstuck
        float maxYF = Math.max(currentBox.maxY(), currentBox.maxY() + dy) + 3.0f; // +3 for water step-up headroom checks
        float minZF = Math.min(currentBox.minZ(), currentBox.minZ() + dz);
        float maxZF = Math.max(currentBox.maxZ(), currentBox.maxZ() + dz);

        com.za.zenith.world.chunks.ChunkCache cache = new com.za.zenith.world.chunks.ChunkCache(
            world,
            (int) Math.floor(minXF),
            (int) Math.floor(minZF),
            (int) Math.floor(maxXF),
            (int) Math.floor(maxZF)
        );

        // 2. UNSTUCK: Softly push the entity up if already stuck inside a solid block
        if (isCollidingAt(cache, currentBox)) {
            for (int i = 0; i < 10; i++) {
                float lift = 0.1f * (i + 1);
                if (!isCollidingAt(cache, boundingBox.offset(position.x, position.y + lift, position.z))) {
                    position.y += lift;
                    currentBox = boundingBox.offset(position);
                    break;
                }
            }
        }

        // 3. VERTICAL COLLISION
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
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(boundingBox, position.x, position.y + dy, position.z, box, x, y, z)) {
                                        if (dy > 0) dy = (box.minY() + y) - (boundingBox.maxY() + position.y) - 0.001f;
                                        else {
                                            dy = (box.maxY() + y) - (boundingBox.minY() + position.y) + 0.001f;
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

        // Reset onGround if falling through air without hitting anything
        if (originalDy < -0.001f && onGround && Math.abs(dy - originalDy) < 0.0001f) {
            onGround = false;
        }

        // 4. HORIZONTAL COLLISION (X)
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
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(boundingBox, position.x + dx, position.y, position.z, box, x, y, z)) {
                                        if (dx > 0) dx = (box.minX() + x) - (boundingBox.maxX() + position.x) - 0.001f;
                                        else dx = (box.maxX() + x) - (boundingBox.minX() + position.x) + 0.001f;
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

        // 5. HORIZONTAL COLLISION (Z)
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
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null) {
                                for (AABB box : shape.getBoxes()) {
                                    if (AABB.intersects(boundingBox, position.x, position.y, position.z + dz, box, x, y, z)) {
                                        if (dz > 0) dz = (box.minZ() + z) - (boundingBox.maxZ() + position.z) - 0.001f;
                                        else dz = (box.maxZ() + z) - (boundingBox.minZ() + position.z) + 0.001f;
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

        this.horizontalCollision = (Math.abs(originalDx) > 0.00001f && Math.abs(dx) < Math.abs(originalDx) * 0.9f) ||
                                   (Math.abs(originalDz) > 0.00001f && Math.abs(dz) < Math.abs(originalDz) * 0.9f);
    }

    private boolean isCollidingAt(com.za.zenith.world.chunks.ChunkCache cache, AABB box) {
        int minX = (int) Math.floor(box.minX());
        int maxX = (int) Math.floor(box.maxX());
        int minY = (int) Math.floor(box.minY());
        int maxY = (int) Math.floor(box.maxY());
        int minZ = (int) Math.floor(box.minZ());
        int maxZ = (int) Math.floor(box.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = cache.getBlock(x, y, z);
                    if (!block.isAir() && block.isSolid()) {
                        VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                        if (shape != null) {
                            for (AABB bBox : shape.getBoxes()) {
                                if (AABB.intersects(box, 0, 0, 0, bBox, x, y, z)) return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public float getSubmersionRatio(World world) {
        if (world == null) return 0.0f;
        
        int px = (int) Math.floor(position.x);
        int pz = (int) Math.floor(position.z);
        float height = boundingBox.maxY() - boundingBox.minY();
        
        // Ищем верхнюю границу воды в колонке вокруг сущности
        float waterY = -999.0f;
        int startY = (int) Math.floor(position.y + height);
        int endY = (int) Math.floor(position.y - 0.5f);
        
        for (int y = startY; y >= endY; y--) {
            if (y < 0 || y >= 256) continue;
            Block b = world.getBlock(px, y, pz);
            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
            if (def != null && def.isFluid()) {
                int level = b.getMetadata() & 0xFF;
                float fluidHeight = (level == 8 || level == 0) ? 0.875f : (1.0f - (level / 8.0f));
                waterY = y + fluidHeight;
                break;
            }
        }
        
        if (waterY == -999.0f) {
            return 0.0f;
        }
        
        float depth = waterY - position.y;
        if (depth <= 0.0f) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, depth / height));
    }

    public boolean isInWater() {
        World w = com.za.zenith.engine.core.GameLoop.getInstance().getWorld();
        return getSubmersionRatio(w) > 0.0f;
    }

    public Block getFluidBlock() {
        World w = com.za.zenith.engine.core.GameLoop.getInstance().getWorld();
        if (w == null) return null;
        
        float height = boundingBox.maxY() - boundingBox.minY();
        // Ищем блок жидкости снизу вверх
        float[] heights = { height * 0.1f, height * 0.5f, height * 0.9f };
        for (float h : heights) {
            int px = (int) Math.floor(position.x);
            int py = (int) Math.floor(position.y + h);
            int pz = (int) Math.floor(position.z);
            if (py >= 0 && py < 256) {
                Block b = w.getBlock(px, py, pz);
                if (com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType()).isFluid()) {
                    return b;
                }
            }
        }
        
        // Фолбек
        int px = (int) Math.floor(position.x);
        int py = (int) Math.floor(position.y + 0.5f);
        int pz = (int) Math.floor(position.z);
        if (py < 0 || py >= 256) return null;
        return w.getBlock(px, py, pz);
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


