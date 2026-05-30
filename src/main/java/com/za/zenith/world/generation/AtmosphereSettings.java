package com.za.zenith.world.generation;

import com.google.gson.annotations.SerializedName;

public class AtmosphereSettings {
    @SerializedName("sun_shafts")
    private SunShaftsSettings sunShafts = new SunShaftsSettings();

    @SerializedName("ambient_particles")
    private AmbientParticlesSettings ambientParticles = new AmbientParticlesSettings();

    public SunShaftsSettings getSunShafts() {
        if (sunShafts == null) sunShafts = new SunShaftsSettings();
        return sunShafts;
    }

    public AmbientParticlesSettings getAmbientParticles() {
        if (ambientParticles == null) ambientParticles = new AmbientParticlesSettings();
        return ambientParticles;
    }

    public static class SunShaftsSettings {
        private boolean enabled = true;
        private float density = 0.92f;
        private float weight = 0.45f;
        private float decay = 0.955f;
        private float exposure = 0.22f;

        @SerializedName("toon_steps")
        private int toonSteps = 4;

        @SerializedName("shaft_color")
        private float[] shaftColor = {1.0f, 0.92f, 0.75f};

        public boolean isEnabled() { return enabled; }
        public float getDensity() { return density; }
        public float getWeight() { return weight; }
        public float getDecay() { return decay; }
        public float getExposure() { return exposure; }
        public int getToonSteps() { return toonSteps; }
        public float[] getShaftColor() { return shaftColor; }
    }

    public static class AmbientParticlesSettings {
        private boolean enabled = false;

        @SerializedName("particle_type")
        private String particleType = "zenith:dust_motes";

        private float density = 0.04f;

        @SerializedName("speed_multiplier")
        private float speedMultiplier = 0.25f;

        @SerializedName("scale_range")
        private float[] scaleRange = {0.08f, 0.22f};

        @SerializedName("glowing_color")
        private float[] glowingColor = {1.0f, 0.88f, 0.6f};

        @SerializedName("fade_in_sunlight")
        private int fadeInSunlight = 12;

        public boolean isEnabled() { return enabled; }
        public String getParticleType() { return particleType; }
        public float getDensity() { return density; }
        public float getSpeedMultiplier() { return speedMultiplier; }
        public float[] getScaleRange() { return scaleRange; }
        public float[] getGlowingColor() { return glowingColor; }
        public int getFadeInSunlight() { return fadeInSunlight; }
    }
}
