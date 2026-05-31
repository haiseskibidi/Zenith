#version 330 core
#include "include/global_data.glsl"

in vec3 fragViewDir;
out vec4 fragColor;

uniform vec3 uSkyColor;
uniform vec3 uSunColor;
uniform float uWarmth;

void main() {
    vec3 viewDir = normalize(fragViewDir);
    // uSunDirection represents light direction (pointing downwards from sun to camera).
    // Negate it to obtain the vector pointing from camera to the sun.
    vec3 sunDir = normalize(-uSunDirection);
    
    // Height above horizon (0.0 at horizon, 1.0 at zenith)
    float h = max(0.0, viewDir.y);
    
    // Angle to the sun
    float cosAngle = dot(viewDir, sunDir);
    
    // Sunset/sunrise factor - sharp decay so orange colors disperse quickly as sun rises
    // Peaks when sun is near horizon (sunDir.y near 0.0), completely gone when sun is above 0.30
    float sunsetFactor = smoothstep(0.30, 0.0, abs(sunDir.y));
    
    // If the sun is completely below horizon, fade sunset factor
    if (sunDir.y < -0.15) {
        sunsetFactor *= smoothstep(-0.5, -0.15, sunDir.y);
    }
    
    // Day vs Night factor (1.0 in midday, 0.0 at midnight)
    float dayFactor = smoothstep(-0.18, 0.18, sunDir.y);
    
    // 1. Dynamic Zenith Color (deep and rich indigo/blue)
    vec3 zenithColor = uSkyColor * mix(vec3(0.92, 0.92, 0.95), vec3(0.72, 0.80, 0.96), dayFactor);
    
    // 2. Dynamic Default Horizon (without sunset colors)
    // Warm pale-cream/gold haze during midday to prevent cyan/turquoise coloring completely
    vec3 dayHorizon = vec3(0.96, 0.92, 0.84); 
    // Night horizon is slightly illuminated dark blue
    vec3 nightHorizon = uSkyColor * 1.45;
    vec3 defaultHorizon = mix(nightHorizon, dayHorizon, dayFactor);
    
    // 3. Dynamic Sunset/Sunrise Horizon
    // Warm peach-gold in cold days, crimson-orange in warm days
    vec3 sunsetColor = mix(vec3(0.98, 0.78, 0.38), vec3(0.98, 0.28, 0.12), uWarmth);
    
    // Sunset colors wrap around the entire horizon, stronger towards the sun
    float sunGlow = smoothstep(-0.35, 0.95, cosAngle);
    
    // Non-linear blend to aggressively push out the cold blue horizon
    float sunsetBlend = pow(sunsetFactor, 1.25);
    vec3 activeHorizon = mix(defaultHorizon, sunsetColor, sunsetBlend * (0.65 + 0.35 * sunGlow));
    
    // 4. Dynamic Middle Layer (Transition)
    // Soft lavender-pink during sunset, pure soft azure blue during midday (no cyan!)
    vec3 dayMiddle = mix(nightHorizon, vec3(0.55, 0.76, 0.98), dayFactor);
    vec3 lavenderMiddle = mix(vec3(0.80, 0.58, 0.72), vec3(0.88, 0.40, 0.56), uWarmth);
    vec3 activeMiddle = mix(dayMiddle, lavenderMiddle, sunsetBlend * 0.90);
    
    // 5. Compute 3-Layer Atmospheric Gradient (Ultra-smooth continuous blend)
    // Dynamic weights using soft power-exponential curves to prevent harsh color lines ("flags")
    float horizonWeight = pow(1.0 - h, 2.2); // Expands the warm horizon glow to a higher sky angle
    float middleWeight = pow(1.0 - h, 0.8);  // Slow decay to spread the lavender-pink transition zone
    
    vec3 baseSkyColor = mix(zenithColor, activeMiddle, middleWeight);
    baseSkyColor = mix(baseSkyColor, activeHorizon, horizonWeight);
    
    // 6. Wide Volumetric Golden Sun Glow
    if (sunDir.y > -0.2) {
        float sunGlowAngle = max(0.0, cosAngle);
        // Exponential wide atmospheric scatter
        float scatterGlow = pow(sunGlowAngle, 8.0) * 0.35 * smoothstep(-0.2, 0.15, sunDir.y);
        scatterGlow += pow(sunGlowAngle, 32.0) * 0.45 * smoothstep(-0.2, 0.15, sunDir.y);
        
        vec3 glowColor = mix(vec3(1.0, 0.75, 0.45), vec3(1.0, 0.45, 0.20), uWarmth);
        baseSkyColor += glowColor * scatterGlow * (0.3 + 0.7 * sunsetFactor);
    }
    
    // 7. Subtle Night Horizon Ambient Glow
    if (sunDir.y < 0.0) {
        float nightFactor = smoothstep(0.0, -0.4, sunDir.y);
        float horizonFade = pow(1.0 - h, 4.0);
        vec3 nightGlowColor = vec3(0.02, 0.03, 0.08) * horizonFade * nightFactor;
        baseSkyColor += nightGlowColor;
    }
    
    fragColor = vec4(baseSkyColor, 1.0);
}
