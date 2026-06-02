package com.za.zenith.world.generation.caves;

import com.za.zenith.utils.LiveReloadable;
import com.za.zenith.utils.Logger;
import java.util.ArrayList;
import java.util.List;

public class CaveSettings implements LiveReloadable {
    private static CaveSettings instance = new CaveSettings();
    private transient String sourcePath;

    @Override
    public String getSourcePath() { return sourcePath; }

    @Override
    public void setSourcePath(String path) { this.sourcePath = path; }

    @Override
    public void onLiveReload() {
        compiledOres = null;
        Logger.info("CaveSettings: Applied live changes");
    }

    public boolean enabled = true;
    public int searchRadius = 4;
    public float chance = 0.12f;
    public int minY = -110;
    public int maxY = 240;

    public TunnelSettings tunnel = new TunnelSettings();
    public CaveRoomSettings caveRooms = new CaveRoomSettings();
    public List<String> carvableBlocks = new ArrayList<>();
    public List<OreSettings> ores = new ArrayList<>();

    public static class OreSettings {
        public String identifier;
        public int minY;
        public int maxY;
        public int weight;
        public int minSize;
        public int maxSize;
    }

    public static class CompiledOreSettings {
        public int blockId;
        public int minY;
        public int maxY;
        public int weight;
        public int minSize;
        public int maxSize;
    }

    private transient List<CompiledOreSettings> compiledOres = null;

    public List<CompiledOreSettings> getCompiledOres() {
        if (compiledOres == null) {
            compiledOres = new ArrayList<>();
            for (OreSettings ore : ores) {
                try {
                    com.za.zenith.utils.Identifier id = com.za.zenith.utils.Identifier.of(ore.identifier);
                    int blockId = com.za.zenith.world.blocks.BlockRegistry.getRegistry().getId(id);
                    if (blockId > 0) {
                        CompiledOreSettings compiled = new CompiledOreSettings();
                        compiled.blockId = blockId;
                        compiled.minY = ore.minY;
                        compiled.maxY = ore.maxY;
                        compiled.weight = ore.weight;
                        compiled.minSize = ore.minSize;
                        compiled.maxSize = ore.maxSize;
                        compiledOres.add(compiled);
                    }
                } catch (Exception e) {
                    Logger.error("Failed to compile ore setting for " + ore.identifier + ": " + e.getMessage());
                }
            }
        }
        return compiledOres;
    }

    public static class TunnelSettings {
        public int minLength = 80;
        public int maxLength = 180;
        public float minRadius = 1.6f;
        public float maxRadius = 3.4f;
        public float pitchMod = 0.4f;
        public float yawMod = 0.7f;
        public float pitchLimit = 0.25f;
        public float branchChance = 0.08f;
        public int maxBranches = 3;
    }

    public static class CaveRoomSettings {
        public boolean enabled = true;
        public float chance = 0.04f;
        public float minRadius = 6.0f;
        public float maxRadius = 11.0f;
        public int minY = -110;
        public int maxY = 40;
    }

    public static CaveSettings getInstance() {
        return instance;
    }

    public static void setInstance(CaveSettings newInstance) {
        instance = newInstance;
    }
}
