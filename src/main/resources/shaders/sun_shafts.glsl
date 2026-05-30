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

// Depth-adaptive visibility function to let shafts shine through tree foliage but block in caves
float getSampleVisibility(float depth) {
    // If depth is in the viewmodel range (0.0 to 0.05), it is the player's hand/tool.
    // The hand should NOT occlude the sun rays or atmospheric blinding glow.
    if (depth <= 0.05) {
        return 1.0;
    }
    if (depth > 0.99) {
        return 1.0; // Clear sky (full visibility)
    }
    if (depth < 0.95) {
        return 0.0; // Close solid wall / cave (complete occlusion)
    }
    // Foliage or far objects: partial visibility (smooth fading between 0.95 and 0.99)
    return smoothstep(0.95, 0.99, depth) * 0.8 + 0.2;
}

void main() {
    vec4 baseColor = texture(screenTexture, fragTexCoord);
    
    if (!uSunVisible) {
        fragColor = baseColor;
        return;
    }
    
    vec3 viewPos = getViewPos(fragTexCoord);
    
    // Calculate Henyey-Greenstein Scattering Phase Function blended with isotropic term.
    // This ensures beautiful light shafts are visible from the sides/back (backscattering)
    // while keeping strong forward scattering (0.78) for the main sun glare.
    float g = 0.78;
    float g2 = g * g;
    float cosTheta = dot(normalize(viewPos), normalize(uSunDirView));
    float hgPhase = (1.0 - g2) / (4.0 * 3.14159265 * pow(1.0 + g2 - 2.0 * g * cosTheta, 1.5));
    float isoPhase = 1.0 / (4.0 * 3.14159265);
    float phase = mix(isoPhase, hgPhase, 0.55); // 55% directional, 45% ambient volumetric glow
    
    // Smooth Volumetric Raymarching
    int NUM_SAMPLES = 64;
    vec2 textCoords = fragTexCoord;
    
    // Perfectly accurate aspect ratio conversion to maintain symmetrical circular shafts and sun halo.
    // Convert texture space vector to physical screen-space vector (width is scaled by uAspectRatio).
    vec2 dir = (textCoords - uSunScreenPos) * vec2(uAspectRatio, 1.0);
    float dist = length(dir);
    vec2 ndir = dist > 0.0001 ? dir / dist : vec2(0.0);
    
    // Clamp the max radial blur step length to prevent banding lines near screen borders
    float clampedDist = clamp(dist, 0.001, 0.35);
    
    // For realistic eye-glare, we sample densely and smoothly near the sun center,
    // which completely eliminates the "black hole" center void and ensures high detail.
    // When looking directly at the sun, we softly expand the radial blur to simulate eye lens glow.
    float lookAtSun = max(0.0, cosTheta);
    float glareShift = pow(lookAtSun, 16.0); // sharp activation peak
    float finalDist = mix(clampedDist, max(clampedDist, 0.05), glareShift * 0.5);
    
    // Convert the physical screen-space step vector back to texture space (width is divided by uAspectRatio).
    vec2 deltaTexCoord = (ndir * finalDist * (uDensity * 0.45) / float(NUM_SAMPLES)) * vec2(1.0 / uAspectRatio, 1.0);
    
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
            
            // Highly sensitive, super prominent crepuscular rays propped by smoothstep
            float intensity = smoothstep(0.35, 0.8, brightness);
            raysColor += sampleColor * intensity * uWeight * illuminationDecay;
        } else if (depth <= 0.05) {
            // Viewmodel hand/tool: does NOT block the sun ray propagation.
            // We just let the light pass through it cleanly without any decay!
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
    
    visibility += getSampleVisibility(texture(depthTexture, uSunScreenPos).r) * 0.3;
    visibility += getSampleVisibility(texture(depthTexture, uSunScreenPos + vec2(offsetVal, 0.0)).r) * 0.175;
    visibility += getSampleVisibility(texture(depthTexture, uSunScreenPos - vec2(offsetVal, 0.0)).r) * 0.175;
    visibility += getSampleVisibility(texture(depthTexture, uSunScreenPos + vec2(0.0, offsetVal)).r) * 0.175;
    visibility += getSampleVisibility(texture(depthTexture, uSunScreenPos - vec2(0.0, offsetVal)).r) * 0.175;
    
    // Apply Henyey-Greenstein phase, exposure, biome tint, and dynamic occlusion visibility
    vec3 finalRays = raysColor * uExposure * phase * uShaftColor * visibility;
    
    // Apply non-linear contrast enhancement to final shafts.
    // This sharpens light beams and deepens ambient crepuscular shadows (dark rays),
    // preventing details from washing out even when looking directly at the sun.
    finalRays = pow(finalRays, vec3(1.45)) * 1.65;
    
    // Volumetric Blend
    vec3 blended = baseColor.rgb + finalRays;
    
    // Reinhard Tonemapping to prevent white sky burnout/overexposure when looking at the sun
    vec3 mapped = blended / (blended + vec3(1.0));
    
    // Exposure correction to match base brightness levels
    vec3 finalColor = mix(blended, mapped * 1.6, smoothstep(0.0, 1.0, length(finalRays)));
    
    fragColor = vec4(finalColor, 1.0);
}
