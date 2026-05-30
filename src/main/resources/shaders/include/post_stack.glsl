// --- STYLIZED AAA POST-STACK (Toon & Atmosphere) ---

uniform vec3 uSkyColor;

float near = 0.01; 
float far  = 1000.0; 

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0; 
    return (2.0 * near * far) / (far + near - z * (far - near));    
}

vec3 applyPostProcessing(vec3 color, vec2 fragTexCoord, vec2 texelSize, sampler2D depthTexture) {
    float rawDepth = texture(depthTexture, fragTexCoord).r;
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
        
        // 2. Atmospheric Fog
        float fogFactor = smoothstep(40.0, 200.0, d);
        color = mix(color, uSkyColor, fogFactor);
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
