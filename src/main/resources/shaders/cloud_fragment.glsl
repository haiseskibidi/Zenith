#version 330 core

#include "include/global_data.glsl"

in vec3 vNormal;
in vec3 vWorldPos;
in float vAlpha;

uniform float uRainIntensity; // Интенсивность дождя [0.0..1.0]

out vec4 fragColor;

void main() {
    vec3 normal = normalize(vNormal);
    vec3 viewDir = normalize(uCameraPos - vWorldPos);
    
    // Astronomical directions
    vec3 sunDir = normalize(uSunDirection);
    vec3 moonDir = -sunDir;

    // 1. Dual-Celestial Lighting
    float sunDiff = smoothstep(-0.3, 0.7, dot(normal, sunDir));
    float moonDiff = smoothstep(-0.3, 0.7, dot(normal, moonDir));
    
    // 2. Day Colors (Storm-aware)
    vec3 dayShadow = mix(vec3(0.68, 0.73, 0.83), vec3(0.10, 0.12, 0.18), uRainIntensity);
    vec3 dayLit = mix(vec3(1.0, 1.0, 1.0), vec3(0.20, 0.24, 0.30), uRainIntensity);
    vec3 dayColor = mix(dayShadow, dayLit, mix(sunDiff, sunDiff * 0.15, uRainIntensity));
    
    if (uRainIntensity > 0.0) {
        dayColor = mix(dayColor, vec3(0.12, 0.14, 0.19), uRainIntensity * 0.95);
    }

    // 3. Night Colors (Storm-aware Moon lighting)
    vec3 nightLit = mix(vec3(0.28, 0.35, 0.52), vec3(0.05, 0.06, 0.10), uRainIntensity);
    vec3 nightColor = mix(vec3(0.02, 0.03, 0.06), nightLit, moonDiff);
    
    // 4. Smooth Day/Night Transition
    vec3 baseColor = mix(dayColor, nightColor, uNightFactor);

    // 5. Silver Lining Effect (Sun only)
    float viewDotSun = max(0.0, dot(viewDir, sunDir));
    float edgeDecline = 1.0 - max(0.0, dot(normal, viewDir));
    float silverLining = pow(viewDotSun, 5.0) * pow(edgeDecline, 2.0) * mix(1.5, 0.0, uRainIntensity);
    
    vec3 sunGlowColor = vec3(1.0, 0.93, 0.82);
    // Silver lining fades at night
    baseColor = mix(baseColor, sunGlowColor, silverLining * 0.7 * (1.0 - uRainIntensity) * (1.0 - uNightFactor));

    // 6. Fresnel Transparency
    float fresnelAlpha = pow(max(0.0, dot(normal, viewDir)), 0.65);
    float targetMaxAlpha = mix(0.88, 0.98, uRainIntensity);
    
    // Night clouds are slightly more transparent/ethereal
    float nightAlphaMult = mix(0.65, 0.95, uRainIntensity);
    float finalAlphaMult = mix(1.0, nightAlphaMult, uNightFactor);
    
    float alpha = targetMaxAlpha * fresnelAlpha * vAlpha * finalAlphaMult;

    fragColor = vec4(baseColor, alpha);
}
