package com.za.zenith.world.particles;

import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.engine.graphics.Shader;
import com.za.zenith.world.World;
import com.za.zenith.world.generation.AtmosphereSettings;
import com.za.zenith.world.generation.BiomeDefinition;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;

public class AmbientParticleManager {
    private static final int MAX_PARTICLES = 300;
    private static final float VISIBILITY_RADIUS = 12.0f;
    private static final int INSTANCE_DATA_SIZE = 12; // PosScale(4) + Visual(4) + ColorSpeed(4)

    private final AmbientParticle[] particles = new AmbientParticle[MAX_PARTICLES];
    private final Random random = new Random();
    private boolean initialized = false;

    // Rendering handles
    private Shader shader;
    private int vaoId;
    private int vboId;
    private int eboId;
    private int instanceVboId;
    private FloatBuffer instanceBuffer;

    private static final float[] QUAD_VERTICES = {
        -0.5f, -0.5f, 0.0f,  0.0f, 1.0f, // BL
         0.5f, -0.5f, 0.0f,  1.0f, 1.0f, // BR
         0.5f,  0.5f, 0.0f,  1.0f, 0.0f, // TR
        -0.5f,  0.5f, 0.0f,  0.0f, 0.0f  // TL
    };

    private static final int[] QUAD_INDICES = { 0, 1, 2, 2, 3, 0 };

    private static class AmbientParticle {
        final Vector3f pos = new Vector3f();
        float scale;
        float age;
        float speed;
        float alpha;
        float sunlight;
        float blocklight;
        final Vector3f color = new Vector3f();
    }

    public void init() {
        if (initialized) return;

        // Initialize particles array (Zero-Alloc pool)
        for (int i = 0; i < MAX_PARTICLES; i++) {
            particles[i] = new AmbientParticle();
        }

        // Load specialized shaders
        shader = new Shader("src/main/resources/shaders/ambient_particle_vertex.glsl", "src/main/resources/shaders/ambient_particle_fragment.glsl");

        // Set up OpenGL VAO, VBO, EBO
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // Quad Vertices
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, QUAD_VERTICES, GL_STATIC_DRAW);

        // aPos (location = 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
        glEnableVertexAttribArray(0);

        // aTexCoord (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
        glEnableVertexAttribArray(1);

        // Quad Indices
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, QUAD_INDICES, GL_STATIC_DRAW);

        // Instanced VBO
        instanceVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVboId);

        // instPosScale (location = 2) - vec4: x, y, z, scale
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, INSTANCE_DATA_SIZE * 4, 0);
        glVertexAttribDivisor(2, 1);

        // instVisual (location = 3) - vec4: sunlight, blocklight, alpha, age
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, INSTANCE_DATA_SIZE * 4, 4 * 4);
        glVertexAttribDivisor(3, 1);

        // instColorSpeed (location = 4) - vec4: r, g, b, speed
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 4, GL_FLOAT, false, INSTANCE_DATA_SIZE * 4, 8 * 4);
        glVertexAttribDivisor(4, 1);

        // Pre-allocate instance buffer on heap to prevent GC
        instanceBuffer = BufferUtils.createFloatBuffer(MAX_PARTICLES * INSTANCE_DATA_SIZE);

        glBindVertexArray(0);
        initialized = true;
    }

    public void update(float deltaTime, Camera camera, World world, BiomeDefinition biome) {
        if (!initialized || biome == null) return;

        AtmosphereSettings.AmbientParticlesSettings settings = biome.getAtmosphere().getAmbientParticles();
        if (!settings.isEnabled()) return;

        Vector3f camPos = camera.getPosition();
        Vector3f camDir = camera.getDirection();

        for (int i = 0; i < MAX_PARTICLES; i++) {
            AmbientParticle p = particles[i];

            // If particle is uninitialized or too far from camera, respawn it in camera frustum sphere
            float distSq = p.pos.distanceSquared(camPos);
            if (p.scale <= 0.0f || distSq > VISIBILITY_RADIUS * VISIBILITY_RADIUS) {
                respawnParticle(p, camPos, camDir, settings);
            }

            // Animate particle position (gentle Brownian drift instead of slow fall)
            float speedMult = settings.getSpeedMultiplier() * p.speed;
            p.pos.x += (float)Math.sin(p.age * 0.7f) * 0.08f * deltaTime * p.speed;
            p.pos.y += (float)Math.cos(p.age * 0.5f) * 0.05f * deltaTime * p.speed;
            p.pos.z += (float)Math.sin(p.age * 0.4f) * 0.08f * deltaTime * p.speed;
            p.age += deltaTime * speedMult;

            // Sample voxel lighting (Sunlight & Blocklight)
            int px = (int) Math.floor(p.pos.x);
            int py = (int) Math.floor(p.pos.y);
            int pz = (int) Math.floor(p.pos.z);

            if (py >= 0 && py < 512) {
                com.za.zenith.world.chunks.Chunk chunk = world.getChunkInternal(px >> 4, pz >> 4);
                if (chunk != null) {
                    p.sunlight = chunk.getSunlight(px & 15, py, pz & 15);
                    p.blocklight = chunk.getBlockLight(px & 15, py, pz & 15);
                } else {
                    p.sunlight = 15.0f;
                    p.blocklight = 0.0f;
                }
            } else {
                p.sunlight = 15.0f;
                p.blocklight = 0.0f;
            }

            // Smoothly fade in/out alpha
            if (p.alpha < 1.0f) {
                p.alpha = Math.min(1.0f, p.alpha + deltaTime * 1.5f);
            }
        }
    }

    private void respawnParticle(AmbientParticle p, Vector3f camPos, Vector3f camDir, AtmosphereSettings.AmbientParticlesSettings settings) {
        // Generate random offset within visibility sphere (front-biased)
        float theta = random.nextFloat() * (float) Math.PI * 2.0f;
        float phi = (float) Math.acos(2.0f * random.nextFloat() - 1.0f);
        float r = random.nextFloat() * VISIBILITY_RADIUS;

        float x = r * (float) Math.sin(phi) * (float) Math.cos(theta);
        float y = r * (float) Math.sin(phi) * (float) Math.sin(theta);
        float z = r * (float) Math.cos(phi);

        // Biased towards where the camera is facing
        p.pos.set(camPos.x + camDir.x * 3.0f + x, camPos.y + y, camPos.z + camDir.z * 3.0f + z);

        float[] scales = settings.getScaleRange();
        p.scale = scales[0] + random.nextFloat() * (scales[1] - scales[0]);
        p.age = random.nextFloat() * 100.0f;
        p.speed = 0.4f + random.nextFloat() * 0.6f;
        p.alpha = 0.0f; // Start faded out

        float[] colors = settings.getGlowingColor();
        p.color.set(colors[0], colors[1], colors[2]);

        p.sunlight = 15.0f;
        p.blocklight = 0.0f;
    }

    public void render(Camera camera, BiomeDefinition biome) {
        if (!initialized || biome == null) return;

        AtmosphereSettings.AmbientParticlesSettings settings = biome.getAtmosphere().getAmbientParticles();
        if (!settings.isEnabled()) return;

        shader.use();
        shader.setLights("uLights", com.za.zenith.world.lighting.LightManager.getActiveLights());

        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, instanceVboId);

        // Populate heap buffer (Zero-Alloc)
        instanceBuffer.clear();
        int activeCount = 0;

        for (int i = 0; i < MAX_PARTICLES; i++) {
            AmbientParticle p = particles[i];
            if (p.scale <= 0.0f) continue;

            // instPosScale (4)
            instanceBuffer.put(p.pos.x).put(p.pos.y).put(p.pos.z).put(p.scale);
            // instVisual (4)
            instanceBuffer.put(p.sunlight).put(p.blocklight).put(p.alpha).put(p.age);
            // instColorSpeed (4)
            instanceBuffer.put(p.color.x).put(p.color.y).put(p.color.z).put(p.speed);

            activeCount++;
        }

        instanceBuffer.flip();
        glBufferData(GL_ARRAY_BUFFER, instanceBuffer, GL_DYNAMIC_DRAW);

        glDrawElementsInstanced(GL_TRIANGLES, QUAD_INDICES.length, GL_UNSIGNED_INT, 0, activeCount);

        glBindVertexArray(0);
        glEnable(GL_CULL_FACE);
    }

    public void cleanup() {
        if (!initialized) return;
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteBuffers(instanceVboId);
        glDeleteVertexArrays(vaoId);
        if (shader != null) shader.cleanup();
        initialized = false;
    }
}
