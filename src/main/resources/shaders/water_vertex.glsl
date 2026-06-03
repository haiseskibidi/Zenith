#version 330 core

layout(location = 0) in vec3 position;
layout(location = 1) in vec4 texCoordOrPackedTex;
layout(location = 2) in vec3 normalOrPackedLayers;
layout(location = 3) in vec4 blockTypeOrPackedBlock;
layout(location = 4) in vec4 neighborOrPackedLight;
layout(location = 5) in vec4 aInstanceData; // MultiDraw: x,y,z = pos, w = spawnTime

#include "include/global_data.glsl"

uniform mat4 model;
uniform bool uIsCompressed;
uniform bool uIsBatch; // New: True if rendering via MultiDrawBatch
uniform float uChunkSpawnTime;

out vec3 fragNormal;
out vec3 fragPos;
out float neighborData;
out vec2 vLight;
out float vAO;
out float blockType;

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
    
    vec3 finalNormal;
    float finalNeighborData;
    vec2 finalLight;
    float finalAO;
    int packedPos = 0;

    if (uIsCompressed) {
        uint packedLayers = floatBitsToUint(normalOrPackedLayers.x);
        uint nIndex = (packedLayers >> 24) & 0x7u;
        vec3 faceNormals[6] = vec3[](vec3(0,0,1), vec3(0,0,-1), vec3(1,0,0), vec3(-1,0,0), vec3(0,1,0), vec3(0,-1,0));
        if (nIndex < 6u) finalNormal = faceNormals[nIndex];
        else finalNormal = vec3(0, 1, 0);

        uint packedBlock = floatBitsToUint(blockTypeOrPackedBlock.x);
        int bType = int(packedBlock & 0xFFFFu);
        if ((bType & 0x8000) != 0) bType |= 0xFFFF0000;
        blockType = float(bType);
        finalNeighborData = float((packedBlock >> 16) & 0x3Fu);

        uint packedLight = floatBitsToUint(neighborOrPackedLight.x);
        finalLight = vec2(float(packedLight & 0xFu), float((packedLight >> 4) & 0xFu));
        float aoIdx = float((packedLight >> 8) & 0x3u);
        finalAO = (aoIdx == 3.0) ? 1.0 : (aoIdx == 2.0) ? 0.8 : (aoIdx == 1.0) ? 0.6 : 0.4;
        packedPos = int((packedLight >> 10) & 0xFFFFu);
    } else {
        finalNormal = normalOrPackedLayers;
        finalNeighborData = neighborOrPackedLight.x;
        finalLight = neighborOrPackedLight.yz;
        finalAO = neighborOrPackedLight.w;
        blockType = blockTypeOrPackedBlock.x;
    }

    if (uIsBatch) {
        fragNormal = finalNormal;
    } else {
        fragNormal = normalize(mat3(model) * finalNormal);
    }
    
    vec3 worldPos;
    if (uIsBatch) {
        worldPos = actualChunkPos + position;
    } else {
        worldPos = vec3(model * vec4(position, 1.0));
    }
    
    fragPos = worldPos;
    neighborData = finalNeighborData;
    vLight = finalLight;
    vAO = finalAO;
    
    gl_Position = gProjection * gView * vec4(worldPos, 1.0);
}
