#version 330 core

in vec2 fragTexCoord;
in float fragAlpha;
in vec3 vColor;
in float vLightIntensity;

out vec4 fragColor;

void main() {
    // Distance from the center of the billboard quad
    float dist = length(fragTexCoord - vec2(0.5));
    if (dist > 0.5) discard;
    
    // Smooth Cel-Shaded edge for the dust circle
    float alphaMask = smoothstep(0.5, 0.43, dist);
    
    // Core glow highlight
    float core = smoothstep(0.3, 0.0, dist);
    
    // Color states:
    // In shadows: light-grey, low alpha, flat
    // In sunlight: bright, glowing gold/custom biome color with neon core
    vec3 shadowColor = vec3(0.68, 0.68, 0.73);
    vec3 brightColor = vColor * 1.5 + vec3(core * 0.4);
    
    vec3 finalColor = mix(shadowColor, brightColor, vLightIntensity);
    
    // Fade in alpha significantly in the sun rays to create a volumetric dust look
    float finalAlpha = fragAlpha * alphaMask * mix(0.1, 0.85, vLightIntensity);
    
    fragColor = vec4(finalColor, finalAlpha);
}
