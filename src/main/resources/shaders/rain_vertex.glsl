#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;

#include "include/global_data.glsl"

out vec2 vUV;
out float vInstanceID;

// Fast hash for 1D float
float hash11(float p) {
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

// 2D Hash
vec2 hash21(float p) {
    vec3 p3 = fract(vec3(p) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx+p3.yz)*p3.zy);
}

void main() {
    vUV = aUV;
    vInstanceID = float(gl_InstanceID);
    
    // Mathemagical Sync: Calculate particle position based on instance ID and time
    // We map each instance to a unique spot in a grid around the player
    
    float id = float(gl_InstanceID);
    vec2 seed = hash21(id);
    
    float radius = 16.0;
    // Circular distribution around camera
    float angle = seed.x * 6.28318;
    float dist = sqrt(seed.y) * radius;
    
    vec3 offset;
    offset.x = cos(angle) * dist;
    offset.z = sin(angle) * dist;
    
    // Snap XZ to a "virtual grid" so surface shader can match it
    // Grid size 0.5m
    float grid = 0.5;
    vec3 worldBase = uCameraPos + offset;
    worldBase.xz = floor(worldBase.xz / grid) * grid + (grid * 0.5);
    
    // Re-hash seed based on actual grid position for deterministic timing
    float cellHash = hash11(worldBase.x * 12.9898 + worldBase.z * 78.233);
    
    // Falling logic
    float fallSpeed = 25.0;
    float cycleTime = 1.2; // One drop every 1.2 seconds per cell
    float t = fract(uTime / cycleTime + cellHash);
    
    // Height: start from sky (e.g. 20m above camera) and fall to ground
    // Ground is assumed at uCameraPos.y - something, but we just fall 30m
    float startHeight = 20.0;
    offset.y = startHeight - (t * 35.0); 
    
    // Billboarding (face camera Y)
    vec3 worldPos = vec3(worldBase.x, uCameraPos.y + offset.y, worldBase.z);
    
    // Apply wind tilt
    float tilt = 0.12;
    mat3 rotX = mat3(
        1.0, 0.0, 0.0,
        0.0, cos(tilt), -sin(tilt),
        0.0, sin(tilt), cos(tilt)
    );
    
    // Face player logic (simplified billboard)
    vec3 dir = normalize(uCameraPos - worldPos);
    float rotY = atan(dir.x, dir.z);
    mat3 rotYMat = mat3(
        cos(rotY), 0.0, sin(rotY),
        0.0, 1.0, 0.0,
        -sin(rotY), 0.0, cos(rotY)
    );

    vec3 localPos = rotYMat * rotX * aPos;
    
    gl_Position = gProjection * gView * vec4(worldPos + localPos, 1.0);
}
