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

    vec2 texelSize = 1.0 / screenSize;
    
    // 1. Read base color
    vec4 centerSample = texture(screenTexture, tc);
    vec3 center = centerSample.rgb;
    
    // 2. APPLY POST-PROCESSING FIRST
    center = applyPostProcessing(center, tc, texelSize, depthTexture);
    
    // 3. FXAA PASS (On Post-Processed Colors)
    vec3 left   = texture(screenTexture, tc - vec2(texelSize.x, 0.0)).rgb;
    vec3 right  = texture(screenTexture, tc + vec2(texelSize.x, 0.0)).rgb;
    vec3 up     = texture(screenTexture, tc - vec2(0.0, texelSize.y)).rgb;
    vec3 down   = texture(screenTexture, tc + vec2(0.0, texelSize.y)).rgb;
    
    left = pow(left, vec3(1.15));
    right = pow(right, vec3(1.15));
    up = pow(up, vec3(1.15));
    down = pow(down, vec3(1.15));
    
    float centerLuma   = dot(center, vec3(0.299, 0.587, 0.114));
    float leftLuma     = dot(left,   vec3(0.299, 0.587, 0.114));
    float rightLuma    = dot(right,  vec3(0.299, 0.587, 0.114));
    float upLuma       = dot(up,     vec3(0.299, 0.587, 0.114));
    float downLuma     = dot(down,   vec3(0.299, 0.587, 0.114));
    
    float minLuma = min(centerLuma, min(min(leftLuma, rightLuma), min(upLuma, downLuma)));
    float maxLuma = max(centerLuma, max(max(leftLuma, rightLuma), max(upLuma, downLuma)));
    
    float range = maxLuma - minLuma;
    vec3 blended = center;
    
    if (range >= max(0.0312, maxLuma * 0.125)) {
        float horizontalGrad = abs(leftLuma - centerLuma) + abs(centerLuma - rightLuma);
        float verticalGrad   = abs(upLuma - centerLuma) + abs(centerLuma - downLuma);
        bool isHorizontal = horizontalGrad > verticalGrad;
        
        vec2 direction = isHorizontal ? vec2(0.0, texelSize.y) : vec2(texelSize.x, 0.0);
        
        vec3 sample1 = texture(screenTexture, tc + direction * 0.5).rgb;
        vec3 sample2 = texture(screenTexture, tc - direction * 0.5).rgb;
        sample1 = pow(sample1, vec3(1.15));
        sample2 = pow(sample2, vec3(1.15));
        
        float blendStrength = clamp(range * 2.0, 0.2, 0.5);
        blended = mix(center, (sample1 + sample2) * 0.5, blendStrength);
    }
    
    fragColor = vec4(blended, 1.0);
}
