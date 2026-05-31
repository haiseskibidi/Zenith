#version 330 core

in vec2 fragTexCoord;
out vec4 fragColor;

uniform sampler2D screenTexture;
uniform sampler2D depthTexture;

uniform vec2 uSunScreenPos; // Sun position on screen [0, 1]
uniform bool uSunVisible;   // Is sun in front of the camera
uniform float uSunVisibility; // CPU-calculated smooth visibility [0, 1]
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

// Helper: linearize depth at a ray sample point (for raymarching occlusion decay)
float getLinearDist(vec2 uv, float rawDepth) {
    float z = rawDepth * 2.0 - 1.0;
    vec4 clipSpacePos = vec4(uv * 2.0 - 1.0, z, 1.0);
    vec4 viewSpacePos = uInvProjection * clipSpacePos;
    viewSpacePos /= viewSpacePos.w;
    return -viewSpacePos.z;
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
        
        // Far-away clouds and sky pixels (depth > 0.98) act as the light sources for crepuscular rays.
        // This beautifully projects rays from sky gaps between tree foliage.
        if (depth > 0.98) {
            float brightness = dot(sampleColor, vec3(0.299, 0.587, 0.114));
            
            // Highly sensitive, super prominent crepuscular rays propped by smoothstep
            float intensity = smoothstep(0.35, 0.8, brightness);
            raysColor += sampleColor * intensity * uWeight * illuminationDecay;
        } else if (depth <= 0.05) {
            // Viewmodel hand/tool: does NOT block the sun ray propagation.
            // We just let the light pass through it cleanly without any decay!
        } else {
            // Geometry occlusion: distance-aware decay.
            // Close blocks (caves, walls) kill rays aggressively.
            // Distant foliage allows rays to shimmer through canopy gaps.
            float linDist = getLinearDist(textCoords, depth);
            // smoothstep: at 5m → decay=0.85 (aggressive), at 25m+ → decay=0.96 (soft foliage)
            float foliageDecay = mix(0.85, 0.96, smoothstep(5.0, 25.0, linDist));
            illuminationDecay *= foliageDecay;
        }
        
        illuminationDecay *= uDecay;
    }
    
    // Apply Henyey-Greenstein phase, exposure, biome tint
    vec3 finalRays = raysColor * uExposure * phase * uShaftColor;
    
    // Apply non-linear contrast enhancement to final shafts.
    // This sharpens light beams into delicate needles of light and deepens shadows.
    finalRays = pow(finalRays, vec3(1.6)) * 1.3;
    
    // Volumetric Blend
    vec3 blended = baseColor.rgb + finalRays;
    
    // Reinhard Tonemapping to prevent white sky burnout/overexposure when looking at the sun
    vec3 mapped = blended / (blended + vec3(1.0));
    
    // Exposure correction to match base brightness levels
    vec3 finalColor = mix(blended, mapped * 1.6, smoothstep(0.0, 1.0, length(finalRays)));
    
    // Dynamic Blinding Glare (Physically-based Eye Adaptation & Lens Flooding)
    // blindingFactor peaks when the sun is in the center of the screen
    float screenCenterDist = distance(uSunScreenPos, vec2(0.5));
    float blindingFactor = smoothstep(0.35, 0.05, screenCenterDist) * uSunVisibility;
    
    if (blindingFactor > 0.001) {
        // Blinding flare is tightly focused around the physical sun position
        float sunDist = length((fragTexCoord - uSunScreenPos) * vec2(uAspectRatio, 1.0));
        
        // Super sharp, physically-realistic tiny atmospheric glow centered strictly on the sun disk
        float centerGlow = exp(-sunDist * 35.0) * 0.70; 
        
        // Gentle blinding color (harmoniously propped by uShaftColor)
        vec3 blindColor = uShaftColor * centerGlow * blindingFactor * 0.4;
        
        // Localized and subtle wash out (only over the sun disk itself to simulate retina adaptation)
        float washOut = exp(-sunDist * 22.0) * 0.15 * blindingFactor;
        finalColor = mix(finalColor, vec3(1.0), washOut);
        finalColor += blindColor;
    }
    
    fragColor = vec4(finalColor, 1.0);
}
