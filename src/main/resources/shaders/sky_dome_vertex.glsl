#version 330 core

layout(location = 0) in vec3 position;

out vec3 fragViewDir;

uniform mat4 uInvViewProj;

void main() {
    // Standard full-screen quad in clip space at the far plane
    gl_Position = vec4(position.xy, 1.0, 1.0); 
    
    // Unproject clip space position to world space view direction (with 0 translation)
    vec4 worldPos = uInvViewProj * vec4(position.xy, 1.0, 1.0);
    fragViewDir = normalize(worldPos.xyz / worldPos.w);
}
