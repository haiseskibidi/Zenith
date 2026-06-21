package com.za.zenith.engine.input;

import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.entities.Player;
import com.za.zenith.network.GameClient;
import com.za.zenith.world.World;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.MiningSettings;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.Items;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.physics.AABB;
import com.za.zenith.world.physics.VoxelShape;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class MiningController {
    private Renderer renderer;
    private GameClient networkClient;

    private BlockPos breakingBlockPos = null;
    private float breakingProgress = 0.0f;
    private float hitPulse = 0.0f; // 0 to 1 pulse on hit
    private float hitCooldownTimer = 0.0f;
    private float wobbleTimer = 0.0f;
    private Vector3f currentWeakSpot = new Vector3f(0.5f);
    private Vector3f currentNormal = new Vector3f(0, 1, 0);
    private final List<Vector4f> hitHistory = new ArrayList<>();
    
    private float breakDelayTimer = 0.0f;

    public MiningController() {
    }

    public void setDependencies(Renderer renderer, GameClient networkClient) {
        this.renderer = renderer;
        this.networkClient = networkClient;
    }

    public void update(float deltaTime) {
        breakDelayTimer = Math.max(0, breakDelayTimer - deltaTime);
        hitCooldownTimer = Math.max(0, hitCooldownTimer - deltaTime);
        hitPulse = Math.max(0, hitPulse - deltaTime * 5.0f); // Fast decay (200ms)
        if (breakingBlockPos != null) {
            wobbleTimer += deltaTime;
        }
    }

    public void startMining(BlockPos hitPos, BlockDefinition blockDef, World world, Vector3f normal) {
        breakingBlockPos = hitPos;
        breakingProgress = world.getBlockDamage(hitPos) / (blockDef.getHardness() * 10.0f);
        wobbleTimer = 1.0f; // Prevent animation leaking to new blocks
        hitHistory.clear();
        hitHistory.addAll(world.getBlockHitHistory(hitPos));
        this.currentNormal.set(normal);
        currentWeakSpot = generateRandomWeakSpot(blockDef.getShape(world.getBlock(hitPos).getMetadata()), normal);
        
        // Сразу передаем цвет в рендерер, чтобы не было желтой вспышки в начале
        if (renderer != null) {
            boolean hasWeakSpots = blockDef.getMiningSettings().strategy().equals("weak_spots");
            Vector3f weakSpot = hasWeakSpots ? currentWeakSpot : null;
            Vector3f wsColor = hasWeakSpots ? blockDef.getMiningSettings().weakSpotColor() : null;
            List<Vector4f> historyToSend = (blockDef.getBreakingPattern() != 0) ? hitHistory : null;
            renderer.setBreakingBlock(hitPos, world.getBlock(hitPos), 0.0f, 1.0f, new Vector3f(0.5f), weakSpot, wsColor, historyToSend, world);
        }
    }

    public void stopMining() {
        stopMining(null);
    }

    public void stopMining(World world) {
        if (breakingBlockPos != null) {
            if (world != null) {
                var block = world.getBlock(breakingBlockPos);
                if (block != null && !block.isAir()) {
                    var def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
                    if (def != null && def.getBreakingPattern() == 0) {
                        world.setBlockDamage(breakingBlockPos, 0); // ponytail: clear damage for generic blocks on stop
                    }
                }
            }
            breakingBlockPos = null;
            breakingProgress = 0.0f;
            if (renderer != null) renderer.setBreakingBlock(null, null, 0.0f, 0.0f, null, null, null, null, null);
        }
    }

    public void reset() {
        stopMining();
        wobbleTimer = 0.0f;
        hitHistory.clear();
    }

    public void mine(World world, Player player, BlockPos hitPos, int blockType, BlockDefinition blockDef, ItemStack currentStack, Item currentItem, boolean isNewLeftClick, Vector3f localHit, Vector3f normal) {
        float hardness = blockDef.getHardness();
        if (hardness < 0) return; // Unbreakable

        // Если сменилась грань (нормаль), перегенерируем слабую точку на новой грани
        if (normal != null && normal.distanceSquared(currentNormal) > 0.01f) {
            currentNormal.set(normal);
            currentWeakSpot = generateRandomWeakSpot(blockDef.getShape(world.getBlock(hitPos).getMetadata()), normal);
        }

        float maxHealth = hardness * 10.0f;
        
        float miningDamage = (currentItem != null) ?
            currentItem.getMiningSpeed(blockType) :
            Items.HAND.getMiningSpeed(blockType);

        // ФИКС: Если урон за один удар больше ХП блока, это Instant Break
        boolean isInstantBreak = miningDamage >= maxHealth || maxHealth <= 0.1f;
        boolean shouldHit = false;

        float currentDamage = world.getBlockDamage(hitPos);
        Block currentBlock = world.getBlock(hitPos);
        if (currentBlock.isAir()) {
            reset();
            return;
        }

        float interval = blockDef.getInteractionCooldown();
        if (currentItem != null) {
            com.za.zenith.world.items.component.ToolComponent tool = currentItem.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
            if (tool != null) {
                interval = tool.attackInterval();
                // ФИКС: Для супер-инструментов (Молот Админа) убираем лишние задержки
                if (tool.isEffectiveAgainstAll()) {
                    interval = Math.min(interval, 0.02f);
                }
            }
        }
        interval /= Math.max(0.1f, player.getMiningSpeedMultiplier());

        if (isInstantBreak) {
            // Instant break: respect tiny interval
            if (breakDelayTimer <= 0.0f) {
                breakingProgress = 1.0f;
                currentDamage = maxHealth;
                shouldHit = true;
            }
        } else {
            // Re-calculate interval for standard blocks if needed, 
            // but usually we want to stay consistent with tool speeds
            if (hitCooldownTimer <= 0.0f) {
                MiningSettings mSettings = blockDef.getMiningSettings();
                boolean isWeakSpotHit = false;
                if (mSettings.strategy().equals("weak_spots")) {
                    float dist = localHit.distance(currentWeakSpot);
                    if (dist < mSettings.precision()) {
                        isWeakSpotHit = true;
                        currentDamage += miningDamage;
                        hitHistory.add(new Vector4f(currentWeakSpot, 1.0f));
                        
                        // СПАВН ЧАСТИЦ ПРИ УДАРЕ В ВИК СПОТ
                        com.za.zenith.world.particles.ParticleManager.getInstance().spawnImpact(
                            new Vector3f(hitPos.x() + 0.5f + currentWeakSpot.x, hitPos.y() + currentWeakSpot.y, hitPos.z() + 0.5f + currentWeakSpot.z),
                            normal,
                            blockDef,
                            world.getBlock(hitPos).getMetadata(),
                            blockDef.getWeakSpotParticles(),
                            miningDamage
                        );

                        // Перегенерируем на ТОЙ ЖЕ грани, используя честную нормаль
                        currentWeakSpot = generateRandomWeakSpot(blockDef.getShape(world.getBlock(hitPos).getMetadata()), currentNormal); 
                        com.za.zenith.utils.Logger.info("Weak spot HIT!");
                        
                        String currentToolStr = "none";
                        if (currentItem != null && currentItem.isTool()) {
                            com.za.zenith.world.items.component.ToolComponent toolComp = currentItem.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
                            if (toolComp != null) currentToolStr = toolComp.type().name().toLowerCase();
                        }
                        
                        for (com.za.zenith.world.blocks.DropRule rule : blockDef.getDropRules()) {
                            if (rule.dropOnHit() && (rule.requiredToolType().equalsIgnoreCase("none") || rule.requiredToolType().equalsIgnoreCase(currentToolStr))) {
                                if (Math.random() <= rule.chance()) {
                                    Item dropItem = com.za.zenith.world.items.ItemRegistry.getItem(com.za.zenith.utils.Identifier.of(rule.dropItemIdentifier()));
                                    if (dropItem != null) {
                                        Vector3f spawnPos = new Vector3f(hitPos.x() + 0.5f + normal.x * 0.5f, hitPos.y() + 0.5f + normal.y * 0.5f, hitPos.z() + 0.5f + normal.z * 0.5f);
                                        com.za.zenith.entities.ItemEntity dropEntity = new com.za.zenith.entities.ItemEntity(spawnPos, new ItemStack(dropItem, 1));
                                        dropEntity.getVelocity().set(normal.x * 2.0f, Math.max(1.0f, normal.y * 2.0f), normal.z * 2.0f);
                                        world.spawnEntity(dropEntity);
                                        com.za.zenith.utils.Logger.info("Progressive drop spawned: " + rule.dropItemIdentifier());
                                        
                                        // Penalty: tearing off a piece damages the block
                                        currentDamage += maxHealth * rule.chance() * rule.durabilityPenalty();
                                    }
                                }
                            }
                        }
                    } else {
                        currentDamage += miningDamage * mSettings.missMultiplier();
                        // No hitHistory.add here anymore
                        
                        // Small chance to get loot even on MISS (5 times lower chance)
                        String currentToolStr = "none";
                        if (currentItem != null && currentItem.isTool()) {
                            com.za.zenith.world.items.component.ToolComponent toolComp = currentItem.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
                            if (toolComp != null) currentToolStr = toolComp.type().name().toLowerCase();
                        }
                        for (com.za.zenith.world.blocks.DropRule rule : blockDef.getDropRules()) {
                            if (rule.dropOnHit() && (rule.requiredToolType().equalsIgnoreCase("none") || rule.requiredToolType().equalsIgnoreCase(currentToolStr))) {
                                if (Math.random() <= rule.chance() * 0.2f) { // 20% of original chance on miss
                                    Item dropItem = com.za.zenith.world.items.ItemRegistry.getItem(com.za.zenith.utils.Identifier.of(rule.dropItemIdentifier()));
                                    if (dropItem != null) {
                                        Vector3f spawnPos = new Vector3f(hitPos.x() + 0.5f + normal.x * 0.5f, hitPos.y() + 0.5f + normal.y * 0.5f, hitPos.z() + 0.5f + normal.z * 0.5f);
                                        com.za.zenith.entities.ItemEntity dropEntity = new com.za.zenith.entities.ItemEntity(spawnPos, new ItemStack(dropItem, 1));
                                        dropEntity.getVelocity().set(normal.x * 1.5f, 0.8f, normal.z * 1.5f);
                                        world.spawnEntity(dropEntity);
                                        currentDamage += maxHealth * rule.chance() * 0.3f;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    currentDamage += miningDamage;
                    hitHistory.add(new Vector4f(localHit, 1.0f)); // General hit
                }

                breakingProgress = Math.min(1.0f, currentDamage / maxHealth);
                world.setBlockDamage(hitPos, currentDamage, hitHistory);

                hitCooldownTimer = interval;
                wobbleTimer = 0.0f;
                shouldHit = true;

                player.setContinuousNoise(Math.min(0.4f, 0.15f + hardness * 0.1f));
                player.performDiscreteAction(com.za.zenith.utils.Identifier.of("zenith:mine"));
            }
        }

        if (shouldHit) {
            if (isInstantBreak) {
                player.interact(interval); // Sync animation duration with physics interval
            } else {
                player.swing(hitCooldownTimer > 0 ? hitCooldownTimer : interval);
            }
            hitPulse = 1.0f;
        }
            
        if (renderer != null && breakingBlockPos != null) {
            boolean hasWeakSpots = blockDef.getMiningSettings().strategy().equals("weak_spots");
            Vector3f weakSpot = hasWeakSpots ? currentWeakSpot : null;
            Vector3f wsColor = hasWeakSpots ? blockDef.getMiningSettings().weakSpotColor() : null;
            List<Vector4f> historyToSend = (blockDef.getBreakingPattern() != 0) ? hitHistory : null;
            renderer.setBreakingBlock(hitPos, world.getBlock(hitPos), breakingProgress, wobbleTimer, localHit, weakSpot, wsColor, historyToSend, world);
        }

        if (breakingProgress >= 1.0f) {
            if (hitPos != null) {
                if (currentItem == null) {
                    if (blockDef.getSoilingAmount() > 0) {
                        player.addDirt(blockDef.getSoilingAmount());
                    }
                }
                if (world.onBlockBreak(hitPos, player)) {
                    world.destroyBlock(hitPos, player);
                    if (networkClient != null && networkClient.isConnected()) {
                        networkClient.sendBlockUpdate(hitPos.x(), hitPos.y(), hitPos.z(), Blocks.AIR.getId());
                    }
                    stopMining();
                    world.setBlockDamage(hitPos, 0); // Clear damage once broken
                } else {
                    breakingProgress = 0.0f;
                    wobbleTimer = 0.0f;
                    // Initialize the damage map with the new block type immediately with a tiny damage
                    // to prevent visual rollback to the full log before the next hit or chunk rebuild
                    world.setBlockDamage(hitPos, 0.001f);
                }
            }

            breakDelayTimer = interval; // Use global interval instead of BREAK_COOLDOWN
            hitCooldownTimer = interval;
            if (currentStack != null && currentItem.isTool()) {
                com.za.zenith.world.items.component.ToolComponent tool = currentItem.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
                if (tool != null && tool.maxDurability() != -1 && (tool.isEffectiveAgainstAll() || tool.type().name().equalsIgnoreCase(blockDef.getRequiredTool()))) {
                    currentStack.setDurability(currentStack.getDurability() - 1);
                    if (currentStack.getDurability() <= 0) player.getInventory().setStackInSlot(player.getInventory().getSelectedSlot(), null);
                }
            }
        }
    }

    public void renderVisuals(BlockPos hitPos, Block block, Vector3f localHit, World world) {
        if (breakingBlockPos != null && hitPos != null && hitPos.equals(breakingBlockPos)) {
            com.za.zenith.world.blocks.BlockDefinition def = com.za.zenith.world.blocks.BlockRegistry.getBlock(block.getType());
            boolean hasWeakSpots = def.getMiningSettings().strategy().equals("weak_spots");
            Vector3f weakSpot = hasWeakSpots ? currentWeakSpot : null;
            Vector3f wsColor = hasWeakSpots ? def.getMiningSettings().weakSpotColor() : null;
            List<Vector4f> historyToSend = (def.getBreakingPattern() != 0) ? hitHistory : null;
            renderer.setBreakingBlock(hitPos, block, breakingProgress, wobbleTimer, localHit, weakSpot, wsColor, historyToSend, world);
        }
    }

    public BlockPos getBreakingBlockPos() { return breakingBlockPos; }
    
    public float getBreakingProgress() { return breakingProgress; }

    public float getHitPulse() { return hitPulse; }

    public float getWobbleTimer() { return wobbleTimer; }

    private Vector3f generateRandomWeakSpot(VoxelShape shape, Vector3f normal) {
        if (shape == null || shape.getBoxes().isEmpty()) return new Vector3f(0, 0.5f, 0);

        AABB box = shape.getBoxes().get(0);
        Vector3f min = box.getMin();
        Vector3f max = box.getMax();

        float padding = 0.15f;

        Vector3f spot = new Vector3f();
        if (normal.x != 0) {
            spot.x = normal.x > 0 ? max.x : min.x;
            spot.y = min.y + padding + (float) Math.random() * (max.y - min.y - 2 * padding);
            spot.z = min.z + padding + (float) Math.random() * (max.z - min.z - 2 * padding);
        } else if (normal.y != 0) {
            spot.y = normal.y > 0 ? max.y : min.y;
            spot.x = min.x + padding + (float) Math.random() * (max.x - min.x - 2 * padding);
            spot.z = min.z + padding + (float) Math.random() * (max.z - min.z - 2 * padding);
        } else if (normal.z != 0) {
            spot.z = normal.z > 0 ? max.z : min.z;
            spot.x = min.x + padding + (float) Math.random() * (max.x - min.x - 2 * padding);
            spot.y = min.y + padding + (float) Math.random() * (max.y - min.y - 2 * padding);
        } else {
            spot.x = min.x + (float) Math.random() * (max.x - min.x);
            spot.y = min.y + (float) Math.random() * (max.y - min.y);
            spot.z = min.z + (float) Math.random() * (max.z - min.z);
            if (Math.abs(normal.x) > 0.5f) spot.x = normal.x > 0 ? max.x : min.x;
            if (Math.abs(normal.y) > 0.5f) spot.y = normal.y > 0 ? max.y : min.y;
            if (Math.abs(normal.z) > 0.5f) spot.z = normal.z > 0 ? max.z : min.z;
        }

        spot.x -= 0.5f;
        spot.z -= 0.5f;

        return spot;
    }
}


