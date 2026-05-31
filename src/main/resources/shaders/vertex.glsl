#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec4 texCoordOrPackedTex;
layout(location = 2) in vec3 normalOrPackedLayers;
layout(location = 3) in vec4 blockTypeOrPackedBlock;
layout(location = 4) in vec4 neighborOrPackedLight;
layout(location = 5) in vec4 aInstanceData; // MultiDraw: x,y,z = pos, w = spawnTime
layout(location = 6) in float verticalWeightAttr;
layout(location = 7) in vec2 lightAttr;
layout(location = 8) in float aoAttr;

#include "include/global_data.glsl"

uniform mat4 model;

uniform bool uIsProxy;
uniform bool uIsCompressed;
uniform bool uIsBatch; // New: True if rendering via MultiDrawBatch
uniform vec3 uWobbleScale;
uniform vec3 uWobbleOffset;
uniform float uWobbleShake;
uniform float uWobbleTime;
uniform float uSwayOverride; // -1.0 = use attribute, 0.0 = force static, 1.0 = force sway
uniform vec3 uOverrideLight; // x=sun, y=block, z=ao. If x >= 0.0, use this instead of attributes.

uniform float uChunkSpawnTime;

uniform vec3 uHiddenPositions[16];
uniform int uHiddenCount;

// Include external modules
#include "include/foliage_animation.glsl"

out vec4 fragTexCoord;
out vec3 fragNormal;
out vec3 fragPos;
out float blockType;
out float neighborData;
out vec3 vLocalPos;
out float vBreakingIntensity;
out vec2 vLight;
out float vAO;
out float vChunkAge;
out float vWetness;
flat out ivec3 vBlockPos;

void main() {
    vec3 actualChunkPos;
    float actualSpawnTime;
    
    if (uIsBatch) {
        actualChunkPos = aInstanceData.xyz;
        actualSpawnTime = aInstanceData.w;
    } else {
        actualChunkPos = vec3(model[3][0], model[3][1], model[3][2]);
        actualSpawnTime = uChunkSpawnTime;
    }

    vChunkAge = gSunDirection.w - actualSpawnTime;
    if (vChunkAge < 0.0) vChunkAge += 3600.0;
    
    vec4 finalTexCoord;
    vec3 finalNormal;
    float finalBlockType;
    float finalNeighborData;
    float finalVerticalWeight;
    vec2 finalLight;
    float finalAO;
    float finalWetness = 0.5;
    int packedPos = 0;

    if (uIsCompressed) {
        uint packedTex = floatBitsToUint(texCoordOrPackedTex.x);
        finalTexCoord = vec4(float(packedTex & 0xFFFFu) / 65535.0, float((packedTex >> 16) & 0xFFFFu) / 65535.0, 0.0, -1.0);

        uint packedLayers = floatBitsToUint(normalOrPackedLayers.x);
        finalTexCoord.z = float(packedLayers & 0xFFFu);
        finalTexCoord.w = float((packedLayers >> 12) & 0xFFFu) - 1.0;
        
        uint nIndex = (packedLayers >> 24) & 0x7u;
        vec3 faceNormals[6] = vec3[](vec3(0,0,1), vec3(0,0,-1), vec3(1,0,0), vec3(-1,0,0), vec3(0,1,0), vec3(0,-1,0));
        if (nIndex < 6u) finalNormal = faceNormals[nIndex];
        else finalNormal = vec3(0, 1, 0);

        uint packedBlock = floatBitsToUint(blockTypeOrPackedBlock.x);
        int bType = int(packedBlock & 0xFFFFu);
        if ((bType & 0x8000) != 0) bType |= 0xFFFF0000;
        finalBlockType = float(bType);
        finalNeighborData = float((packedBlock >> 16) & 0x3Fu);
        finalVerticalWeight = float((packedBlock >> 22) & 0x1u);
        finalWetness = float((packedBlock >> 23) & 0xFu) / 15.0;

        uint packedLight = floatBitsToUint(neighborOrPackedLight.x);
        finalLight = vec2(float(packedLight & 0xFu), float((packedLight >> 4) & 0xFu));
        float aoIdx = float((packedLight >> 8) & 0x3u);
        finalAO = (aoIdx == 3.0) ? 1.0 : (aoIdx == 2.0) ? 0.8 : (aoIdx == 1.0) ? 0.6 : 0.4;
        packedPos = int((packedLight >> 10) & 0xFFFFu);
    } else {
        finalTexCoord = texCoordOrPackedTex;
        finalNormal = normalOrPackedLayers;
        finalBlockType = blockTypeOrPackedBlock.x;
        finalNeighborData = neighborOrPackedLight.x;
        finalVerticalWeight = verticalWeightAttr;
        finalLight = lightAttr;
        
        packedPos = int(aoAttr / 10.0);
        finalAO = aoAttr - float(packedPos) * 10.0;
    }

    fragTexCoord = finalTexCoord;
    vWetness = finalWetness;

    if (uOverrideLight.x >= 0.0) {
        vLight = uOverrideLight.xy;
        vAO = uOverrideLight.z;
    } else {
        vLight = finalLight;
        vAO = finalAO;
    }
   
    int localX = packedPos & 15;
    int localZ = (packedPos >> 4) & 15;
    int localY = packedPos >> 8;
    
    vBlockPos = ivec3(floor(actualChunkPos + vec3(float(localX), float(localY), float(localZ)) + 0.1));

    // Zero-Alloc GPU-Driven Hiding in Vertex Shader
    // Collapses the vertices of active breaking blocks to (0,0,0) so the GPU clips them out.
    // This fully bypasses fragment shading and keeps Early-Z activated!
    // IMPORTANT: Only hide in batch chunk renders (uIsBatch) where packedPos is valid.
    // Single-mesh renders (HoleMesh, BreakingProxy) have invalid packedPos and must be skipped.
    if (!uIsProxy && uIsBatch) {
        for (int i = 0; i < uHiddenCount; i++) {
            if (vBlockPos == ivec3(uHiddenPositions[i])) {
                gl_Position = vec4(0.0);
                return;
            }
        }
    }
    
    if (uIsBatch) {
        fragNormal = finalNormal; // Simplified: chunks usually have identity rotation
    } else {
        fragNormal = normalize(mat3(model) * finalNormal);
    }
    
    vec3 worldPos;
    if (uIsBatch) {
        worldPos = actualChunkPos + position;
    } else {
        worldPos = vec3(model * vec4(position, 1.0));
    }
    
    vBreakingIntensity = 0.0;

    if (vChunkAge < 1.0 && !uIsProxy) {
        float revealProgress = clamp(vChunkAge, 0.0, 1.0);
        float offset = -4.0 * (1.0 - pow(revealProgress, 3.0));
        worldPos.y += offset;
    }

    vLocalPos = position;
    if (uIsProxy) {
        vec3 localDeformed = position;
        if (uWobbleScale != vec3(1.0) || uWobbleOffset != vec3(0.0) || uWobbleShake > 0.0) {
            vec3 localCenter = vec3(0.0, 0.5, 0.0);
            vec3 dir = position - localCenter;
            localDeformed = localCenter + (dir * uWobbleScale) + uWobbleOffset;
            
            if (uWobbleShake > 0.0) {
                float time = uWobbleTime * 35.0;
                float angleZ = sin(time) * 0.06 * uWobbleShake;
                float angleX = cos(time * 0.8) * 0.06 * uWobbleShake;
                
                mat3 rotZ = mat3(
                    cos(angleZ), -sin(angleZ), 0.0,
                    sin(angleZ),  cos(angleZ), 0.0,
                    0.0,          0.0,         1.0
                );
                mat3 rotX = mat3(
                    1.0, 0.0,         0.0,
                    0.0, cos(angleX), -sin(angleX),
                    0.0, sin(angleX),  cos(angleX)
                );
                
                localDeformed = localCenter + rotZ * rotX * (localDeformed - localCenter);
            }
        }
        worldPos = vec3(model * vec4(localDeformed, 1.0));
        vBreakingIntensity = 1.0;
    }
    
    float swayIntense = (uSwayOverride < 0.0) ? 1.0 : uSwayOverride;
    worldPos = applyFoliageWind(worldPos, position, finalTexCoord.w, gSunDirection.w, uIsProxy, finalVerticalWeight, swayIntense);

    fragPos = worldPos;
    blockType = finalBlockType;
    neighborData = finalNeighborData;
    gl_Position = gProjection * gView * vec4(worldPos, 1.0);
}
