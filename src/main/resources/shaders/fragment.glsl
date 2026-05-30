#version 330 core

in vec4 fragTexCoord;
in vec3 fragNormal;
in vec3 fragPos;
in float blockType;
in float neighborData;
in vec3 vLocalPos;
in float vBreakingIntensity;
in vec2 vLight;
in float vAO;
in float vChunkAge;
flat in ivec3 vBlockPos;

#include "include/global_data.glsl"

out vec4 fragColor;

uniform sampler2DArray textureSampler;
uniform float glassLayer;   
uniform int highlightPass; // 1 = solid color mode, 0 = texture mode
uniform vec3 highlightColor;
uniform bool previewPass; 
uniform float previewAlpha;
uniform bool viewModelPass; 
uniform float brightnessMultiplier = 1.0;
uniform int faceMask = 0; // 16-bit mask for 4x4 grid
uniform bool useMask = false;
uniform float overlayLayer;
uniform float uWobbleTime;
uniform bool uIsProxy;

uniform vec3 uCondition; // x=dirt, y=blood, z=wetness
uniform bool isHand = false;
uniform float uHandPartWeight = 0.0; // 1.0=hand, 0.6=forearm, 0.3=shoulder

// Modular Includes
#include "include/noise.glsl"
#include "include/hand_conditions.glsl"
#include "include/block_features.glsl"
#include "include/lighting.glsl"
#include "include/breaking_patterns.glsl"

uniform ZenithLight uLights[8];
uniform int uLightCount;

void main() {
    vec3 baseColor;    float alpha = 1.0;

    if (highlightPass != 0) {
        baseColor = highlightColor;
    } else {
        vec4 textureColor;
        if (useMask) {
            vec2 localUV = fragTexCoord.xy;
            int bit = int(clamp(localUV.y * 4.0, 0.0, 3.99)) * 4 + int(clamp(localUV.x * 4.0, 0.0, 3.99));
            if (((faceMask >> bit) & 1) == 0) discard;
            textureColor = texture(textureSampler, vec3(localUV, overlayLayer));
        } else {
            textureColor = texture(textureSampler, fragTexCoord.xyz);
        }

        // Handle specific block logic
        BlockInfo info = decodeBlockInfo(blockType);
        
        // Glass connectivity
        if (info.isGlass) {
            textureColor = applyGlassConnections(textureColor, fragTexCoord.xy, neighborData, fragTexCoord.z, textureSampler);
        }

        // With MSAA + Alpha-to-Coverage, we use a higher threshold (0.5) for foliage-like textures
        // to ensure sharp cutouts and lower threshold (0.1) for general transparency.
        float discardThreshold = (info.isTinted && !info.isGlass) ? 0.45 : 0.1;
        if (textureColor.a < discardThreshold) discard;

        baseColor = textureColor.rgb;
        alpha = textureColor.a;

        // Apply hand overlays (dirt, blood)
        if (isHand) {
            baseColor = applyHandConditions(baseColor, vLocalPos, uCondition, uHandPartWeight);
        }

        // Feature: Brighten Stump tops
        baseColor = brightenTopFace(baseColor, info.type, fragNormal);

        // Unified Tinting (Leaves/Grass)
        if (info.isTinted) {
            if (fragTexCoord.w >= 0.0 && !info.isGlass) {
                vec4 overlayTex = texture(textureSampler, vec3(fragTexCoord.xy, fragTexCoord.w));
                if (overlayTex.a > 0.1) {
                    baseColor = mix(baseColor, overlayTex.rgb * uGrassColor, overlayTex.a);
                }
            } else if (blockType < 0.0) {
                baseColor *= uGrassColor;
            }
        }

        // Apply Breaking Patterns
        if (uIsProxy && uBreakingProgress > 0.0) {
            baseColor = applyBreakingPattern(uBreakingPattern, baseColor, vLocalPos, uBreakingProgress);
        }
    }

    // Apply Lighting
    vec3 totalDynamicLight = vec3(0.0);
    vec3 sunLightContribution = vec3(0.0);
    
    // Sunlight Intensity (0-1)
    float sunlightMask = vLight.x / 15.0;

    // Process all lights
    for (int i = 0; i < uLightCount; i++) {
        if (uLights[i].type == 1) { // Directional (Sun/Moon)
            // 1. Direct toon-shaded sunlight (provides volume and steps)
            // Scaled to 0.8 to leave room for scattered component and prevent overexposure
            vec3 directSun = calculateLighting(fragNormal, uSunDirection, uLights[i].color * sunlightMask * 0.8, vec3(0.0));
            
            // 2. Scattered/Volumetric sunlight (illuminates backfaces in lit areas)
            // Scaled to 0.2. Total (Direct + Scattered) will be 1.0 on the surface.
            vec3 scatteredSun = uLights[i].color * sunlightMask * 0.2; 
            
            sunLightContribution += directSun + scatteredSun;
        } else {
            // Point and Spot lights (don't care about sunlight mask, they are internal)
            totalDynamicLight += calculateDynamicLighting(fragNormal, fragPos, uLights[i]);
        }
    }
    
    // Final lighting assembly
    vec3 lighting = uAmbientColor * vec3(0.85, 0.88, 0.95); // Start with ambient
    lighting += sunLightContribution;                     // Add sun/moon
    lighting += totalDynamicLight;                        // Add lamps/torches
    
    // Add baked blocklight (warm orange tint)
    float blocklightIntensity = vLight.y / 15.0;
    lighting += vec3(1.0, 0.85, 0.6) * blocklightIntensity;
    
    // Apply Ambient Occlusion (always apply for volume)
    lighting *= vAO;
    
    // Ensure absolute minimum brightness (eye adaptation / night vision)
    // Apply AFTER AO so corners are slightly darker but still visible
    lighting = max(lighting, vec3(0.05));
    
    // Clamp to prevent eye-bleeding brightness
    lighting = min(lighting, vec3(2.5)); 
    
    fragColor = vec4(lighting * baseColor * brightnessMultiplier, alpha);       

    if (previewPass) {
        fragColor.rgb = mix(fragColor.rgb, vec3(1.0), 0.3);
        fragColor.a *= previewAlpha;
    }
}
