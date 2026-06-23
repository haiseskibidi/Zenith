package com.za.zenith.entities;

import com.za.zenith.engine.graphics.model.*;
import com.za.zenith.entities.parkour.animation.AnimationRegistry;
import com.za.zenith.entities.parkour.animation.AnimationProfile;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.World;
import com.za.zenith.utils.Identifier;

/**
 * Coordinates visual locomotion states and delegates bone hierarchy updates.
 * ponytail: minimized design delegation to fit the 250-line requirement.
 */
public class PlayerViewmodelAnimator {
    private final PlayerLocomotionState locomotionState = new PlayerLocomotionState();
    private final ViewmodelBoneProcessor boneProcessor = new ViewmodelBoneProcessor();
    private final AnimationRegistry animationRegistry = new AnimationRegistry();
    private Viewmodel viewmodel;

    // item animation state
    private boolean swinging = false;
    private String currentSwingAnim = "swing";
    private float itemSwingTimer = 0.0f;
    private float itemOffsetX = 0.0f;
    private float itemOffsetY = 0.0f;
    private float itemOffsetZ = 0.0f;
    private float itemPitchOffset = 0.0f;
    private float itemYawOffset = 0.0f;
    private float itemRollOffset = 0.0f;
    private float itemSwingDuration = 0.5f;
    private float parkourWeight = 0.0f; 
    private boolean isFirstFrame = true;

    public PlayerViewmodelAnimator() {
        ViewmodelDefinition handsDef = ModelRegistry.getViewmodel(Identifier.of("zenith:hands"));
        if (handsDef != null) {
            this.viewmodel = new Viewmodel(handsDef);
        }
    }

    public Viewmodel getViewmodel() { return viewmodel; }

    public void update(Player player, float deltaTime, World world) {
        locomotionState.update(player, deltaTime, world, animationRegistry);
        updateAnimations(player, deltaTime, world);
    }

    private void updateAnimations(Player player, float deltaTime, World world) {
        deltaTime = Math.min(deltaTime, 0.05f);

        float leanAmount = locomotionState.getLeanAmount();
        float fallingTimer = locomotionState.getFallingTimer();

        // calculate falling intensity
        float fallIntensity = 0.0f;
        if (!player.isOnGround() && !player.getParkourHandler().isInParkour() && player.getVelocity().y < -2.0f) {
            fallIntensity = Math.min(1.0f, (Math.abs(player.getVelocity().y) - 2.0f) / 25.0f);
        }

        Item heldItem = player.getInventory().getSelectedItem();
        float horizontalSpeed = (float) Math.sqrt(player.getVelocity().x * player.getVelocity().x + player.getVelocity().z * player.getVelocity().z);
        float maxSneakSpeed = com.za.zenith.world.physics.PhysicsSettings.getInstance().baseMoveSpeed * 0.35f;
        float speedIntensity = Math.min(1.0f, horizontalSpeed / maxSneakSpeed);

        String parkourStateName = (player.getParkourHandler().getState() == com.za.zenith.entities.parkour.ParkourHandler.ParkourState.CLIMBING) ? "climbing" : "grabbing";
        this.parkourWeight = player.getParkourHandler().isInParkour() ? 1.0f : 0.0f;
        
        PlayerAnimationProfileEvaluator.AnimatedFrameState eval = PlayerAnimationProfileEvaluator.evaluate(
            animationRegistry, heldItem, player.isSneaking(), player.isSprinting(), player.isInWater(),
            locomotionState.getLocomotionTimer(), locomotionState.getMovementAlpha(), fallingTimer, fallIntensity, speedIntensity, leanAmount,
            parkourWeight, player.getParkourHandler().getProgress(), player.getParkourHandler().getClimbSide(),
            locomotionState.getLandingTimer(), locomotionState.getLandingSide(), locomotionState.getLandingScale(), parkourStateName
        );

        float targetTilt = eval.cameraTilt;
        float targetRoll = eval.cameraRoll;
        float tCamY = eval.cameraY;
        float tCamX = eval.cameraX;
        float targetFov = eval.fov;
        float tItX = eval.itemX;
        float tItY = eval.itemY;
        float tItZ = eval.itemZ;
        float tItP = eval.itemPitch;
        float tItYw = eval.itemYaw;
        float tItR = eval.itemRoll;

        // swing evaluation
        float swingPosX = 0f, swingPosY = 0f, swingPosZ = 0f;
        float swingPitch = 0f, swingYaw = 0f, swingRoll = 0f;

        if (swinging) {
            String swingKey = "item_" + currentSwingAnim;
            String sN = heldItem != null ? heldItem.getAnimation(swingKey) : swingKey.replace("item_", "hand_");
            AnimationProfile swingAnim = animationRegistry.get(sN);
            if (swingAnim != null) {
                itemSwingTimer += deltaTime / itemSwingDuration; 
                if (itemSwingTimer >= 1.0f) { swinging = false; itemSwingTimer = 0; }
                else {
                    swingPosX = swingAnim.evaluate("item_x", itemSwingTimer, 1.0f) / 16.0f;
                    swingPosY = swingAnim.evaluate("item_y", itemSwingTimer, 1.0f) / 16.0f;
                    swingPosZ = swingAnim.evaluate("item_z", itemSwingTimer, 1.0f) / 16.0f;
                    swingPitch = (float)Math.toRadians(swingAnim.evaluate("item_pitch", itemSwingTimer, 1.0f));
                    swingYaw = (float)Math.toRadians(swingAnim.evaluate("item_yaw", itemSwingTimer, 1.0f));
                    swingRoll = (float)Math.toRadians(swingAnim.evaluate("item_roll", itemSwingTimer, 1.0f));
                    targetTilt += swingAnim.evaluate("camera_tilt", itemSwingTimer, 1.0f);
                    targetRoll += swingAnim.evaluate("camera_roll", itemSwingTimer, 1.0f);
                }
            } else swinging = false;
        }

        // swimming custom movement adjustments
        if (player.isInWater()) {
            float swimCycle = locomotionState.getLocomotionTimer() * (float)Math.PI * 2.0f;
            targetTilt += 0.12f * locomotionState.getMovementAlpha();
            targetRoll += (float)Math.cos(swimCycle) * 0.015f * locomotionState.getMovementAlpha();
            tCamY += (float)Math.sin(swimCycle) * 0.02f * locomotionState.getMovementAlpha();
            tItY += (float)Math.sin(swimCycle) * 0.03f * locomotionState.getMovementAlpha();
            tItX += (float)Math.cos(swimCycle) * 0.02f * locomotionState.getMovementAlpha();
            tItP += 0.12f * locomotionState.getMovementAlpha() + (float)Math.cos(swimCycle) * 0.04f * locomotionState.getMovementAlpha();
            tItR += (float)Math.sin(swimCycle) * 0.03f * locomotionState.getMovementAlpha();
        }

        // sync visual target weights
        float syncLerp = 12.0f;
        if (isFirstFrame) {
            locomotionState.setParkourCameraTilt(targetTilt);
            locomotionState.setParkourCameraRoll(targetRoll);
            locomotionState.setFovOffset(targetFov);
            locomotionState.setCameraOffsetY(tCamY);
            locomotionState.setCameraOffsetX(tCamX);
            itemOffsetX = tItX + (leanAmount * 0.1f);
            itemOffsetY = tItY;
            itemOffsetZ = tItZ;
            itemPitchOffset = tItP;
            itemYawOffset = tItYw;
            itemRollOffset = tItR + (leanAmount * 0.5f);
            isFirstFrame = false;
        }
        locomotionState.addParkourCameraTilt((targetTilt - locomotionState.getParkourCameraTilt()) * syncLerp * deltaTime);
        locomotionState.addParkourCameraRoll((targetRoll - locomotionState.getParkourCameraRoll()) * syncLerp * deltaTime);
        locomotionState.addFovOffset((targetFov - locomotionState.getFovOffset()) * 4.0f * deltaTime);
        locomotionState.addCameraOffsetY((tCamY - locomotionState.getCameraOffsetY()) * syncLerp * deltaTime);
        locomotionState.addCameraOffsetX((tCamX - locomotionState.getCameraOffsetX()) * syncLerp * deltaTime);
        itemOffsetX += (tItX + (leanAmount * 0.1f) - itemOffsetX) * syncLerp * deltaTime;
        itemOffsetY += (tItY - itemOffsetY) * syncLerp * deltaTime;
        itemOffsetZ += (tItZ - itemOffsetZ) * syncLerp * deltaTime;
        itemPitchOffset += (tItP - itemPitchOffset) * syncLerp * deltaTime;
        itemYawOffset += (tItYw - itemYawOffset) * syncLerp * deltaTime;
        itemRollOffset += (tItR + (leanAmount * 0.5f) - itemRollOffset) * syncLerp * deltaTime;

        // delegate bone hierarchy processing
        boneProcessor.processBones(
            player, deltaTime, world, viewmodel, animationRegistry, locomotionState,
            itemOffsetX, itemOffsetY, itemOffsetZ, itemPitchOffset, itemYawOffset, itemRollOffset,
            swinging, currentSwingAnim, itemSwingTimer, swingPosX, swingPosY, swingPosZ,
            swingPitch, swingYaw, swingRoll, eval, parkourWeight, parkourStateName
        );
    }

    public void swing() { swing(0.35f); }
    public void swing(float duration) { 
        if (!swinging) { swinging = true; itemSwingTimer = 0; itemSwingDuration = duration; currentSwingAnim = "swing"; } 
    }
    
    public void interact() { interact(0.25f); }
    public void interact(float duration) {
        if (!swinging) { swinging = true; itemSwingTimer = 0; itemSwingDuration = duration; currentSwingAnim = "pickup"; }
    }

    public void place() { place(0.25f); }
    public void place(float duration) {
        if (!swinging) { swinging = true; itemSwingTimer = 0; itemSwingDuration = duration; currentSwingAnim = "place"; }
    }

    public boolean isSwinging() { return swinging; }
    public float getCameraPitchOffset() { return locomotionState.getParkourCameraTilt(); }
    public float getCameraRollOffset() { return locomotionState.getParkourCameraRoll(); }
    public float getFovOffset() { return locomotionState.getFovOffset(); }
    public float getCameraOffsetX() { return locomotionState.getCameraOffsetX(); }
    public float getCameraOffsetY() { 
        float offset = locomotionState.getStepUpVisualOffset();
        if (locomotionState.isStepUpActiveInTick()) {
            offset -= locomotionState.getStepUpPendingHeight() * locomotionState.getCurrentAlpha();
        }
        return locomotionState.getCameraOffsetY() + offset; 
    }
    
    public float getItemOffsetX() { return itemOffsetX; }
    public float getItemOffsetY() { return itemOffsetY; }
    public float getItemOffsetZ() { return itemOffsetZ; }
    public float getItemPitchOffset() { return itemPitchOffset; }
    public float getItemYawOffset() { return itemYawOffset; }
    public float getItemRollOffset() { return itemRollOffset; }

    public void setStepUpParams(boolean active, float pending, float visualOffset, float alpha) {
        locomotionState.setStepUpParams(active, pending, visualOffset, alpha);
    }

    public void addStepUpVisualOffset(float val) {
        locomotionState.addStepUpVisualOffset(val);
    }

    public void setCurrentAlpha(float alpha) {
        locomotionState.setStepUpParams(
            locomotionState.isStepUpActiveInTick(),
            locomotionState.getStepUpPendingHeight(),
            locomotionState.getStepUpVisualOffset(),
            alpha
        );
    }
}
