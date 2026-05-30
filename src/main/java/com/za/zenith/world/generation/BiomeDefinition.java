package com.za.zenith.world.generation;

import com.google.gson.annotations.SerializedName;
import com.za.zenith.utils.Identifier;

import java.util.ArrayList;
import java.util.List;

public class BiomeDefinition implements com.za.zenith.utils.LiveReloadable {
    private transient String sourcePath;

    @Override
    public String getSourcePath() { return sourcePath; }

    @Override
    public void setSourcePath(String path) { this.sourcePath = path; }

    public enum TerrainType {
        @SerializedName("flat") FLAT,
        @SerializedName("rolling") ROLLING,
        @SerializedName("mountain") MOUNTAIN,
        @SerializedName("islands") ISLANDS
    }

    private transient Identifier id;
    
    @SerializedName("terrain_type")
    private TerrainType terrainType = TerrainType.ROLLING;

    @SerializedName("climate_points")
    private List<ClimatePoint> climatePoints = new ArrayList<>();
    
    private float temperature; 
    private float humidity;    
    
    @SerializedName("base_height")
    private int baseHeight = 64; 

    public static class ClimatePoint {
        public float temperature;
        public float humidity;
        public float continentalness;
        public float erosion;
        public float weirdness;
        public float offset = 0.0f; 
    }

    @SerializedName("surface_block")
    private String surfaceBlock = "zenith:grass_block";
    
    @SerializedName("underground_block")
    private String undergroundBlock = "zenith:dirt";
    
    @SerializedName("tree_density")
    private double treeDensity = 0.0;
    
    @SerializedName("surface_rules")
    private List<com.za.zenith.world.generation.rules.SurfaceRule> surfaceRules = new ArrayList<>();
    
    private List<FeatureEntry> features = new ArrayList<>();
    
    private AtmosphereSettings atmosphere = new AtmosphereSettings();

    public Identifier getId() { return id; }
    public void setId(Identifier id) { this.id = id; }
    
    public AtmosphereSettings getAtmosphere() {
        if (atmosphere == null) atmosphere = new AtmosphereSettings();
        return atmosphere;
    }
    
    public TerrainType getTerrainType() { return terrainType; }
    
    public List<ClimatePoint> getClimatePoints() {
        if (climatePoints.isEmpty()) {
            ClimatePoint p = new ClimatePoint();
            p.temperature = temperature;
            p.humidity = humidity;
            climatePoints.add(p);
        }
        return climatePoints;
    }
    
    public int getBaseHeight() { return baseHeight; }
    public Identifier getSurfaceBlock() { return Identifier.of(surfaceBlock); }
    public Identifier getUndergroundBlock() { return Identifier.of(undergroundBlock); }
    public double getTreeDensity() { return treeDensity; }
    public List<FeatureEntry> getFeatures() { return features; }
    public List<com.za.zenith.world.generation.rules.SurfaceRule> getSurfaceRules() { return surfaceRules; }

    public static class FeatureEntry {
        private String id;
        private int weight;
        public Identifier getId() { return Identifier.of(id); }
        public int getWeight() { return weight; }
    }
}
