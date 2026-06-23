package com.za.zenith.world.weather;

import com.za.zenith.entities.Player;
import com.za.zenith.world.World;
import com.za.zenith.world.World.CloudInstance;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * System for simulating clouds, wind movement, and smooth weather transitions.
 * ponytail: isolated from World.java to keep file sizes small and clean.
 */
public class CloudSimulationSystem {
    private final List<CloudInstance> activeClouds = new CopyOnWriteArrayList<>();
    private float windTime = 0.0f;

    public float getWindTime() {
        return windTime;
    }

    public List<CloudInstance> getActiveClouds() {
        return activeClouds;
    }

    public void update(float deltaTime, World world, Player player, WeatherManager weatherManager) {
        windTime += deltaTime * 1.5f; // Облака плавно плывут по небу со скоростью 1.5 блока/сек
        
        float px = 0;
        float playerZ = 0;
        if (player != null) {
            px = player.getPosition().x;
            playerZ = player.getPosition().z;
        }

        // 1. Обновляем и фильтруем активные облака
        // ponytail: standard reverse loop for safe removal (or CopyOnWriteArrayList remove)
        for (CloudInstance c : activeClouds) {
            c.update(deltaTime);
            
            // Реальные мировые координаты облака с учетом ветра
            float wx = c.x + windTime;
            float dx = wx - px;
            float dz = c.z - playerZ;
            float distSq = dx * dx + dz * dz;
            
            // Удаляем облако, если оно слишком далеко (более 256 метров по горизонтали)
            // или если оно было собрано (разрушено ПКМ) и полностью растворилось
            if (distSq > 256.0f * 256.0f) {
                activeClouds.remove(c);
            } else if (c.isCollected()) {
                activeClouds.remove(c);
            }
        }

        // 2. Поддерживаем стабильное количество облаков на небе (целевое число: 28 в ясную погоду, до 60 во время дождя)
        if (player != null) {
            float rainIntensity = weatherManager.getRainIntensity();
            int targetClouds = (int) (28 + rainIntensity * 32);
            
            if (activeClouds.isEmpty()) {
                // При инициализации заполняем небо равномерно вокруг игрока в радиусе 200 метров,
                // чтобы игрок не видел пустое небо при входе
                for (int i = 0; i < targetClouds; i++) {
                    float angle = (float) (Math.random() * Math.PI * 2.0);
                    float dist = 20.0f + (float) Math.random() * 180.0f;
                    float spawnX = px + (float) Math.cos(angle) * dist;
                    float spawnZ = playerZ + (float) Math.sin(angle) * dist;
                    
                    float worldX = spawnX - windTime; // компенсируем смещение ветра
                    float worldY = 270.0f + (float) Math.random() * 25.0f;
                    float scale = (6.0f + (float) Math.random() * 9.0f) * (1.0f + rainIntensity * 0.8f);
                    float seed = (float) Math.random();
                    
                    CloudInstance c = new CloudInstance(worldX, worldY, spawnZ, scale, seed);
                    c.currentAlpha = 1.0f; // Начальные облака сразу плотные
                    activeClouds.add(c);
                }
            } else if (activeClouds.size() < targetClouds) {
                float worldY = 270.0f + (float) Math.random() * 25.0f;
                float scale = (6.0f + (float) Math.random() * 9.0f) * (1.0f + rainIntensity * 0.8f);
                float seed = (float) Math.random();
                float spawnX, spawnZ;
                float initAlpha = 1.0f;
                
                if (rainIntensity > 0.0f) {
                    // Во время дождя спавним тучи по всей зоне видимости вокруг игрока,
                    // чтобы они плотно затянули небо прямо над головой.
                    // Но они спавнятся плавно конденсирующимися (initAlpha = 0.0f), чтобы исключить popping!
                    float angle = (float) (Math.random() * Math.PI * 2.0);
                    float dist = (float) Math.random() * 220.0f;
                    spawnX = px + (float) Math.cos(angle) * dist;
                    spawnZ = playerZ + (float) Math.sin(angle) * dist;
                    initAlpha = 0.0f; // плавно проявляются прямо на небе за 2.5 секунды
                } else {
                    // В ясную погоду спавним облака строго на западном горизонте (наветренная сторона),
                    // откуда ветер несет их прямо на восток через координату игрока.
                    // Они спавнятся сразу плотными (initAlpha = 1.0f) за пределами видимости (200-240 метров),
                    // и бесшовно вплывают в зону видимости.
                    float dist = 200.0f + (float) Math.random() * 40.0f;
                    spawnX = px - dist; // с запада (наветренная сторона)
                    spawnZ = playerZ + ((float) Math.random() - 0.5f) * 360.0f; // широкий фронт
                    initAlpha = 1.0f;
                }
                
                float worldX = spawnX - windTime; // компенсируем ветер в рендере
                CloudInstance c = new CloudInstance(worldX, worldY, spawnZ, scale, seed);
                c.currentAlpha = initAlpha;
                activeClouds.add(c);
            }
        }
    }
}
