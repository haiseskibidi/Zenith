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
in float vWetness;
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

uniform float uRainIntensity; // Global rain intensity (0.0 to 1.0)
uniform sampler2D uHeightmap;
uniform vec2 uGridStart;
uniform vec2 uGridSize;

// Modular Includes
#include "include/noise.glsl"
#include "include/hand_conditions.glsl"
#include "include/block_features.glsl"
#include "include/breaking_patterns.glsl"
#include "include/lighting.glsl"

uniform ZenithLight uLights[8];
uniform int uLightCount;

// Mathemagical Sync Helper
float hash_rain_sync(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 baseColor;    
    float alpha = 1.0;

    // Determine if block is exposed to rain (smooth step based on sunlight to prevent blocky discrete wetness boundaries)
    float rainExposure = smoothstep(7.0, 12.0, vLight.x);
    
    // AAA Polish: Heightmap-based precise occlusion for splashes
    // This ensures that even if light leaks into a cave, splashes only appear where rain actually hits the ground.
    vec2 heightmapUV = (fragPos.xz - uGridStart) / uGridSize;
    if (heightmapUV.x >= 0.0 && heightmapUV.x <= 1.0 && heightmapUV.y >= 0.0 && heightmapUV.y <= 1.0) {
        float groundHeight = texture(uHeightmap, heightmapUV).r;
        // If the block surface is significantly below the highest block at this XZ, it's occluded.
        // We use a small epsilon (0.2) to allow splashes on the very top surface of the block.
        if (fragPos.y < groundHeight - 0.2) {
            rainExposure = 0.0;
        }
    }

    float rainEffect = uRainIntensity * rainExposure;

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

        BlockInfo info = decodeBlockInfo(blockType);

        if (info.isGlass) {
            textureColor = applyGlassConnections(textureColor, fragTexCoord.xy, neighborData, fragTexCoord.z, textureSampler);
        }

        float discardThreshold = (info.isTinted && !info.isGlass) ? 0.45 : 0.1;
        if (textureColor.a < discardThreshold) discard;

        baseColor = textureColor.rgb;
        alpha = textureColor.a;

        if (isHand) {
            baseColor = applyHandConditions(baseColor, vLocalPos, uCondition, uHandPartWeight);
        }

        baseColor = brightenTopFace(baseColor, info.type, fragNormal);

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

        if (uIsProxy && uBreakingProgress > 0.0) {
            baseColor = applyBreakingPattern(uBreakingPattern, baseColor, vLocalPos, uBreakingProgress);
        }

        // --- SYNCHRONIZED WETNESS & SPLASH EFFECTS ---
        if (rainEffect > 0.0) {
            // 1. Darken porous materials
            float darkenFactor = mix(0.7, 1.0, vWetness);
            baseColor *= mix(1.0, darkenFactor, rainEffect);

            if (fragNormal.y > 0.5) {
                // Top faces: Mathemagical Ripples & Splashes
                float grid = 0.5; // MUST MATCH rain_vertex.glsl
                vec2 gridPos = floor(fragPos.xz / grid) * grid + (grid * 0.5);
                vec2 localPos = fract(fragPos.xz / grid) - 0.5;
                
                float cellHash = hash_rain_sync(gridPos);
                float cycleTime = 1.2; // MUST MATCH rain_vertex.glsl
                float t = fract(uTime / cycleTime + cellHash);
                
                // Moments before impact (t -> 1.0)
                // Ripple expands when t is small (just after impact)
                float rippleT = fract(t + 0.1); // Shift so it starts at t=0.9
                
                float dist = length(localPos);
                
                // 2. Ripple Ring
                if (rippleT < 0.4) {
                    float ring = smoothstep(rippleT - 0.1, rippleT, dist) - smoothstep(rippleT, rippleT + 0.1, dist);
                    ring *= (1.0 - rippleT * 2.5);
                    baseColor += ring * 0.25 * rainEffect * max(0.4, vWetness);
                }
                
                // 3. Impact Splash (Micro-particles jumping up)
                // Visible only at the very moment of impact
                if (t > 0.92 || t < 0.08) {
                    float impact = 1.0 - smoothstep(0.0, 0.15, dist);
                    // Pulsing vertical needle
                    float splashHeight = (t > 0.92) ? (t - 0.92) * 10.0 : (0.08 - t) * 5.0;
                    baseColor += impact * splashHeight * 0.4 * rainEffect;
                }
                
            } else if (abs(fragNormal.y) < 0.5) {
                // Side faces: Flowing Droplets
                vec2 dp = vec2(dot(fragNormal.z > 0.5 || fragNormal.z < -0.5 ? fragPos.x : fragPos.z, 1.0), fragPos.y);
                dp.x *= 4.0;
                dp.y *= 2.0;
                float h = hash_rain_sync(vec2(floor(dp.x), 0.0));
                float dropTime = uTime * (2.5 + h * 1.5);
                dp.y += dropTime;
                vec2 localDp = fract(dp) - 0.5;
                float streak = 1.0 - smoothstep(0.0, 0.15, length(vec2(localDp.x * 2.0, localDp.y)));
                streak *= step(0.85, h);
                baseColor += streak * 0.12 * rainEffect * vWetness;
            }
        }
    }

    // Apply Lighting
    vec3 totalDynamicLight = vec3(0.0);
    vec3 sunLightContribution = vec3(0.0);
    float sunlightMask = vLight.x / 15.0;

    for (int i = 0; i < uLightCount; i++) {
        if (uLights[i].type == 1) { // Directional (Sun/Moon)
            vec3 directSun = calculateLighting(fragNormal, uSunDirection, uLights[i].color * sunlightMask * 0.8, vec3(0.0));
            vec3 scatteredSun = uLights[i].color * sunlightMask * 0.2;
            sunLightContribution += directSun + scatteredSun;
            
            if (rainEffect > 0.0 && !isHand && highlightPass == 0) {
                vec3 viewDir = normalize(uCameraPos - fragPos);
                vec3 halfDir = normalize(uSunDirection + viewDir);
                float specAngle = max(dot(fragNormal, halfDir), 0.0);
                float glossiness = mix(16.0, 128.0, vWetness);
                float specular = pow(specAngle, glossiness);
                vec3 specColor = uLights[i].color * specular * vWetness * rainEffect * sunlightMask * 0.5;
                sunLightContribution += specColor;
            }
        } else {
            totalDynamicLight += calculateDynamicLighting(fragNormal, fragPos, uLights[i]);
            if (rainEffect > 0.0 && !isHand && highlightPass == 0) {
                vec3 lightDir = normalize(uLights[i].position - fragPos);
                vec3 viewDir = normalize(uCameraPos - fragPos);
                vec3 halfDir = normalize(lightDir + viewDir);
                float specAngle = max(dot(fragNormal, halfDir), 0.0);
                float glossiness = mix(16.0, 128.0, vWetness);
                float specular = pow(specAngle, glossiness);
                float dist = length(uLights[i].position - fragPos);
                float attenuation = 1.0 - smoothstep(uLights[i].radius * 0.5, uLights[i].radius, dist);
                vec3 specColor = uLights[i].color * specular * vWetness * rainEffect * attenuation * 0.4;
                totalDynamicLight += specColor;
            }
        }
    }
    
    vec3 lighting = uAmbientColor * vec3(0.85, 0.88, 0.95);
    lighting += sunLightContribution;
    lighting += totalDynamicLight;
    lighting += vec3(1.0, 0.85, 0.6) * (vLight.y / 15.0);
    lighting *= vAO;
    lighting = max(lighting, vec3(0.05));
    lighting = min(lighting, vec3(2.5)); 
    
    fragColor = vec4(lighting * baseColor * brightnessMultiplier, alpha);

    if (previewPass) {
        fragColor.rgb = mix(fragColor.rgb, vec3(1.0), 0.3);
        fragColor.a *= previewAlpha;
    }
}
