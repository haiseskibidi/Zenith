#version 330 core

in vec4 fragTexCoord;
in vec3 fragNormal;
in vec3 fragPos;
in vec3 vLocalPos;
in float blockType;
in float neighborData;

#include "include/global_data.glsl"

out vec4 fragColor;

uniform sampler2DArray textureSampler;

uniform vec3 uCondition; // x=dirt, y=blood, z=wetness
uniform bool isHand = false;
uniform float uHandPartWeight = 0.0; // 1.0=hand, 0.6=forearm, 0.3=shoulder

// Modular Includes
#include "include/noise.glsl"
#include "include/hand_conditions.glsl"
#include "include/block_features.glsl"
#include "include/lighting.glsl"

uniform ZenithLight uLights[8];
uniform int uLightCount;

uniform float uMiningHeat = 0.0; // 0.0 to 1.0 intensity
uniform float uAlpha = 1.0;
uniform vec3 uOverrideLight; // x=sun, y=block, z=ao

void main() {
    vec4 textureColor = texture(textureSampler, fragTexCoord.xyz);
    if (textureColor.a < 0.5) discard;

    vec3 baseColor = textureColor.rgb;
    float alpha = textureColor.a;

    BlockInfo info = decodeBlockInfo(blockType);

    // 1. Apply Hand Conditions (Dirt, Blood)
    if (isHand) {
        baseColor = applyHandConditions(baseColor, vLocalPos, uCondition, uHandPartWeight);
    }

    // 2. Mining Heat Logic (Subtle Hot Metal Look)
    float glowMask = 0.0;
    if (isHand) {
        glowMask = smoothstep(0.85, 1.0, uHandPartWeight);
    } else {
        // Starts just above the handle, full at the tip
        glowMask = smoothstep(-0.2, 0.4, vLocalPos.y);
    }

    if (uMiningHeat > 0.01) {
        float h = glowMask * uMiningHeat;
        vec3 hotRed = vec3(1.0, 0.05, 0.0); // Slightly more natural hot red
        
        // Subtle pulse (range 0.95 - 1.05)
        float pulse = (0.95 + 0.05 * sin(uTime * 20.0)) * h;
        
        // 2.1 Tint the base texture (keeps details visible)
        baseColor = mix(baseColor, hotRed, h * 0.6);
        
        // 2.2 Add subtle emission (doesn't blind the player)
        baseColor += hotRed * pulse * 0.7; 
    }

    // 3. Apply Lighting
    vec3 totalDynamicLight = vec3(0.0);
    vec3 sunlighting = vec3(0.0);
    float sunlightMask = uOverrideLight.x / 15.0;
    
    for (int i = 0; i < uLightCount; i++) {
        if (uLights[i].type == 1) { // Directional (Sun/Moon)
            // FIXED: Use uLights[i].direction (view-space) instead of uSunDirection (world-space)
            // AAA Polish: Ultra-soft diffuse for viewmodel rotation (0.0 to 1.0 range)
            float diffuse = max(dot(fragNormal, -uLights[i].direction), 0.0);
            float softToon = smoothstep(-0.2, 0.5, diffuse) * 0.5 + smoothstep(0.1, 0.9, diffuse) * 0.5;
            
            // Mask sunlight by local voxel light (stops leaking into caves)
            // Added wrap-around (0.15) also masked by sunlight level
            sunlighting += uLights[i].color * (softToon * sunlightMask + 0.15 * sunlightMask);
        } else {
            // Dynamic lights: smoother transitions for viewmodel
            vec3 toLight = uLights[i].position - fragPos;
            float distance = length(toLight);
            if (distance < uLights[i].radius) {
                vec3 lightDir = normalize(-toLight);
                float attenuation = pow(clamp(1.0 - distance / uLights[i].radius, 0.0, 1.0), 2.0);
                float diffuse = max(dot(fragNormal, -lightDir), 0.0);
                // Wider steps for dynamic lights on viewmodel for smoother rotation
                float softToon = smoothstep(0.0, 0.4, diffuse) * 0.7 + smoothstep(0.2, 0.8, diffuse) * 0.3;
                totalDynamicLight += uLights[i].color * (softToon * attenuation * uLights[i].intensity);
            }
        }
    }
    
    // AAA Polish: Ambient from sky + block light (torch in cave) + visibility boost
    vec3 lighting = max(uAmbientColor, vec3(0.16)) * vec3(0.85, 0.88, 0.95);
    lighting += sunlighting + totalDynamicLight;
    lighting += vec3(1.0, 0.85, 0.6) * (uOverrideLight.y / 15.0); // Local block light (Torches)
    
    lighting = max(lighting, vec3(0.05)); // Hard baseline
    
    // 4. Unified Tinting (Leaves/Grass)
    if (info.isTinted) {
        if (fragTexCoord.w >= 0.0 && !info.isGlass) {
            vec4 overlayTex = texture(textureSampler, vec3(fragTexCoord.xy, fragTexCoord.w));
            if (overlayTex.a > 0.1) {
                baseColor = mix(baseColor, overlayTex.rgb * uGrassColor, overlayTex.a);
            }
        } else {
            baseColor *= uGrassColor;
        }
    }

    fragColor = vec4(lighting * baseColor, alpha * uAlpha);
}
