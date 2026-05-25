package com.za.zenith.engine.input.controllers;

import com.za.zenith.engine.event.EventBus;
import com.za.zenith.engine.event.events.PlayerAttackEntityEvent;
import com.za.zenith.entities.components.HealthComponent;
import com.za.zenith.utils.Logger;

/**
 * Controller handling all combat-related events and actions.
 */
public class CombatController {

    public void init() {
        EventBus.getInstance().subscribe(PlayerAttackEntityEvent.class, this::onPlayerAttack);
    }

    private void onPlayerAttack(PlayerAttackEntityEvent event) {
        HealthComponent health = event.target().getComponent(HealthComponent.class);
        if (health != null) {
            event.player().swing();
            // In the future, apply custom attributes & affixes for damage calculations
            health.takeDamage(2.0f);
            event.player().addBlood(0.15f);
            Logger.info("Attacked entity through HealthComponent, hands are now bloody");
        }
    }
}
