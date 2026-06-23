package com.za.zenith.world.blocks.entity;

import com.za.zenith.world.BlockPos;

/**
 * Сущность бензогенератора.
 * Потребляет топливо, генерирует энергию и создает шум.
 */
public class GeneratorBlockEntity extends BlockEntity implements ITickable, IEnergyStorage {
    private float fuel = 0.0f;
    private final float maxFuel = 1000.0f;
    private float energy = 0.0f;
    private final float maxEnergy = 5000.0f;
    private boolean running = false;

    public GeneratorBlockEntity(BlockPos pos) {
        super(pos);
    }

    @Override
    public void update(float deltaTime) {
        if (world == null) return;
        if (running) {
            if (fuel > 0) {
                float consumeAmount = 1.0f * deltaTime;
                fuel -= consumeAmount;
                
                // Генерируем энергию
                if (energy < maxEnergy) {
                    energy += 10.0f * deltaTime;
                }
            } else {
                fuel = 0;
                running = false;
                com.za.zenith.utils.Logger.info("Generator at %s stopped: out of fuel", pos);
            }
        }
    }

    @Override
    public float receiveEnergy(float amount, boolean simulate) {
        return 0; // Генератор не принимает внешнюю энергию
    }

    @Override
    public float extractEnergy(float amount, boolean simulate) {
        float extracted = Math.min(energy, amount);
        if (!simulate) energy -= extracted;
        return extracted;
    }

    @Override
    public float getEnergyStored() {
        return energy;
    }

    @Override
    public float getMaxEnergyStored() {
        return maxEnergy;
    }

    @Override
    public boolean canReceive() {
        return false;
    }

    public void addFuel(float amount) {
        this.fuel = Math.min(maxFuel, this.fuel + amount);
        this.running = true;
        com.za.zenith.utils.Logger.info("Generator at %s refueled. Current fuel: %.1f", pos, fuel);
    }

    public float getFuel() {
        return fuel;
    }

    public float getEnergy() {
        return energy;
    }

    public boolean isRunning() {
        return running;
    }
    
    public void setRunning(boolean running) {
        this.running = running && fuel > 0;
    }

    @Override
    public boolean shouldTick() {
        return running;
    }
}


