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

    protected float stepHeight = 0.0f;
    protected String stepUpMode = "NONE";
    public float lastStepUpHeight = 0.0f;

    public float getStepHeight() { return stepHeight; }
    public void setStepHeight(float stepHeight) { this.stepHeight = stepHeight; }
    public String getStepUpMode() { return stepUpMode; }
    public void setStepUpMode(String stepUpMode) { this.stepUpMode = stepUpMode; }

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

    public Vector3f getInterpolatedPosition(float alpha, Vector3f dest) {
        return dest.set(prevPosition).lerp(position, alpha);
    }

    public Vector3f getInterpolatedRotation(float alpha) {
        Vector3f result = new Vector3f();
        result.x = lerpAngle(prevRotation.x, rotation.x, alpha);
        result.y = lerpAngle(prevRotation.y, rotation.y, alpha);
        result.z = lerpAngle(prevRotation.z, rotation.z, alpha);
        return result;
    }

    public Vector3f getInterpolatedRotation(float alpha, Vector3f dest) {
        dest.x = lerpAngle(prevRotation.x, rotation.x, alpha);
        dest.y = lerpAngle(prevRotation.y, rotation.y, alpha);
        dest.z = lerpAngle(prevRotation.z, rotation.z, alpha);
        return dest;
    }

    private float lerpAngle(float start, float end, float t) {
        float diff = end - start;
        while (diff < -Math.PI) diff += Math.PI * 2;
        while (diff > Math.PI) diff -= Math.PI * 2;
        return start + diff * t;
    }

    protected void move(World world, float dx, float dy, float dz) {
        this.lastStepUpHeight = 0.0f;
        boolean wasOnGround = onGround;
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
                                    if (shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                                        // Рампа: вертикальное приземление на наклонную поверхность.
                                        // Вычисляем высоту склона в точке центра игрока.
                                        float rampH = getRampAbsoluteHeight(cache, shape, x, y, z, boundingBox, position.x, position.z);
                                        if (dy < 0) {
                                            float feetAfterFall = position.y + dy;
                                            // Если игрок падает сквозь поверхность склона и находится в досягаемости
                                            if (feetAfterFall < rampH && position.y >= rampH - 1.0f) {
                                                dy = rampH - position.y;
                                                onGround = true;
                                                velocity.y = 0;
                                            }
                                        } else if (dy > 0) {
                                            // Движение вверх (прыжок) — рампа блокирует голову снизу
                                            float headAfterMove = position.y + boundingBox.maxY() + dy;
                                            if (headAfterMove > y && position.y + boundingBox.maxY() <= y + 0.01f) {
                                                dy = y - (position.y + boundingBox.maxY()) - 0.001f;
                                                velocity.y = 0;
                                            }
                                        }
                                        continue;
                                    }
                                    if (AABB.intersects(boundingBox, position.x, position.y + dy, position.z, box, x, y, z)) {
                                        if (shouldSkipCollisionWithBlock(cache, x, y, z, box, 0, 0)) {
                                            continue;
                                        }
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

        float xBeforeHorizontal = position.x;
        float yBeforeHorizontal = position.y;
        float zBeforeHorizontal = position.z;

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
                                    if (shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                                        if (com.za.zenith.world.physics.StepUpHandler.isRampPassable(cache, shape, x, y, z, position.x, position.y, position.z, stepHeight)) {
                                            continue;
                                        }
                                    }
                                    if (AABB.intersects(boundingBox, position.x + dx, position.y, position.z, box, x, y, z)) {
                                        if (shouldSkipCollisionWithBlock(cache, x, y, z, box, dx, 0)) {
                                            continue;
                                        }
                                        if (originalDx > 0) {
                                            float newDx = (box.minX() + x) - (boundingBox.maxX() + position.x) - 0.001f;
                                            dx = Math.max(0.0f, Math.min(dx, newDx));
                                        } else if (originalDx < 0) {
                                            float newDx = (box.maxX() + x) - (boundingBox.minX() + position.x) + 0.001f;
                                            dx = Math.min(0.0f, Math.max(dx, newDx));
                                        }
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
                                    if (shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                                        if (com.za.zenith.world.physics.StepUpHandler.isRampPassable(cache, shape, x, y, z, position.x, position.y, position.z, stepHeight)) {
                                            continue;
                                        }
                                    }
                                    if (AABB.intersects(boundingBox, position.x, position.y, position.z + dz, box, x, y, z)) {
                                        if (shouldSkipCollisionWithBlock(cache, x, y, z, box, 0, dz)) {
                                            continue;
                                        }
                                        if (originalDz > 0) {
                                            float newDz = (box.minZ() + z) - (boundingBox.maxZ() + position.z) - 0.001f;
                                            dz = Math.max(0.0f, Math.min(dz, newDz));
                                        } else if (originalDz < 0) {
                                            float newDz = (box.maxZ() + z) - (boundingBox.minZ() + position.z) + 0.001f;
                                            dz = Math.min(0.0f, Math.max(dz, newDz));
                                        }
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

        boolean collidedHorizontally = (Math.abs(originalDx) > 0.00001f && Math.abs(dx) < Math.abs(originalDx) * 0.9f) ||
                                       (Math.abs(originalDz) > 0.00001f && Math.abs(dz) < Math.abs(originalDz) * 0.9f);

        boolean didStepUp = false;

        if (stepHeight > 0.0f && !stepUpMode.equalsIgnoreCase("NONE") && onGround && collidedHorizontally) {
            com.za.zenith.world.physics.StepUpHandler.StepResult result = com.za.zenith.world.physics.StepUpHandler.tryStepUp(
                this, cache,
                xBeforeHorizontal, yBeforeHorizontal, zBeforeHorizontal,
                originalDx, originalDz,
                dx, dz,
                stepHeight, stepUpMode
            );

            if (result.success) {
                didStepUp = true;
                this.lastStepUpHeight = result.stepHeight;
                dx = result.actualDx;
                dz = result.actualDz;
                onGround = true;
            }
        }

        adjustYForRamps(cache, wasOnGround, originalDy);

        this.horizontalCollision = !didStepUp && (
            (Math.abs(originalDx) > 0.00001f && Math.abs(dx) < Math.abs(originalDx) * 0.9f) ||
            (Math.abs(originalDz) > 0.00001f && Math.abs(dz) < Math.abs(originalDz) * 0.9f)
        );
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
                            for (AABB sbox : shape.getBoxes()) {
                                if (shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                                    continue; // Рампа не считается «застреванием»
                                }
                                if (AABB.intersects(sbox, (float)x, (float)y, (float)z, box, 0, 0, 0)) {
                                    if (shouldSkipCollisionWithBlock(cache, x, y, z, sbox, 0, 0)) {
                                        continue;
                                    }
                                    return true;
                                }
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
    public AABB getLocalBoundingBox() { return boundingBox; }
    public boolean isOnGround() { return onGround; }
    public boolean isFlying() { return flying; }
    public void setFlying(boolean flying) { this.flying = flying; }
    public void setPosition(float x, float y, float z) { position.set(x, y, z); }

    /**
     * Вычисляет высоту наклонной поверхности рампы в точке центра игрока.
     * Используется для вертикального приземления и горизонтальной проходимости.
     */
    private float getRampAbsoluteHeight(com.za.zenith.world.chunks.ChunkCache cache, com.za.zenith.world.physics.VoxelShape shape, int blockX, int blockY, int blockZ, AABB localBox, float entityX, float entityZ) {
        return com.za.zenith.world.physics.StepUpHandler.getRampAbsoluteHeight(cache, shape, blockX, blockY, blockZ, entityX, entityZ);
    }

    private boolean shouldSkipCollisionWithBlock(com.za.zenith.world.chunks.ChunkCache cache, int blockX, int blockY, int blockZ, AABB box, float dx, float dz) {
        // Проверяем блок строго непосредственно над текущим блоком
        int rampY = blockY + 1;
        Block b = cache.getBlock(blockX, rampY, blockZ);
        if (b.isAir() || !b.isSolid()) {
            return false;
        }
        
        com.za.zenith.world.physics.VoxelShape rampShape = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType()).getShape(b.getMetadata());
        if (rampShape == null || rampShape.getGeometry() != com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
            return false;
        }
        
        // Вычисляем целевую координату на основе взаимного расположения
        float targetX = position.x;
        if (blockX > position.x) {
            targetX = blockX;
        } else if (blockX + 1.0f < position.x) {
            targetX = blockX + 1.0f;
        }
        
        float targetZ = position.z;
        if (blockZ > position.z) {
            targetZ = blockZ;
        } else if (blockZ + 1.0f < position.z) {
            targetZ = blockZ + 1.0f;
        }
        
        float rampH = com.za.zenith.world.physics.StepUpHandler.getRampAbsoluteHeight(cache, rampShape, blockX, rampY, blockZ, targetX, targetZ);
        float blockMaxY = blockY + box.maxY();
        
        // Пропускаем коллизию только если игрок находится сверху на уровне рампы (или переходит на неё)
        if (position.y < blockMaxY - 0.6f) {
            return false;
        }
        
        return blockMaxY <= rampH + 0.01f;
    }

    /**
     * Подстройка Y-позиции игрока к поверхности рампы ПОСЛЕ горизонтального движения.
     * Обеспечивает плавный подъём/спуск по склону при ходьбе.
     * Работает в обе стороны: и вверх, и вниз (прилипание к поверхности).
     */
    private void adjustYForRamps(com.za.zenith.world.chunks.ChunkCache cache, boolean wasOnGround, float originalDy) {
        int centerX = (int) Math.floor(position.x);
        int centerZ = (int) Math.floor(position.z);
        AABB currentBox = boundingBox.offset(position);
        int minY = (int) Math.floor(currentBox.minY() - 1.0f);
        int maxY = (int) Math.floor(currentBox.maxY());

        float bestRampY = -999.0f;
        boolean foundOnCenter = false;

        // 1. Сначала проверяем колонку прямо под центром игрока (основная опора)
        for (int y = minY; y <= maxY; y++) {
            Block block = cache.getBlock(centerX, y, centerZ);
            if (!block.isAir() && block.isSolid()) {
                com.za.zenith.world.physics.VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                if (shape != null && shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                    float rampH = getRampAbsoluteHeight(cache, shape, centerX, y, centerZ, boundingBox, position.x, position.z);
                    if (rampH > bestRampY) {
                        bestRampY = rampH;
                        foundOnCenter = true;
                    }
                }
            }
        }

        // 2. Если под центром рампы нет, ищем по всей площади AABB игрока (боковая поддержка)
        if (!foundOnCenter) {
            int minBoxX = (int) Math.floor(currentBox.minX());
            int maxBoxX = (int) Math.floor(currentBox.maxX());
            int minBoxZ = (int) Math.floor(currentBox.minZ());
            int maxBoxZ = (int) Math.floor(currentBox.maxZ());

            for (int x = minBoxX; x <= maxBoxX; x++) {
                for (int z = minBoxZ; z <= maxBoxZ; z++) {
                    if (x == centerX && z == centerZ) continue;
                    for (int y = minY; y <= maxY; y++) {
                        Block block = cache.getBlock(x, y, z);
                        if (!block.isAir() && block.isSolid()) {
                            com.za.zenith.world.physics.VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType()).getShape(block.getMetadata());
                            if (shape != null && shape.getGeometry() == com.za.zenith.world.physics.VoxelShape.ShapeGeometry.RAMP) {
                                float minX = position.x + boundingBox.minX();
                                float maxX = position.x + boundingBox.maxX();
                                float minZ = position.z + boundingBox.minZ();
                                float maxZ = position.z + boundingBox.maxZ();
                                float rampH = com.za.zenith.world.physics.StepUpHandler.getRampAbsoluteHeight(cache, shape, x, y, z, minX, maxX, minZ, maxZ);
                                 // Учитываем боковую рампу, только если игрок уже стоит на ней (разница высот минимальна)
                                 if (Math.abs(rampH - position.y) <= stepHeight) {
                                     if (rampH > bestRampY) {
                                         bestRampY = rampH;
                                     }
                                 }
                            }
                        }
                    }
                }
            }
        }

        if (bestRampY != -999.0f) {
            float diff = bestRampY - position.y;
            // Прилипание к склону: подтягиваем вверх (до +stepHeight) или прижимаем вниз (до -stepHeight)
            if (diff > 0.0f && diff <= stepHeight && (onGround || wasOnGround)) {
                // Подъём по склону
                position.y = bestRampY;
                onGround = true;
                if (velocity.y < 0) velocity.y = 0;
            } else if (diff < 0.0f && diff >= -stepHeight && (onGround || wasOnGround) && originalDy <= 0.001f) {
                // Спуск по склону — прижимаем к поверхности (только если были или стоим на земле и не прыгаем)
                position.y = bestRampY;
                onGround = true;
                if (velocity.y < 0) velocity.y = 0;
            }
        }
    }
}
