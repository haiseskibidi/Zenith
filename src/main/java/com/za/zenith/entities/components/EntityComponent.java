package com.za.zenith.entities.components;

import com.za.zenith.entities.Entity;
import com.za.zenith.world.World;

/**
 * Base interface for all entity components in Zenith (ECS Lite).
 */
public interface EntityComponent {
    /**
     * Updates the component logic each game tick.
     * 
     * @param entity    The owner entity of this component.
     * @param deltaTime Time elapsed since the last frame.
     * @param world     The game world instance.
     */
    default void update(Entity entity, float deltaTime, World world) {}
}
