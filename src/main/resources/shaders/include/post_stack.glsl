// --- STYLIZED AAA POST-STACK (Toon & Atmosphere) ---

#include "include/global_data.glsl"

uniform vec3 uSkyColor;
uniform float uHazeDensity;
uniform bool uIsUnderwater;

float near = 0.01; 
float far  = 1000.0; 

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0; 
    return (2.0 * near * far) / (far + near - z * (far - near));    
}

vec3 applyPostProcessing(vec3 color, vec2 fragTexCoord, vec2 texelSize, sampler2D depthTexture) {
    float rawDepth = texture(depthTexture, fragTexCoord).r;
    
    // Calculate View Direction from screen space for directional fog
    vec4 clipPos = vec4(fragTexCoord * 2.0 - 1.0, 1.0, 1.0);
    vec4 viewDir4 = gInvProjection * clipPos;
    vec3 viewDir = normalize(viewDir4.xyz);
    
    if (rawDepth < 0.99999) {
        float d = LinearizeDepth(rawDepth);
        
        // 1. Stylized Crease AO
        if (d > 0.05) {
            float dL = LinearizeDepth(texture(depthTexture, fragTexCoord + vec2(-texelSize.x, 0.0)).r);
            float dR = LinearizeDepth(texture(depthTexture, fragTexCoord + vec2( texelSize.x, 0.0)).r);
            float dU = LinearizeDepth(texture(depthTexture, fragTexCoord + vec2(0.0,  texelSize.y)).r);
            float dD = LinearizeDepth(texture(depthTexture, fragTexCoord + vec2(0.0, -texelSize.y)).r);
            
            float averageDepth = (dL + dR + dU + dD) * 0.25;
            float diff = d - averageDepth;
            
            // Sharper Toon Outlines: tighter threshold to combat MSAA softening
            if (diff > 0.02) {
                float ao = smoothstep(0.02, 0.08, diff);
                color *= mix(1.0, 0.55, ao);
            }
        }
        
        // 2. Atmospheric Fog (Physically-based Exponential Haze)
        float baseHaze = uHazeDensity;

        // Directional Golden Dawn Haze: Denser towards the sun when it's low
        // Using astronomical sun direction from UBO
        float sunElevation = uSunDirection.y;
        if (sunElevation > -0.85) {
            float sunGlowFactor = max(0.0, dot(viewDir, uSunDirection));
            // Intensity peaks at the horizon (sunElevation = 0), fades towards -0.85 and 0.40
            float lowSunWeight = 1.0 - abs(sunElevation - 0.05) / 0.80;
            lowSunWeight = clamp(lowSunWeight, 0.0, 1.0);

            // Apply directional density boost (up to 3x denser towards the sun)
            baseHaze *= (1.0 + lowSunWeight * pow(sunGlowFactor, 3.0) * 2.5);
        }

        float fogFactor = 1.0 - exp(-max(0.0, d - 8.0) * baseHaze);
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        color = mix(color, uSkyColor, fogFactor);
    }

    if (uIsUnderwater) {
        float d = LinearizeDepth(rawDepth);
        if (rawDepth > 0.99999) {
            d = 48.0;
        }
        vec3 waterFogColor = vec3(0.02, 0.16, 0.32);
        float fogFactor = 1.0 - exp(-d * 0.18);
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        color = mix(color * vec3(0.5, 0.78, 0.98), waterFogColor, fogFactor);
    }
    
    // 3. Filmic Contrast
    color = pow(color, vec3(1.15));
    
    // 4. Balanced Vibrance
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    float maxColorC = max(color.r, max(color.g, color.b));
    float minColorC = min(color.r, min(color.g, color.b));
    float sat = maxColorC - minColorC;
    color = mix(vec3(luma), color, 1.0 + (0.35 * (1.0 - sat)));

    // 5. Cinematic Vignette
    vec2 uv = fragTexCoord * (1.0 - fragTexCoord.yx);
    float vig = uv.x * uv.y * 15.0; 
    vig = pow(vig, 0.2); 
    color *= mix(0.7, 1.0, vig);
    
    return color;
}
