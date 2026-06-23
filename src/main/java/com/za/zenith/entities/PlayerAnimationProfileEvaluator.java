package com.za.zenith.entities;

import com.za.zenith.entities.parkour.animation.AnimationRegistry;
import com.za.zenith.entities.parkour.animation.AnimationProfile;
import com.za.zenith.world.items.Item;

/**
 * Evaluates movement, falling, landing and parkour animation profiles to compute camera and item offsets.
 */
public class PlayerAnimationProfileEvaluator {

    public static class AnimatedFrameState {
        public float cameraTilt;
        public float cameraRoll;
        public float cameraY;
        public float cameraX;
        public float fov;
        public float itemX;
        public float itemY;
        public float itemZ;
        public float itemPitch;
        public float itemYaw;
        public float itemRoll;
        public float landingItY;
        public float landingItP;
        public float landingItR;
    }

    public static AnimatedFrameState evaluate(
        AnimationRegistry animationRegistry,
        Item heldItem,
        boolean sneaking,
        boolean sprinting,
        boolean inWater,
        float locomotionTimer,
        float movementAlpha,
        float fallingTimer,
        float fallIntensity,
        float speedIntensity,
        float leanAmount,
        float parkourWeight,
        float parkourProgress,
        float parkourClimbSide,
        float landingTimer,
        float landingSide,
        float landingScale,
        String parkourStateName
    ) {
        AnimatedFrameState state = new AnimatedFrameState();

        String cN = sneaking ? "sneak" : ((sprinting && !inWater) ? "sprint" : "walk");
        String iN = heldItem != null ? heldItem.getAnimation(sneaking ? "item_sneak" : ((sprinting && !inWater) ? "item_sprint" : "item_walk")) : (sneaking ? "item_sneak" : ((sprinting && !inWater) ? "item_sprint" : "item_walk"));
        String iiN = heldItem != null ? heldItem.getAnimation("item_idle") : "item_idle";

        AnimationProfile cp = animationRegistry.get(cN);
        AnimationProfile ip = animationRegistry.get(iN);
        AnimationProfile cip = animationRegistry.get(sneaking ? "sneak_idle" : "idle");
        AnimationProfile iip = animationRegistry.get(iiN);
        AnimationProfile fp = animationRegistry.get("falling");

        float wTilt = (cp != null) ? cp.evaluate("camera_tilt", locomotionTimer, 1.0f) : 0;
        float wRoll = (cp != null) ? cp.evaluate("camera_roll", locomotionTimer, 1.0f) : 0;
        float wFov = (cp != null) ? cp.evaluate("fov_offset", locomotionTimer, 1.0f) : 0;
        float wCamY = (cp != null) ? cp.evaluate("camera_y", locomotionTimer, 1.0f) : 0;
        float wItX = (ip != null) ? ip.evaluate("item_x", locomotionTimer, 1.0f) : 0;
        float wItY = (ip != null) ? ip.evaluate("item_y", locomotionTimer, 1.0f) : 0;
        float wItZ = (ip != null) ? ip.evaluate("item_z", locomotionTimer, 1.0f) : 0;
        float wItP = (ip != null) ? ip.evaluate("item_pitch", locomotionTimer, 1.0f) : 0;
        float wItR = (ip != null) ? ip.evaluate("item_roll", locomotionTimer, 1.0f) : 0;

        float iTilt = (cip != null) ? cip.evaluate("camera_tilt", locomotionTimer, 1.0f) : 0;
        float iRoll = (cip != null) ? cip.evaluate("camera_roll", locomotionTimer, 1.0f) : 0;
        float iCamY = (cip != null) ? cip.evaluate("camera_y", locomotionTimer, 1.0f) : 0;
        float iCamX = (cip != null) ? cip.evaluate("camera_x", locomotionTimer, 1.0f) : 0;
        float iItX = (iip != null) ? iip.evaluate("item_x", sneaking ? 0.0f : locomotionTimer, 1.0f) : 0;
        float iItY = (iip != null) ? iip.evaluate("item_y", locomotionTimer, 1.0f) : 0;
        float iItZ = (iip != null) ? iip.evaluate("item_z", locomotionTimer, 1.0f) : 0;
        float iItP = (iip != null) ? iip.evaluate("item_pitch", locomotionTimer, 1.0f) : 0;
        float iItR = (iip != null) ? iip.evaluate("item_roll", sneaking ? 0.0f : locomotionTimer, 1.0f) : 0;

        float fTilt = (fp != null) ? fp.evaluate("camera_tilt", fallingTimer, 1.0f) : 0;
        float fCamY = (fp != null) ? fp.evaluate("camera_y", fallingTimer, 1.0f) : 0;
        float fFov = (fp != null) ? fp.evaluate("fov_offset", fallingTimer, 1.0f) : 0;
        float fItY = (fp != null) ? fp.evaluate("item_y", fallingTimer, 1.0f) : 0;
        float fItZ = (fp != null) ? fp.evaluate("item_z", fallingTimer, 1.0f) : 0;
        float fItP = (fp != null) ? fp.evaluate("item_pitch", fallingTimer, 1.0f) : 0;

        state.fov = (wFov * movementAlpha) + (fFov * fallIntensity);

        if (sneaking) {
            float breathWeight = 0.4f + (speedIntensity * 0.6f);
            float walkWeight = speedIntensity; 
            state.cameraTilt = iTilt + (wTilt - iTilt) * breathWeight + fTilt * fallIntensity;
            state.cameraRoll = (iRoll + (wRoll - iRoll) * breathWeight) + leanAmount;
            state.cameraY = iCamY + (wCamY - iCamY) * breathWeight + fCamY * fallIntensity;
            state.cameraX = iCamX * walkWeight; 
            state.itemX = iItX + (wItX - iItX) * walkWeight;
            state.itemY = iItY + (wItY - iItY) * breathWeight + fItY * fallIntensity;
            state.itemZ = iItZ + (wItZ - iItZ) * breathWeight + fItZ * fallIntensity;
            state.itemPitch = iItP + (wItP - iItP) * breathWeight + fItP * fallIntensity;
            state.itemYaw = (leanAmount * 0.2f);
            state.itemRoll = iItR + (wItR - iItR) * walkWeight;
        } else {
            state.cameraTilt = iTilt + (wTilt - iTilt) * movementAlpha + fTilt * fallIntensity;
            state.cameraRoll = (iRoll + (wRoll - iRoll) * movementAlpha) + leanAmount;
            state.cameraY = iCamY + (wCamY - iCamY) * movementAlpha + fCamY * fallIntensity;
            state.cameraX = 0; 
            state.itemX = iItX + (wItX - iItX) * movementAlpha;
            state.itemY = iItY + (wItY - iItY) * movementAlpha + fItY * fallIntensity;
            state.itemZ = iItZ + (wItZ - iItZ) * movementAlpha + fItZ * fallIntensity;
            state.itemPitch = iItP + (wItP - iItP) * movementAlpha + fItP * fallIntensity;
            state.itemYaw = (leanAmount * 0.2f);
            state.itemRoll = iItR + (wItR - iItR) * movementAlpha;
        }

        // Procedural Wind Shake for hands during fall
        if (fallIntensity > 0.01f) {
            float shakeFreq = 45.0f;
            float shake = (float) Math.sin(fallingTimer * shakeFreq) * 0.02f * fallIntensity;
            state.itemY += shake;
            state.itemX += (float) Math.cos(fallingTimer * shakeFreq * 0.8f) * 0.01f * fallIntensity;
        }

        // Parkour blending
        if (parkourWeight > 0.001f) {
            AnimationProfile pp = animationRegistry.get(parkourStateName);
            if (pp != null) {
                state.cameraTilt = state.cameraTilt + (pp.evaluate("camera_tilt", parkourProgress, parkourClimbSide) - state.cameraTilt) * parkourWeight;
                state.cameraRoll = state.cameraRoll + (pp.evaluate("camera_roll", parkourProgress, parkourClimbSide) - state.cameraRoll) * parkourWeight;
                state.cameraY = state.cameraY + (pp.evaluate("camera_y", parkourProgress, parkourClimbSide) - state.cameraY) * parkourWeight;
                state.cameraX = state.cameraX + (pp.evaluate("camera_x", parkourProgress, parkourClimbSide) - state.cameraX) * parkourWeight;
                state.fov = state.fov + (pp.evaluate("fov_offset", parkourProgress, parkourClimbSide) - state.fov) * parkourWeight;
                state.itemX = state.itemX + (pp.evaluate("item_x", parkourProgress, parkourClimbSide) - state.itemX) * parkourWeight;
                state.itemY = state.itemY + (pp.evaluate("item_y", parkourProgress, parkourClimbSide) - state.itemY) * parkourWeight;
                state.itemZ = state.itemZ + (pp.evaluate("item_z", parkourProgress, parkourClimbSide) - state.itemZ) * parkourWeight;
                state.itemPitch = state.itemPitch + (pp.evaluate("item_pitch", parkourProgress, parkourClimbSide) - state.itemPitch) * parkourWeight;
                state.itemYaw = state.itemYaw + (pp.evaluate("item_yaw", parkourProgress, parkourClimbSide) - state.itemYaw) * parkourWeight;
                state.itemRoll = state.itemRoll + (pp.evaluate("item_roll", parkourProgress, parkourClimbSide) - state.itemRoll) * parkourWeight;
            }
        }

        // Landing Apply
        if (landingTimer < 1.0f) {
            AnimationProfile lp = animationRegistry.get("landing");
            if (lp != null) {
                state.cameraTilt += lp.evaluate("camera_tilt", landingTimer, landingSide) * landingScale;
                state.cameraRoll += lp.evaluate("camera_roll", landingTimer, landingSide) * landingScale;
                state.cameraY += lp.evaluate("camera_y", landingTimer, landingSide) * landingScale;
                state.cameraX += lp.evaluate("camera_x", landingTimer, landingSide) * landingScale;
                state.fov += lp.evaluate("fov_offset", landingTimer, landingSide) * landingScale;
                state.landingItY = lp.evaluate("item_y", landingTimer, landingSide) * landingScale;
                state.landingItP = lp.evaluate("item_pitch", landingTimer, landingSide) * landingScale;
                state.landingItR = lp.evaluate("item_roll", landingTimer, landingSide) * landingScale;
            }
        }

        return state;
    }
}
