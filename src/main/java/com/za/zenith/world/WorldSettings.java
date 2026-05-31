package com.za.zenith.world;

public class WorldSettings implements com.za.zenith.utils.LiveReloadable {
    private static WorldSettings instance = new WorldSettings();
    private transient String sourcePath;

    @Override
    public String getSourcePath() { return sourcePath; }

    @Override
    public void setSourcePath(String path) { this.sourcePath = path; }

    @Override
    public void onLiveReload() {
        com.za.zenith.utils.Logger.info("WorldSettings: Applied live changes");
    }

    public long initialTime = 6000;
    public float dayCycleSpeed = 1.0f; // 1.0 = 20 minutes per day
    public int dayLength = 24000;
    public int sunriseTime = 0;
    public int sunsetTime = 12000;

    public float[] sunLightColor = {1.0f, 0.95f, 0.8f};
    public float[] moonLightColor = {0.2f, 0.3f, 0.6f};
    public float[] ambientColor = {0.4f, 0.45f, 0.55f};

    public static class WeatherSettings {
        public float checkIntervalSeconds = 300.0f; // Check weather every 5 real-life minutes by default
        public float clearWeight = 0.7f;
        public float rainWeight = 0.3f;
        public float stormWeight = 0.0f; // Storm not fully implemented yet
        public float transitionDurationSeconds = 10.0f; // Fade in/out over 10 seconds
    }
    public WeatherSettings weather = new WeatherSettings();

    public static WorldSettings getInstance() {
        return instance;
    }

    public static void setInstance(WorldSettings newInstance) {
        instance = newInstance;
    }
}
