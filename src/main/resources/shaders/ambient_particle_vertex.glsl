#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;

// Instance data
layout (location = 2) in vec4 instPosScale;  // x, y, z, scale
layout (location = 3) in vec4 instVisual;    // sunlight, blocklight, alpha, age
layout (location = 4) in vec4 instColorSpeed; // r, g, b, speed

out vec2 fragTexCoord;
out float fragAlpha;
out vec3 vColor;
out float vLightIntensity;

#include "include/global_data.glsl"

void main() {
    vec3 worldPos = instPosScale.xyz;
    float scale = instPosScale.w;
    
    float sunlight = instVisual.x;
    float blocklight = instVisual.y;
    float alpha = instVisual.z;
    float age = instVisual.w;
    
    vec3 glowingColor = instColorSpeed.xyz;
    float speed = instColorSpeed.w;
    
    // BILLBOARDING: Extract camera right and up vectors from gView
    vec3 camRight = vec3(gView[0][0], gView[1][0], gView[2][0]);
    vec3 camUp = vec3(gView[0][1], gView[1][1], gView[2][1]);
    
    // Stylized wind sway wave calculation
    float waveX = sin(age * 1.5 + worldPos.x + worldPos.y) * 0.12 * speed;
    float waveY = cos(age * 1.2 + worldPos.z + worldPos.y) * 0.08 * speed;
    
    vec3 vertexWorldPos = worldPos + 
                         (camRight * (aPos.x * scale + waveX)) + 
                         (camUp * (aPos.y * scale + waveY));
    
    gl_Position = gProjection * gView * vec4(vertexWorldPos, 1.0);
    
    fragTexCoord = aTexCoord;
    fragAlpha = alpha;
    vColor = glowingColor;
    
    // Calculate light intensity: glow when in sunlight >= 12 or near torches (blocklight)
    float sunGlow = smoothstep(11.0, 13.0, sunlight);
    float blockGlow = blocklight / 15.0;
    vLightIntensity = max(sunGlow, blockGlow);
}
