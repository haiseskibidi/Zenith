package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.PlayerMode;
import com.za.zenith.engine.core.SettingsManager;
import com.za.zenith.engine.core.Window;
import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.entities.Player;
import com.za.zenith.world.physics.PhysicsSettings;
import com.za.zenith.world.World;
import com.za.zenith.engine.input.InputAction;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Обрабатывает перемещение игрока, вращение камеры и паркур.
 * Полностью интегрирован со слоем InputAction, исключая локальное отслеживание состояний.
 */
public class MovementInputHandler {
    
    public void update(Window window, Camera camera, Player player, float deltaTime, com.za.zenith.engine.input.InputManager manager) {
        boolean anyScreen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().isAnyScreenOpen();
        boolean nappingOpen = GameLoop.getInstance().isNappingOpen();
        
        com.za.zenith.world.physics.PhysicsSettings settings = PhysicsSettings.getInstance();
        com.za.zenith.entities.parkour.ParkourHandler parkour = player.getParkourHandler();

        Vector2f currentPos = manager.getCurrentMousePos();
        Vector2f previousPos = manager.getPreviousMousePos();
        
        Vector2f rotVec = new Vector2f();
        if (manager.isFirstMouse()) {
            previousPos.x = currentPos.x;
            previousPos.y = currentPos.y;
            manager.setFirstMouse(false);
        } else if (manager.isInWindow() && !anyScreen && !nappingOpen) {
            double deltaX = currentPos.x - previousPos.x;
            double deltaY = currentPos.y - previousPos.y;
            rotVec.y = (float) -deltaX;
            rotVec.x = (float) -deltaY;
        }
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;

        if (!anyScreen && !nappingOpen) {
            float baseSens = 0.002f; // Base sensitivity for 800 DPI
            float currentSens = SettingsManager.getInstance().getMouseSensitivity() * baseSens;
            float deltaPitch = rotVec.x * currentSens;
            float deltaYaw = rotVec.y * currentSens;

            if (parkour.isRestrictingCamera()) {
                float baseYaw = parkour.getBaseYaw();
                Vector3f currentRot = camera.getRotation();
                
                // Clamp Pitch (X)
                float newPitch = currentRot.x + deltaPitch;
                newPitch = Math.max(-1.4f, Math.min(1.2f, newPitch));
                camera.getRotation().x = newPitch;

                // Clamp Yaw (Y) relative to baseYaw
                float newYaw = currentRot.y + deltaYaw;
                float relativeYaw = newYaw - baseYaw;
                
                // Normalize relativeYaw to -PI to PI
                while (relativeYaw < -Math.PI) relativeYaw += Math.PI * 2;
                while (relativeYaw > Math.PI) relativeYaw -= Math.PI * 2;
                
                float yawLimit = 1.4f; // ~80 degrees
                relativeYaw = Math.max(-yawLimit, Math.min(yawLimit, relativeYaw));
                
                camera.getRotation().y = baseYaw + relativeYaw;
            } else {
                camera.moveRotation(deltaPitch, deltaYaw, 0);
            }
        }

        camera.setOffsets(player.getCameraOffsetX(), player.getCameraOffsetY(), player.getCameraOffsetZ());
        camera.setPitchOffset(player.getCameraPitchOffset());
        camera.setRollOffset(player.getCameraRollOffset());
        camera.setFovOffset(player.getFovOffset());

        Vector2f moveVector = new Vector2f();

        if (!anyScreen && !nappingOpen) {
            if (manager.isPressed(InputAction.MOVE_FORWARD)) moveVector.y = 1;
            if (manager.isPressed(InputAction.MOVE_BACK)) moveVector.y = -1;
            if (manager.isPressed(InputAction.MOVE_LEFT)) moveVector.x = -1;
            if (manager.isPressed(InputAction.MOVE_RIGHT)) moveVector.x = 1;
        }
        
        float moveY = 0;
        boolean spaceDown = manager.isPressed(InputAction.JUMP);
        boolean spaceNewPress = manager.wasJustPressed(InputAction.JUMP);

        if (!anyScreen && !nappingOpen) {
            if (spaceDown) moveY = 1;
        }
        
        boolean shiftPressed = manager.isPressed(InputAction.SNEAK);
        if (shiftPressed && !anyScreen && !nappingOpen) moveY = -1;
        
        boolean sneaking = shiftPressed && !player.isFlying() && !anyScreen && !nappingOpen;
        player.setSneaking(sneaking);

        boolean inParkour = parkour.isInParkour();

        boolean physicallySneaking = player.isPhysicallySneaking();
        boolean sprinting = manager.isPressed(InputAction.SPRINT) && !anyScreen && !nappingOpen;
        player.setSprinting(sprinting);
        
        float baseSpeed = player.isFlying() ? settings.flySpeed : (physicallySneaking ? settings.baseMoveSpeed * settings.sneakSpeedMultiplier : settings.baseMoveSpeed);
        if (sprinting && !physicallySneaking) baseSpeed *= (player.isFlying() ? settings.flySprintMultiplier : settings.sprintMultiplier);
        
        if (player.isInWater()) {
            float dragFactor = 0.5f;
            World w = GameLoop.getInstance().getWorld();
            if (w != null) {
                com.za.zenith.world.blocks.Block b = w.getBlock((int)Math.floor(player.getPosition().x), (int)Math.floor(player.getPosition().y + 0.5f), (int)Math.floor(player.getPosition().z));
                com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
                if (def != null && def.isFluid()) {
                    if ("oil".equals(def.getFluidType()) || "lava".equals(def.getFluidType())) {
                        dragFactor = 0.25f;
                    }
                }
            }
            baseSpeed *= dragFactor;
        }

        player.setMoving(moveVector.length() > 0);
        if (player.isInWater() && !inParkour) {
            if (moveVector.length() > 0) {
                moveVector.normalize();
                float yaw = -camera.getRotation().y;
                float moveX = (float)Math.sin(yaw) * moveVector.y + (float)Math.cos(yaw) * moveVector.x;
                float moveZ = -(float)Math.cos(yaw) * moveVector.y + (float)Math.sin(yaw) * moveVector.x;
                
                float waterAccel = 2.4f; // Сила гребка в воде
                player.getVelocity().x += moveX * waterAccel * deltaTime;
                player.getVelocity().z += moveZ * waterAccel * deltaTime;
            }
            // В воде торможение ввода не требуется, трение воды плавно остановит игрока самостоятельно
        } else {
            if (moveVector.length() > 0 && !inParkour) {
                moveVector.normalize();
                float yaw = -camera.getRotation().y;
                float moveX = (float)Math.sin(yaw) * moveVector.y + (float)Math.cos(yaw) * moveVector.x;
                float moveZ = -(float)Math.cos(yaw) * moveVector.y + (float)Math.sin(yaw) * moveVector.x;
                float targetVx = moveX * baseSpeed;
                float targetVz = moveZ * baseSpeed;
                float accelGain = player.getMode() == PlayerMode.DEVELOPER ? 30.0f : (player.isFlying() ? 24.0f : 18.0f);
                player.applyHorizontalAcceleration((targetVx - player.getVelocity().x) * accelGain * deltaTime, (targetVz - player.getVelocity().z) * accelGain * deltaTime, baseSpeed);
            } else if (!inParkour) {
                float decelGain = player.isFlying() ? 20.0f : 15.0f;
                player.applyHorizontalAcceleration(-player.getVelocity().x * decelGain * deltaTime, -player.getVelocity().z * decelGain * deltaTime, baseSpeed);
            }
        }
        
        if (player.isInWater() && !inParkour) {
            float swimSpeed = 2.4f;
            World w = GameLoop.getInstance().getWorld();
            if (w != null) {
                com.za.zenith.world.blocks.Block b = player.getFluidBlock();
                if (b != null) {
                    com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(b.getType());
                    if (def != null && def.isFluid()) {
                        if ("oil".equals(def.getFluidType()) || "lava".equals(def.getFluidType())) {
                            swimSpeed = 1.2f;
                        }
                    }
                }
            }

            float swimUpForce = 0.0f;
            if (moveY > 0) {
                float submersion = player.getSubmersionRatio(w);
                float baseForce = player.isMoving() ? 45.0f : 36.0f;
                swimUpForce = baseForce * submersion;
            } else if (moveY < 0) {
                swimUpForce = -12.0f;
            }

            if (moveY != 0) {
                float limit = (moveY > 0) ? swimSpeed : -swimSpeed;
                if (moveY > 0) {
                    if (player.getVelocity().y < limit) {
                        player.getVelocity().y += swimUpForce * deltaTime;
                        if (player.getVelocity().y > limit) player.getVelocity().y = limit;
                    }
                } else {
                    if (player.getVelocity().y > limit) {
                        player.getVelocity().y += swimUpForce * deltaTime;
                        if (player.getVelocity().y < limit) player.getVelocity().y = limit;
                    }
                }
            }
        } else if (player.isFlying() && !inParkour) {
            player.addVelocity(0, (moveY * baseSpeed - player.getVelocity().y) * 25.0f * deltaTime, 0);
        }
        
        // Parkour and Jump logic
        if (!anyScreen && !nappingOpen) {
            Vector3f lookDir = new Vector3f(0, 0, -1).rotateX(camera.getRotation().x).rotateY(camera.getRotation().y).normalize();

            if (spaceNewPress) {
                if (parkour.isHanging()) {
                    parkour.startClimb(player);
                } else if (!parkour.isClimbing()) {
                    if (player.isOnGround() || player.isInWater()) {
                        player.jump();
                    } else {
                        parkour.tryLedgeGrab(player, GameLoop.getInstance().getWorld(), lookDir);
                    }
                }
            }

            if (shiftPressed && parkour.isHanging()) {
                // Add a tiny backward impulse when dropping to prevent clipping into the block
                float pushBack = 0.15f;
                float yaw = -camera.getRotation().y;
                player.getVelocity().x = (float) Math.sin(yaw) * -pushBack;
                player.getVelocity().z = (float) Math.cos(yaw) * pushBack;
                
                parkour.cancel(player);
            }
        }
    }
}