#version 330 core

in vec2 fragTexCoord;
out vec4 fragColor;

uniform sampler2D screenTexture;
uniform sampler2D depthTexture;
uniform vec2 screenSize;

#include "include/post_stack.glsl"

float getDepth(vec2 uv) {
    return LinearizeDepth(texture(depthTexture, uv).r);
}

// Reconstruct world-space normal from depth derivatives
vec3 getNormal(vec2 uv, float centerDepth) {
    vec2 texelSize = 1.0 / screenSize;
    
    float d1 = getDepth(uv + vec2(texelSize.x, 0.0));
    float d2 = getDepth(uv + vec2(0.0, texelSize.y));
    
    vec3 v1 = vec3(texelSize.x, 0.0, d1 - centerDepth);
    vec3 v2 = vec3(0.0, texelSize.y, d2 - centerDepth);
    
    return normalize(cross(v1, v2));
}

uniform float uUnderwaterTime;

void main() {
    vec2 tc = fragTexCoord;
    if (uIsUnderwater) {
        tc.x += sin(tc.y * 25.0 + uUnderwaterTime * 2.5) * 0.0035;
        tc.y += cos(tc.x * 25.0 + uUnderwaterTime * 2.5) * 0.0035;
    }
    
    vec4 texSample = texture(screenTexture, tc);
    vec3 color = texSample.rgb;
    
    vec2 texelSize = 1.0 / screenSize;
    
    // Apply AAA Stylized Post-Stack
    color = applyPostProcessing(color, tc, texelSize, depthTexture);

    fragColor = vec4(color, 1.0);
}
