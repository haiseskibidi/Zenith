package com.za.zenith.engine.graphics;

import com.za.zenith.entities.ItemEntity;
import com.za.zenith.world.items.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Handles visual rendering of stacked item entities.
 * Splitting this logic prevents EntityRenderSystem from growing too large.
 */
public class ItemEntityStacker {

    public static void render(ItemEntity entity, Vector3f interpPos, float alpha, Shader shader, DynamicTextureAtlas atlas) {
        var item = entity.getStack().getItem();
        Mesh mesh = MeshRegistry.getItemMesh(item, atlas);
        
        if (mesh == null) return;

        float age = entity.getAge() + alpha * 0.016f;
        float scale = item.getDroppedScale() * (item.isBlock() ? 0.25f : 0.45f);
        float yOff = item.isBlock() ? 0 : scale * 0.5f;
        var rot = entity.getInterpolatedRotation(alpha);
        
        // Медленное плавное вращение вокруг вертикальной оси Y (как в Minecraft) для придания динамики
        float renderRotY = rot.y + age * 1.2f;
        
        int count = entity.getStack().getCount();
        int numModels = 1;
        
        if (item.isBlock()) {
            if (count > 15) {
                numModels = 3;
            } else if (count > 1) {
                numModels = 2;
            } else {
                numModels = 1;
            }
        } else {
            if (count > 48) {
                numModels = 5;
            } else if (count > 32) {
                numModels = 4;
            } else if (count > 16) {
                numModels = 3;
            } else if (count > 1) {
                numModels = 2;
            }
        }

        int entityHash = entity.hashCode();
        
        // Базовый детерминированный сдвиг кучки, чтобы разные сущности в одной точке не сливались визуально (в мировых координатах)
        float baseRx = (float) Math.sin(entityHash * 13.17f) * (scale * 0.15f);
        float baseRz = (float) Math.cos(entityHash * 23.41f) * (scale * 0.15f);

        // Направление лесенки-веера в локальной плоскости XY предмета
        float dirAngle = (float) (Math.sin(entityHash * 19.13f) * Math.PI);
        
        float horizontalStep = item.isBlock() ? 0.0f : (scale * 0.25f);
        float thickness = item.isBlock() ? 0.0f : (scale * 0.12f);

        for (int idx = 0; idx < numModels; idx++) {
            // Расчет локального смещения относительно осей вращения самого предмета.
            // Блоки сдвигаются строго по фиксированной локальной диагонали (как в Minecraft).
            float localRx, localRy, localRz;
            if (item.isBlock()) {
                float blockStep = scale * 0.12f;
                localRx = idx * blockStep;
                localRy = idx * blockStep;
                localRz = idx * blockStep;
            } else {
                localRx = idx * (float) Math.cos(dirAngle) * horizontalStep;
                localRy = idx * (float) Math.sin(dirAngle) * horizontalStep;
                localRz = idx * thickness;
            }

            Matrix4f model = RenderContext.getMatrix();
            // 1. Переносим в мировую позицию с учетом базового глобального сдвига кучек
            model.translate(
                interpPos.x + baseRx, 
                interpPos.y + (float)Math.sin(age * 2.5f) * 0.02f + yOff, 
                interpPos.z() + baseRz
            )
            // 2. Вращаем модель (добавлено медленное вращение по Y)
            .rotateX(rot.x)
            .rotateY(renderRotY)
            .rotateZ(rot.z)
            // 3. Сдвигаем по локальным осям
            .translate(localRx, localRy, localRz)
            // 4. Масштабируем
            .scale(scale);
            
            shader.setMatrix4f("model", model);
            shader.setInt("highlightPass", 0);
            mesh.render(shader);
        }
    }
}
