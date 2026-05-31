#version 330 core
#include "include/global_data.glsl"

in vec3 fragViewDir;
out vec4 fragColor;

uniform vec3 uSkyColor;
uniform vec3 uSunColor;
uniform vec3 uMoonColor;
uniform vec3 uHorizonColor;
uniform float uWarmth;
uniform float uRainIntensity;

void main() {
    vec3 viewDir = normalize(fragViewDir);
    
    // uSunDirection now holds raw astronomical sun direction (unflipped)
    vec3 sunDir = normalize(uSunDirection);
    vec3 moonDir = -sunDir;
    
    // Height above horizon (0.0 at horizon, 1.0 at zenith)
    float h = max(0.0, viewDir.y);
    
    // Angle to the sun/moon
    float cosSun = dot(viewDir, sunDir);
    float cosMoon = dot(viewDir, moonDir);
    
    // 1. Day vs Night factor (Restored to original tight range)
    float dayFactor = smoothstep(-0.15, 0.15, sunDir.y);
    
    // 2. Dynamic Zenith Color
    vec3 zenithColor = uSkyColor * mix(vec3(0.92, 0.92, 0.95), vec3(0.72, 0.80, 0.96), dayFactor);
    
    // 3. Dynamic Default Horizon (Synced with uHorizonColor as base)
    // uHorizonColor from Java is pre-calculated to match the atmosphere haze/fog.
    vec3 defaultHorizon = uHorizonColor;
    
    // 4. Dynamic Sunset/Sunrise directional overlay
    vec3 sunsetColor = mix(vec3(0.98, 0.78, 0.38), vec3(0.98, 0.28, 0.12), uWarmth);
    float sunGlowWeight = smoothstep(-0.35, 0.95, cosSun);
    
    float sunsetFactor = smoothstep(0.30, 0.0, abs(sunDir.y));
    if (sunDir.y < -0.15) {
        sunsetFactor *= smoothstep(-0.5, -0.15, sunDir.y);
    }
    float sunsetBlend = pow(sunsetFactor, 1.25) * mix(1.0, 0.05, uRainIntensity);
    
    // Add directional richness to the already warm horizon
    vec3 activeHorizon = mix(defaultHorizon, sunsetColor, sunsetBlend * 0.40 * sunGlowWeight);
    
    // 5. Dynamic Middle Layer
    vec3 nightHorizon = uSkyColor * 1.45;
    vec3 dayMiddle = mix(nightHorizon, vec3(0.55, 0.76, 0.98), dayFactor);
    if (uRainIntensity > 0.0) dayMiddle = mix(dayMiddle, vec3(0.12, 0.14, 0.19), uRainIntensity);

    vec3 lavenderMiddle = mix(vec3(0.80, 0.58, 0.72), vec3(0.88, 0.40, 0.56), uWarmth);
    vec3 activeMiddle = mix(dayMiddle, lavenderMiddle, sunsetBlend * 0.90);
    
    // 6. Gradient Blend
    float horizonWeight = pow(1.0 - h, 2.2); 
    float middleWeight = pow(1.0 - h, 0.8);  
    
    vec3 baseSkyColor = mix(zenithColor, activeMiddle, middleWeight);
    baseSkyColor = mix(baseSkyColor, activeHorizon, horizonWeight);
    
    // 7. Celestial Glows
    
    // --- Sun Glow (Golden/Warm) ---
    if (sunDir.y > -0.85) {
        float visibility = smoothstep(-0.85, -0.40, sunDir.y);
        float sunAngle = max(0.0, cosSun);
        float scatter = pow(sunAngle, 8.0) * 0.35 + pow(sunAngle, 32.0) * 0.45;
        scatter *= mix(1.0, 0.05, uRainIntensity) * visibility;
        
        vec3 glowColor = mix(vec3(1.0, 0.75, 0.45), vec3(1.0, 0.45, 0.20), uWarmth);
        baseSkyColor += glowColor * scatter * (0.3 + 0.7 * sunsetFactor);
    }
    
    // --- Moon Glow (Silver/Indigo) ---
    if (moonDir.y > -0.40) {
        float moonAngle = max(0.0, cosMoon);
        float visibility = smoothstep(-0.40, 0.10, moonDir.y);
        float scatter = pow(moonAngle, 16.0) * 0.25;
        scatter *= mix(1.0, 0.1, uRainIntensity) * visibility;
        
        baseSkyColor += uMoonColor * scatter * 1.5;
    }
    
    // 8. Night Horizon Ambient Glow
    if (sunDir.y < 0.0) {
        float nightGlowWeight = smoothstep(0.0, -0.85, sunDir.y);
        float horizonFade = pow(1.0 - h, 4.0);
        vec3 nightGlowColor = vec3(0.02, 0.03, 0.08) * horizonFade * nightGlowWeight;
        baseSkyColor += nightGlowColor;
    }
    
    fragColor = vec4(baseSkyColor, 1.0);
}
