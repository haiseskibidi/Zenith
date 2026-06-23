package com.za.zenith.entities;

import com.za.zenith.engine.graphics.model.*;
import com.za.zenith.entities.parkour.animation.AnimationProfile;
import com.za.zenith.entities.parkour.animation.AnimationRegistry;
import com.za.zenith.world.World;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

/**
 * Processes bone hierarchies and updates model node translations/rotations for the player viewmodel.
 * ponytail: offloaded bone update routine keeping Zero-Allocation constraints.
 */
public class ViewmodelBoneProcessor {
    private final PlayerGripSimulator gripSimulator = new PlayerGripSimulator();
    private final ViewmodelPhysics mainHandPhys = new ViewmodelPhysics();
    private final ViewmodelController viewmodelController = new ViewmodelController();
    
    private boolean physicsInitialized = false;
    private float lerpedWeight = 0.2f;
    private float offhandWeight = 0.0f;
    private Item lastFrameItem;

    // zero-allocation reusable structures
    private final Vector3f extF = new Vector3f();
    private final Vector3f tPos = new Vector3f();
    private final Quaternionf tRot = new Quaternionf();
    private final Vector3f eulerAngles = new Vector3f();
    private final Matrix4f transformMatrix = new Matrix4f();

    public void processBones(
            Player player, 
            float deltaTime, 
            World world, 
            Viewmodel viewmodel, 
            AnimationRegistry animationRegistry,
            PlayerLocomotionState locomotionState,
            float itemOffsetX, float itemOffsetY, float itemOffsetZ,
            float itemPitchOffset, float itemYawOffset, float itemRollOffset,
            boolean swinging, String currentSwingAnim, float itemSwingTimer,
            float swingPosX, float swingPosY, float swingPosZ,
            float swingPitch, float swingYaw, float swingRoll,
            PlayerAnimationProfileEvaluator.AnimatedFrameState eval,
            float parkourWeight, String parkourStateName
    ) {
        if (viewmodel == null) return;

        Item heldItem = player.getInventory().getSelectedItem();
        if (lastFrameItem != heldItem) {
            viewmodelController.startTransition(viewmodel, 0.25f);
            lastFrameItem = heldItem;
        }
        viewmodelController.resetAnimation(viewmodel);
        
        // re-fetch profiles for blending idle and active states
        String sneakSuffix = player.isSneaking() ? "item_sneak" : ((player.isSprinting() && !player.isInWater()) ? "item_sprint" : "item_walk");
        AnimationProfile ip = animationRegistry.get(heldItem != null ? heldItem.getAnimation(sneakSuffix) : sneakSuffix);
        AnimationProfile cip = animationRegistry.get(player.isSneaking() ? "sneak_idle" : "idle");
        AnimationProfile iip = animationRegistry.get(heldItem != null ? heldItem.getAnimation("item_idle") : "item_idle");
        AnimationProfile cp = animationRegistry.get(player.isSneaking() ? "sneak" : ((player.isSprinting() && !player.isInWater()) ? "sprint" : "walk"));

        float locomotionTimer = locomotionState.getLocomotionTimer();
        float movementAlpha = locomotionState.getMovementAlpha();

        viewmodelController.applyAnimation(viewmodel, cip, player.isSneaking() ? 0.0f : locomotionTimer, (1.0f - movementAlpha));
        viewmodelController.applyAnimation(viewmodel, cp, locomotionTimer, movementAlpha);
        viewmodelController.applyAnimation(viewmodel, iip, locomotionTimer, (1.0f - movementAlpha));
        viewmodelController.applyAnimation(viewmodel, ip, locomotionTimer, movementAlpha);
        
        if (parkourWeight > 0.001f) {
            AnimationProfile pp = animationRegistry.get(parkourStateName);
            if (pp != null) {
                viewmodelController.applyAnimation(viewmodel, pp, player.getParkourHandler().getProgress(), parkourWeight);
            }
        }
        if (locomotionState.getLandingTimer() < 1.0f) {
            AnimationProfile lp = animationRegistry.get("landing");
            if (lp != null) {
                viewmodelController.applyAnimation(viewmodel, lp, Math.min(1.0f, locomotionState.getLandingTimer()), locomotionState.getLandingScale());
            }
        }
        if (swinging) {
            String swingKey = "item_" + currentSwingAnim;
            String sN = heldItem != null ? heldItem.getAnimation(swingKey) : swingKey.replace("item_", "hand_");
            AnimationProfile swingAnim = animationRegistry.get(sN);
            if (swingAnim != null) {
                viewmodelController.applyAnimation(viewmodel, swingAnim, itemSwingTimer, 1.0f);
            }
        }

        ModelNode sh = viewmodel.getNode("shoulder_r");
        ModelNode fo = viewmodel.getNode("forearm_r");
        ModelNode hand = viewmodel.getNode("hand_r");
        
        if (sh != null && fo != null && hand != null) {
            Vector3f camForward = com.za.zenith.engine.core.GameLoop.getInstance().getCamera().getDirection();
            float currentPitch = com.za.zenith.engine.core.GameLoop.getInstance().getCamera().getRotation().x;
            float pDelta = currentPitch - locomotionState.getLastPitch(); 
            locomotionState.setLastPitch(currentPitch);
            
            float yawDelta = locomotionState.getYawDelta(player.getRotation().y);
            extF.set(-yawDelta * 60.0f, pDelta * 60.0f, 0);

            tPos.set(itemOffsetX, itemOffsetY, itemOffsetZ);
            tPos.y += eval.landingItY;
            float fallIntensity = 0.0f;
            if (!player.isOnGround() && !player.getParkourHandler().isInParkour() && player.getVelocity().y < -2.0f) {
                fallIntensity = Math.min(1.0f, (Math.abs(player.getVelocity().y) - 2.0f) / 25.0f);
            }
            AnimationProfile fp = animationRegistry.get("falling");
            tPos.z += (fp != null ? fp.evaluate("item_z", locomotionState.getFallingTimer(), 1.0f) : 0) * fallIntensity;

            tRot.identity().rotateX(itemPitchOffset + eval.landingItP).rotateY(itemYawOffset).rotateZ(itemRollOffset + eval.landingItR);

            if (!physicsInitialized) {
                mainHandPhys.reset(tPos, tRot);
                physicsInitialized = true;
            }

            // tarkov-style collisions
            PlayerViewmodelCollision.applyCollisions(world, player.getPosition(), player.getEyeHeight(), camForward, heldItem, tPos, tRot, extF);

            float tW = (heldItem != null) ? heldItem.getWeight() : 0.2f;
            lerpedWeight += (tW - lerpedWeight) * 5.0f * deltaTime;
            sh.animTranslation.y -= lerpedWeight * 0.05f; 
            
            mainHandPhys.update(deltaTime, tPos, tRot, lerpedWeight, extF);
            mainHandPhys.currentRot.getEulerAnglesXYZ(eulerAngles);
            
            // update offhand visibility weight
            boolean offhandNeeded = player.getInventory().getStack(Inventory.SLOT_OFFHAND) != null || parkourWeight > 0.1f;
            float offhandTarget = offhandNeeded ? 1.0f : 0.0f;
            offhandWeight += (offhandTarget - offhandWeight) * 5.0f * deltaTime;

            // sync grips
            gripSimulator.updateGrips(player.getInventory().getSelectedItemStack(), player.getInventory().getStack(Inventory.SLOT_OFFHAND), deltaTime);

            // universal bone update
            for (ModelNode node : viewmodel.getAllNodes()) {
                boolean isLeft = node.name.endsWith("_l");
                boolean isHandPart = node.name.contains("hand") || node.name.contains("thumb") || node.name.contains("finger") || node.name.contains("index");
                float depthFactor = isHandPart ? 0.7f : (node.name.contains("forearm") ? 0.2f : 0.1f);
                
                node.animRotation.set(eulerAngles.x * depthFactor, eulerAngles.y * depthFactor * (isLeft ? -1 : 1), eulerAngles.z * depthFactor * (isLeft ? -1 : 1));
                
                float sF = isHandPart ? 0.4f : (node.name.contains("forearm") ? 0.35f : 0.25f);
                node.animRotation.add(swingPitch * sF, swingYaw * sF, swingRoll * sF);
                
                if (node.name.equals("shoulder_r")) {
                    node.animRotation.x += lerpedWeight * 0.05f;
                }

                // apply grip state to bone
                gripSimulator.applyGripToBones(node, isLeft);

                // dynamic visibility for left hand: rotate down if not needed
                if (isLeft) {
                    float hideFactor = 1.0f - offhandWeight;
                    if (node.name.startsWith("shoulder")) {
                        node.animRotation.x -= (float)Math.toRadians(110.0f * hideFactor);
                        node.animRotation.z += (float)Math.toRadians(25.0f * hideFactor);
                        node.animTranslation.y -= 0.8f * hideFactor;
                        node.animTranslation.z += 0.2f * hideFactor;
                    }
                }
            }

            viewmodelController.updateTransition(deltaTime);
            viewmodelController.applyTransition(viewmodel);

            tPos.set(mainHandPhys.currentPos).add(swingPosX, swingPosY, swingPosZ);
            transformMatrix.identity().translate(tPos);
            viewmodel.updateHierarchy(transformMatrix);
        }
    }
}
