package com.za.zenith.engine.graphics;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Matrix4f;

public class ShaderStateManager {
    private Shader activeShader;

    public void bind(Shader shader) {
        this.activeShader = shader;
        if (shader != null) {
            shader.use();
        }
    }

    public Shader getActiveShader() {
        return activeShader;
    }

    public void resetToBaseline() {
        if (activeShader == null) return;
        activeShader.setBoolean("uIsBatch", false);
        activeShader.setBoolean("uIsCompressed", false);
        activeShader.setBoolean("uIsProxy", false);
        activeShader.setBoolean("useMask", false);
        activeShader.setBoolean("previewPass", false);
        activeShader.setBoolean("isHand", false);
        activeShader.setFloat("brightnessMultiplier", 1.0f);
        activeShader.setInt("highlightPass", 0);
        activeShader.setInt("uHitCount", 0);
        activeShader.setFloat("uBreakingProgress", 0.0f);
        activeShader.setInt("uBreakingPattern", 0);
        activeShader.setFloat("uSwayOverride", -1.0f);
        activeShader.setVector3f("uOverrideLight", -1.0f, -1.0f, -1.0f);
        activeShader.setFloat("uChunkSpawnTime", -100.0f);
    }

    public void setBoolean(String name, boolean value) {
        if (activeShader != null) activeShader.setBoolean(name, value);
    }

    public void setFloat(String name, float value) {
        if (activeShader != null) activeShader.setFloat(name, value);
    }

    public void setInt(String name, int value) {
        if (activeShader != null) activeShader.setInt(name, value);
    }

    public void setVector3f(String name, Vector3f value) {
        if (activeShader != null) activeShader.setVector3f(name, value);
    }

    public void setVector3f(String name, float x, float y, float z) {
        if (activeShader != null) activeShader.setVector3f(name, x, y, z);
    }

    public void setVector4f(String name, Vector4f value) {
        if (activeShader != null) activeShader.setVector4f(name, value);
    }

    public void setMatrix4f(String name, Matrix4f value) {
        if (activeShader != null) activeShader.setMatrix4f(name, value);
    }
}
