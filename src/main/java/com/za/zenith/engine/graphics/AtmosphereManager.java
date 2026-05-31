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

    public void update(World world) {
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
        computeAmbientColor(cosVal, cosVal >= 0.0f ? Math.max(0.0f, cosVal) : 0.0f, cosVal < 0.0f ? Math.max(0.0f, -cosVal) : 0.0f);

        // 6. Compute Horizon Color (for matching volumetric fog)
        computeHorizonColor(cosVal);

        // 7. Apply Rain Darkening
        float rainIntensity = world.getWeatherManager() != null ? world.getWeatherManager().getRainIntensity() : 0.0f;
        if (rainIntensity > 0.0f) {
            float darkening = 1.0f - (rainIntensity * 0.25f); // Reduced from 0.45 to 0.25
            skyColor.mul(darkening);
            horizonColor.mul(darkening);
            sunColor.mul(1.0f - (rainIntensity * 0.5f)); // Reduced from 0.7 to 0.5
            ambientColor.mul(1.0f - (rainIntensity * 0.15f)); // Reduced from 0.3 to 0.15
            
            // Also increase haze/fog density during rain
            haze = Math.max(haze, rainIntensity * 0.8f);
        }
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
        if (cosVal >= 0.0f && cosVal < 0.25f) {
            float t = cosVal / 0.25f;
            float goldenWeight = 1.0f - t; // stronger at the horizon (cos = 0)
            
            // Mix sun color with dynamic golden hour warmth
            tempVec2.set(1.0f, 0.85f, 0.38f); // cold lemon-gold sun
            tempVec3.set(1.0f, 0.36f, 0.02f); // warm intense orange-crimson sun
            tempVec2.lerp(tempVec3, warmth, tempVec3); // tempVec3 now holds active goldenSunColor
            
            daySunColor.lerp(tempVec3, goldenWeight * 0.95f, sunColor);
        } else if (cosVal < 0.0f) {
            sunColor.set(0.0f, 0.0f, 0.0f); // sun is down
        } else {
            sunColor.set(daySunColor);
        }
    }

    private void computeAmbientColor(float cosVal, float sunIntensity, float moonIntensity) {
        if (cosVal < 0.0f && cosVal >= -0.15f) {
            // Blue Hour Ambient
            float t = (cosVal - (-0.15f)) / 0.15f; // [0, 1]
            blueHourAmbient.lerp(baseAmbient, t, ambientColor);
            ambientColor.mul(0.35f);
        } else {
            ambientColor.set(baseAmbient).mul(0.2f + 0.8f * sunIntensity + 0.3f * moonIntensity);
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
    public float getHaze() { return haze; }
    public float getHazeMultiplier() { return 0.5f + 1.0f * haze; }
}
