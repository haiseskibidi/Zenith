layout (std140) uniform GlobalData {
    mat4 gProjection;   // 0 - 63
    mat4 gView;         // 64 - 127
    vec4 gCameraPos;    // 128 - 143 (w unused)
    vec4 gSunDirection; // 144 - 159 (w is time)
    vec4 gAmbientColor; // 160 - 175 (w isNight flag)
    vec4 gGrassColor;   // 176 - 191 (w unused)
};

// Explicit helpers that don't use macros if possible, but macros are fine for convenience
#define uCameraPos gCameraPos.xyz
#define uSunDirection gSunDirection.xyz
#define uTime gSunDirection.w
#define uAmbientColor gAmbientColor.xyz
#define uGrassColor gGrassColor.xyz
#define uIsNight (gAmbientColor.w > 0.5)

