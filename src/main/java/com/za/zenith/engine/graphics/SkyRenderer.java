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
    private int vaoId;
    private int vboId;
    private Texture sunTexture;
    private Texture moonTexture;
    private String lastSunPath;
    private String lastMoonPath;

    public void init() {
        shader = new Shader("src/main/resources/shaders/sky_vertex.glsl", "src/main/resources/shaders/sky_fragment.glsl");
        
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

    public void render(Camera camera, Vector3f lightDirection) {
        SkySettings settings = SkySettings.getInstance();
        
        // Update textures if needed
        updateTextures(settings);

        shader.use();

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_BLEND);

        glBindVertexArray(vaoId);

        // 1. Render Sun (additive glow blend) - Sun is at negate(lightDirection) pointing up during the day
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        renderBody(settings.sun, new Vector3f(lightDirection).negate(), shader, true);

        // 2. Render Moon (normal alpha blend, no glow) - Moon is at lightDirection pointing up during the night
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderBody(settings.moon, lightDirection, shader, false);

        glBindVertexArray(0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    private void renderBody(SkySettings.BodyConfig body, Vector3f direction, Shader shader, boolean isSun) {
        int type = "procedural".equals(body.type) ? 1 : 0;
        shader.setInt("uType", type);
        shader.setBoolean("uIsSun", isSun);
        shader.setVector3f("uOffset", direction);
        shader.setFloat("uScale", body.scale);
        float[] c = body.color;
        shader.setVector4f("uColor", new Vector4f(c[0], c[1], c[2], c[3]));

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
        if (sunTexture != null) sunTexture.cleanup();
        if (moonTexture != null) moonTexture.cleanup();
        for (Texture t : pixelTextures.values()) t.cleanup();
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}
