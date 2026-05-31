// Procedural Material Breaking Patterns

uniform vec4 uHitHistory[16];
uniform int uHitCount;
uniform float uBreakingProgress;
uniform int uBreakingPattern;
uniform vec3 uWeakSpotPos;
uniform vec3 uWeakSpotColor;

vec3 applyWoodNotch(vec3 color, vec3 localPos, vec4 notchPosAndIntensity, float progress, bool isFresh) {
    vec3 notchPos = notchPosAndIntensity.xyz;
    float intensityMultiplier = notchPosAndIntensity.w;

    // --- Optimization: Exit early if too far from the notch ---
    float dist = distance(localPos, notchPos);
    if (dist > 0.25) return color; 

    // Determine the dominant axis of the surface normal to project the notch correctly
    vec3 absNormal = abs(fragNormal);
    
    // Choose projection planes based on normal
    vec3 pPos, nPos;
    if (absNormal.y > 0.5) {
        // Top/Bottom faces: Vertical is Z or X
        pPos = localPos.zyx;
        nPos = notchPos.zyx;
    } else if (absNormal.x > 0.5) {
        // Side faces (X-normal): Vertical is Y, horizontal is Z
        pPos = localPos.yzx;
        nPos = notchPos.yzx;
    } else {
        // Side faces (Z-normal): Vertical is Y, horizontal is X
        pPos = localPos.yxz;
        nPos = notchPos.yxz;
    }

    // Seed based on notch position for uniqueness
    float seed = hash3D(notchPos * 10.0);
    
    // Pixelated coordinates in projected space
    vec3 pixelPos = floor(pPos * 16.0);
    
    // Jagged horizontal cut (in projected space)
    float jagged = floor(noise(pPos.yz * 10.0 + vec2(seed)) * 2.0);
    float cutV = floor(nPos.x * 16.0) + jagged;
    
    // V-shaped notch mask
    float horizDist = distance(pPos.yz, nPos.yz);
    float range = (1.0 + progress * 2.0) * smoothstep(0.25, 0.0, horizDist);
    
    if (range > 0.0 && abs(pixelPos.x - cutV) < range) {
        float vDist = abs(pixelPos.x - cutV);
        float intensity = (1.0 - (vDist / range)) * smoothstep(0.25, 0.0, horizDist) * intensityMultiplier;
        
        if (isFresh) {
            // Use dynamic weak spot color instead of hardcoded white (critical for birch)
            return mix(color, uWeakSpotColor, intensity * 0.9);
        } else {
            // Dark damaged old cut
            return mix(color, color * 0.15, intensity * 0.8);
        }
    }
    
    return color;
}

vec3 applyStoneChip(vec3 color, vec3 localPos, vec4 notchPosAndIntensity, float progress, bool isFresh) {
    vec3 notchPos = notchPosAndIntensity.xyz;
    float intensityMultiplier = notchPosAndIntensity.w;

    // Discritize coordinates to 16x16 grid immediately
    vec3 p16 = floor(localPos * 16.0) / 16.0;
    vec3 n16 = floor(notchPos * 16.0) / 16.0;

    float dist = distance(p16, n16);
    if (dist > 0.22) return color;

    // Determine normal-aligned local 2D space
    vec3 absNormal = abs(fragNormal);
    vec2 p2d, n2d;
    if (absNormal.y > 0.5) { p2d = p16.xz; n2d = n16.xz; }
    else if (absNormal.x > 0.5) { p2d = p16.yz; n2d = n16.yz; }
    else { p2d = p16.xy; n2d = n16.xy; }

    vec2 dir = p2d - n2d;
    float d = length(dir);
    float angle = atan(dir.y, dir.x);
    
    // Star shape cracks using discrete noise
    float n = noise(vec3(floor(p2d * 16.0), hash(n2d)));
    float star = cos(angle * 5.0 + n * 3.0); 
    
    float mask = 0.0;
    // Center impact hole (pixelated)
    if (d < 0.065 + progress * 0.04) mask = 1.0;
    // Cracks spreading out (pixelated)
    if (star > 0.75 && d < 0.16 + progress * 0.05) mask = 1.0;

    if (mask > 0.5) {
        float intensity = (1.0 - (d / 0.25)) * (0.8 + n * 0.2) * intensityMultiplier;
        if (isFresh) {
            return mix(color, uWeakSpotColor, intensity);
        } else {
            return mix(color, vec3(0.05), intensity * 0.8);
        }
    }

    return color;
}

vec3 applyWeakSpotMarker(vec3 color, vec3 localPos, vec3 weakSpotPos, float progress) {
    vec3 p16 = floor(localPos * 16.0) / 16.0;
    vec3 n16 = floor(weakSpotPos * 16.0) / 16.0;
    float d = distance(p16, n16);
    
    if (d < 0.08) {
        float pulse = sin(uWobbleTime * 25.0) * 0.5 + 0.5;
        // Pixel-perfect pulse marker
        return mix(color, uWeakSpotColor, (0.5 + pulse * 0.5) * smoothstep(0.08, 0.0, d));
    }
    return color;
}

vec3 applyBreakingPattern(int patternId, vec3 color, vec3 localPos, float progress) {
    if (progress <= 0.0) return color;
    
    vec3 result = color;
    
    if (patternId == 1) { // WOOD
        // Draw pulsing marker for better visibility on wood too
        result = applyWeakSpotMarker(result, localPos, uWeakSpotPos, progress);

        for (int i = 0; i < uHitCount; i++) {
            if (i < 16) result = applyWoodNotch(result, localPos, uHitHistory[i], progress, false);
        }
        result = applyWoodNotch(result, localPos, vec4(uWeakSpotPos, 1.0), progress, true);
    } else if (patternId == 2) { // STONE / ORE
        // Draw marker only for stone/ore pattern
        result = applyWeakSpotMarker(result, localPos, uWeakSpotPos, progress);
        
        for (int i = 0; i < uHitCount; i++) {
            if (i < 16) result = applyStoneChip(result, localPos, uHitHistory[i], progress, false);
        }
        result = applyStoneChip(result, localPos, vec4(uWeakSpotPos, 1.0), progress, true);
    }
    
    return result;
}
