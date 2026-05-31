package com.za.zenith.engine.graphics;

import com.za.zenith.utils.Logger;
import com.za.zenith.world.World;
import com.za.zenith.world.WorldSettings;
import org.joml.Vector3f;

/**
 * AtmosphereManager calculates dynamic daily weather variations (warmth & haze)
 * and computes daily gradients for sky, sun, and ambient colors.
 * Ensures 100% Zero-Allocation in the render loop.
 */
public class AtmosphereManager {
    private static AtmosphereManager instance;

    public static AtmosphereManager getInstance() {
        if (instance == null) {
            instance = new AtmosphereManager();
        }
        return instance;
    }

    private long lastCalculatedDay = -1;
    private float warmth = 0.5f;
    private float haze = 0.5f;
    private float computedHaze = 0.5f; // Вычисленный туман с учетом осадков и прояснений
    private float rainIntensity = 0.0f;
    private float currentCloudShadow = 0.0f; // Текущий уровень тени от облаков
    private float clearUpFactor = 0.0f; // Коэффициент прояснения [0.0..1.0] после дождя
    private float lastRainIntensity = 0.0f; // Предыдущая интенсивность дождя для отслеживания момента окончания
    private boolean postRainClearing = false; // Фаза активного роста прояснения после дождя
    private boolean wasRaining = false;       // Был ли дождь в последнее время

    // Computed colors (cached for Zero-Allocation access)
    private final Vector3f skyColor = new Vector3f();
    private final Vector3f horizonColor = new Vector3f();
    private final Vector3f sunColor = new Vector3f();
    private final Vector3f ambientColor = new Vector3f();

    // Constant reference colors for LERPing
    private final Vector3f nightSkyColor = new Vector3f(0.012f, 0.012f, 0.024f);
    
    // Golden Hour configurations (Lemon-Gold vs Crimson-Red)
    private final Vector3f coldGoldenColor = new Vector3f(0.95f, 0.88f, 0.40f);
    private final Vector3f warmGoldenColor = new Vector3f(0.95f, 0.22f, 0.15f);
    private final Vector3f goldenHourColor = new Vector3f();

    // Blue Hour configurations (Indigo vs Violet-Purple)
    private final Vector3f coldBlueHourColor = new Vector3f(0.04f, 0.06f, 0.22f);
    private final Vector3f warmBlueHourColor = new Vector3f(0.08f, 0.04f, 0.26f);
    private final Vector3f blueHourColor = new Vector3f();

    private final Vector3f daySkyColor = new Vector3f(0.50f, 0.73f, 0.98f);
    
    // Sun & Ambient base colors
    private final Vector3f daySunColor = new Vector3f();
    private final Vector3f goldenSunColor = new Vector3f(1.0f, 0.58f, 0.12f);
    private final Vector3f moonLightCol = new Vector3f();
    private final Vector3f baseAmbient = new Vector3f();
    private final Vector3f blueHourAmbient = new Vector3f(0.08f, 0.10f, 0.24f);

    // Temp variables for zero-allocation calculations
    private final Vector3f tempVec1 = new Vector3f();
    private final Vector3f tempVec2 = new Vector3f();
    private final Vector3f tempVec3 = new Vector3f();

    private AtmosphereManager() {
        // Private constructor for Singleton
    }

    public void update(World world, float deltaTime) {
        WorldSettings settings = WorldSettings.getInstance();
        float worldTime = world.getWorldTime();
        long currentDay = (long) (worldTime / settings.dayLength);

        // Calculate procedural forecast if the day has changed
        if (currentDay != lastCalculatedDay) {
            java.util.Random rand = new java.util.Random(currentDay * 12345L + 54321L);
            warmth = rand.nextFloat(); // [0.0, 1.0]
            haze = rand.nextFloat();   // [0.0, 1.0]
            lastCalculatedDay = currentDay;
            Logger.info("Day " + currentDay + " Atmospheric forecast initialized: warmth = " + String.format("%.2f", warmth) + ", haze = " + String.format("%.2f", haze));
        }

        // 1. Calculate dynamic baseline colors based on warmth
        coldGoldenColor.lerp(warmGoldenColor, warmth, goldenHourColor);
        coldBlueHourColor.lerp(warmBlueHourColor, warmth, blueHourColor);

        // Fetch settings baseline colors
        daySunColor.set(settings.sunLightColor[0], settings.sunLightColor[1], settings.sunLightColor[2]);
        moonLightCol.set(settings.moonLightColor[0], settings.moonLightColor[1], settings.moonLightColor[2]);
        baseAmbient.set(settings.ambientColor[0], settings.ambientColor[1], settings.ambientColor[2]);

        // 2. Calculate sun angle and cosine value
        float timeRatio = (float) worldTime / settings.dayLength;
        float angle = (timeRatio - 0.25f) * (float) Math.PI * 2.0f;
        float cosVal = (float) Math.cos(angle);

        // 3. Compute Dynamic Sky Color
        computeSkyColor(cosVal);

        // 4. Compute Dynamic Sun Light Color
        computeSunColor(cosVal);

        // 5. Compute Dynamic Ambient Color
        float smoothSunInt = (float) Math.pow(Math.clamp((cosVal + 0.12f) / 0.45f, 0.0f, 1.0f), 1.2);
        float smoothMoonInt = (float) Math.pow(Math.clamp((-cosVal + 0.12f) / 0.45f, 0.0f, 1.0f), 1.2);
        computeAmbientColor(cosVal, smoothSunInt, smoothMoonInt);

        // 6. Compute Horizon Color (for matching volumetric fog)
        computeHorizonColor(cosVal);

        // 7. Apply Rain Darkening and Dynamic Clear-Up Effect
        float rainIntensity = world.getWeatherManager() != null ? world.getWeatherManager().getRainIntensity() : 0.0f;
        this.rainIntensity = rainIntensity;
        
        // Отслеживаем наличие дождя
        if (rainIntensity > 0.0f) {
            wasRaining = true;
            postRainClearing = false;
            // Во время дождя прояснение плавно угасает (за 0.5с), если оно еще оставалось от прошлого раза
            clearUpFactor = Math.max(0.0f, clearUpFactor - deltaTime * 2.0f);
        } else {
            // Дождь закончился (rainIntensity == 0.0f)
            if (wasRaining) {
                postRainClearing = true;
                wasRaining = false;
            }
        }
        
        if (postRainClearing) {
            // Плавно наращиваем прояснение от 0.0f до 1.0f за ~8 секунд
            clearUpFactor = Math.min(1.0f, clearUpFactor + deltaTime * 0.12f);
            if (clearUpFactor >= 1.0f) {
                postRainClearing = false; // Достигли максимума, переходим к фазе медленного затухания
            }
        } else if (clearUpFactor > 0.0f && rainIntensity == 0.0f) {
            // Плавно возвращаем туман к обычному значению за ~125 секунд
            clearUpFactor = Math.max(0.0f, clearUpFactor - deltaTime * 0.008f);
        }
        
        lastRainIntensity = rainIntensity;

        // Вычисляем итоговый туман
        float activeHaze = haze;
        if (clearUpFactor > 0.0f) {
            // После дождя туман рассеивается почти до нуля (на 98% прозрачности при максимальном clearUpFactor!)
            activeHaze = activeHaze * (1.0f - clearUpFactor * 0.98f);
        }
        if (rainIntensity > 0.0f) {
            activeHaze = Math.max(activeHaze, rainIntensity * 0.85f);
        }
        this.computedHaze = activeHaze;

        if (rainIntensity > 0.0f) {
            float darkening = 1.0f - (rainIntensity * 0.45f); // Сгущаем краски неба на 45%
            skyColor.mul(darkening);
            horizonColor.mul(darkening);
            
            // Солнце и эмбиент темнеют слегка от дождя (основное затемнение идет от Cloud Shadows)
            sunColor.mul(1.0f - (rainIntensity * 0.35f)); 
            ambientColor.mul(1.0f - (rainIntensity * 0.15f)); 
        }

        // 8. Calculate Dynamic Cloud Shadows for the Player
        float maxShadow = 0.0f;
        if (world.getPlayer() != null && cosVal > 0.0f) {
            org.joml.Vector3f playerPos = world.getPlayer().getPosition();
            float windTime = world.getWindTime();
            float sinVal = (float) Math.sin(angle);
            org.joml.Vector3f sunDir = new org.joml.Vector3f(-0.2f, cosVal, -sinVal).normalize();
            
            for (com.za.zenith.world.World.CloudInstance c : world.getActiveClouds()) {
                if (c.isMarkedCollected()) continue;
                
                // Центр облака в мировых координатах (с учетом ветра)
                float cx = c.x + windTime;
                float cy = c.y;
                float cz = c.z;
                
                // Смещение тени облака на землю в зависимости от угла солнца.
                // Ограничиваем знаменатель, чтобы тень от облака не улетала за пределы активной зоны (до 200 метров)
                float angleDivisor = Math.max(0.45f, sunDir.y); 
                float heightDiff = cy - playerPos.y;
                float shadowShiftX = sunDir.x * heightDiff / angleDivisor;
                float shadowShiftZ = sunDir.z * heightDiff / angleDivisor;
                
                // Проецируем центр облака на высоту игрока вдоль луча солнца
                float shadowX = cx - shadowShiftX;
                float shadowZ = cz - shadowShiftZ;
                
                // Горизонтальное расстояние от игрока до центра тени на земле
                float dx = playerPos.x - shadowX;
                float dz = playerPos.z - shadowZ;
                float distSq = dx * dx + dz * dz;
                
                // Теневой радиус облака (зависит от его масштаба)
                float shadowRadius = c.scale * 2.5f; 
                float shadowRadiusSq = shadowRadius * shadowRadius;
                
                if (distSq < shadowRadiusSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float shadowFactor = 1.0f - (dist / shadowRadius);
                    
                    // Тень зависит от прозрачности облака
                    float cloudShadow = shadowFactor * c.getAlpha();
                    if (cloudShadow > maxShadow) {
                        maxShadow = cloudShadow;
                    }
                }
            }
        }
        
        // Плавное изменение уровня тени игрока (lerp за ~1.5 секунды)
        currentCloudShadow = currentCloudShadow + (maxShadow - currentCloudShadow) * deltaTime * 2.5f;
        
        // Применим тени от облаков к солнцу и окружающему миру
        float shadowSunMultiplier = 1.0f - (currentCloudShadow * 0.70f); // Затенение солнца до 70%
        float shadowAmbientMultiplier = 1.0f - (currentCloudShadow * 0.28f); // Затенение окружения до 28%
        
        sunColor.mul(shadowSunMultiplier);
        ambientColor.mul(shadowAmbientMultiplier);
    }

    private void computeSkyColor(float cos) {
        if (cos < -0.15f) {
            // Deep Night
            skyColor.set(nightSkyColor);
        } else if (cos < 0.0f) {
            // Blue Hour (interpolating from Night to Blue Hour)
            float t = (cos - (-0.15f)) / 0.15f; // [0, 1]
            nightSkyColor.lerp(blueHourColor, t, skyColor);
        } else if (cos < 0.25f) {
            // Golden Hour (interpolating from Blue Hour/Dawn to Day)
            float t = cos / 0.25f; // [0, 1]
            float goldenWeight = (float) Math.sin(t * Math.PI); // peak at t = 0.5 (cos = 0.125)
            
            blueHourColor.lerp(daySkyColor, t, tempVec1);
            tempVec1.lerp(goldenHourColor, goldenWeight * 0.85f, skyColor);
        } else {
            // Daytime
            skyColor.set(daySkyColor);
        }
    }

    private void computeSunColor(float cosVal) {
        // Allow smooth fade-in from deeper below the horizon (-0.15 to 0.10)
        // This makes the dawn glow appear much earlier and sunset fade much later.
        float visibility = (cosVal + 0.15f) / 0.25f;
        visibility = Math.clamp(visibility, 0.0f, 1.0f);
        
        if (cosVal >= -0.15f && cosVal < 0.35f) {
            // Golden hour starts slightly earlier and ends slightly later
            float t = Math.clamp((cosVal + 0.05f) / 0.40f, 0.0f, 1.0f);
            float goldenWeight = 1.0f - t; 
            
            tempVec2.set(1.0f, 0.85f, 0.38f); // cold lemon-gold sun
            tempVec3.set(1.0f, 0.36f, 0.02f); // warm intense orange-crimson sun
            tempVec2.lerp(tempVec3, warmth, tempVec3); 
            
            daySunColor.lerp(tempVec3, goldenWeight * 0.95f, sunColor);
            sunColor.mul(visibility); // smooth fade-in
        } else if (cosVal < -0.15f) {
            sunColor.set(0.0f, 0.0f, 0.0f); 
        } else {
            sunColor.set(daySunColor);
        }
    }

    private void computeAmbientColor(float cosVal, float sunIntensity, float moonIntensity) {
        // Unified smooth ambient calculation without sharp branch jumps at the horizon
        float baseIntensity = 0.22f + 0.78f * sunIntensity + 0.35f * moonIntensity;
        ambientColor.set(baseAmbient).mul(baseIntensity);

        // Smooth Blue Hour Ambient (Transition -0.18 to 0.10)
        float blueHourWeight = 1.0f - Math.abs(cosVal - (-0.04f)) / 0.14f;
        blueHourWeight = (float) Math.pow(Math.clamp(blueHourWeight, 0.0f, 1.0f), 1.5);
        
        if (blueHourWeight > 0.0f) {
            tempVec1.set(blueHourAmbient).mul(0.35f + 0.15f * warmth);
            ambientColor.lerp(tempVec1, blueHourWeight, ambientColor);
        }
    }

    private void computeHorizonColor(float cosVal) {
        // Day vs Night factor (1.0 day, 0.0 night)
        float dayFactor = (cosVal >= -0.18f) ? Math.min(1.0f, (cosVal - (-0.18f)) / 0.36f) : 0.0f;
        
        // Midday horizon haze color (warm pale cream-gold)
        tempVec1.set(0.96f, 0.92f, 0.84f);
        
        // Night horizon color: skyColor * 1.45
        tempVec2.set(skyColor).mul(1.45f);
        
        // Base horizon color: lerp(night, day, dayFactor)
        tempVec2.lerp(tempVec1, dayFactor, tempVec3);
        
        // Sunset factor (sharp decay, threshold = 0.30)
        float sunsetFactor = (cosVal >= 0.0f) ? Math.max(0.0f, 1.0f - (cosVal / 0.30f)) : 0.0f;
        if (cosVal < 0.0f) {
            sunsetFactor = (cosVal >= -0.5f) ? Math.min(1.0f, (cosVal - (-0.5f)) / 0.35f) : 0.0f;
        }
        
        // Non-linear blend like in the shader
        float sunsetBlend = (float) Math.pow(sunsetFactor, 1.25);
        
        // Sunset color
        tempVec1.set(0.98f, 0.78f, 0.38f); // coldSunset
        tempVec2.set(0.98f, 0.28f, 0.12f); // warmSunset
        tempVec1.lerp(tempVec2, warmth, tempVec1);
        
        // Blend in sunset color (using average sunGlow = 0.5 for volumetric fog)
        float sunGlow = 0.5f;
        float sunsetWeight = sunsetBlend * (0.65f + 0.35f * sunGlow);
        
        tempVec3.lerp(tempVec1, sunsetWeight, horizonColor);
    }

    // Getters for read-only vectors
    public Vector3f getSkyColor() { return skyColor; }
    public Vector3f getHorizonColor() { return horizonColor; }
    public Vector3f getSunColor() { return sunColor; }
    public Vector3f getAmbientColor() { return ambientColor; }
    public float getWarmth() { return warmth; }
    public float getHaze() { return computedHaze; }
    public float getHazeMultiplier() { return 0.5f + 1.0f * computedHaze; }
    public float getRainIntensity() { return rainIntensity; }
}
