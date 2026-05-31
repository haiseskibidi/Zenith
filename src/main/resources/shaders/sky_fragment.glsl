#version 330 core

in vec2 fragTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec4 uColor;
uniform int uType; // 0=Texture, 1=Procedural
uniform bool uIsSun;
uniform float uMoonPhase; // -1.0 (New), 0.0 (Half), 1.0 (Full)

void main() {
    if (uType == 1) {
        float dist = distance(fragTexCoord, vec2(0.5));
        
        if (uIsSun) {
            // Perfect Round Sun with rich, warm, glorious atmospheric glow
            float sunDisk = smoothstep(0.182, 0.178, dist);
            
            // Beautiful physically-based exponential atmospheric glow decay for Sun
            // Gives a soft, expansive, glowing golden halo initially in the sky
            float glow = exp(-dist * 4.5) * 0.85;
            
            // Bright white core, warm golden-tinted outer atmospheric glow
            vec3 finalRGB = mix(uColor.rgb * glow * 2.0, vec3(1.0), sunDisk);
            float finalAlpha = max(sunDisk, glow * uColor.a);
            
            fragColor = vec4(finalRGB, finalAlpha);
        } else {
            // Perfect 3D Sphere Moon with craters and silver-indigo velvet glow
            float moonDisk = smoothstep(0.182, 0.178, dist);
            
            // Reconstruct 3D normal vector on the front hemisphere of the Moon sphere
            float nx = (fragTexCoord.x - 0.5) / 0.182;
            float ny = (fragTexCoord.y - 0.5) / 0.182;
            float nz = sqrt(max(0.0, 1.0 - nx*nx - ny*ny));
            vec3 N = vec3(nx, ny, nz);
            
            // Calculate virtual light direction based on uMoonPhase [-1.0 (New), 0.0 (Half), 1.0 (Full)]
            float angle = (1.0 - uMoonPhase) * 1.570796325; // 90 degrees factor
            vec3 L = vec3(sin(angle), 0.0, cos(angle));
            
            // 3D illumination dot product with smooth shadow terminator
            float diffuse = dot(N, L);
            float illumination = smoothstep(-0.06, 0.06, diffuse);
            
            // Subtly simulate lunar seas (craters and dark spots) inside the moon disk
            vec2 detailUV = (fragTexCoord - vec2(0.5)) * 5.0; // scale up for detailing
            float detail = sin(detailUV.x * 2.2 + detailUV.y) * cos(detailUV.y * 2.5 - detailUV.x) * 0.12;
            detail += sin(detailUV.y * 5.0) * cos(detailUV.x * 4.0) * 0.05;
            detail = clamp(detail, -0.2, 0.2);
            
            // Soft, silver-indigo velvet edge glow for realistic depth and ambient dispersion
            float glow = exp(-dist * 14.0) * 0.15;
            
            // Lunar cool silver-grey base color with detail
            vec3 moonBaseColor = uColor.rgb * (0.88 + detail);
            
            // Dynamic moon color blended with soft procedural phase shadow
            vec3 illuminatedMoon = mix(vec3(0.01, 0.02, 0.04), moonBaseColor, illumination);
            
            vec3 finalRGB = mix(uColor.rgb * glow, illuminatedMoon, moonDisk);
            float finalAlpha = max(moonDisk * mix(0.1, 0.95, illumination), glow * uColor.a);
            
            fragColor = vec4(finalRGB, finalAlpha);
        }
    } else {
        // Texture Mode
        vec4 texColor = texture(uTexture, fragTexCoord);
        if (texColor.a < 0.01) discard;
        fragColor = texColor * uColor;
    }
}
