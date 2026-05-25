package com.za.zenith.entities.components;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.World;
import com.za.zenith.world.items.stats.StatContainer;
import com.za.zenith.world.items.stats.StatRegistry;
import com.za.zenith.utils.Identifier;

/**
 * Component managing health, max health, healing, stats, and damage calculation.
 */
public class HealthComponent implements EntityComponent {
    private float health;
    private float maxHealth;
    private final StatContainer stats = new StatContainer();

    public HealthComponent(float maxHealth) {
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public void takeDamage(float amount) {
        float defense = getDefense();
        // Formula: damage = base_damage * (100 / (100 + defense))
        // 100 defense = 50% reduction, 200 = 66% reduction
        float multiplier = 100.0f / (100.0f + Math.max(0, defense));
        float finalDamage = amount * multiplier;
        
        this.health = Math.max(0, this.health - finalDamage);
    }

    public void heal(float amount) {
        this.health = Math.min(this.maxHealth, this.health + amount);
    }

    public float getDefense() {
        return stats.get(StatRegistry.DEFENSE);
    }

    public float getStat(Identifier statId) {
        return stats.get(statId);
    }

    public StatContainer getStats() {
        return stats;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
    }

    public boolean isDead() {
        return health <= 0;
    }
}
