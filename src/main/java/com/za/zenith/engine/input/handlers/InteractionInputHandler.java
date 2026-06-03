package com.za.zenith.engine.input.handlers;

import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.core.Window;
import com.za.zenith.engine.graphics.Camera;
import com.za.zenith.entities.LivingEntity;
import com.za.zenith.entities.Player;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.blocks.PlacementType;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.items.Items;
import com.za.zenith.world.physics.PhysicsSettings;
import com.za.zenith.world.physics.RaycastResult;
import org.joml.Vector3f;

public class InteractionInputHandler {
    
    public RaycastResult update(Window window, Camera camera, Player player, World world, RaycastResult raycast, float deltaTime, com.za.zenith.engine.input.InputManager manager, com.za.zenith.network.GameClient networkClient) {
        boolean anyScreen = com.za.zenith.engine.graphics.ui.ScreenManager.getInstance().isAnyScreenOpen();
        boolean nappingOpen = GameLoop.getInstance().isNappingOpen();
        
        if (anyScreen || nappingOpen) {
            manager.setLootboxOpeningTimer(0);
            manager.setLootboxStack(null);
            return raycast;
        }

        ItemStack currentStack = player.getInventory().getSelectedItemStack();
        Item currentItem = currentStack != null ? currentStack.getItem() : null;

        boolean lm = manager.isPressed(com.za.zenith.engine.input.InputAction.ATTACK_MINE);
        boolean isNewLeftClick = manager.wasJustPressed(com.za.zenith.engine.input.InputAction.ATTACK_MINE);

        com.za.zenith.engine.input.MiningController miningController = manager.getMiningController();
        com.za.zenith.entities.Entity hitEntity = manager.getHitEntity();

        if (raycast.isHit()) {
            BlockPos hitPos = raycast.getBlockPos();
            if (miningController.getBreakingBlockPos() != null && !hitPos.equals(miningController.getBreakingBlockPos())) {
                miningController.stopMining();
            }
        } else {
            if (miningController.getBreakingBlockPos() != null) {
                miningController.stopMining();
            }
        }

        if (lm) {
            boolean actionConsumed = false;
            if (isNewLeftClick && hitEntity != null) {
                if (hitEntity instanceof LivingEntity) {
                    var event = com.za.zenith.engine.event.events.PlayerAttackEntityEvent.obtain(player, hitEntity, currentStack);
                    com.za.zenith.engine.event.EventBus.getInstance().publish(event);
                    event.release();
                    actionConsumed = true;
                } else if (hitEntity instanceof com.za.zenith.entities.ResourceEntity || hitEntity instanceof com.za.zenith.entities.ItemEntity) {
                    var event = com.za.zenith.engine.event.events.PlayerPickupEvent.obtain(player, hitEntity);
                    com.za.zenith.engine.event.EventBus.getInstance().publish(event);
                    event.release();
                    actionConsumed = true;
                }
            }

            if (!actionConsumed && raycast.isHit()) {
                BlockPos hitPos = raycast.getBlockPos();
                int blockType = world.getBlock(hitPos).getType();
                BlockDefinition blockDef = BlockRegistry.getBlock(blockType);
                
                float rx = raycast.getHitPoint().x - hitPos.x() - 0.5f;
                float ry = raycast.getHitPoint().y - hitPos.y();
                float rz = raycast.getHitPoint().z - hitPos.z() - 0.5f;
                Vector3f localHit = new Vector3f(rx, ry, rz);

                com.za.zenith.engine.event.events.BlockLeftClickEvent leftClickEvent = com.za.zenith.engine.event.events.BlockLeftClickEvent.obtain(
                    player, world, hitPos, currentStack, rx + 0.5f, ry, rz + 0.5f, isNewLeftClick
                );
                com.za.zenith.engine.event.EventBus.getInstance().publish(leftClickEvent);

                boolean consumed = leftClickEvent.isConsumed();
                leftClickEvent.release();

                if (consumed) {
                    manager.setLeftMousePressed(true);
                    return null; 
                }

                if (blockDef.getHardness() >= 0) {
                    if (miningController.getBreakingBlockPos() == null) {
                        miningController.startMining(hitPos, blockDef, world, raycast.getNormal());
                    }
                    miningController.mine(world, player, hitPos, blockType, blockDef, currentStack, currentItem, isNewLeftClick, localHit, raycast.getNormal());
                } else {
                    if (isNewLeftClick) {
                        player.swing();
                    }
                }
            }
        } else {
            if (raycast.isHit()) {
                Block block = world.getBlock(raycast.getBlockPos());
                float rx = raycast.getHitPoint().x - raycast.getBlockPos().x();
                float ry = raycast.getHitPoint().y - raycast.getBlockPos().y();
                float rz = raycast.getHitPoint().z - raycast.getBlockPos().z();
                miningController.renderVisuals(raycast.getBlockPos(), block, new Vector3f(rx, ry, rz), world);
            }
        }
        manager.setLeftMousePressed(lm);
        
        boolean rm = manager.isPressed(com.za.zenith.engine.input.InputAction.INTERACT_PLACE);
        boolean isNewRightClick = manager.wasJustPressed(com.za.zenith.engine.input.InputAction.INTERACT_PLACE);
        
        if (rm) {
            boolean actionConsumed = false;

            // Lootbox Opening Logic
            if (currentStack != null) {
                com.za.zenith.world.items.component.LootboxComponent lootbox = currentStack.getItem().getComponent(com.za.zenith.world.items.component.LootboxComponent.class);
                if (lootbox != null) {
                    if (isNewRightClick || manager.getLootboxStack() != currentStack) {
                        manager.setLootboxOpeningTimer(0);
                        manager.setLootboxStack(currentStack);
                        com.za.zenith.utils.Logger.info("Starting to open tactical case: %s", currentStack.getDisplayName());
                    }

                    manager.setLootboxOpeningTimer(manager.getLootboxOpeningTimer() + deltaTime);

                    if (manager.getLootboxOpeningTimer() >= lootbox.openingTime()) {
                        java.util.List<ItemStack> rewards = com.za.zenith.world.items.loot.LootGenerator.generateFromCase(currentStack);

                        // Remove one case from hand
                        if (currentStack.getCount() > 1) {
                            currentStack.setCount(currentStack.getCount() - 1);
                        } else {
                            player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), null);
                        }

                        // Add rewards
                        for (ItemStack reward : rewards) {
                            if (!player.getInventory().addItem(reward)) {
                                manager.dropStack(reward, player, world, camera, true);
                            }
                            com.za.zenith.utils.Logger.info("Unpacked reward: %s", reward.getDisplayName());
                        }

                        manager.setLootboxOpeningTimer(0);
                        manager.setLootboxStack(null);
                    }
                    actionConsumed = true;
                } else {
                    manager.setLootboxOpeningTimer(0);
                    manager.setLootboxStack(null);
                }
            } else {
                manager.setLootboxOpeningTimer(0);
                manager.setLootboxStack(null);
            }

            // Entity Interaction (RMB Pickup)
            if (!actionConsumed && isNewRightClick && hitEntity != null) {
                if (hitEntity instanceof com.za.zenith.entities.ResourceEntity || hitEntity instanceof com.za.zenith.entities.ItemEntity) {
                    com.za.zenith.engine.event.events.PlayerPickupEvent pickupEvent = com.za.zenith.engine.event.events.PlayerPickupEvent.obtain(player, hitEntity);
                    try {
                        com.za.zenith.engine.event.EventBus.getInstance().publish(pickupEvent);
                        if (pickupEvent.isConsumed()) {
                            actionConsumed = true;
                            manager.setPlaceDelayTimer(manager.PLACE_COOLDOWN);
                        }
                    } finally {
                        pickupEvent.release();
                    }
                }
            }

            if (!actionConsumed && isNewRightClick) {
                float windTime = world.getWindTime();
                Vector3f camPos = camera.getPosition();
                Vector3f dir = camera.getDirection();
                
                World.CloudInstance hitCloud = null;
                float minT = Float.MAX_VALUE;
                
                for (World.CloudInstance c : world.getActiveClouds()) {
                    if (c.isMarkedCollected()) continue;
                    
                    // Визуальное положение облака с учетом плавающего ветра (прямые мировые координаты)
                    float vx = c.x + windTime;
                    float vy = c.y;
                    float vz = c.z;
                    
                    Vector3f p = new Vector3f(vx, vy, vz);
                    float distToCam = camPos.distance(p);
                    
                    // Кликабельная дистанция взаимодействия зависит от масштаба (размера) облака
                    float maxInteractDist = c.scale * 0.8f + 8.0f;
                    if (distToCam > maxInteractDist) continue;
                    
                    Vector3f v = new Vector3f(p).sub(camPos);
                    float t = v.dot(dir);
                    if (t < 0) continue; // сзади
                    
                    Vector3f proj = new Vector3f(dir).mul(t).add(camPos);
                    float distToRay = p.distance(proj);
                    
                    // Облако считается пораженным, если луч проходит сквозь его объем
                    float interactRadius = c.scale * 0.7f;
                    if (distToRay < interactRadius && t < minT) {
                        minT = t;
                        hitCloud = c;
                    }
                }
                
                if (hitCloud != null) {
                    // 1. Сбор облака!
                    hitCloud.collect();
                    
                    // Вычисляем реальное положение для собранного облака (прямые мировые координаты)
                    float hitVx = hitCloud.x + windTime;
                    
                    // 2. Спавним красивые мультяшные частицы пара прямо в его визуальной точке!
                    com.za.zenith.engine.graphics.DynamicTextureAtlas atlas = com.za.zenith.engine.core.GameLoop.getInstance().getRenderer().getAtlas();
                    int textureLayer = 0;
                    try {
                        textureLayer = (int)atlas.getLayer("zenith/textures/block/powder_snow.png");
                    } catch (Exception e) {}
                    
                    Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f); // Чисто белый цвет пара
                    
                    // Количество частиц пропорционально размеру облака
                    int particleCount = Math.min(80, Math.max(20, (int)(hitCloud.scale * 4.0f)));
                    for (int i = 0; i < particleCount; i++) {
                        Vector3f pPos = new Vector3f(
                            hitVx + ((float)Math.random() - 0.5f) * hitCloud.scale * 0.8f,
                            hitCloud.y + ((float)Math.random() - 0.5f) * hitCloud.scale * 0.8f,
                            hitCloud.z + ((float)Math.random() - 0.5f) * hitCloud.scale * 0.8f
                        );
                        
                        Vector3f vel = new Vector3f(
                            ((float)Math.random() - 0.5f) * 6.0f,
                            ((float)Math.random() - 0.2f) * 5.0f,
                            ((float)Math.random() - 0.5f) * 6.0f
                        );
                        
                        float life = 1.0f + (float)Math.random() * 1.2f;
                        float pScale = 0.4f + (float)Math.random() * 0.6f;
                        
                        com.za.zenith.world.particles.ParticleManager.getInstance().addParticle(
                            new com.za.zenith.world.particles.ShardParticle(pPos, vel, life, pScale, textureLayer, 0, color)
                        );
                    }
                    
                    // 3. Выводим красивое уведомление о сборе облака
                    com.za.zenith.engine.graphics.ui.NotificationManager.getInstance().pushAlert("Cloud Collected!", null, 3.0f);
                    
                    actionConsumed = true;
                    manager.setPlaceDelayTimer(manager.PLACE_COOLDOWN);
                }
            }

            if (!actionConsumed && raycast.isHit() && isNewRightClick) {
                BlockPos hitPos = raycast.getBlockPos();
                int hitBlockType = world.getBlock(hitPos).getType();
                BlockDefinition blockDef = BlockRegistry.getBlock(hitBlockType);
                
                float rx = raycast.getHitPoint().x - hitPos.x();
                float ry = raycast.getHitPoint().y - hitPos.y();
                float rz = raycast.getHitPoint().z - hitPos.z();

                if (blockDef != null) {
                    if (blockDef.getCleaningAmount() > 0) {
                        if (blockDef.getCleaningAmount() >= 1.0f) {
                            player.washHands();
                            com.za.zenith.utils.Logger.info("Washed hands");
                        } else {
                            player.addDirt(-blockDef.getCleaningAmount());
                            com.za.zenith.utils.Logger.info("Cleaned hands slightly");
                        }
                        actionConsumed = true;
                    }

                    if (!actionConsumed && blockDef.onUse(world, hitPos, player, currentStack, rx, ry, rz)) {
                        actionConsumed = true;
                        manager.setPlaceDelayTimer(manager.PLACE_COOLDOWN); // Prevent accidental placement on next frame
                    }
                }
            }

            if (!actionConsumed && isNewRightClick && currentStack != null) {
                if (currentStack.getCount() >= 2) {
                    java.util.List<com.za.zenith.world.recipes.IRecipe> nappingRecipes = com.za.zenith.world.recipes.RecipeRegistry.getRecipesByType("napping");
                    
                    boolean hasNapping = false;
                    for (com.za.zenith.world.recipes.IRecipe r : nappingRecipes) {
                        com.za.zenith.world.recipes.NappingRecipe nr = (com.za.zenith.world.recipes.NappingRecipe) r;
                        if (nr.isInputValid(currentItem.getIdentifier())) {
                            hasNapping = true;
                            break;
                        }
                    }
                    
                    if (hasNapping) {
                        GameLoop.getInstance().startNapping(currentItem);
                        actionConsumed = true;
                    }
                }
            }

            if (!actionConsumed && isNewRightClick && currentItem != null && currentItem.isFood()) {
                if (player.getHunger() < 20.0f) {
                    player.eat(currentItem);
                    ItemStack newStack = currentStack.getCount() > 1 ? new ItemStack(currentItem, currentStack.getCount() - 1) : null;
                    player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), newStack);
                    actionConsumed = true;
                }
            }
            
            if (!actionConsumed && (isNewRightClick || manager.getPlaceDelayTimer() <= 0) && raycast.isHit() && !manager.isSpecialInteracting(player, raycast, currentStack)) {
                if (currentItem != null && currentItem.isBlock()) {
                    int blockType = currentItem.getId();
                    BlockDefinition def = BlockRegistry.getBlock(blockType);
                    Vector3f normal = raycast.getNormal();
                    BlockPos pPos = new BlockPos(raycast.getBlockPos().x() + (int)normal.x, raycast.getBlockPos().y() + (int)normal.y, raycast.getBlockPos().z() + (int)normal.z);
                    
                    if (!manager.isPlayerAt(player, pPos) && world.getBlock(pPos).isReplaceable()) {
                        if (def.getPlacementType() == PlacementType.DOUBLE_PLANT) {
                            BlockPos topPos = pPos.up();
                            if (world.getBlock(topPos).isReplaceable() && !manager.isPlayerAt(player, topPos)) {
                                world.setBlock(pPos, new Block(blockType, (byte)0));
                                world.setBlock(topPos, new Block(blockType, (byte)1));
                                
                                if (networkClient != null && networkClient.isConnected()) {
                                    networkClient.sendBlockUpdate(pPos.x(), pPos.y(), pPos.z(), blockType);
                                    networkClient.sendBlockUpdate(topPos.x(), topPos.y(), topPos.z(), blockType);
                                }
                                
                                ItemStack newStack = currentStack.getCount() > 1 ? new ItemStack(currentItem, currentStack.getCount() - 1) : null;
                                player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), newStack);
                                player.place();
                                manager.setPlaceDelayTimer(manager.PLACE_COOLDOWN);
                                actionConsumed = true;
                            }
                        } else {
                            byte meta = manager.calculateMetadata(blockType, normal, raycast.getHitPoint(), camera);
                            if (def.isFluid()) {
                                meta = 0; // Источники жидкостей всегда ставятся с метаданными 0
                            }
                            world.setBlock(pPos, new Block(blockType, meta));
                            if (networkClient != null && networkClient.isConnected()) networkClient.sendBlockUpdate(pPos.x(), pPos.y(), pPos.z(), blockType);
                            ItemStack newStack = currentStack.getCount() > 1 ? new ItemStack(currentItem, currentStack.getCount() - 1) : null;
                            player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), newStack);
                            player.place();
                            manager.setPlaceDelayTimer(manager.PLACE_COOLDOWN);
                            actionConsumed = true;
                        }
                    }
                }
            }
        } else {
            manager.setLootboxOpeningTimer(0);
            manager.setLootboxStack(null);
        }
        manager.setRightMousePressed(rm);

        if (player.isSneaking() && raycast.isHit() && currentItem != null && currentItem.isBlock() && !manager.isSpecialInteracting(player, raycast, currentStack)) {
            int blockType = currentItem.getId();
            Vector3f normal = raycast.getNormal();
            BlockPos pPos = new BlockPos(raycast.getBlockPos().x() + (int)normal.x, raycast.getBlockPos().y() + (int)normal.y, raycast.getBlockPos().z() + (int)normal.z);
            if (!manager.isPlayerAt(player, pPos) && world.getBlock(pPos).isReplaceable() && manager.needsPreview(blockType)) {
                byte meta = manager.calculateMetadata(blockType, normal, raycast.getHitPoint(), camera);
                GameLoop.getInstance().getRenderer().setPreviewBlock(pPos, new Block(blockType, meta));
            } else GameLoop.getInstance().getRenderer().setPreviewBlock(null, null);
        } else GameLoop.getInstance().getRenderer().setPreviewBlock(null, null);

        return raycast;
    }
}