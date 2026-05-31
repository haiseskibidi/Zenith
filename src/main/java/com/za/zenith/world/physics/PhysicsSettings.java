package com.za.zenith.world.physics;

/**
 * Global physics and player configuration loaded from JSON.
 */
public class PhysicsSettings implements com.za.zenith.utils.LiveReloadable {
    private static PhysicsSettings instance = new PhysicsSettings();
    private transient String sourcePath;

    @Override
    public String getSourcePath() { return sourcePath; }

    @Override
    public void setSourcePath(String path) { this.sourcePath = path; }

    @Override
    public void onLiveReload() {
        com.za.zenith.utils.Logger.info("PhysicsSettings: Applied live changes");
    }

    // Player Dimensions
    public float playerWidth = 0.6f;
    public float standingHeight = 1.8f;
    public float sneakingHeight = 1.45f;
    public float standingEyeHeight = 1.62f;
    public float sneakingEyeHeight = 1.25f;

    // Movement Multipliers
    public float sprintMultiplier = 1.85f;
    public float sneakSpeedMultiplier = 0.45f;
    public float flySprintMultiplier = 2.0f;

    // Parkour
    public float maxGrabHeight = 2.45f;
    public float minGrabHeight = 1.25f;
    public float grabDistance = 0.7f;
    public float climbDuration = 0.28f;

    // Base Physics
    public float jumpVelocity = 11.5f;
    public float baseMoveSpeed = 2.4f;
    public float flySpeed = 10.0f;
    
    public float baseMiningCooldown = 0.5f;

    // Input
    public float mouseSensitivity = 0.002f;

    // Items
    public float itemAttractionRadius = 3.2f;
    public float itemPickupRadius = 1.5f;
    public float itemAttractionForce = 12.0f;
    public float itemMergeRadius = 1.2f;
    public float itemMergeInterval = 0.5f;
    public float itemDespawnTime = 300.0f;

    // Particles
    public int particleLimit = 1500;
    public int particleBudget = 300;

    public static PhysicsSettings getInstance() {
        return instance;
    }

    public static void setInstance(PhysicsSettings newSettings) {
        instance = newSettings;
    }
}


