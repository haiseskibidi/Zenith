package com.za.zenith.entities;

import com.za.zenith.entities.ai.AIState;
import com.za.zenith.entities.components.AIComponent;
import com.za.zenith.world.World;
import org.joml.Vector3f;

/**
 * A basic infected scout. 
 * Behavior is entirely delegated to AIComponent (ECS Lite).
 */
public class ScoutEntity extends LivingEntity {
    private static final float SCOUT_WIDTH = 0.6f;
    private static final float SCOUT_HEIGHT = 1.8f;
    private static final float WANDER_SPEED = 2.0f;
    private static final float CHASE_SPEED = 4.5f;
    private static final float HEARING_RADIUS = 32.0f;
    private static final float DETECTION_THRESHOLD = 0.15f; // Player noise needed to detect at distance

    public ScoutEntity(Vector3f position) {
        super(position, SCOUT_WIDTH, SCOUT_HEIGHT, 15.0f);
        this.addComponent(new AIComponent(
            WANDER_SPEED, CHASE_SPEED, HEARING_RADIUS, DETECTION_THRESHOLD
        ));
    }

    @Override
    protected void onUpdate(float deltaTime, World world) {
        applyGravity(deltaTime);
        move(world, velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);
        // AI logic is updated automatically via components in Entity.update()
    }

    public AIState getCurrentState() {
        AIComponent ai = getComponent(AIComponent.class);
        return ai != null ? ai.getCurrentState() : AIState.WANDER;
    }
}
