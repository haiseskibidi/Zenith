package com.za.zenith.engine.graphics.vfx;

import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.world.World;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

public class RainRenderer {
    private final int vaoId;
    private final int vboId;
    private final Shader rainShader;
    private final int heightmapTexId;
    private final float[] heightData = new float[64 * 64];
    
    // Using a fixed count of virtual "cells" around the player
    private static final int RAIN_COUNT = 3000; 

    public RainRenderer() {
        this.rainShader = new Shader("src/main/resources/shaders/rain_vertex.glsl", "src/main/resources/shaders/rain_fragment.glsl");
        
        // Создаем текстуру карты высот 64x64
        heightmapTexId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, heightmapTexId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 64, 64, 0, GL_RED, GL_FLOAT, (java.nio.ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        // Simple quad for a rain drop
        float w = 0.015f;
        float h = 0.8f;
        float[] vertices = {
            -w, 0, 0,  0, 0,
             w, 0, 0,  1, 0,
             w, h, 0,  1, 1,
            -w, h, 0,  0, 1
        };

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        // Attribute 0: Position
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // Attribute 1: UV
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    public void render(Camera camera, World world, float alpha, float deltaTime) {
        float rainIntensity = world.getWeatherManager().getRainIntensity();
        if (rainIntensity <= 0.05f) return;

        // 1. Рассчитываем границы сетки высот вокруг камеры
        Vector3f camPos = camera.getPosition();
        float grid = 0.5f;
        float startX = (float)Math.floor(camPos.x / grid) * grid - 32.0f * grid;
        float startZ = (float)Math.floor(camPos.z / grid) * grid - 32.0f * grid;
        float gridSize = 64.0f * grid; // 32.0m
        
        // 2. Заполняем массив высот на CPU
        for (int z = 0; z < 64; z++) {
            float worldZ = startZ + z * grid;
            int iz = (int)Math.floor(worldZ);
            for (int x = 0; x < 64; x++) {
                float worldX = startX + x * grid;
                int ix = (int)Math.floor(worldX);
                heightData[z * 64 + x] = world.getHighestBlock(ix, iz);
            }
        }
        
        // 3. Загружаем данные в текстуру на GPU
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, heightmapTexId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 64, 64, GL_RED, GL_FLOAT, heightData);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        
        rainShader.use();
        rainShader.setFloat("uRainIntensity", rainIntensity);
        rainShader.setInt("uHeightmap", 0);
        rainShader.setVector2f("uGridStart", new org.joml.Vector2f(startX, startZ));
        rainShader.setVector2f("uGridSize", new org.joml.Vector2f(gridSize, gridSize));
        
        glBindVertexArray(vaoId);
        // Draw RAIN_COUNT drops using Instanced Rendering
        // The vertex shader will calculate the position based on gl_InstanceID and uTime
        glDrawArraysInstanced(GL_TRIANGLE_FAN, 0, 4, RAIN_COUNT);
        glBindVertexArray(0);

        glBindTexture(GL_TEXTURE_2D, 0);
        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }
    
    public void cleanup() {
        glDeleteTextures(heightmapTexId);
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        rainShader.cleanup();
    }
}
