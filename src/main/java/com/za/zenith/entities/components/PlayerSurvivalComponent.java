package com.za.zenith.entities.components;

import com.za.zenith.entities.Entity;
import com.za.zenith.entities.Player;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.utils.Identifier;
import java.util.Set;
import java.util.HashSet;

/**
 * Component managing survival stats, temperature impacts, conditions and actions for the Player.
 */
public class PlayerSurvivalComponent implements EntityComponent {
    private float hunger = 20.0f;
    private float saturation = 5.0f;
    private float stamina = 1.0f;
    private float noiseLevel = 0.0f;
    private float continuousNoise = 0.0f;
    private float oxygen = 300.0f;
    public static final float MAX_OXYGEN = 300.0f;
    private float drownDamageTimer = 0.0f;

    // Body Condition (Hand State)
    private float dirt = 0.0f;
    private float blood = 0.0f;
    private float wetness = 0.0f;
    private boolean hasParasites = false;
    private float parasitesTimer = 0.0f;

    // Action System
    private final Set<Identifier> activeActions = new HashSet<>();

    public PlayerSurvivalComponent() {}

    @Override
    public void update(Entity entity, float deltaTime, World world) {
        if (!(entity instanceof Player player)) return;

        updateHunger(player, deltaTime);
        updateActions(player, deltaTime);
        updateThermalAndConditions(player, deltaTime, world);
        updateOxygenLevel(player, deltaTime, world);
    }

    private void updateOxygenLevel(Player player, float deltaTime, World world) {
        float cameraY = player.getPosition().y + player.getEyeHeight();
        Block eyeBlock = world.getBlock((int)Math.floor(player.getPosition().x), (int)Math.floor(cameraY), (int)Math.floor(player.getPosition().z));
        BlockDefinition eyeDef = BlockRegistry.getBlock(eyeBlock.getType());
        boolean isEyesSubmerged = eyeDef != null && eyeDef.isFluid();
        
        if (isEyesSubmerged && player.getMode() == com.za.zenith.engine.core.PlayerMode.SURVIVAL) {
            oxygen = Math.max(0.0f, oxygen - 20.0f * deltaTime);
            if (oxygen <= 0.0f) {
                drownDamageTimer += deltaTime;
                if (drownDamageTimer >= 1.0f) {
                    player.takeDamage(1.0f);
                    drownDamageTimer = 0.0f;
                }
            }
        } else {
            oxygen = Math.min(MAX_OXYGEN, oxygen + 150.0f * deltaTime);
            drownDamageTimer = 0.0f;
        }
    }

    private void updateHunger(Player player, float deltaTime) {
        float mult = player.isSprinting() ? 3.0f : (!player.isOnGround() && !player.isFlying() ? 2.0f : 1.0f);
        if (hasParasites) mult *= 2.0f;
        if (saturation > 0) saturation -= 0.1f * mult * deltaTime;
        else hunger = Math.max(0, hunger - 0.1f * mult * deltaTime);
    }

    private void updateActions(Player player, float deltaTime) {
        float floorNoise = 0.0f;
        
        // Automatic locomotion actions
        if (!player.isFlying() && player.isMoving()) {
            if (player.isSprinting()) startAction(Identifier.of("zenith:sprint"));
            else stopAction(Identifier.of("zenith:sprint"));
            
            if (player.isSneaking()) startAction(Identifier.of("zenith:sneak"));
            else stopAction(Identifier.of("zenith:sneak"));
            
            if (!player.isSprinting() && !player.isSneaking()) startAction(Identifier.of("zenith:walk"));
            else stopAction(Identifier.of("zenith:walk"));
        } else {
            stopAction(Identifier.of("zenith:sprint"));
            stopAction(Identifier.of("zenith:sneak"));
            stopAction(Identifier.of("zenith:walk"));
        }

        boolean staminaConsumingAction = false;

        for (Identifier id : activeActions) {
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
        if (!staminaConsumingAction && player.isOnGround()) {
            stamina = Math.min(1.0f, stamina + 0.1f * deltaTime);
        }

        noiseLevel = Math.max(floorNoise, Math.max(continuousNoise, noiseLevel - 0.5f * deltaTime));
        continuousNoise = 0.0f;
    }

    private void updateThermalAndConditions(Player player, float deltaTime, World world) {
        ItemStack held = player.getInventory().getSelectedItemStack();
        if (held != null) {
            float ambient = 20.0f; 
            if (player.isInWater()) ambient = 15.0f;
            held.updateTemperature(ambient, deltaTime);
            com.za.zenith.world.items.component.ThermalComponent thermal = held.getItem().getComponent(com.za.zenith.world.items.component.ThermalComponent.class);
            float threshold = (thermal != null) ? thermal.burnThreshold() : 55.0f;
            if (held.getTemperature() > threshold) {
                player.takeDamage(0.5f * deltaTime);
                if (Math.random() < 0.05f * deltaTime) {
                    player.getInventory().dropSelected(player, world, com.za.zenith.engine.core.GameLoop.getInstance().getCamera(), true);
                }
            }
        }
        if (player.isInWater()) {
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

    public void startAction(Identifier id) { activeActions.add(id); }
    public void stopAction(Identifier id) { activeActions.remove(id); }

    public void performDiscreteAction(Player player, Identifier id) {
        com.za.zenith.world.actions.ActionDefinition def = com.za.zenith.world.actions.ActionRegistry.get(id);
        if (def != null) {
            addNoise(def.noiseLevel);
            stamina = Math.max(0.0f, stamina - def.staminaCostPerUse);
            hunger = Math.max(0.0f, hunger - def.hungerCostPerUse);
        }
    }

    public void eat(Player player, com.za.zenith.world.items.Item item) {
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

    public void addNoise(float amount) { this.noiseLevel = Math.min(1.0f, this.noiseLevel + amount); }
    public void setContinuousNoise(float level) { this.continuousNoise = Math.max(this.continuousNoise, level); }
    public float getNoiseLevel() { return noiseLevel; }
    
    public float getStamina() { return stamina; }
    public void setStamina(float stamina) { this.stamina = stamina; }
    
    public void addDirt(float amount) { this.dirt = Math.min(1.0f, this.dirt + amount); }
    public void addBlood(float amount) { this.blood = Math.min(1.0f, this.blood + amount); }
    public void washHands() { this.dirt = 0; this.blood = 0; this.wetness = 1.0f; }
    
    public float getDirt() { return dirt; }
    public float getBlood() { return blood; }
    public float getWetness() { return wetness; }
    public float getScentLevel() { return blood * 2.0f; }
    
    public float getHunger() { return hunger; }
    public float getOxygen() { return oxygen; }
}
