// Zenith Lighting System - Shared Types and Functions

struct ZenithLight {
    int type;
    vec3 position;
    vec3 direction;
    vec3 color;
    float intensity;
    float radius;
    float spotAngle;
};

vec3 calculateLighting(vec3 normal, vec3 lightDir, vec3 lightCol, vec3 ambient) {
    if (dot(normal, normal) < 0.0001) return ambient + lightCol;
    float diffuse = max(dot(normal, -lightDir), 0.0);
    
    // AAA Stylized toon-stepping
    float toonDiffuse = smoothstep(0.1, 0.2, diffuse) * 0.7 + smoothstep(0.5, 0.6, diffuse) * 0.3;
    
    return ambient * vec3(0.85, 0.88, 0.95) + lightCol * toonDiffuse;
}

vec3 calculateDynamicLighting(vec3 normal, vec3 fragPos, ZenithLight light) {
    if (light.type == 0) return vec3(0.0);
    
    vec3 lightDir;
    float attenuation = 1.0;
    
    if (light.type == 1) { // Directional
        lightDir = normalize(light.direction);
    } else {
        vec3 toLight = light.position - fragPos;
        float distance = length(toLight);
        lightDir = normalize(-toLight);
        
        if (distance > light.radius) return vec3(0.0);
        
        // Quad attenuation for soft falloff
        attenuation = pow(clamp(1.0 - distance / light.radius, 0.0, 1.0), 2.0);
        
        if (light.type == 3) { // Spot
            float theta = dot(lightDir, normalize(light.direction));
            if (theta < light.spotAngle) {
                attenuation = 0.0;
            } else {
                attenuation *= smoothstep(light.spotAngle, light.spotAngle + 0.05, theta);
            }
        }
    }
    
    float diffuse = max(dot(normal, -lightDir), 0.0);
    // Apply toon-stepping to dynamic lights too for visual consistency
    float toonDiffuse = smoothstep(0.05, 0.15, diffuse) * 0.8 + smoothstep(0.4, 0.6, diffuse) * 0.2;
    
    return light.color * (toonDiffuse * attenuation * light.intensity);
}
