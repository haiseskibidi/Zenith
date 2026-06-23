package com.za.zenith.entities;

import com.za.zenith.entities.parkour.animation.AnimationProfile;
import com.za.zenith.entities.parkour.animation.AnimationRegistry;
import com.za.zenith.world.World;

/**
 * Helper class holding and updating player visual locomotion, lean, fall, and landing states.
 * ponytail: simple state class to offload logic from PlayerViewmodelAnimator and fit line limits.
 */
public class PlayerLocomotionState {
    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private float leanAmount = 0.0f;

    private float locomotionTimer = 0.0f;
    private float movementAlpha = 0.0f;
    private boolean wasOnGround = true;

    private float landingTimer = 1.0f;
    private float landingScale = 0.0f;
    private float landingSide = 1.0f;
    private float fallingTimer = 0.0f;

    private float moveLatchTimer = 0.0f;
    private static final float LATCH_DURATION = 0.15f;

    private float parkourCameraTilt = 0.0f;
    private float parkourCameraRoll = 0.0f;
    private float fovOffset = 0.0f;
    private float cameraOffsetX = 0.0f;
    private float cameraOffsetY = 0.0f;
    private float stepUpVisualOffset = 0.0f;
    private float stepUpPendingHeight = 0.0f;
    private boolean stepUpActiveInTick = false;
    private float currentAlpha = 0.0f;
    private boolean isFirstFrame = true;

    public void update(Player player, float deltaTime, World world, AnimationRegistry animationRegistry) {
        float preUpdateVelocityY = player.getPreUpdateVelocityY();

        // locomotion calculation
        boolean isMovingPhysically = (player.isOnGround() || player.isInWater()) && player.isMoving() && player.getVelocity().lengthSquared() > 0.0001f;
        if (isMovingPhysically) {
            moveLatchTimer = LATCH_DURATION;
        } else {
            moveLatchTimer = Math.max(0, moveLatchTimer - deltaTime);
        }
        
        float alphaTarget = (moveLatchTimer > 0) ? 1.0f : 0.0f;
        movementAlpha += (alphaTarget - movementAlpha) * (player.isSneaking() ? 4.0f : 8.0f) * deltaTime;

        String cN = player.isSneaking() ? "sneak" : ((player.isSprinting() && !player.isInWater()) ? "sprint" : "walk");
        AnimationProfile cp = animationRegistry.get(cN);
        AnimationProfile cip = animationRegistry.get(player.isSneaking() ? "sneak_idle" : "idle");
        
        float iDur = (cip != null) ? cip.getDuration() : 1.0f;
        float wDur = (cp != null) ? cp.getDuration() : 1.0f;
        
        float speedFactor = 1.0f;
        if (player.isSneaking()) {
            float horizontalSpeed = (float) Math.sqrt(player.getVelocity().x * player.getVelocity().x + player.getVelocity().z * player.getVelocity().z);
            float baseSneakSpeed = com.za.zenith.world.physics.PhysicsSettings.getInstance().baseMoveSpeed * 0.35f;
            speedFactor = Math.max(0.2f, horizontalSpeed / baseSneakSpeed);
        }
        
        float currentDuration = iDur + ((wDur / speedFactor) - iDur) * movementAlpha;
        locomotionTimer = (locomotionTimer + deltaTime / currentDuration) % 1.0f;

        // landing check
        if (player.isOnGround() && !wasOnGround && preUpdateVelocityY < -1.5f) {
            landingTimer = 0.0f;
            landingSide = Math.random() > 0.5 ? 1.0f : -1.0f;
            float v = Math.abs(preUpdateVelocityY);
            landingScale = Math.min(1.8f, (v * v) / 220.0f + (v * 0.03f)); 
        }
        wasOnGround = player.isOnGround();

        // increment landing timer based on actual profile duration
        if (landingTimer < 1.0f) {
            AnimationProfile lp = animationRegistry.get("landing");
            float duration = (lp != null) ? lp.getDuration() : 1.0f;
            landingTimer = Math.min(1.0f, landingTimer + deltaTime / duration);
        }

        // step-up visual offset smoothing
        if (player.lastStepUpHeight > 0.0f) {
            this.stepUpActiveInTick = true;
            this.stepUpPendingHeight = player.lastStepUpHeight;
        } else {
            if (this.stepUpActiveInTick) {
                this.stepUpVisualOffset -= this.stepUpPendingHeight;
                this.stepUpActiveInTick = false;
                this.stepUpPendingHeight = 0.0f;
            }
        }

        if (!stepUpActiveInTick) {
            stepUpVisualOffset += (0.0f - stepUpVisualOffset) * 12.0f * deltaTime;
        }

        if (isFirstFrame) {
            lastYaw = player.getRotation().y;
            lastPitch = player.getRotation().x;
            wasOnGround = player.isOnGround();
            isFirstFrame = false;
        }

        // mouse delta & leaning
        float currentYaw = player.getRotation().y;
        float yawDelta = currentYaw - lastYaw;
        while (yawDelta < -Math.PI) yawDelta += Math.PI * 2;
        while (yawDelta > Math.PI) yawDelta -= Math.PI * 2;
        if (Math.abs(yawDelta) < 0.0001f) yawDelta = 0;
        float leanTarget = -yawDelta * 0.8f; 
        if (player.isSprinting()) leanTarget *= 1.2f;
        leanAmount += (leanTarget - leanAmount) * (player.isSneaking() ? 3.0f : 7.0f) * deltaTime;
        lastYaw = currentYaw;

        // falling intensity
        if (!player.isOnGround() && !player.getParkourHandler().isInParkour() && player.getVelocity().y < -2.0f) {
            fallingTimer += deltaTime;
        } else {
            fallingTimer = Math.max(0.0f, fallingTimer - deltaTime * 5.0f);
        }
    }

    public float getLeanAmount() { return leanAmount; }
    public float getLocomotionTimer() { return locomotionTimer; }
    public float getMovementAlpha() { return movementAlpha; }
    public float getLandingTimer() { return landingTimer; }
    public float getLandingScale() { return landingScale; }
    public float getLandingSide() { return landingSide; }
    public float getFallingTimer() { return fallingTimer; }
    public float getYawDelta(float currentYaw) {
        float yawDelta = currentYaw - lastYaw;
        while (yawDelta < -Math.PI) yawDelta += Math.PI * 2;
        while (yawDelta > Math.PI) yawDelta -= Math.PI * 2;
        return (Math.abs(yawDelta) < 0.0001f) ? 0.0f : yawDelta;
    }

    public float getParkourCameraTilt() { return parkourCameraTilt; }
    public void setParkourCameraTilt(float val) { this.parkourCameraTilt = val; }
    public void addParkourCameraTilt(float val) { this.parkourCameraTilt += val; }

    public float getParkourCameraRoll() { return parkourCameraRoll; }
    public void setParkourCameraRoll(float val) { this.parkourCameraRoll = val; }
    public void addParkourCameraRoll(float val) { this.parkourCameraRoll += val; }

    public float getFovOffset() { return fovOffset; }
    public void setFovOffset(float val) { this.fovOffset = val; }
    public void addFovOffset(float val) { this.fovOffset += val; }

    public float getCameraOffsetX() { return cameraOffsetX; }
    public void setCameraOffsetX(float val) { this.cameraOffsetX = val; }
    public void addCameraOffsetX(float val) { this.cameraOffsetX += val; }

    public float getCameraOffsetY() { return cameraOffsetY; }
    public void setCameraOffsetY(float val) { this.cameraOffsetY = val; }
    public void addCameraOffsetY(float val) { this.cameraOffsetY += val; }

    public float getStepUpVisualOffset() { return stepUpVisualOffset; }
    public void addStepUpVisualOffset(float val) { this.stepUpVisualOffset += val; }

    public boolean isStepUpActiveInTick() { return stepUpActiveInTick; }
    public float getStepUpPendingHeight() { return stepUpPendingHeight; }
    public float getCurrentAlpha() { return currentAlpha; }

    public void setStepUpParams(boolean active, float pending, float visualOffset, float alpha) {
        this.stepUpActiveInTick = active;
        this.stepUpPendingHeight = pending;
        this.stepUpVisualOffset = visualOffset;
        this.currentAlpha = alpha;
    }

    public float getLastPitch() { return lastPitch; }
    public void setLastPitch(float pitch) { this.lastPitch = pitch; }
}
