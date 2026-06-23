package com.za.zenith.entities;

import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;

/**
 * Статичная сущность ресурса, лежащего на земле (палка, камень).
 * Не имеет логики обновления, только данные для рендеринга.
 */
public class ResourceEntity extends Entity {
    private final ItemStack stack;
    private final float rotation;

    public ResourceEntity(Vector3f position, ItemStack stack, float rotation) {
        // Базовый размер 0.5x0.1x0.5, но мы переопределим boundingBox ниже
        super(position, 0.5f, 0.1f);
        this.stack = stack;
        this.rotation = rotation;
        this.getRotation().y = rotation;
        
        // Генерируем точный хитбокс на основе текстуры
        com.za.zenith.world.physics.AABB texAABB = com.za.zenith.utils.TextureAABBGenerator.generateAABB(stack.getItem().getTexturePath());
        if (texAABB != null) {
            float visualScale = stack.getItem().getDroppedScale();
            // Текстура сканируется в XY, но предмет лежит в XZ.
            // Центрируем хитбокс относительно позиции сущности.
            float width = (texAABB.maxX() - texAABB.minX()) * visualScale;
            float depth = (texAABB.maxY() - texAABB.minY()) * visualScale;
            float height = 0.0625f * visualScale; // Толщина предмета

            // Создаем новый AABB, отцентрированный по XZ
            this.boundingBox = new com.za.zenith.world.physics.AABB(
                -width / 2, 0, -depth / 2,
                width / 2, height, depth / 2
            );
        }
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    protected void onUpdate(float delta, com.za.zenith.world.World world) {
        // Вызываем базовую физику (гравитацию), чтобы предмет падал, если сломать блок под ним
        applyGravity(delta);
        move(world, velocity.x * delta, velocity.y * delta, velocity.z * delta);
    }
}


