package com.za.zenith.entities;

import com.za.zenith.entities.components.HealthComponent;
import org.joml.Vector3f;

/**
 * Base class for all living entities (Players, Mobs).
 * Adds health and combat mechanics via HealthComponent delegator (ECS Lite).
 */
public abstract class LivingEntity extends Entity {

    public LivingEntity(Vector3f position, float width, float height, float maxHealth) {
        super(position, width, height);
        this.addComponent(new HealthComponent(maxHealth));
    }

    public void takeDamage(float amount) {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        if (healthComp != null) {
            healthComp.takeDamage(amount);
        }
    }

    public float getDefense() {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null ? healthComp.getDefense() : 0.0f;
    }

    public float getStat(com.za.zenith.utils.Identifier statId) {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null ? healthComp.getStat(statId) : 0.0f;
    }

    public com.za.zenith.world.items.stats.StatContainer getStats() {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null ? healthComp.getStats() : null;
    }

    public void heal(float amount) {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        if (healthComp != null) {
            healthComp.heal(amount);
        }
    }

    public boolean isDead() {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null && healthComp.isDead();
    }

    public float getHealth() {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null ? healthComp.getHealth() : 0.0f;
    }

    public float getMaxHealth() {
        HealthComponent healthComp = getComponent(HealthComponent.class);
        return healthComp != null ? healthComp.getMaxHealth() : 0.0f;
    }

    @Override
    protected void onUpdate(float deltaTime, com.za.zenith.world.World world) {
        float submersion = getSubmersionRatio(world);
        if (submersion > 0.0f) {
            float fluidGravity = GRAVITY * (1.0f - submersion * 0.8f);
            velocity.y = Math.max(velocity.y + fluidGravity * deltaTime, -4.0f);
            float fluidDrag = 0.8f * submersion;
            velocity.x *= (1.0f - fluidDrag * deltaTime);
            velocity.z *= (1.0f - fluidDrag * deltaTime);
            velocity.y *= (1.0f - fluidDrag * deltaTime);
            
            // Применяем снос течением
            com.za.zenith.world.blocks.Block b = getFluidBlock();
            if (b != null) {
                com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
                float flowForce = 1.4f;
                if (def != null && def.isFluid()) {
                    String fluidType = def.getFluidType();
                    if ("oil".equals(fluidType) || "lava".equals(fluidType)) {
                        flowForce = 0.5f;
                    }
                }
                org.joml.Vector3f flowVec = world.getInterpolatedFluidFlowVector(position.x, position.y + 0.5f, position.z, b.getType());

                velocity.x += flowVec.x * flowForce * submersion * deltaTime;
                velocity.z += flowVec.z * flowForce * submersion * deltaTime;
                if (flowVec.y < 0) {
                    velocity.y = Math.max(velocity.y + flowVec.y * flowForce * submersion * deltaTime * 2.0f, -6.0f);
                }
            }
        } else {
            applyGravity(deltaTime);
        }
        move(world, velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);
    }
}
