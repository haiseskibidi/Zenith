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
        applyGravity(deltaTime);
        move(world, velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);
    }
}
