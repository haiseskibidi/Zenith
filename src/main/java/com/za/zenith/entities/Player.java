package com.za.zenith.entities;

import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.entities.parkour.animation.AnimationRegistry;
import com.za.zenith.entities.parkour.animation.AnimationProfile;
import com.za.zenith.world.items.ItemStack;
import org.joml.Vector3f;

/**
 * AAA Player Entity.
 * Optimized version with physical viewmodel and clean animation logic.
 */
public class Player extends LivingEntity {
    private final Inventory inventory;
    private com.za.zenith.engine.core.PlayerMode mode = com.za.zenith.engine.core.PlayerMode.SURVIVAL;
    private final com.za.zenith.entities.parkour.ParkourHandler parkourHandler = new com.za.zenith.entities.parkour.ParkourHandler();
    private final AnimationRegistry animationRegistry = new AnimationRegistry();
    
    // Survival stats
    private float hunger = 20.0f;
    private float saturation = 5.0f;
    private float stamina = 1.0f;
    private float noiseLevel = 0.0f;
    private float continuousNoise = 0.0f;
    private boolean sneaking = false;
    private float oxygen = 300.0f;
    private static final float MAX_OXYGEN = 300.0f;
    private float drownDamageTimer = 0.0f;
    private boolean moving = false;
    private boolean sprinting = false;

    // Body Condition (Hand State)
    private float dirt = 0.0f;
    private float blood = 0.0f;
    private float wetness = 0.0f;
    private boolean hasParasites = false;
    private float parasitesTimer = 0.0f;

    // Action System
    private final java.util.Set<com.za.zenith.utils.Identifier> activeActions = new java.util.HashSet<>();
    
    // Animation State
    private float currentEyeHeight;
    private float parkourCameraTilt = 0.0f;
    private float parkourCameraRoll = 0.0f;
    private float fovOffset = 0.0f;
    private float cameraOffsetX = 0.0f;
    private float cameraOffsetY = 0.0f;
    private float lastYaw = 0.0f;
    private float lastPitch = 0.0f;
    private float leanAmount = 0.0f;

    // Item Animation State
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
    
    // Viewmodel Physics
    private com.za.zenith.engine.graphics.model.Viewmodel viewmodel;
    private final com.za.zenith.engine.graphics.model.ViewmodelController viewmodelController = new com.za.zenith.engine.graphics.model.ViewmodelController();
    private final com.za.zenith.engine.graphics.model.ViewmodelPhysics mainHandPhys = new com.za.zenith.engine.graphics.model.ViewmodelPhysics();
    private float lerpedWeight = 0.2f;
    private float offhandWeight = 0.0f; // Вес видимости левой руки
    private boolean physicsInitialized = false;
    private com.za.zenith.world.items.Item lastFrameItem;

    // Grip States
    private final Vector3f gripThumbR = new Vector3f();
    private final Vector3f gripThumbTipR = new Vector3f();
    private final Vector3f gripIndexR = new Vector3f();
    private final Vector3f gripIndexTipR = new Vector3f();
    private final Vector3f gripFingersR = new Vector3f();
    private final Vector3f gripFingersTipR = new Vector3f();
    private final Vector3f gripThumbL = new Vector3f();
    private final Vector3f gripThumbTipL = new Vector3f();
    private final Vector3f gripIndexL = new Vector3f();
    private final Vector3f gripIndexTipL = new Vector3f();
    private final Vector3f gripFingersL = new Vector3f();
    private final Vector3f gripFingersTipL = new Vector3f();

    // Locomotion Engine
    private float locomotionTimer = 0.0f;
    private float movementAlpha = 0.0f;
    private boolean wasOnGround = true;
    private boolean isFirstFrame = true;
    
    // Impulse States
    private float landingTimer = 1.0f;
    private float landingScale = 0.0f;
    private float landingSide = 1.0f;
    private float preUpdateVelocityY = 0.0f;
    private float fallingTimer = 0.0f;

    // Transitions
    private float parkourWeight = 0.0f; 
    private float moveLatchTimer = 0.0f;
    private static final float LATCH_DURATION = 0.15f; 

    public Player(Vector3f startPosition) {
        super(startPosition, 
              com.za.zenith.world.physics.PhysicsSettings.getInstance().playerWidth, 
              com.za.zenith.world.physics.PhysicsSettings.getInstance().standingHeight, 
              20.0f);
        this.inventory = new Inventory();
        this.addComponent(new com.za.zenith.entities.components.InventoryComponent(this.inventory));
        
        com.za.zenith.world.physics.PhysicsSettings settings = com.za.zenith.world.physics.PhysicsSettings.getInstance();
        this.currentEyeHeight = settings.standingEyeHeight;
        this.wasOnGround = true; 

        com.za.zenith.engine.graphics.model.ViewmodelDefinition handsDef = 
            com.za.zenith.engine.graphics.model.ModelRegistry.getViewmodel(com.za.zenith.utils.Identifier.of("zenith:hands"));
        if (handsDef != null) {
            this.viewmodel = new com.za.zenith.engine.graphics.model.Viewmodel(handsDef);
        }
    }
    
    public com.za.zenith.engine.graphics.model.Viewmodel getViewmodel() { return viewmodel; }
    
    @Override
    protected void onUpdate(float deltaTime, World world) {
        // Save velocity BEFORE physics clears it on impact
        preUpdateVelocityY = velocity.y;
        
        // Physics and Movement handled by super.move() or manual logic here
        // Note: Player uses a lot of custom movement logic, but we still use applyGravity or manual gravity
        if (!flying) {
            float submersion = getSubmersionRatio(world);
            if (submersion > 0.0f) {
                // Плавное ослабление гравитации в зависимости от степени погружения
                float fluidGravity = GRAVITY * (1.0f - submersion * 0.8f);
                velocity.y = Math.max(velocity.y + fluidGravity * deltaTime, -4.0f);
                
                // Трение (сопротивление) воды гасит скорость
                float fluidDrag = 0.8f * submersion;
                velocity.x *= (1.0f - fluidDrag * deltaTime);
                velocity.z *= (1.0f - fluidDrag * deltaTime);
                velocity.y *= (1.0f - fluidDrag * deltaTime);

                // Применяем снос течением
                Block b = getFluidBlock();
                if (b != null) {
                    com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
                    float flowForce = 1.4f;
                    if (def != null && def.isFluid()) {
                        String fluidType = def.getFluidType();
                        if ("oil".equals(fluidType) || "lava".equals(fluidType)) {
                            flowForce = 0.5f;
                        }
                    }
                    Vector3f flowVec = world.getInterpolatedFluidFlowVector(position.x, position.y + 0.5f, position.z, b.getType());

                    velocity.x += flowVec.x * flowForce * submersion * deltaTime;
                    velocity.z += flowVec.z * flowForce * submersion * deltaTime;
                    if (flowVec.y < 0) {
                        velocity.y = Math.max(velocity.y + flowVec.y * flowForce * submersion * deltaTime * 2.0f, -6.0f);
                    }
                }
            } else {
                velocity.y = Math.max(velocity.y + GRAVITY * deltaTime, TERMINAL_VELOCITY);
            }
        }
        
        
        move(world, velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);

        // Update RPG stats from equipment
        updateEquipmentStats();

        // --- Плавный выход из воды (Water Exit / Wall Climb) ---
        if (isInWater()) {
            waterExitGraceTimer = 0.4f;
        } else if (waterExitGraceTimer > 0.0f) {
            waterExitGraceTimer -= deltaTime;
        }

        boolean exitingWater = waterExitGraceTimer > 0.0f;

        if (exitingWater && horizontalCollision && moving) {
            velocity.y = Math.max(velocity.y, 4.5f);
            waterExitBoostTimer = 0.25f;
        }

        if (waterExitBoostTimer > 0.0f) {
            waterExitBoostTimer -= deltaTime;
            Vector3f lookDir = com.za.zenith.engine.core.GameLoop.getInstance().getCamera().getDirection();
            lookDir.y = 0;
            if (lookDir.lengthSquared() > 0.001f) {
                lookDir.normalize();
                velocity.x += lookDir.x * 20.0f * deltaTime;
                velocity.z += lookDir.z * 20.0f * deltaTime;
            }
        }

        // Impulse Trigger: Landing (Moved from updateAnimations for physical accuracy)
        if (onGround && !wasOnGround && preUpdateVelocityY < -1.5f) {
            landingTimer = 0.0f;
            landingSide = Math.random() > 0.5 ? 1.0f : -1.0f;
            float v = Math.abs(preUpdateVelocityY);
            // Softer scaling: tuned for direct physics application
            landingScale = Math.min(1.8f, (v * v) / 220.0f + (v * 0.03f)); 
        }
        wasOnGround = onGround;
        
        inventory.update(world, this, com.za.zenith.engine.core.GameLoop.getInstance().getCamera());
        updateHunger(deltaTime);
        updateActions(deltaTime);
        updateSneakState(world, deltaTime);
        parkourHandler.update(this, deltaTime, world);
        updateThermalAndConditions(deltaTime, world);
        
        boolean isMovingPhysically = (onGround || isInWater()) && moving && velocity.lengthSquared() > 0.0001f;
        if (isMovingPhysically) moveLatchTimer = LATCH_DURATION;
        else moveLatchTimer = Math.max(0, moveLatchTimer - deltaTime);
        
        float alphaTarget = (moveLatchTimer > 0) ? 1.0f : 0.0f;
        movementAlpha += (alphaTarget - movementAlpha) * (sneaking ? 4.0f : 8.0f) * deltaTime;

        String cN = sneaking ? "sneak" : ((sprinting && !isInWater()) ? "sprint" : "walk");
        AnimationProfile cp = animationRegistry.get(cN);
        AnimationProfile cip = animationRegistry.get(sneaking ? "sneak_idle" : "idle");
        
        float iDur = (cip != null) ? cip.getDuration() : 1.0f;
        float wDur = (cp != null) ? cp.getDuration() : 1.0f;
        
        float speedFactor = 1.0f;
        if (sneaking) {
            float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            float baseSneakSpeed = com.za.zenith.world.physics.PhysicsSettings.getInstance().baseMoveSpeed * 0.35f;
            speedFactor = Math.max(0.2f, horizontalSpeed / baseSneakSpeed);
        }
        
        float currentDuration = iDur + ( (wDur / speedFactor) - iDur) * movementAlpha;
        locomotionTimer = (locomotionTimer + deltaTime / currentDuration) % 1.0f;

        // Oxygen & Drowning tick
        float cameraY = position.y + currentEyeHeight;
        com.za.zenith.world.blocks.Block eyeBlock = world.getBlock((int)Math.floor(position.x), (int)Math.floor(cameraY), (int)Math.floor(position.z));
        com.za.zenith.world.blocks.BlockDefinition eyeDef = com.za.zenith.world.blocks.BlockRegistry.getBlock(eyeBlock.getType());
        boolean isEyesSubmerged = eyeDef != null && eyeDef.isFluid();
        
        if (isEyesSubmerged && mode == com.za.zenith.engine.core.PlayerMode.SURVIVAL) {
            oxygen = Math.max(0.0f, oxygen - 20.0f * deltaTime);
            if (oxygen <= 0.0f) {
                drownDamageTimer += deltaTime;
                if (drownDamageTimer >= 1.0f) {
                    takeDamage(1.0f);
                    drownDamageTimer = 0.0f;
                }
            }
        } else {
            oxygen = Math.min(MAX_OXYGEN, oxygen + 150.0f * deltaTime);
            drownDamageTimer = 0.0f;
        }
    }

    private float waterExitGraceTimer = 0.0f;
    private float waterExitBoostTimer = 0.0f;

    public void updateAnimations(float deltaTime, World world) {
        // Clamp deltaTime to prevent explicit Euler lerp explosions (NaN) during lag spikes
        deltaTime = Math.min(deltaTime, 0.05f);

        if (isFirstFrame) {
            lastYaw = getRotation().y;
            lastPitch = getRotation().x;
            wasOnGround = onGround;
            isFirstFrame = false;
        }

        // 1. Mouse Delta & Leaning
        float currentYaw = getRotation().y;
        float yawDelta = currentYaw - lastYaw;
        while (yawDelta < -Math.PI) yawDelta += Math.PI * 2;
        while (yawDelta > Math.PI) yawDelta -= Math.PI * 2;
        if (Math.abs(yawDelta) < 0.0001f) yawDelta = 0;
        float leanTarget = -yawDelta * 0.8f; 
        if (sprinting) leanTarget *= 1.2f;
        leanAmount += (leanTarget - leanAmount) * (sneaking ? 3.0f : 7.0f) * deltaTime;
        lastYaw = currentYaw;

        // 2.1 Falling logic (Tension & Wind Shake)
        float fallIntensity = 0.0f;
        if (!onGround && !parkourHandler.isInParkour() && velocity.y < -2.0f) {
            fallingTimer += deltaTime;
            // fallIntensity builds up based on vertical speed
            fallIntensity = Math.min(1.0f, (Math.abs(velocity.y) - 2.0f) / 25.0f);
        } else {
            fallingTimer = Math.max(0.0f, fallingTimer - deltaTime * 5.0f);
        }

        // 3. Profiles & Evaluation
        com.za.zenith.world.items.Item heldItem = inventory.getSelectedItem();
        boolean inWater = isInWater();
        String cN = sneaking ? "sneak" : ((sprinting && !inWater) ? "sprint" : "walk");
        String iN = heldItem != null ? heldItem.getAnimation(sneaking ? "item_sneak" : ((sprinting && !inWater) ? "item_sprint" : "item_walk")) : (sneaking ? "item_sneak" : ((sprinting && !inWater) ? "item_sprint" : "item_walk"));
        String iiN = heldItem != null ? heldItem.getAnimation("item_idle") : "item_idle";

        AnimationProfile cp = animationRegistry.get(cN);
        AnimationProfile ip = animationRegistry.get(iN);
        AnimationProfile cip = animationRegistry.get(sneaking ? "sneak_idle" : "idle");
        AnimationProfile iip = animationRegistry.get(iiN);
        AnimationProfile fp = animationRegistry.get("falling");

        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float maxSneakSpeed = com.za.zenith.world.physics.PhysicsSettings.getInstance().baseMoveSpeed * 0.35f;
        float speedIntensity = Math.min(1.0f, horizontalSpeed / maxSneakSpeed);

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

        float targetTilt, targetRoll, tCamY, tCamX, targetFov;
        float tItX, tItY, tItZ, tItP, tItYw, tItR;

        targetFov = (wFov * movementAlpha) + (fFov * fallIntensity);

        if (sneaking) {
            float breathWeight = 0.4f + (speedIntensity * 0.6f);
            float walkWeight = speedIntensity; 
            targetTilt = iTilt + (wTilt - iTilt) * breathWeight + fTilt * fallIntensity;
            targetRoll = (iRoll + (wRoll - iRoll) * breathWeight) + leanAmount;
            tCamY = iCamY + (wCamY - iCamY) * breathWeight + fCamY * fallIntensity;
            tCamX = iCamX * walkWeight; 
            tItX = iItX + (wItX - iItX) * walkWeight;
            tItY = iItY + (wItY - iItY) * breathWeight + fItY * fallIntensity;
            tItZ = iItZ + (wItZ - iItZ) * breathWeight + fItZ * fallIntensity;
            tItP = iItP + (wItP - iItP) * breathWeight + fItP * fallIntensity;
            tItYw = (leanAmount * 0.2f);
            tItR = iItR + (wItR - iItR) * walkWeight;
        } else {
            targetTilt = iTilt + (wTilt - iTilt) * movementAlpha + fTilt * fallIntensity;
            targetRoll = (iRoll + (wRoll - iRoll) * movementAlpha) + leanAmount;
            tCamY = iCamY + (wCamY - iCamY) * movementAlpha + fCamY * fallIntensity;
            tCamX = 0; 
            tItX = iItX + (wItX - iItX) * movementAlpha;
            tItY = iItY + (wItY - iItY) * movementAlpha + fItY * fallIntensity;
            tItZ = iItZ + (wItZ - iItZ) * movementAlpha + fItZ * fallIntensity;
            tItP = iItP + (wItP - iItP) * movementAlpha + fItP * fallIntensity;
            tItYw = (leanAmount * 0.2f);
            tItR = iItR + (wItR - iItR) * movementAlpha;
        }

        // Procedural Wind Shake for hands during fall
        if (fallIntensity > 0.01f) {
            float shakeFreq = 45.0f;
            float shake = (float) Math.sin(fallingTimer * shakeFreq) * 0.02f * fallIntensity;
            tItY += shake;
            tItX += (float) Math.cos(fallingTimer * shakeFreq * 0.8f) * 0.01f * fallIntensity;
        }

        // 4. Parkour blending
        float parkourTarget = parkourHandler.isInParkour() ? 1.0f : 0.0f;
        parkourWeight += (parkourTarget - parkourWeight) * 18.0f * deltaTime;
        if (parkourWeight > 0.001f) {
            String pN = (parkourHandler.getState() == com.za.zenith.entities.parkour.ParkourHandler.ParkourState.CLIMBING) ? "climbing" : "grabbing";
            AnimationProfile pp = animationRegistry.get(pN);
            if (pp != null) {
                float pT = parkourHandler.getProgress();
                float pSide = parkourHandler.getClimbSide();
                targetTilt = targetTilt + (pp.evaluate("camera_tilt", pT, pSide) - targetTilt) * parkourWeight;
                targetRoll = targetRoll + (pp.evaluate("camera_roll", pT, pSide) - targetRoll) * parkourWeight;
                tCamY = tCamY + (pp.evaluate("camera_y", pT, pSide) - tCamY) * parkourWeight;
                tCamX = tCamX + (pp.evaluate("camera_x", pT, pSide) - tCamX) * parkourWeight;
                targetFov = targetFov + (pp.evaluate("fov_offset", pT, pSide) - targetFov) * parkourWeight;
                tItX = tItX + (pp.evaluate("item_x", pT, pSide) - tItX) * parkourWeight;
                tItY = tItY + (pp.evaluate("item_y", pT, pSide) - tItY) * parkourWeight;
                tItZ = tItZ + (pp.evaluate("item_z", pT, pSide) - tItZ) * parkourWeight;
                tItP = tItP + (pp.evaluate("item_pitch", pT, pSide) - tItP) * parkourWeight;
                tItYw = tItYw + (pp.evaluate("item_yaw", pT, pSide) - tItYw) * parkourWeight;
                tItR = tItR + (pp.evaluate("item_roll", pT, pSide) - tItR) * parkourWeight;
            }
        }

        // 5. Impulse Apply (Moved to variables for direct physics application)
        float lItY = 0, lItP = 0, lItR = 0;
        if (landingTimer < 1.0f) {
            AnimationProfile lp = animationRegistry.get("landing");
            if (lp != null) {
                landingTimer += deltaTime / lp.getDuration();
                float t = Math.min(1.0f, landingTimer);
                targetTilt += lp.evaluate("camera_tilt", t, landingSide) * landingScale;
                targetRoll += lp.evaluate("camera_roll", t, landingSide) * landingScale;
                tCamY += lp.evaluate("camera_y", t, landingSide) * landingScale;
                tCamX += lp.evaluate("camera_x", t, landingSide) * landingScale;
                targetFov += lp.evaluate("fov_offset", t, landingSide) * landingScale;
                lItY = lp.evaluate("item_y", t, landingSide) * landingScale;
                lItP = lp.evaluate("item_pitch", t, landingSide) * landingScale;
                lItR = lp.evaluate("item_roll", t, landingSide) * landingScale;
            } else landingTimer = 1.0f;
        }

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
                    // Positions in JSON are in voxels (16 per block), convert to meters
                    swingPosX = swingAnim.evaluate("item_x", itemSwingTimer, 1.0f) / 16.0f;
                    swingPosY = swingAnim.evaluate("item_y", itemSwingTimer, 1.0f) / 16.0f;
                    swingPosZ = swingAnim.evaluate("item_z", itemSwingTimer, 1.0f) / 16.0f;
                    
                    // Rotations in JSON are in degrees, convert to radians
                    swingPitch = (float)Math.toRadians(swingAnim.evaluate("item_pitch", itemSwingTimer, 1.0f));
                    swingYaw = (float)Math.toRadians(swingAnim.evaluate("item_yaw", itemSwingTimer, 1.0f));
                    swingRoll = (float)Math.toRadians(swingAnim.evaluate("item_roll", itemSwingTimer, 1.0f));
                    
                    // Add camera impact from swing
                    targetTilt += swingAnim.evaluate("camera_tilt", itemSwingTimer, 1.0f);
                    targetRoll += swingAnim.evaluate("camera_roll", itemSwingTimer, 1.0f);
                }
            } else swinging = false;
        }
        if (isInWater()) {
            float swimCycle = locomotionTimer * (float)Math.PI * 2.0f;
            // Плавно наклоняем камеру вперед (имитирует горизонтальное тело пловца)
            targetTilt += 0.12f * movementAlpha;
            // Очень мягкое покачивание камеры влево-вправо (чтобы не укачивало)
            targetRoll += (float)Math.cos(swimCycle) * 0.015f * movementAlpha;
            // Мягкое опускание и подъем (вдох-выдох / фаза гребка)
            tCamY += (float)Math.sin(swimCycle) * 0.02f * movementAlpha;
            
            // Покачивание рук в такт плаванию (плавные гребки)
            tItY += (float)Math.sin(swimCycle) * 0.03f * movementAlpha;
            tItX += (float)Math.cos(swimCycle) * 0.02f * movementAlpha;
            
            // Мягкие круговые вращения рук Pitch/Roll без резких заломов
            tItP += 0.12f * movementAlpha + (float)Math.cos(swimCycle) * 0.04f * movementAlpha;
            tItR += (float)Math.sin(swimCycle) * 0.03f * movementAlpha;
        }

        // 6. Final Sync
        float syncLerp = 12.0f;
        
        if (!physicsInitialized) {
            parkourCameraTilt = targetTilt;
            parkourCameraRoll = targetRoll;
            fovOffset = targetFov;
            cameraOffsetY = tCamY;
            cameraOffsetX = tCamX;
            itemOffsetX = tItX + (leanAmount * 0.1f);
            itemOffsetY = tItY;
            itemOffsetZ = tItZ;
            itemPitchOffset = tItP;
            itemYawOffset = tItYw;
            itemRollOffset = tItR + (leanAmount * 0.5f);
            
            // Set it true here if no viewmodel or nodes found later
            if (viewmodel == null) physicsInitialized = true;
        }

        parkourCameraTilt += (targetTilt - parkourCameraTilt) * syncLerp * deltaTime;
        parkourCameraRoll += (targetRoll - parkourCameraRoll) * syncLerp * deltaTime;
        fovOffset += (targetFov - fovOffset) * 4.0f * deltaTime;
        cameraOffsetY += (tCamY - cameraOffsetY) * syncLerp * deltaTime;
        cameraOffsetX += (tCamX - cameraOffsetX) * syncLerp * deltaTime;
        itemOffsetX += (tItX + (leanAmount * 0.1f) - itemOffsetX) * syncLerp * deltaTime;
        itemOffsetY += (tItY - itemOffsetY) * syncLerp * deltaTime;
        itemOffsetZ += (tItZ - itemOffsetZ) * syncLerp * deltaTime;
        itemPitchOffset += (tItP - itemPitchOffset) * syncLerp * deltaTime;
        itemYawOffset += (tItYw - itemYawOffset) * syncLerp * deltaTime;
        itemRollOffset += (tItR + (leanAmount * 0.5f) - itemRollOffset) * syncLerp * deltaTime;

        // 7. Physical Simulation
        if (viewmodel != null) {
            if (lastFrameItem != heldItem) {
                viewmodelController.startTransition(viewmodel, 0.25f);
                lastFrameItem = heldItem;
            }

            viewmodelController.resetAnimation(viewmodel);
            viewmodelController.applyAnimation(viewmodel, cip, sneaking ? 0.0f : locomotionTimer, (1.0f - movementAlpha));
            viewmodelController.applyAnimation(viewmodel, cp, locomotionTimer, movementAlpha);
            viewmodelController.applyAnimation(viewmodel, iip, locomotionTimer, (1.0f - movementAlpha));
            viewmodelController.applyAnimation(viewmodel, ip, locomotionTimer, movementAlpha);
            
            if (parkourWeight > 0.001f) {
                String pN = (parkourHandler.getState() == com.za.zenith.entities.parkour.ParkourHandler.ParkourState.CLIMBING) ? "climbing" : "grabbing";
                AnimationProfile pp = animationRegistry.get(pN);
                if (pp != null) viewmodelController.applyAnimation(viewmodel, pp, parkourHandler.getProgress(), parkourWeight);
            }
            if (landingTimer < 1.0f) {
                AnimationProfile lp = animationRegistry.get("landing");
                if (lp != null) viewmodelController.applyAnimation(viewmodel, lp, Math.min(1.0f, landingTimer), landingScale);
            }
            if (swinging) {
                String swingKey = "item_" + currentSwingAnim;
                String sN = heldItem != null ? heldItem.getAnimation(swingKey) : swingKey.replace("item_", "hand_");
                AnimationProfile swingAnim = animationRegistry.get(sN);
                if (swingAnim != null) viewmodelController.applyAnimation(viewmodel, swingAnim, itemSwingTimer, 1.0f);
            }

            com.za.zenith.engine.graphics.model.ModelNode sh = viewmodel.getNode("shoulder_r");
            com.za.zenith.engine.graphics.model.ModelNode fo = viewmodel.getNode("forearm_r");
            com.za.zenith.engine.graphics.model.ModelNode hand = viewmodel.getNode("hand_r");
            
            if (sh != null && fo != null && hand != null) {
                Vector3f camForward = com.za.zenith.engine.core.GameLoop.getInstance().getCamera().getDirection();

                float currentPitch = com.za.zenith.engine.core.GameLoop.getInstance().getCamera().getRotation().x;
                float pDelta = currentPitch - lastPitch; lastPitch = currentPitch;
                Vector3f extF = new Vector3f(-yawDelta * 60.0f, pDelta * 60.0f, 0);

                // --- KEY FIX: Apply landing and falling RAW values directly to physics target ---
                Vector3f tPos = new Vector3f(itemOffsetX, itemOffsetY, itemOffsetZ);
                tPos.y += lItY; // Apply landing impulse directly
                tPos.z += (fp != null ? fp.evaluate("item_z", fallingTimer, 1.0f) : 0) * fallIntensity;

                org.joml.Quaternionf tRot = new org.joml.Quaternionf().rotateX(itemPitchOffset + lItP).rotateY(itemYawOffset).rotateZ(itemRollOffset + lItR);

                if (!physicsInitialized) {
                    mainHandPhys.reset(new Vector3f(tPos), new org.joml.Quaternionf(tRot));
                    physicsInitialized = true;
                }


                // --- 7.1 WORLD COLLISIONS (Tarkov-style) ---
                float reach = 0.6f; 
                if (heldItem != null) reach += heldItem.getViewmodelScale() * 0.4f;
                Vector3f eyePos = new Vector3f(position).add(0, getEyeHeight(), 0);
                com.za.zenith.world.physics.RaycastResult hit = com.za.zenith.world.physics.Raycast.raycast(world, eyePos, camForward);
                Vector3f probePoint = new Vector3f(eyePos).fma(reach * 0.8f, camForward);
                com.za.zenith.world.blocks.Block probedBlock = world.getBlock((int)Math.floor(probePoint.x), (int)Math.floor(probePoint.y), (int)Math.floor(probePoint.z));
                boolean colliding = (hit != null && hit.isHit() && hit.getDistance() < reach) || !probedBlock.isAir();
                
                if (colliding) {
                    float dist = (hit != null && hit.isHit()) ? hit.getDistance() : reach * 0.5f;
                    float factor = (reach - dist) / reach;
                    float pf = Math.clamp(factor, 0.0f, 1.0f);
                    tPos.z += 0.8f * pf; tPos.x += 0.15f * pf;
                    tRot.rotateX((float)Math.toRadians(-75.0f * pf));
                    tRot.rotateZ((float)Math.toRadians(15.0f * pf));
                    extF.z += 60.0f * pf;
                }

                float tW = (heldItem != null) ? heldItem.getWeight() : 0.2f;
                lerpedWeight += (tW - lerpedWeight) * 5.0f * deltaTime;
                sh.animTranslation.y -= lerpedWeight * 0.05f; 
                
                mainHandPhys.update(deltaTime, tPos, tRot, lerpedWeight, extF);
                
                Vector3f a = mainHandPhys.currentRot.getEulerAnglesXYZ(new Vector3f());
                
                // Update offhand visibility weight
                boolean offhandNeeded = inventory.getStack(Inventory.SLOT_OFFHAND) != null || parkourWeight > 0.1f;
                float offhandTarget = offhandNeeded ? 1.0f : 0.0f;
                offhandWeight += (offhandTarget - offhandWeight) * 5.0f * deltaTime;

                // Get grips for hands
                ItemStack mainHandStack = inventory.getSelectedItemStack();
                ItemStack offHandStack = inventory.getStack(Inventory.SLOT_OFFHAND);
                com.za.zenith.engine.graphics.model.GripDefinition mainGrip = null;
                com.za.zenith.engine.graphics.model.GripDefinition offGrip = null;
                
                if (mainHandStack != null && mainHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class) != null) {
                    mainGrip = mainHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class).grip();
                }
                if (offHandStack != null && offHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class) != null) {
                    offGrip = offHandStack.getItem().getComponent(com.za.zenith.world.items.component.ViewmodelComponent.class).grip();
                }

                // Плавная интерполяция состояний хвата (один раз за кадр, до цикла!)
                com.za.zenith.engine.graphics.model.GripDefinition activeGripR = mainGrip != null ? mainGrip : 
                    (mainHandStack != null ? com.za.zenith.engine.graphics.model.GripDefinition.createAuto(mainHandStack.getItem()) : com.za.zenith.engine.graphics.model.GripDefinition.createRelaxed());
                    
                com.za.zenith.engine.graphics.model.GripDefinition activeGripL = offGrip != null ? offGrip : 
                    (offHandStack != null ? com.za.zenith.engine.graphics.model.GripDefinition.createAuto(offHandStack.getItem()) : com.za.zenith.engine.graphics.model.GripDefinition.createRelaxed());
                
                float gripLerpSpeed = 12.0f * deltaTime;
                gripThumbR.x += (activeGripR.thumb()[0] - gripThumbR.x) * gripLerpSpeed;
                gripThumbR.y += (activeGripR.thumb()[1] - gripThumbR.y) * gripLerpSpeed;
                gripThumbR.z += (activeGripR.thumb()[2] - gripThumbR.z) * gripLerpSpeed;
                gripThumbTipR.x += (activeGripR.thumb_tip()[0] - gripThumbTipR.x) * gripLerpSpeed;
                gripThumbTipR.y += (activeGripR.thumb_tip()[1] - gripThumbTipR.y) * gripLerpSpeed;
                gripThumbTipR.z += (activeGripR.thumb_tip()[2] - gripThumbTipR.z) * gripLerpSpeed;

                gripIndexR.x += (activeGripR.index()[0] - gripIndexR.x) * gripLerpSpeed;
                gripIndexR.y += (activeGripR.index()[1] - gripIndexR.y) * gripLerpSpeed;
                gripIndexR.z += (activeGripR.index()[2] - gripIndexR.z) * gripLerpSpeed;
                gripIndexTipR.x += (activeGripR.index_tip()[0] - gripIndexTipR.x) * gripLerpSpeed;
                gripIndexTipR.y += (activeGripR.index_tip()[1] - gripIndexTipR.y) * gripLerpSpeed;
                gripIndexTipR.z += (activeGripR.index_tip()[2] - gripIndexTipR.z) * gripLerpSpeed;

                gripFingersR.x += (activeGripR.fingers()[0] - gripFingersR.x) * gripLerpSpeed;
                gripFingersR.y += (activeGripR.fingers()[1] - gripFingersR.y) * gripLerpSpeed;
                gripFingersR.z += (activeGripR.fingers()[2] - gripFingersR.z) * gripLerpSpeed;
                gripFingersTipR.x += (activeGripR.fingers_tip()[0] - gripFingersTipR.x) * gripLerpSpeed;
                gripFingersTipR.y += (activeGripR.fingers_tip()[1] - gripFingersTipR.y) * gripLerpSpeed;
                gripFingersTipR.z += (activeGripR.fingers_tip()[2] - gripFingersTipR.z) * gripLerpSpeed;
                
                gripThumbL.x += (activeGripL.thumb()[0] - gripThumbL.x) * gripLerpSpeed;
                gripThumbL.y += (activeGripL.thumb()[1] - gripThumbL.y) * gripLerpSpeed;
                gripThumbL.z += (activeGripL.thumb()[2] - gripThumbL.z) * gripLerpSpeed;
                gripThumbTipL.x += (activeGripL.thumb_tip()[0] - gripThumbTipL.x) * gripLerpSpeed;
                gripThumbTipL.y += (activeGripL.thumb_tip()[1] - gripThumbTipL.y) * gripLerpSpeed;
                gripThumbTipL.z += (activeGripL.thumb_tip()[2] - gripThumbTipL.z) * gripLerpSpeed;

                gripIndexL.x += (activeGripL.index()[0] - gripIndexL.x) * gripLerpSpeed;
                gripIndexL.y += (activeGripL.index()[1] - gripIndexL.y) * gripLerpSpeed;
                gripIndexL.z += (activeGripL.index()[2] - gripIndexL.z) * gripLerpSpeed;
                gripIndexTipL.x += (activeGripL.index_tip()[0] - gripIndexTipL.x) * gripLerpSpeed;
                gripIndexTipL.y += (activeGripL.index_tip()[1] - gripIndexTipL.y) * gripLerpSpeed;
                gripIndexTipL.z += (activeGripL.index_tip()[2] - gripIndexTipL.z) * gripLerpSpeed;

                gripFingersL.x += (activeGripL.fingers()[0] - gripFingersL.x) * gripLerpSpeed;
                gripFingersL.y += (activeGripL.fingers()[1] - gripFingersL.y) * gripLerpSpeed;
                gripFingersL.z += (activeGripL.fingers()[2] - gripFingersL.z) * gripLerpSpeed;
                gripFingersTipL.x += (activeGripL.fingers_tip()[0] - gripFingersTipL.x) * gripLerpSpeed;
                gripFingersTipL.y += (activeGripL.fingers_tip()[1] - gripFingersTipL.y) * gripLerpSpeed;
                gripFingersTipL.z += (activeGripL.fingers_tip()[2] - gripFingersTipL.z) * gripLerpSpeed;

                // Universal bone update
                for (com.za.zenith.engine.graphics.model.ModelNode node : viewmodel.getAllNodes()) {
                    boolean isLeft = node.name.endsWith("_l");
                    boolean isHandPart = node.name.contains("hand") || node.name.contains("thumb") || node.name.contains("finger") || node.name.contains("index");
                    float depthFactor = isHandPart ? 0.7f : (node.name.contains("forearm") ? 0.2f : 0.1f);
                    
                    // Apply base inertia
                    node.animRotation.set(a.x * depthFactor, a.y * depthFactor * (isLeft ? -1 : 1), a.z * depthFactor * (isLeft ? -1 : 1));
                    
                    // Apply swing impact
                    float sF = isHandPart ? 0.4f : (node.name.contains("forearm") ? 0.35f : 0.25f);
                    node.animRotation.add(swingPitch * sF, swingYaw * sF, swingRoll * sF);
                    
                    if (node.name.equals("shoulder_r")) node.animRotation.x += lerpedWeight * 0.05f;

                    // Apply Grip logic
                    boolean isThumb = node.name.startsWith("thumb");
                    boolean isIndex = node.name.startsWith("index");
                    boolean isFingers = node.name.startsWith("fingers");
                    
                    if (isThumb || isIndex || isFingers) {
                        boolean isTip = node.name.contains("tip");
                        Vector3f state = isLeft ? 
                            (isThumb ? (isTip ? gripThumbTipL : gripThumbL) : (isIndex ? (isTip ? gripIndexTipL : gripIndexL) : (isTip ? gripFingersTipL : gripFingersL))) :
                            (isThumb ? (isTip ? gripThumbTipR : gripThumbR) : (isIndex ? (isTip ? gripIndexTipR : gripIndexR) : (isTip ? gripFingersTipR : gripFingersR)));
                            
                        float signX = 1.0f;
                        float signY = isLeft ? -1.0f : 1.0f;
                        float signZ = isLeft ? -1.0f : 1.0f;
                        
                        node.animRotation.x += (float)Math.toRadians(state.x * signX);
                        node.animRotation.y += (float)Math.toRadians(state.y * signY);
                        node.animRotation.z += (float)Math.toRadians(state.z * signZ);
                    }

                    // Dynamic visibility for left hand: rotate down if not needed
                    if (isLeft) {
                        float hideFactor = 1.0f - offhandWeight;
                        if (node.name.startsWith("shoulder")) {
                            node.animRotation.x -= (float)Math.toRadians(110.0f * hideFactor); // Rotate DOWN
                            node.animRotation.z += (float)Math.toRadians(25.0f * hideFactor);  // Tilt slightly
                            node.animTranslation.y -= 0.8f * hideFactor; // Pull down further
                            node.animTranslation.z += 0.2f * hideFactor; // Pull back slightly
                        }
                    }
                }

                // --- 7.2 APPLY TRANSITION (V1 <-> V2 CROSSFADE) ---
                // Applies smooth interpolation for all bones AFTER Inertia, Grip, and V1/V2 animations have been combined
                viewmodelController.updateTransition(deltaTime);
                viewmodelController.applyTransition(viewmodel);

                Vector3f finalPos = new Vector3f(mainHandPhys.currentPos).add(swingPosX, swingPosY, swingPosZ);
                viewmodel.updateHierarchy(new org.joml.Matrix4f().identity().translate(finalPos));
            }
        }
    }

    public com.za.zenith.entities.parkour.ParkourHandler getParkourHandler() { return parkourHandler; }

    private void updateSneakState(World world, float deltaTime) {
        com.za.zenith.world.physics.PhysicsSettings settings = com.za.zenith.world.physics.PhysicsSettings.getInstance();
        float targetEyeHeight = sneaking ? settings.sneakingEyeHeight : settings.standingEyeHeight;
        if (!sneaking && boundingBox.getMax().y < settings.standingHeight) {
            if (canStandUp(world)) setBoundingBox(settings.playerWidth, settings.standingHeight);
            else targetEyeHeight = settings.sneakingEyeHeight;
        } else if (sneaking && boundingBox.getMax().y > settings.sneakingHeight) {
            setBoundingBox(settings.playerWidth, settings.sneakingHeight);
        }
        currentEyeHeight += (targetEyeHeight - currentEyeHeight) * 10.0f * deltaTime;
    }

    private boolean canStandUp(World world) {
        com.za.zenith.world.physics.PhysicsSettings settings = com.za.zenith.world.physics.PhysicsSettings.getInstance();
        com.za.zenith.world.physics.AABB standingBox = new com.za.zenith.world.physics.AABB(-settings.playerWidth / 2, 0, -settings.playerWidth / 2, settings.playerWidth / 2, settings.standingHeight, settings.playerWidth / 2).offset(position);
        for (int x = (int)Math.floor(standingBox.getMin().x); x <= (int)Math.floor(standingBox.getMax().x); x++) {
            for (int y = (int)Math.floor(standingBox.getMin().y); y <= (int)Math.floor(standingBox.getMax().y); y++) {
                for (int z = (int)Math.floor(standingBox.getMin().z); z <= (int)Math.floor(standingBox.getMax().z); z++) {
                    com.za.zenith.world.blocks.Block b = world.getBlock(x, y, z);
                    if (!b.isAir() && b.isSolid()) return false;
                }
            }
        }
        return true;
    }

    public float getEyeHeight() { return currentEyeHeight; }
    public float getCameraPitchOffset() { return parkourCameraTilt; }
    public float getCameraRollOffset() { return parkourCameraRoll; }
    public float getFovOffset() { return fovOffset; }
    public float getCameraOffsetX() { return cameraOffsetX; }
    public float getCameraOffsetY() { return cameraOffsetY; }
    public float getCameraOffsetZ() { return 0.0f; } 
    public float getItemOffsetX() { return itemOffsetX; }
    public float getItemOffsetY() { return itemOffsetY; }
    public float getItemOffsetZ() { return itemOffsetZ; }
    public float getItemPitchOffset() { return itemPitchOffset; }
    public float getItemYawOffset() { return itemYawOffset; }
    public float getItemRollOffset() { return itemRollOffset; }
    
    public void swing() { swing(0.35f); }
    public void swing(float duration) { 
        if (!swinging) { 
            swinging = true; 
            itemSwingTimer = 0; 
            itemSwingDuration = duration; 
            currentSwingAnim = "swing";
        } 
    }
    
    public void interact() { interact(0.25f); }
    public void interact(float duration) {
        if (!swinging) {
            swinging = true;
            itemSwingTimer = 0;
            itemSwingDuration = duration;
            currentSwingAnim = "pickup";
        }
    }

    public void place() { place(0.25f); }
    public void place(float duration) {
        if (!swinging) {
            swinging = true;
            itemSwingTimer = 0;
            itemSwingDuration = duration;
            currentSwingAnim = "place";
        }
    }

    public boolean isSwinging() { return swinging; }
    public boolean isMoving() { return moving; }



    public float getOxygen() { return oxygen; }
    public float getMaxOxygen() { return MAX_OXYGEN; }

    public boolean isInRain() {
        return false;
    }

    public void startAction(com.za.zenith.utils.Identifier id) {
        activeActions.add(id);
    }

    public void stopAction(com.za.zenith.utils.Identifier id) {
        activeActions.remove(id);
    }

    public void performDiscreteAction(com.za.zenith.utils.Identifier id) {
        com.za.zenith.world.actions.ActionDefinition def = com.za.zenith.world.actions.ActionRegistry.get(id);
        if (def != null) {
            addNoise(def.noiseLevel);
            stamina = Math.max(0.0f, stamina - def.staminaCostPerUse);
            hunger = Math.max(0.0f, hunger - def.hungerCostPerUse);
        }
    }

    private void updateActions(float deltaTime) {
        float floorNoise = 0.0f;
        
        // Automatic locomotion actions
        if (!flying && moving) {
            if (sprinting) startAction(com.za.zenith.utils.Identifier.of("zenith:sprint"));
            else stopAction(com.za.zenith.utils.Identifier.of("zenith:sprint"));
            
            if (sneaking) startAction(com.za.zenith.utils.Identifier.of("zenith:sneak"));
            else stopAction(com.za.zenith.utils.Identifier.of("zenith:sneak"));
            
            if (!sprinting && !sneaking) startAction(com.za.zenith.utils.Identifier.of("zenith:walk"));
            else stopAction(com.za.zenith.utils.Identifier.of("zenith:walk"));
        } else {
            stopAction(com.za.zenith.utils.Identifier.of("zenith:sprint"));
            stopAction(com.za.zenith.utils.Identifier.of("zenith:sneak"));
            stopAction(com.za.zenith.utils.Identifier.of("zenith:walk"));
        }

        boolean staminaConsumingAction = false;

        for (com.za.zenith.utils.Identifier id : activeActions) {
            com.za.zenith.world.actions.ActionDefinition def = com.za.zenith.world.actions.ActionRegistry.get(id);
            if (def != null) {
                if (def.staminaCostPerSecond > 0) {
                    staminaConsumingAction = true;
                    stamina = Math.max(0.0f, stamina - def.staminaCostPerSecond * deltaTime);
                }
                hunger = Math.max(0.0f, hunger - def.hungerCostPerSecond * deltaTime);
                floorNoise = Math.max(floorNoise, def.noiseLevel);
            }
        }

        // Stamina regeneration
        if (!staminaConsumingAction && onGround) {
            stamina = Math.min(1.0f, stamina + 0.1f * deltaTime); // Regenerate 10% per second when idle
        }

        noiseLevel = Math.max(floorNoise, Math.max(continuousNoise, noiseLevel - 0.5f * deltaTime));
        continuousNoise = 0.0f;
    }

    public void addNoise(float amount) { this.noiseLevel = Math.min(1.0f, this.noiseLevel + amount); }
    public void setContinuousNoise(float level) { this.continuousNoise = Math.max(this.continuousNoise, level); }
    public float getNoiseLevel() { return noiseLevel; }
    public void setSneaking(boolean sneaking) { this.sneaking = sneaking; }
    public boolean isSneaking() { return sneaking; }
    public boolean isPhysicallySneaking() { return boundingBox.getMax().y < com.za.zenith.world.physics.PhysicsSettings.getInstance().standingHeight - 0.01f; }
    public void setMoving(boolean moving) { this.moving = moving; }
    public void setSprinting(boolean sprinting) { this.sprinting = sprinting; }
    public float getStamina() { return stamina; }
    public void setStamina(float stamina) { this.stamina = stamina; }
    public float getMiningSpeedMultiplier() { return 1.0f; }
    private void updateThermalAndConditions(float deltaTime, World world) {
        ItemStack held = inventory.getSelectedItemStack();
        if (held != null) {
            float ambient = 20.0f; 
            if (isInWater()) ambient = 15.0f;
            held.updateTemperature(ambient, deltaTime);
            com.za.zenith.world.items.component.ThermalComponent thermal = held.getItem().getComponent(com.za.zenith.world.items.component.ThermalComponent.class);
            float threshold = (thermal != null) ? thermal.burnThreshold() : 55.0f;
            if (held.getTemperature() > threshold) {
                takeDamage(0.5f * deltaTime);
                if (Math.random() < 0.05f * deltaTime) {
                    inventory.dropSelected(this, world, com.za.zenith.engine.core.GameLoop.getInstance().getCamera(), true);
                }
            }
        }
        if (isInWater()) {
            wetness = 1.0f;
            dirt = Math.max(0, dirt - 2.0f * deltaTime);
            blood = Math.max(0, blood - 1.0f * deltaTime);
        } else {
            wetness = Math.max(0, wetness - 0.1f * deltaTime);
        }
        if (parasitesTimer > 0) {
            parasitesTimer -= deltaTime;
            if (parasitesTimer <= 0) hasParasites = false;
        }
    }

    public void addDirt(float amount) { this.dirt = Math.min(1.0f, this.dirt + amount); }
    public void addBlood(float amount) { this.blood = Math.min(1.0f, this.blood + amount); }
    public void washHands() { this.dirt = 0; this.blood = 0; this.wetness = 1.0f; }
    public float getDirt() { return dirt; }
    public float getBlood() { return blood; }
    public float getWetness() { return wetness; }
    public float getScentLevel() { return blood * 2.0f; }

    public void updateHunger(float deltaTime) {
        float mult = sprinting ? 3.0f : (!onGround && !flying ? 2.0f : 1.0f);
        if (hasParasites) mult *= 2.0f;
        if (saturation > 0) saturation -= 0.1f * mult * deltaTime;
        else hunger = Math.max(0, hunger - 0.1f * mult * deltaTime);
    }

    public void eat(com.za.zenith.world.items.Item item) {
        com.za.zenith.world.items.component.FoodComponent food = item.getComponent(com.za.zenith.world.items.component.FoodComponent.class);
        if (food != null && hunger < 20.0f) {
            hunger = Math.min(20.0f, hunger + food.nutrition());
            saturation = Math.min(20.0f, saturation + food.saturationBonus());
            if (dirt > 0.5f && Math.random() < 0.3f) {
                hasParasites = true;
                parasitesTimer = 600.0f; 
            }
        }
    }
    public void jump() {
        if (onGround || flying) {
            velocity.y = com.za.zenith.world.physics.PhysicsSettings.getInstance().jumpVelocity;
            onGround = false;
            performDiscreteAction(com.za.zenith.utils.Identifier.of("zenith:jump"));
        }
    }
    public void addVelocity(float vx, float vy, float vz) { velocity.add(vx, vy, vz); }
    public void applyHorizontalAcceleration(float ax, float az, float maxSpeed) {
        velocity.x += ax; velocity.z += az;
        float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed > maxSpeed && speed > 0.0001f) { float scale = maxSpeed / speed; velocity.x *= scale; velocity.z *= scale; }
    }
    public void setHorizontalVelocity(float vx, float vz) { velocity.x = vx; velocity.z = vz; }
    public float getHeight() {
        return sneaking ? com.za.zenith.world.physics.PhysicsSettings.getInstance().sneakingHeight : 
                         com.za.zenith.world.physics.PhysicsSettings.getInstance().standingHeight;
    }

    public float getHunger() { return hunger; }

    public Inventory getInventory() { return inventory; }
    public com.za.zenith.engine.core.PlayerMode getMode() { return mode; }
    public void setMode(com.za.zenith.engine.core.PlayerMode mode) { this.mode = mode; }

    public float getStat(com.za.zenith.utils.Identifier statId) {
        float total = getStats().get(statId);
        
        // Add active hand item bonus
        ItemStack held = inventory.getSelectedItemStack();
        if (held != null) {
            total += held.getStat(statId);
        }
        
        // Weight penalty for mobility: every 10kg above 5kg reduces mobility by 5
        if (statId.equals(com.za.zenith.world.items.stats.StatRegistry.MOBILITY)) {
            float totalWeight = 0;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (stack != null) {
                    totalWeight += stack.getItem().getWeight() * stack.getCount();
                }
            }
            float penalty = Math.max(0, (totalWeight - 5.0f) * 0.5f); // 5kg free, then 0.5 mobility per kg
            total = Math.max(1, total - penalty); // Never drop below 1 mobility
        }

        return total;
    }

    private void updateEquipmentStats() {
        // Clear old equipment modifiers
        getStats().removeModifiersFrom(com.za.zenith.utils.Identifier.of("zenith:equipment"));

        // 1. Sum stats from all equipment slots (PASSIVE bonuses)
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack == null) continue;

            // Stats from EQUIPMENT slots apply fully
            if (i >= Inventory.START_EQUIPMENT && i < Inventory.START_EQUIPMENT + Inventory.EQUIPMENT_SIZE) {
                for (com.za.zenith.world.items.stats.StatDefinition def : com.za.zenith.world.items.stats.StatRegistry.getAll()) {
                    float value = stack.getStat(def.identifier());
                    if (value != 0) {
                        getStats().addModifier(def.identifier(), new com.za.zenith.world.items.stats.StatModifier(
                            com.za.zenith.utils.Identifier.of("zenith:equipment"),
                            com.za.zenith.world.items.stats.StatModifier.Operation.ADD,
                            value
                        ));
                    }
                }
            }
        }
    }

    public float getImpact() {
        return getStat(com.za.zenith.world.items.stats.StatRegistry.IMPACT);
    }

    public float getAttackDamage() {
        // Base punch damage (1.0) + Impact bonus (1.0 per 10 impact)
        return 1.0f + (getImpact() / 10.0f);
    }

    public float getMobilityMultiplier() {
        // 10 mobility = 1.0 speed, 20 = 2.0 speed (scaled for zero-to-hero)
        return getStat(com.za.zenith.world.items.stats.StatRegistry.MOBILITY) / 10.0f;
    }

    public float getDefense() {
        return getStat(com.za.zenith.world.items.stats.StatRegistry.DEFENSE);
    }
}


