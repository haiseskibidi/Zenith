package com.za.zenith.world.generation;

import com.za.zenith.utils.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Глобальные настройки спавна ресурсов Foraging.
 */
public class ScavengeSettings {
    public static class ScavengeDefinition {
        public final Identifier itemId;
        public final float chance;
        public final int minGroup;
        public final int maxGroup;
        public final java.util.Set<Identifier> biomes;
        public final java.util.Set<Identifier> groundBlocks;

        public ScavengeDefinition(Identifier itemId, float chance, int minGroup, int maxGroup, 
                                  java.util.Set<Identifier> biomes, java.util.Set<Identifier> groundBlocks) {
            this.itemId = itemId;
            this.chance = chance;
            this.minGroup = minGroup;
            this.maxGroup = maxGroup;
            this.biomes = biomes;
            this.groundBlocks = groundBlocks;
        }
    }
    
    private static final List<ScavengeDefinition> DEFINITIONS = new ArrayList<>();

    public static void register(ScavengeDefinition definition) {
        DEFINITIONS.add(definition);
    }

    public static void clear() {
        DEFINITIONS.clear();
    }

    public static List<ScavengeDefinition> getDefinitions() {
        return DEFINITIONS;
    }
}


