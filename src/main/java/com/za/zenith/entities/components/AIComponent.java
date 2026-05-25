package com.za.zenith.entities.components;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.Player;
import com.za.zenith.entities.ai.AIState;
import com.za.zenith.world.World;
import org.joml.Vector3f;
import java.util.Random;

/**
 * Component managing AI states, perception, decision-making, and goal-directed movement.
 */
public class AIComponent implements EntityComponent {
    private AIState currentState = AIState.WANDER;
    private final Vector3f targetLocation = new Vector3f();
    private final Random random = new Random();
    private float stateTimer = 0;
    
    private final float wanderSpeed;
    private final float chaseSpeed;
    private final float hearingRadius;
    private final float detectionThreshold;

    public AIComponent(float wanderSpeed, float chaseSpeed, float hearingRadius, float detectionThreshold) {
        this.wanderSpeed = wanderSpeed;
        this.chaseSpeed = chaseSpeed;
        this.hearingRadius = hearingRadius;
        this.detectionThreshold = detectionThreshold;
    }

    @Override
    public void update(Entity entity, float deltaTime, World world) {
        Player player = world.getPlayer();
        if (player == null) return;

        float distToPlayer = entity.getPosition().distance(player.getPosition());
        stateTimer -= deltaTime;

        // 1. Perception: Hearing
        float perceivedNoise = world.getNoiseLevelAt(entity.getPosition());
        if (perceivedNoise > detectionThreshold) {
            if (currentState != AIState.CHASE) {
                currentState = AIState.SEARCH;
                if (player.getNoiseLevel() > 0.1f) {
                    targetLocation.set(player.getPosition());
                } else {
                    targetLocation.set(player.getPosition());
                }
                stateTimer = 5.0f; // Search for 5 seconds
            }
        }

        // 2. Perception: Visual (distance-based)
        float visibilityRange = player.isSneaking() ? 4.0f : 14.0f;
        if (distToPlayer < visibilityRange) {
            currentState = AIState.CHASE;
        }

        // 3. State Actions
        switch (currentState) {
            case WANDER:
                if (stateTimer <= 0) {
                    float rx = (random.nextFloat() - 0.5f) * 20.0f;
                    float rz = (random.nextFloat() - 0.5f) * 20.0f;
                    targetLocation.set(entity.getPosition().x + rx, entity.getPosition().y, entity.getPosition().z + rz);
                    stateTimer = 3.0f + random.nextFloat() * 5.0f;
                }
                moveToTarget(entity, wanderSpeed);
                break;

            case SEARCH:
                moveToTarget(entity, wanderSpeed);
                if (stateTimer <= 0) {
                    currentState = AIState.WANDER;
                    stateTimer = 2.0f;
                }
                break;

            case CHASE:
                targetLocation.set(player.getPosition());
                moveToTarget(entity, chaseSpeed);
                
                // Lose interest if player is too far or hidden
                boolean tooFar = distToPlayer > hearingRadius;
                boolean lostSight = player.isSneaking() && distToPlayer > 6.0f;
                
                if (tooFar || lostSight) {
                    currentState = AIState.SEARCH;
                    stateTimer = 7.0f; // Look around for 7 seconds
                }
                break;
                
            case IDLE:
                entity.getVelocity().x = 0;
                entity.getVelocity().z = 0;
                if (stateTimer <= 0) currentState = AIState.WANDER;
                break;
        }
    }

    private void moveToTarget(Entity entity, float speed) {
        Vector3f dir = new Vector3f(targetLocation).sub(entity.getPosition());
        dir.y = 0; // Only horizontal movement
        if (dir.lengthSquared() > 0.01f) {
            dir.normalize().mul(speed);
            entity.getVelocity().x = dir.x;
            entity.getVelocity().z = dir.z;
            
            // Set rotation to face direction of movement
            entity.getRotation().y = (float) Math.atan2(dir.x, dir.z);
        } else {
            entity.getVelocity().x = 0;
            entity.getVelocity().z = 0;
        }
    }

    public AIState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(AIState currentState) {
        this.currentState = currentState;
    }
}
