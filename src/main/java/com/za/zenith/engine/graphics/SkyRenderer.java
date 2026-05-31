package com.za.zenith.engine.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SkyRenderer {
    private Shader shader;
    private Shader domeShader;
    private int vaoId;
    private int vboId;
    private Texture sunTexture;
    private Texture moonTexture;
    private String lastSunPath;
    private String lastMoonPath;

    public void init() {
        shader = new Shader("src/main/resources/shaders/sky_vertex.glsl", "src/main/resources/shaders/sky_fragment.glsl");
        domeShader = new Shader("src/main/resources/shaders/sky_dome_vertex.glsl", "src/main/resources/shaders/sky_dome_fragment.glsl");
        
        // Simple quad [-1, 1]
        float[] positions = {
            -1.0f,  1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        };
        float[] texCoords = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        };
        int[] indices = { 0, 1, 2, 2, 3, 0 };

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, positions.length * 4L + texCoords.length * 4L, GL_STATIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, positions);
        glBufferSubData(GL_ARRAY_BUFFER, positions.length * 4L, texCoords);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, positions.length * 4L);
        glEnableVertexAttribArray(1);

        int eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void render(Camera camera, SceneState state, float moonPhase, float alpha) {
        SkySettings settings = SkySettings.getInstance();
        
        // Update textures if needed
        updateTextures(settings);

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        glBindVertexArray(vaoId);

        // 1. Render Sky Dome Background
        glDisable(GL_BLEND);
        domeShader.use();
        
        org.joml.Matrix4f viewMatrix = new org.joml.Matrix4f(camera.getViewMatrix(alpha));
        viewMatrix.m30(0.0f);
        viewMatrix.m31(0.0f);
        viewMatrix.m32(0.0f);
        org.joml.Matrix4f invViewProj = new org.joml.Matrix4f(camera.getProjectionMatrix()).mul(viewMatrix).invert();
        domeShader.setMatrix4f("uInvViewProj", invViewProj);
        
        AtmosphereManager atmosphere = AtmosphereManager.getInstance();
        domeShader.setVector3f("uSkyColor", atmosphere.getSkyColor());
        domeShader.setVector3f("uSunColor", atmosphere.getSunColor());
        domeShader.setVector3f("uMoonColor", atmosphere.getMoonColor());
        domeShader.setVector3f("uHorizonColor", atmosphere.getHorizonColor()); // NEW: Direct fog/horizon sync
        domeShader.setFloat("uWarmth", atmosphere.getWarmth());
        domeShader.setFloat("uRainIntensity", atmosphere.getRainIntensity());
        
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

        // 2. Render Celestial Bodies
        glEnable(GL_BLEND);
        shader.use();

        // Get absolute astronomical sun direction from SceneState
        Vector3f sunDir = state.getSunDirection();
        
        // --- Render Sun ---
        glBlendFunc(GL_SRC_ALPHA, GL_ONE); 
        renderBody(settings.sun, sunDir, shader, true, moonPhase);

        // --- Render Moon ---
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); 
        renderBody(settings.moon, new Vector3f(sunDir).negate(), shader, false, moonPhase);

        glBindVertexArray(0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void renderBody(SkySettings.BodyConfig body, Vector3f direction, Shader shader, boolean isSun, float moonPhase) {
        // Celestial bodies are always "there", even below horizon, until they fully fade out.
        // We only skip rendering if they are deep below (-0.75) to save some draw calls.
        if (direction.y < -0.75f) {
            return;
        }
        int type = "procedural".equals(body.type) ? 1 : 0;
        shader.setInt("uType", type);
        shader.setBoolean("uIsSun", isSun);
        if (!isSun) {
            shader.setFloat("uMoonPhase", moonPhase);
        }
        shader.setVector3f("uOffset", direction);
        shader.setFloat("uScale", body.scale);
        
        float[] c = body.color;
        org.joml.Vector4f finalColor = new org.joml.Vector4f(c[0], c[1], c[2], c[3]);
        if (isSun) {
            Vector3f dynSunColor = AtmosphereManager.getInstance().getSunColor();
            // Use the average brightness to determine the alpha. 
            // 5.0x multiplier ensures the disk is solid enough to be seen early.
            float intensity = (dynSunColor.x + dynSunColor.y + dynSunColor.z) / 3.0f;
            finalColor.set(dynSunColor.x, dynSunColor.y, dynSunColor.z, Math.clamp(intensity * 5.0f, 0.0f, 1.0f));
        } else {
            Vector3f dynMoonColor = AtmosphereManager.getInstance().getMoonColor();
            float intensity = (dynMoonColor.x + dynMoonColor.y + dynMoonColor.z) / 3.0f;
            finalColor.set(dynMoonColor.x, dynMoonColor.y, dynMoonColor.z, Math.clamp(intensity * 8.0f, 0.0f, 1.0f));
        }
        shader.setVector4f("uColor", finalColor);

        if (type == 0) {
            Texture tex = "pixels".equals(body.type) ? pixelTextures.get(body) : (body == SkySettings.getInstance().sun ? sunTexture : moonTexture);
            if (tex != null) tex.bind();
        }
        
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
    }

    private java.util.Map<SkySettings.BodyConfig, Texture> pixelTextures = new java.util.HashMap<>();

    private void updateTextures(SkySettings settings) {
        // SUN
        if ("texture".equals(settings.sun.type)) {
            if (settings.sun.texture != null && !settings.sun.texture.equals(lastSunPath)) {
                if (sunTexture != null) sunTexture.cleanup();
                sunTexture = new Texture("src/main/resources/" + settings.sun.texture);
                lastSunPath = settings.sun.texture;
            }
        } else if ("pixels".equals(settings.sun.type)) {
            if (!pixelTextures.containsKey(settings.sun)) {
                pixelTextures.put(settings.sun, createPixelTexture(settings.sun));
            }
        }

        // MOON
        if ("texture".equals(settings.moon.type)) {
            if (settings.moon.texture != null && !settings.moon.texture.equals(lastMoonPath)) {
                if (moonTexture != null) moonTexture.cleanup();
                moonTexture = new Texture("src/main/resources/" + settings.moon.texture);
                lastMoonPath = settings.moon.texture;
            }
        } else if ("pixels".equals(settings.moon.type)) {
            if (!pixelTextures.containsKey(settings.moon)) {
                pixelTextures.put(settings.moon, createPixelTexture(settings.moon));
            }
        }
    }

    private Texture createPixelTexture(SkySettings.BodyConfig config) {
        if (config.pixels == null || config.width <= 0 || config.height <= 0) return null;
        byte[] data = new byte[config.pixels.length];
        for (int i = 0; i < config.pixels.length; i++) {
            data[i] = (byte) config.pixels[i];
        }
        return new Texture(config.width, config.height, data);
    }

    public void cleanup() {
        if (shader != null) shader.cleanup();
        if (domeShader != null) domeShader.cleanup();
        if (sunTexture != null) sunTexture.cleanup();
        if (moonTexture != null) moonTexture.cleanup();
        for (Texture t : pixelTextures.values()) t.cleanup();
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}
