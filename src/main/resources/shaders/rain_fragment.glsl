#version 330 core
out vec4 fragColor;

uniform float uRainIntensity;
uniform float uTime;

in vec2 vUV;

void main() {
    // Create a needle-like shape (motion blurred drop)
    float centerX = 0.5;
    float distToCenter = abs(vUV.x - centerX);
    
    // Tapered width: sharper at the bottom (vUV.y=0), wider at the top
    float width = 0.1 + vUV.y * 0.4;
    float streak = smoothstep(width, width - 0.1, distToCenter);
    
    // Fade out at the very top and bottom of the quad
    streak *= smoothstep(0.0, 0.1, vUV.y) * smoothstep(1.0, 0.8, vUV.y);
    
    // Emissive bright blue-white color for high visibility
    vec3 rainColor = vec3(0.8, 0.9, 1.0);
    float alpha = streak * 0.7 * uRainIntensity;
    
    if (alpha < 0.01) discard;
    
    fragColor = vec4(rainColor, alpha);
}
