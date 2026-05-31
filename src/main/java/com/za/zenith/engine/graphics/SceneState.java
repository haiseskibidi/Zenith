package com.za.zenith.engine.graphics;

import com.za.zenith.world.World;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * SceneState encapsulates all global data for a single frame.
 * It is updated once per frame by the RenderPipeline.
 */
public class SceneState {
    private Camera camera;
    private World world;
    private float alpha;
    private float deltaTime;
    
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f frustumMatrix = new Matrix4f();
    private final FrustumIntersection frustum = new FrustumIntersection();
    
    private final Vector3f lightDirection = new Vector3f();
    private final Vector3f sunDirection = new Vector3f();
    private final Vector3f ambientLight = new Vector3f();
    private final Vector3f cameraPos = new Vector3f();
    
    private float time;
    private long frameCounter;

    public SceneState(Camera camera, World world, float alpha, float deltaTime) {
        update(camera, world, alpha, deltaTime);
    }

    public void update(Camera camera, World world, float alpha, float deltaTime) {
        this.camera = camera;
        this.world = world;
        this.alpha = alpha;
        this.deltaTime = deltaTime;
        
        if (camera != null) {
            // Initialize from camera/world
            this.projectionMatrix.set(camera.getProjectionMatrix());
            this.viewMatrix.set(camera.getViewMatrix(alpha));
            this.frustumMatrix.set(projectionMatrix).mul(viewMatrix);
            this.frustum.set(frustumMatrix);
            
            this.cameraPos.set(camera.getPosition());
        }
        this.time = (float) (org.lwjgl.glfw.GLFW.glfwGetTime() % 3600.0);
    }

    public void updateLights(Vector3f direction, Vector3f ambient) {
        this.lightDirection.set(direction);
        this.ambientLight.set(ambient);
    }

    public void updateSunDirection(Vector3f direction) {
        this.sunDirection.set(direction);
    }

    public void setFrameCounter(long counter) {
        this.frameCounter = counter;
    }

    // Getters
    public Camera getCamera() { return camera; }
    public World getWorld() { return world; }
    public float getAlpha() { return alpha; }
    public float getDeltaTime() { return deltaTime; }
    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getViewMatrix() { return viewMatrix; }
    public Matrix4f getFrustumMatrix() { return frustumMatrix; }
    public FrustumIntersection getFrustum() { return frustum; }
    public Vector3f getLightDirection() { return lightDirection; }
    public Vector3f getSunDirection() { return sunDirection; }
    public Vector3f getAmbientLight() { return ambientLight; }
    public Vector3f getCameraPos() { return cameraPos; }
    public float getTime() { return time; }
    public long getFrameCounter() { return frameCounter; }
}
