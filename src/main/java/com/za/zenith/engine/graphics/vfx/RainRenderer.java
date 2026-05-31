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
    
    // Using a fixed count of virtual "cells" around the player
    private static final int RAIN_COUNT = 3000; 

    public RainRenderer() {
        this.rainShader = new Shader("src/main/resources/shaders/rain_vertex.glsl", "src/main/resources/shaders/rain_fragment.glsl");
        
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

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glDepthMask(false);
        
        rainShader.use();
        rainShader.setFloat("uRainIntensity", rainIntensity);
        
        // Use standard RenderContext data via UBO (Projection/View/Time is inside)
        
        glBindVertexArray(vaoId);
        // Draw RAIN_COUNT drops using Instanced Rendering
        // The vertex shader will calculate the position based on gl_InstanceID and uTime
        glDrawArraysInstanced(GL_TRIANGLE_FAN, 0, 4, RAIN_COUNT);
        glBindVertexArray(0);

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }
    
    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        rainShader.cleanup();
    }
}
