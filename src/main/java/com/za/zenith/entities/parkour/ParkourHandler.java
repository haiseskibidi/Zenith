package com.za.zenith.entities.parkour;

import com.za.zenith.entities.Player;
import com.za.zenith.world.World;
import com.za.zenith.world.physics.PhysicsSettings;
import com.za.zenith.world.physics.AABB;
import org.joml.Vector3f;

/**
 * Advanced parkour handler with alternating ledge grab sides.
 */
public class ParkourHandler {
    public enum ParkourState {
        NONE,
        GRABBING,
        HANGING,
        CLIMBING
    }

    private ParkourState state = ParkourState.NONE;
    private float transitionTimer = 0.0f;
    private float baseYaw = 0.0f;
    private float climbSide = 1.0f; // 1.0 = right, -1.0 = left
    private boolean wasFlyingBeforeParkour = false;
    
    private final Vector3f startTransitionPosition = new Vector3f();
    private final Vector3f hangingPosition = new Vector3f();
    private final Vector3f targetClimbPosition = new Vector3f();
    
    private static final float GRAB_TRANSITION_TIME = 0.25f; // Matches grabbing.json

    public void update(Player player, float deltaTime, World world) {
        PhysicsSettings settings = PhysicsSettings.getInstance();
        com.za.zenith.entities.parkour.animation.AnimationProfile grabAnim = com.za.zenith.entities.parkour.animation.AnimationRegistry.get("grabbing");
        com.za.zenith.entities.parkour.animation.AnimationProfile climbAnim = com.za.zenith.entities.parkour.animation.AnimationRegistry.get("climbing");

        if (state == ParkourState.GRABBING) {
            transitionTimer += deltaTime;
            float duration = (grabAnim != null) ? grabAnim.getDuration() : GRAB_TRANSITION_TIME;
            float t = Math.min(1.0f, transitionTimer / duration);
            
            String interp = (grabAnim != null) ? grabAnim.getPathInterpolation() : "smoothstep";
            float easedT = interpolate(t, interp);
            
            player.getPosition().set(new Vector3f(startTransitionPosition).lerp(hangingPosition, easedT));
            player.getVelocity().set(0, 0, 0);
            player.setFlying(true);

            if (t >= 1.0f) {
                setState(player, ParkourState.HANGING);
            }
        } else if (state == ParkourState.HANGING) {
            if (player.getStamina() <= 0.01f) {
                setState(player, ParkourState.NONE);
                float pushBack = 0.15f;
                player.getVelocity().x = (float) Math.sin(-baseYaw) * -pushBack;
                player.getVelocity().y = -0.5f; // Small detach impulse
                player.getVelocity().z = (float) Math.cos(-baseYaw) * pushBack;
                return;
            }
            player.getVelocity().set(0, 0, 0);
            player.getPosition().set(hangingPosition);
            player.setFlying(true);
        } else if (state == ParkourState.CLIMBING) {
            transitionTimer += deltaTime;
            float duration = settings.climbDuration;
            float t = Math.min(1.0f, transitionTimer / duration);

            String interp = (climbAnim != null) ? climbAnim.getPathInterpolation() : "smootherstep";
            float smoothT = interpolate(t, interp);

            float p0x = startTransitionPosition.x;
            float p0y = startTransitionPosition.y;
            float p0z = startTransitionPosition.z;

            float apexOffset = (climbAnim != null) ? climbAnim.getApexYOffset() : 0.25f;
            float p1x = startTransitionPosition.x;
            float p1y = targetClimbPosition.y + apexOffset; 
            float p1z = startTransitionPosition.z;

            float p2x = targetClimbPosition.x;
            float p2y = targetClimbPosition.y;
            float p2z = targetClimbPosition.z;

            float invT = 1.0f - smoothT;
            player.getPosition().x = invT * invT * p0x + 2 * invT * smoothT * p1x + smoothT * smoothT * p2x;
            player.getPosition().y = invT * invT * p0y + 2 * invT * smoothT * p1y + smoothT * smoothT * p2y;
            player.getPosition().z = invT * invT * p0z + 2 * invT * smoothT * p1z + smoothT * smoothT * p2z;

            player.getVelocity().set(0, 0, 0);
            player.setFlying(true);

            if (t >= 1.0f) {
                player.getPosition().set(targetClimbPosition);
                setState(player, ParkourState.NONE);
            }
        }
    }

    private float interpolate(float t, String type) {
        return switch (type.toLowerCase()) {
            case "smoothstep" -> t * t * (3 - 2 * t);
            case "smootherstep" -> t * t * t * (t * (t * 6 - 15) + 10);
            case "sine" -> (float) Math.sin(t * Math.PI / 2.0);
            case "quad_in" -> t * t;
            case "quad_out" -> 1.0f - (1.0f - t) * (1.0f - t);
            default -> t;
        };
    }

    public void tryLedgeGrab(Player player, World world, Vector3f lookDir) {
        if (state != ParkourState.NONE || player.isOnGround()) return;

        PhysicsSettings settings = PhysicsSettings.getInstance();
        Vector3f horizontalDir = new Vector3f(lookDir.x, 0, lookDir.z).normalize();
        Vector3f playerPos = player.getPosition();

        for (float h = settings.maxGrabHeight; h >= settings.minGrabHeight; h -= 0.1f) {
            Vector3f rayStart = new Vector3f(playerPos).add(0, h, 0);
            Vector3f rayEnd = new Vector3f(rayStart).add(new Vector3f(horizontalDir).mul(settings.grabDistance));

            int bx = (int) Math.floor(rayEnd.x);
            int by = (int) Math.floor(rayEnd.y);
            int bz = (int) Math.floor(rayEnd.z);

            // Determine the block in front of the ledge (where the player's body will hang)
            int fbx = bx;
            int fbz = bz;
            if (Math.abs(horizontalDir.x) > Math.abs(horizontalDir.z)) {
                fbx -= (int) Math.signum(horizontalDir.x);
            } else {
                fbz -= (int) Math.signum(horizontalDir.z);
            }

            // Require at least a 2-block high vertical surface and ensure there's room to hang.
            // This prevents grabbing 1-block high obstacles sitting on the ground because
            // the block at (fbx, by - 2, fbz) would be the ground (solid).
            if (world.getBlock(bx, by, bz).isAir() &&                     // Room above ledge
                world.getBlock(bx, by - 1, bz).isSolid() &&              // The ledge
                world.getBlock(bx, by - 2, bz).isSolid() &&              // The wall below
                world.getBlock(fbx, by - 1, fbz).isAir() &&              // Room for arms/head
                world.getBlock(fbx, by - 2, fbz).isAir()) {               // Room for torso/feet
                
                if (canClimbTo(player, world, rayEnd)) {
                    float offsetFromCenter = 0.5f + (settings.playerWidth / 2.0f) + 0.05f;
                    
                    hangingPosition.set(playerPos.x, (float) by - 1.35f, playerPos.z);
                    float blockCenterX = bx + 0.5f;
                    float blockCenterZ = bz + 0.5f;
                    
                    // NEW: Snap to the closest face to prevent clipping when looking diagonally
                    if (Math.abs(horizontalDir.x) > Math.abs(horizontalDir.z)) {
                        // Snapping to East/West face
                        float sign = Math.signum(horizontalDir.x);
                        hangingPosition.x = blockCenterX - sign * offsetFromCenter;
                        hangingPosition.z = blockCenterZ; // Center on the block face
                        baseYaw = (sign > 0) ? (float)-Math.PI/2 : (float)Math.PI/2;
                    } else {
                        // Snapping to North/South face
                        float sign = Math.signum(horizontalDir.z);
                        hangingPosition.z = blockCenterZ - sign * offsetFromCenter;
                        hangingPosition.x = blockCenterX; // Center on the block face
                        baseYaw = (sign > 0) ? (float)-Math.PI : 0.0f;
                    }

                    startTransitionPosition.set(playerPos);
                    transitionTimer = 0;
                    wasFlyingBeforeParkour = player.isFlying(); // REMEMBER STATE
                    state = ParkourState.GRABBING;
                    return;
                }
            }
        }
    }

    private boolean canClimbTo(Player player, World world, Vector3f targetPos) {
        PhysicsSettings settings = PhysicsSettings.getInstance();
        float tx = (float) Math.floor(targetPos.x) + 0.5f;
        float ty = (float) Math.floor(targetPos.y);
        float tz = (float) Math.floor(targetPos.z) + 0.5f;

        AABB testBox = new AABB(
            -settings.playerWidth / 2, 0.05f, -settings.playerWidth / 2,
            settings.playerWidth / 2, settings.standingHeight, settings.playerWidth / 2
        ).offset(tx, ty, tz);

        int minX = (int) Math.floor(testBox.minX());
        int maxX = (int) Math.floor(testBox.maxX());
        int minY = (int) Math.floor(testBox.minY());
        int maxY = (int) Math.floor(testBox.maxY());
        int minZ = (int) Math.floor(testBox.minZ());
        int maxZ = (int) Math.floor(testBox.maxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlock(x, y, z).isSolid()) return false;
                }
            }
        }
        return true;
    }

    public void startClimb(Player player) {
        if (state != ParkourState.HANGING) return;

        PhysicsSettings settings = PhysicsSettings.getInstance();
        startTransitionPosition.set(player.getPosition());
        
        // NO TOGGLE HERE - Keep the same side as the grab!

        float forwardX = (float) Math.sin(-baseYaw);
        float forwardZ = -(float) Math.cos(-baseYaw);
        
        targetClimbPosition.set(
            (float) Math.floor(player.getPosition().x + forwardX * 0.8f) + 0.5f,
            (float) Math.floor(player.getPosition().y + 1.45f),
            (float) Math.floor(player.getPosition().z + forwardZ * 0.8f) + 0.5f
        );

        transitionTimer = 0;
        setState(player, ParkourState.CLIMBING);
    }

    public float getClimbSide() {
        return climbSide;
    }

    public float getProgress() {
        if (state == ParkourState.GRABBING) {
            com.za.zenith.entities.parkour.animation.AnimationProfile grabAnim = com.za.zenith.entities.parkour.animation.AnimationRegistry.get("grabbing");
            float duration = (grabAnim != null) ? grabAnim.getDuration() : GRAB_TRANSITION_TIME;
            return Math.min(1.0f, transitionTimer / duration);
        }
        if (state == ParkourState.CLIMBING) {
            return Math.min(1.0f, transitionTimer / PhysicsSettings.getInstance().climbDuration);
        }
        return 0.0f;
    }

    public boolean isRestrictingCamera() {
        return state == ParkourState.HANGING || state == ParkourState.GRABBING || state == ParkourState.CLIMBING;
    }

    public boolean isHanging() {
        return state == ParkourState.HANGING || state == ParkourState.GRABBING;
    }

    public boolean isClimbing() {
        return state == ParkourState.CLIMBING;
    }

    public boolean isInParkour() {
        return state != ParkourState.NONE;
    }

    public void cancel(Player player) {
        setState(player, ParkourState.NONE);
    }

    public float getBaseYaw() { return baseYaw; }

    public ParkourState getState() { return state; }

    public void setState(Player player, ParkourState newState) {
        if (this.state == ParkourState.HANGING) {
            player.stopAction(com.za.zenith.utils.Identifier.of("zenith:hang"));
        } else if (this.state == ParkourState.CLIMBING) {
            player.stopAction(com.za.zenith.utils.Identifier.of("zenith:climb"));
        }

        this.state = newState;

        if (newState == ParkourState.HANGING) {
            player.startAction(com.za.zenith.utils.Identifier.of("zenith:hang"));
        } else if (newState == ParkourState.CLIMBING) {
            player.startAction(com.za.zenith.utils.Identifier.of("zenith:climb"));
        }

        if (newState == ParkourState.NONE) {
            player.setFlying(wasFlyingBeforeParkour);
        }
    }
}


