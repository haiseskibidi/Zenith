package com.za.zenith.engine.graphics;

import com.za.zenith.utils.Logger;
import com.za.zenith.world.World;
import com.za.zenith.world.WorldSettings;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;

/**
 * RenderContext manages global shader data using Uniform Buffer Objects (UBO)
 * and provides object pools for JOML objects to ensure Zero Alloc rendering.
 */
public class RenderContext {
    public static final int GLOBAL_BINDING_POINT = 0;
    private static int uboId;
    private static final FloatBuffer uboBuffer = BufferUtils.createFloatBuffer(64); 

    // Scene Data
    private static float time;
    private static final Vector3f sunDirection = new Vector3f();
    private static final Vector3f ambientColor = new Vector3f();
    private static final Matrix4f viewMatrix = new Matrix4f();
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static final Vector3f cameraPos = new Vector3f();
    private static final Vector3f grassColor = new Vector3f();
    private static boolean isNight;

    // Zero Alloc Pools
    private static final Matrix4f[] matrixPool = new Matrix4f[256];
    private static final Vector3f[] vectorPool = new Vector3f[512];
    private static int matrixIdx = 0;
    private static int vectorIdx = 0;

    static {
        for (int i = 0; i < matrixPool.length; i++) matrixPool[i] = new Matrix4f();
        for (int i = 0; i < vectorPool.length; i++) vectorPool[i] = new Vector3f();
    }

    /**
     * Returns a pooled Matrix4f initialized to identity.
     */
    public static Matrix4f getMatrix() {
        return matrixPool[matrixIdx++ % matrixPool.length].identity();
    }

    /**
     * Returns a pooled Vector3f initialized to zero.
     */
    public static Vector3f getVector() {
        return vectorPool[vectorIdx++ % vectorPool.length].set(0);
    }

    public static void init() {
        uboId = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, uboId);
        // Size: 2 * 64 (matrices) + 4 * 16 (vectors/floats with padding) = 192 bytes
        glBufferData(GL_UNIFORM_BUFFER, 256, GL_DYNAMIC_DRAW); 
        glBindBufferBase(GL_UNIFORM_BUFFER, GLOBAL_BINDING_POINT, uboId);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    public static void update(World world, Camera camera, float alpha, Vector3f lightDir, Vector3f ambient, boolean isNightFlag) {
        time = (float) (org.lwjgl.glfw.GLFW.glfwGetTime() % 3600.0);
        sunDirection.set(lightDir);
        ambientColor.set(ambient);
        viewMatrix.set(camera.getViewMatrix(alpha));
        projectionMatrix.set(camera.getProjectionMatrix());
        cameraPos.set(camera.getPosition());
        grassColor.set(com.za.zenith.engine.graphics.ColorProvider.getGrassColor());
        isNight = isNightFlag;

        sync();
    }

    private static void sync() {
        uboBuffer.clear();
        
        // 0-15: Projection Matrix
        projectionMatrix.get(0, uboBuffer);
        
        // 16-31: View Matrix
        viewMatrix.get(16, uboBuffer);
        
        // 32-35: Camera Data (xyz=pos, w=0)
        uboBuffer.put(32, cameraPos.x);
        uboBuffer.put(33, cameraPos.y);
        uboBuffer.put(34, cameraPos.z);
        uboBuffer.put(35, 0.0f);
        
        // 36-39: Sun Data (xyz=dir, w=time)
        uboBuffer.put(36, sunDirection.x);
        uboBuffer.put(37, sunDirection.y);
        uboBuffer.put(38, sunDirection.z);
        uboBuffer.put(39, time);
        
        // 40-43: Ambient Data (xyz=color, w=isNight)
        uboBuffer.put(40, ambientColor.x);
        uboBuffer.put(41, ambientColor.y);
        uboBuffer.put(42, ambientColor.z);
        uboBuffer.put(43, isNight ? 1.0f : 0.0f);
        
        // 44-47: Grass Data (xyz=color, w=0)
        uboBuffer.put(44, grassColor.x);
        uboBuffer.put(45, grassColor.y);
        uboBuffer.put(46, grassColor.z);
        uboBuffer.put(47, 0.0f);
 
        // Limit the buffer to the used portion
        uboBuffer.position(0);
        uboBuffer.limit(48);

        glBindBuffer(GL_UNIFORM_BUFFER, uboId);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, uboBuffer);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    public static void bindShader(Shader shader) {
        int blockIndex = glGetUniformBlockIndex(shader.getProgramId(), "GlobalData");
        if (blockIndex != -1) {
            glUniformBlockBinding(shader.getProgramId(), blockIndex, GLOBAL_BINDING_POINT);
        }
    }

    /**
     * Resets block shader flags to a clean baseline state.
     * Prevents state leakage between different rendering passes.
     */
    public static void resetBlockShader(Shader shader) {
        shader.use();
        shader.setBoolean("uIsBatch", false);
        shader.setBoolean("uIsCompressed", false);
        shader.setBoolean("uIsProxy", false);
        shader.setBoolean("useMask", false);
        shader.setBoolean("previewPass", false);
        shader.setBoolean("isHand", false);
        shader.setFloat("brightnessMultiplier", 1.0f);
        shader.setInt("highlightPass", 0);
        shader.setInt("uHitCount", 0);
        shader.setFloat("uBreakingProgress", 0.0f);
        shader.setInt("uBreakingPattern", 0);
        shader.setFloat("uSwayOverride", -1.0f);
        shader.setVector3f("uOverrideLight", -1.0f, -1.0f, -1.0f);
        shader.setFloat("uChunkSpawnTime", -100.0f);
    }

    public static void cleanup() {
        glDeleteBuffers(uboId);
    }
}
