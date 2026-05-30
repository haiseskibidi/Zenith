#version 330 core

in vec2 fragTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec4 uColor;
uniform int uType; // 0=Texture, 1=Procedural
uniform bool uIsSun;

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
            // Perfect Round Moon with silver lunar seas and subtle velvet edge glow (no neon look)
            float moonDisk = smoothstep(0.182, 0.178, dist);
            
            // Subtly simulate lunar seas (craters and dark spots) inside the moon disk
            vec2 uv = (fragTexCoord - vec2(0.5)) * 5.0; // scale up for detailing
            float detail = sin(uv.x * 2.2 + uv.y) * cos(uv.y * 2.5 - uv.x) * 0.12;
            detail += sin(uv.y * 5.0) * cos(uv.x * 4.0) * 0.05;
            detail = clamp(detail, -0.2, 0.2);
            
            // Very subtle, soft silver edge glow for realistic depth and anti-aliasing
            float glow = exp(-dist * 16.0) * 0.1;
            
            // Lunar cool silver-grey base color with detail, white highlighted core
            vec3 moonBaseColor = uColor.rgb * (0.85 + detail);
            
            vec3 finalRGB = mix(uColor.rgb * glow, moonBaseColor, moonDisk);
            float finalAlpha = max(moonDisk * 0.95, glow * uColor.a);
            
            fragColor = vec4(finalRGB, finalAlpha);
        }
    } else {
        // Texture Mode
        vec4 texColor = texture(uTexture, fragTexCoord);
        if (texColor.a < 0.01) discard;
        fragColor = texColor * uColor;
    }
}
