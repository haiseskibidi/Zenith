#version 330 core

in vec2 fragTexCoord;
out vec4 fragColor;

uniform sampler2D screenTexture;
uniform sampler2D depthTexture;

uniform vec2 uSunScreenPos; // Sun position on screen [0, 1]
uniform bool uSunVisible;   // Is sun in front of the camera
uniform vec3 uSunDirView;   // Sun direction in View Space

// Atmosphere Sun Shafts parameters (loaded from Biome JSON)
uniform float uDensity;
uniform float uWeight;
uniform float uDecay;
uniform float uExposure;
uniform vec3 uShaftColor;
uniform mat4 uInvProjection; // Inverse projection to reconstruct View Space
uniform float uAspectRatio;  // Screen aspect ratio (width / height)

vec3 getViewPos(vec2 uv) {
    float depth = texture(depthTexture, uv).r;
    float z = depth * 2.0 - 1.0;
    vec4 clipSpacePos = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePos = uInvProjection * clipSpacePos;
    viewSpacePos /= viewSpacePos.w;
    return viewSpacePos.xyz;
}

// Interleaved Gradient Noise for anti-banding dithering
float getNoise(vec2 co) {
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec4 baseColor = texture(screenTexture, fragTexCoord);
    
    if (!uSunVisible) {
        fragColor = baseColor;
        return;
    }
    
    vec3 viewPos = getViewPos(fragTexCoord);
    
    // Calculate Henyey-Greenstein Scattering Phase Function
    // g = 0.78 (high forward scattering for realistic sun glare)
    float g = 0.78;
    float g2 = g * g;
    float cosTheta = dot(normalize(viewPos), normalize(uSunDirView));
    float phase = (1.0 - g2) / (4.0 * 3.14159265 * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
    
    // Smooth Volumetric Raymarching
    int NUM_SAMPLES = 64;
    vec2 textCoords = fragTexCoord;
    
    // Aspect ratio correction to maintain perfectly circular shafts and sun halo
    vec2 aspectCorrection = vec2(1.0 / uAspectRatio, 1.0);
    vec2 dir = (textCoords - uSunScreenPos) * aspectCorrection;
    float dist = length(dir);
    vec2 ndir = dist > 0.0001 ? dir / dist : vec2(0.0);
    
    // Clamp the max radial blur step length to prevent banding lines near screen borders
    float clampedDist = clamp(dist, 0.001, 0.35);
    
    // Smooth glare shift look-at factor: expand sampling step when looking straight at the sun
    float lookAtSun = max(0.0, cosTheta);
    float glareShift = pow(lookAtSun, 12.0); // 1.0 exactly at the center of the sun
    
    // Force a minimum scattering bloom radius of 0.12 screen-width when looking directly at the sun
    float finalDist = mix(clampedDist, max(clampedDist, 0.12), glareShift);
    
    // Convert back from corrected circular space to texture space
    vec2 deltaTexCoord = (ndir * finalDist * (uDensity * 0.45) / float(NUM_SAMPLES)) / aspectCorrection;
    
    // Dithering: randomize start offset using Interleaved Gradient Noise to dissolve banding
    float noise = getNoise(textCoords * 100.0);
    textCoords += deltaTexCoord * (noise - 0.5); // Apply jitter
    
    float illuminationDecay = 1.0;
    vec3 raysColor = vec3(0.0);
    
    for (int i = 0; i < NUM_SAMPLES; i++) {
        textCoords -= deltaTexCoord;
        
        if (textCoords.x < 0.0 || textCoords.x > 1.0 || textCoords.y < 0.0 || textCoords.y > 1.0) {
            break;
        }
        
        vec3 sampleColor = texture(screenTexture, textCoords).rgb;
        float depth = texture(depthTexture, textCoords).r;
        
        // Geometric occlusion check
        if (depth > 0.98) {
            float brightness = dot(sampleColor, vec3(0.299, 0.587, 0.114));
            
            // 100% Realistic smooth scattering without toon/anime quantization steps
            float intensity = smoothstep(0.35, 0.75, brightness);
            raysColor += sampleColor * intensity * uWeight * illuminationDecay;
        } else {
            // Occlusion: foreground geometry (foliage, blocks) blocks the sun ray propagation
            illuminationDecay *= 0.88;
        }
        
        illuminationDecay *= uDecay;
    }
    
    // 5-Point Depth Probe around uSunScreenPos on GPU to dynamically check sun occlusion.
    // Fades glare when behind walls/caves, but shimmers realistically when behind tree foliage.
    float visibility = 0.0;
    float offsetVal = 0.012; // Radius around the sun disk to probe
    
    visibility += texture(depthTexture, uSunScreenPos).r > 0.98 ? 0.3 : 0.0;
    visibility += texture(depthTexture, uSunScreenPos + vec2(offsetVal, 0.0)).r > 0.98 ? 0.175 : 0.0;
    visibility += texture(depthTexture, uSunScreenPos - vec2(offsetVal, 0.0)).r > 0.98 ? 0.175 : 0.0;
    visibility += texture(depthTexture, uSunScreenPos + vec2(0.0, offsetVal)).r > 0.98 ? 0.175 : 0.0;
    visibility += texture(depthTexture, uSunScreenPos - vec2(0.0, offsetVal)).r > 0.98 ? 0.175 : 0.0;
    
    // Apply Henyey-Greenstein phase, exposure, biome tint, and dynamic occlusion visibility
    vec3 finalRays = raysColor * uExposure * phase * uShaftColor * visibility;
    
    // Volumetric Blend
    vec3 blended = baseColor.rgb + finalRays;
    
    // Reinhard Tonemapping to prevent white sky burnout/overexposure when looking at the sun
    vec3 mapped = blended / (blended + vec3(1.0));
    
    // Exposure correction to match base brightness levels
    vec3 finalColor = mix(blended, mapped * 1.6, smoothstep(0.0, 1.0, length(finalRays)));
    
    fragColor = vec4(finalColor, 1.0);
}
