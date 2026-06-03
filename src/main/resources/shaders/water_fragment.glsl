#version 330 core

in vec3 fragNormal;
in vec3 fragPos;
in float neighborData;
in vec2 vLight;
in float vAO;
in float blockType;

#include "include/global_data.glsl"
#include "include/noise.glsl"
#include "include/lighting.glsl"
#include "include/block_features.glsl"

out vec4 fragColor;

uniform int uLightCount;
uniform ZenithLight uLights[8];

void main() {
    BlockInfo info = decodeBlockInfo(blockType);
    
    // Choose properties based on fluid type
    vec3 baseColor = vec3(0.015, 0.22, 0.44); // Default deep teal-blue (Water)
    float alpha = 0.65;
    vec3 rippleColor = vec3(0.35, 0.75, 0.88);
    float foamStrength = 0.32;
    float rippleIntensity = 0.28;
    
    if (info.fluidType == 2) { // Oil
        baseColor = vec3(0.04, 0.03, 0.04);
        alpha = 0.95;
        rippleColor = vec3(0.09, 0.08, 0.09);
        foamStrength = 0.05;
        rippleIntensity = 0.15;
    } else if (info.fluidType == 3) { // Lava
        baseColor = vec3(0.92, 0.24, 0.04);
        alpha = 1.0;
        rippleColor = vec3(0.98, 0.68, 0.15);
        foamStrength = 0.45;
        rippleIntensity = 0.35;
    }

    // neighborData contains packed flow direction: 0-15 (static if >= 15.0)
    vec2 flowDir = vec2(0.0);
    if (neighborData < 14.5) {
        float angle = neighborData * (2.0 * 3.14159265 / 16.0);
        flowDir = vec2(cos(angle), sin(angle));
    }

    // Two layered scrolling noise fields for ripples
    vec2 uv1 = fragPos.xz * 0.35 + flowDir * uTime * 0.12 + vec2(uTime * 0.015, uTime * 0.008);
    vec2 uv2 = fragPos.xz * 0.70 - flowDir * uTime * 0.06 + vec2(-uTime * 0.008, uTime * 0.015);

    float n1 = noise(uv1 * 4.0);
    float n2 = noise(uv2 * 5.0);
    float ripple = (n1 + n2) * 0.5;

    // White foam lines on high ripple peaks
    float foam = smoothstep(0.68, 0.8, ripple) * foamStrength;
    baseColor = mix(baseColor, rippleColor, ripple * rippleIntensity);
    baseColor += vec3(foam);

    // Calculate lighting
    vec3 totalDynamicLight = vec3(0.0);
    vec3 sunLightContribution = vec3(0.0);
    float sunlightMask = vLight.x / 15.0;

    for (int i = 0; i < uLightCount; i++) {
        if (uLights[i].type == 1) { // Directional (Sun/Moon)
            vec3 directSun = calculateLighting(fragNormal, uSunDirection, uLights[i].color * sunlightMask * 0.8, vec3(0.0));
            vec3 scatteredSun = uLights[i].color * sunlightMask * 0.2;
            sunLightContribution += directSun + scatteredSun;
            
            // Wet Blinn-Phong Specular for water surface
            vec3 viewDir = normalize(uCameraPos - fragPos);
            vec3 halfDir = normalize(uSunDirection + viewDir);
            float specAngle = max(dot(fragNormal, halfDir), 0.0);
            float specular = pow(specAngle, 96.0); // sharp glossiness
            vec3 specColor = uLights[i].color * specular * sunlightMask * 0.75;
            sunLightContribution += specColor;
        } else {
            totalDynamicLight += calculateDynamicLighting(fragNormal, fragPos, uLights[i]);
            
            // Attenuated specular from local light sources (campfires, electric lamps)
            vec3 lightDir = normalize(uLights[i].position - fragPos);
            vec3 viewDir = normalize(uCameraPos - fragPos);
            vec3 halfDir = normalize(lightDir + viewDir);
            float specAngle = max(dot(fragNormal, halfDir), 0.0);
            float specular = pow(specAngle, 64.0);
            float dist = length(uLights[i].position - fragPos);
            float attenuation = 1.0 - smoothstep(uLights[i].radius * 0.5, uLights[i].radius, dist);
            vec3 specColor = uLights[i].color * specular * attenuation * 0.5;
            totalDynamicLight += specColor;
        }
    }
    
    vec3 lighting = uAmbientColor * vec3(0.85, 0.88, 0.95);
    lighting += sunLightContribution;
    lighting += totalDynamicLight;
    lighting += vec3(1.0, 0.85, 0.6) * (vLight.y / 15.0); // block light
    lighting *= vAO;
    lighting = max(lighting, vec3(0.05));
    
    // Add lava self-illumination (emission)
    if (info.fluidType == 3) {
        lighting = max(lighting, vec3(1.3, 0.4, 0.15));
    }
    
    lighting = min(lighting, vec3(2.5));

    fragColor = vec4(lighting * baseColor, alpha);
}
