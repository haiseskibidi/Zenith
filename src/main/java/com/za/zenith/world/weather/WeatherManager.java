package com.za.zenith.world.weather;

import com.za.zenith.world.WorldSettings;
import java.util.Random;

public class WeatherManager {
    public enum WeatherState {
        CLEAR, RAIN, STORM
    }

    private WeatherState currentState = WeatherState.CLEAR;
    private WeatherState targetState = WeatherState.CLEAR;
    
    private float currentRainIntensity = 0.0f;
    private float targetRainIntensity = 0.0f;
    
    private float timeSinceLastCheck = 0.0f;
    private final Random random = new Random();

    public void update(float deltaTime) {
        WorldSettings.WeatherSettings settings = WorldSettings.getInstance().weather;
        if (settings == null) return;
        
        timeSinceLastCheck += deltaTime;
        if (timeSinceLastCheck >= settings.checkIntervalSeconds) {
            timeSinceLastCheck = 0.0f;
            rollWeather(settings);
        }

        // Smooth transition
        if (currentRainIntensity != targetRainIntensity) {
            float transitionSpeed = 1.0f / Math.max(0.1f, settings.transitionDurationSeconds);
            if (currentRainIntensity < targetRainIntensity) {
                currentRainIntensity = Math.min(targetRainIntensity, currentRainIntensity + transitionSpeed * deltaTime);
            } else {
                currentRainIntensity = Math.max(targetRainIntensity, currentRainIntensity - transitionSpeed * deltaTime);
            }
        }
        
        if (currentRainIntensity == targetRainIntensity) {
            currentState = targetState;
        }
    }
    
    private void rollWeather(WorldSettings.WeatherSettings settings) {
        float totalWeight = settings.clearWeight + settings.rainWeight + settings.stormWeight;
        if (totalWeight <= 0) {
            targetState = WeatherState.CLEAR;
            targetRainIntensity = 0.0f;
            return;
        }
        
        float roll = random.nextFloat() * totalWeight;
        
        if (roll < settings.clearWeight) {
            targetState = WeatherState.CLEAR;
            targetRainIntensity = 0.0f;
        } else if (roll < settings.clearWeight + settings.rainWeight) {
            targetState = WeatherState.RAIN;
            targetRainIntensity = 1.0f;
        } else {
            targetState = WeatherState.STORM;
            targetRainIntensity = 1.0f;
        }
    }

    public void forceWeather(WeatherState state) {
        this.targetState = state;
        this.currentState = state;
        this.targetRainIntensity = (state == WeatherState.CLEAR) ? 0.0f : 1.0f;
        this.currentRainIntensity = this.targetRainIntensity;
        this.timeSinceLastCheck = 0.0f;
    }

    public float getRainIntensity() {
        return currentRainIntensity;
    }
    
    public WeatherState getCurrentState() {
        return currentState;
    }
}
