package com.za.zenith.entities;

import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.entities.components.InventoryComponent;
import com.za.zenith.entities.components.PlayerSurvivalComponent;
import com.za.zenith.world.physics.PhysicsSettings;
import org.joml.Vector3f;

/**
 * AAA Player Entity.
 * Coordinates movement physics, inventory, survival components and viewmodel animations.
 */
public class Player extends LivingEntity {
    private final Inventory inventory;
    private com.za.zenith.engine.core.PlayerMode mode = com.za.zenith.engine.core.PlayerMode.SURVIVAL;
    private final com.za.zenith.entities.parkour.ParkourHandler parkourHandler = new com.za.zenith.entities.parkour.ParkourHandler();
    private final PlayerViewmodelAnimator viewmodelAnimator;

    private boolean moving = false;
    private boolean sprinting = false;
    private boolean sneaking = false;
    private float preUpdateVelocityY = 0.0f;
    private float waterExitGraceTimer = 0.0f;
    private float waterExitBoostTimer = 0.0f;
    private float currentEyeHeight;

    public Player(Vector3f startPosition) {
        super(startPosition, 
              com.za.zenith.world.physics.PhysicsSettings.getInstance().playerWidth, 
              com.za.zenith.world.physics.PhysicsSettings.getInstance().standingHeight, 
              20.0f);
        this.inventory = new Inventory();
        this.addComponent(new InventoryComponent(this.inventory));
        this.addComponent(new PlayerSurvivalComponent());
        
        PhysicsSettings settings = PhysicsSettings.getInstance();
        this.stepHeight = settings.stepHeight;
        this.stepUpMode = settings.stepUpMode; 
        this.currentEyeHeight = settings.standingEyeHeight;

        this.viewmodelAnimator = new PlayerViewmodelAnimator();
    }
    
    public PlayerViewmodelAnimator getViewmodelAnimator() { return viewmodelAnimator; }
    public com.za.zenith.engine.graphics.model.Viewmodel getViewmodel() { return viewmodelAnimator.getViewmodel(); }
    
    @Override
    protected void onUpdate(float deltaTime, World world) {
        preUpdateVelocityY = velocity.y;
        
        if (!flying) {
            float submersion = getSubmersionRatio(world);
            if (submersion > 0.0f) {
                float fluidGravity = GRAVITY * (1.0f - submersion * 0.8f);
                velocity.y = Math.max(velocity.y + fluidGravity * deltaTime, -4.0f);
                
                float fluidDrag = 0.8f * submersion;
                velocity.x *= (1.0f - fluidDrag * deltaTime);
                velocity.z *= (1.0f - fluidDrag * deltaTime);
                velocity.y *= (1.0f - fluidDrag * deltaTime);

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

        updateEquipmentStats();

        // water exit grace boost
        if (isInWater()) {
            waterExitGraceTimer = 0.4f;
        } else if (waterExitGraceTimer > 0.0f) {
            waterExitGraceTimer -= deltaTime;
        }

        if (waterExitGraceTimer > 0.0f && horizontalCollision && moving) {
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
        
        inventory.update(world, this, com.za.zenith.engine.core.GameLoop.getInstance().getCamera());
        updateSneakState(world, deltaTime);
        parkourHandler.update(this, deltaTime, world);
        
        // let components update (like PlayerSurvivalComponent)
        // note: super.update() updates components and sets prevPosition
        
        PhysicsSettings settings = PhysicsSettings.getInstance();
        float targetEyeHeight = (sneaking || boundingBox.getMax().y < settings.standingHeight - 0.01f) 
            ? settings.sneakingEyeHeight 
            : settings.standingEyeHeight;
        currentEyeHeight += (targetEyeHeight - currentEyeHeight) * 10.0f * deltaTime;

        // delegate views and animation updates
        viewmodelAnimator.update(this, deltaTime, world);
    }
    public void updateAnimations(float deltaTime, World world) {
        // kept for backward compatibility, update is done inside viewmodelAnimator
    }
    public void setCurrentAlpha(float alpha) { viewmodelAnimator.setCurrentAlpha(alpha); }
    public com.za.zenith.entities.parkour.ParkourHandler getParkourHandler() { return parkourHandler; }
    public float getEyeHeight() { return currentEyeHeight; }

    public void updateSneakState(World world, float deltaTime) {
        com.za.zenith.world.physics.PhysicsSettings settings = com.za.zenith.world.physics.PhysicsSettings.getInstance();
        if (!sneaking && boundingBox.getMax().y < settings.standingHeight) {
            if (canStandUp(world)) setBoundingBox(settings.playerWidth, settings.standingHeight);
        } else if (sneaking && boundingBox.getMax().y > settings.sneakingHeight) {
            setBoundingBox(settings.playerWidth, settings.sneakingHeight);
        }
    }

    private boolean canStandUp(World world) {
        com.za.zenith.world.physics.PhysicsSettings settings = com.za.zenith.world.physics.PhysicsSettings.getInstance();
        
        float playerHalfWidth = settings.playerWidth / 2.0f;
        float minX = position.x - playerHalfWidth;
        float maxX = position.x + playerHalfWidth;
        float minY = position.y;
        float maxY = position.y + settings.standingHeight;
        float minZ = position.z - playerHalfWidth;
        float maxZ = position.z + playerHalfWidth;

        int gridMinX = (int) Math.floor(minX);
        int gridMaxX = (int) Math.floor(maxX);
        int gridMinY = (int) Math.floor(minY);
        int gridMaxY = (int) Math.floor(maxY);
        int gridMinZ = (int) Math.floor(minZ);
        int gridMaxZ = (int) Math.floor(maxZ);

        for (int x = gridMinX; x <= gridMaxX; x++) {
            for (int y = gridMinY; y <= gridMaxY; y++) {
                for (int z = gridMinZ; z <= gridMaxZ; z++) {
                    com.za.zenith.world.blocks.Block b = world.getBlock(x, y, z);
                    if (!b.isAir() && b.isSolid()) {
                        com.za.zenith.world.physics.VoxelShape shape = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType()).getShape(b.getMetadata());
                        if (shape != null) {
                            for (com.za.zenith.world.physics.AABB box : shape.getBoxes()) {
                                float bMinX = box.minX() + x;
                                float bMaxX = box.maxX() + x;
                                float bMinY = box.minY() + y;
                                float bMaxY = box.maxY() + y;
                                float bMinZ = box.minZ() + z;
                                float bMaxZ = box.maxZ() + z;

                                // Строгая проверка пересечения AABB стоящего игрока с рамкой коллизии блока
                                if (maxX > bMinX && minX < bMaxX &&
                                    maxY > bMinY && minY < bMaxY &&
                                    maxZ > bMinZ && minZ < bMaxZ) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    public float getCameraPitchOffset() { return viewmodelAnimator.getCameraPitchOffset(); }
    public float getCameraRollOffset() { return viewmodelAnimator.getCameraRollOffset(); }
    public float getFovOffset() { return viewmodelAnimator.getFovOffset(); }
    public float getCameraOffsetX() { return viewmodelAnimator.getCameraOffsetX(); }
    public float getCameraOffsetY() { return viewmodelAnimator.getCameraOffsetY(); }
    public float getCameraOffsetZ() { return 0.0f; } 
    public float getItemOffsetX() { return viewmodelAnimator.getItemOffsetX(); }
    public float getItemOffsetY() { return viewmodelAnimator.getItemOffsetY(); }
    public float getItemOffsetZ() { return viewmodelAnimator.getItemOffsetZ(); }
    public float getItemPitchOffset() { return viewmodelAnimator.getItemPitchOffset(); }
    public float getItemYawOffset() { return viewmodelAnimator.getItemYawOffset(); }
    public float getItemRollOffset() { return viewmodelAnimator.getItemRollOffset(); }
    
    public void swing() { viewmodelAnimator.swing(); }
    public void swing(float duration) { viewmodelAnimator.swing(duration); }
    public void interact() { viewmodelAnimator.interact(); }
    public void interact(float duration) { viewmodelAnimator.interact(duration); }
    public void place() { viewmodelAnimator.place(); }
    public void place(float duration) { viewmodelAnimator.place(duration); }
    public boolean isSwinging() { return viewmodelAnimator.isSwinging(); }

    public boolean isMoving() { return moving; }
    public boolean isSneaking() { return sneaking; }
    public boolean isSprinting() { return sprinting; }
    public float getPreUpdateVelocityY() { return preUpdateVelocityY; }

    public float getOxygen() { return getComponent(PlayerSurvivalComponent.class).getOxygen(); }
    public float getMaxOxygen() { return PlayerSurvivalComponent.MAX_OXYGEN; }
    public boolean isInRain() { return false; }

    public void startAction(com.za.zenith.utils.Identifier id) { getComponent(PlayerSurvivalComponent.class).startAction(id); }
    public void stopAction(com.za.zenith.utils.Identifier id) { getComponent(PlayerSurvivalComponent.class).stopAction(id); }
    public void performDiscreteAction(com.za.zenith.utils.Identifier id) { getComponent(PlayerSurvivalComponent.class).performDiscreteAction(this, id); }

    public void addNoise(float amount) { getComponent(PlayerSurvivalComponent.class).addNoise(amount); }
    public void setContinuousNoise(float level) { getComponent(PlayerSurvivalComponent.class).setContinuousNoise(level); }
    public float getNoiseLevel() { return getComponent(PlayerSurvivalComponent.class).getNoiseLevel(); }
    public void setSneaking(boolean sneaking) { this.sneaking = sneaking; }
    public boolean isPhysicallySneaking() { return boundingBox.getMax().y < com.za.zenith.world.physics.PhysicsSettings.getInstance().standingHeight - 0.01f; }
    public void setMoving(boolean moving) { this.moving = moving; }
    public void setSprinting(boolean sprinting) { this.sprinting = sprinting; }
    public float getStamina() { return getComponent(PlayerSurvivalComponent.class).getStamina(); }
    public void setStamina(float stamina) { getComponent(PlayerSurvivalComponent.class).setStamina(stamina); }
    public float getMiningSpeedMultiplier() { return 1.0f; }

    public void addDirt(float amount) { getComponent(PlayerSurvivalComponent.class).addDirt(amount); }
    public void addBlood(float amount) { getComponent(PlayerSurvivalComponent.class).addBlood(amount); }
    public void washHands() { getComponent(PlayerSurvivalComponent.class).washHands(); }
    public float getDirt() { return getComponent(PlayerSurvivalComponent.class).getDirt(); }
    public float getBlood() { return getComponent(PlayerSurvivalComponent.class).getBlood(); }
    public float getWetness() { return getComponent(PlayerSurvivalComponent.class).getWetness(); }
    public float getScentLevel() { return getComponent(PlayerSurvivalComponent.class).getScentLevel(); }
    public float getHunger() { return getComponent(PlayerSurvivalComponent.class).getHunger(); }

    public void eat(com.za.zenith.world.items.Item item) { getComponent(PlayerSurvivalComponent.class).eat(this, item); }
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

    public Inventory getInventory() { return inventory; }
    public com.za.zenith.engine.core.PlayerMode getMode() { return mode; }
    public void setMode(com.za.zenith.engine.core.PlayerMode mode) { this.mode = mode; }

    public float getStat(com.za.zenith.utils.Identifier statId) { return PlayerStatsCalculator.calculateStat(this, statId); }
    private void updateEquipmentStats() { PlayerStatsCalculator.updateEquipmentStats(this); }

    public float getImpact() { return getStat(com.za.zenith.world.items.stats.StatRegistry.IMPACT); }
    public float getAttackDamage() { return 1.0f + (getImpact() / 10.0f); }
    public float getMobilityMultiplier() { return getStat(com.za.zenith.world.items.stats.StatRegistry.MOBILITY) / 10.0f; }
    public float getDefense() { return getStat(com.za.zenith.world.items.stats.StatRegistry.DEFENSE); }
}
