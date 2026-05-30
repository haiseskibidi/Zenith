package com.za.zenith.engine.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.za.zenith.engine.resources.AbstractJsonLoader;
import com.za.zenith.utils.Identifier;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.generation.ScavengeSettings;

public class ScavengeDataLoader extends AbstractJsonLoader<JsonArray> {

    public ScavengeDataLoader() {
        super("registry/scavenge.json");
    }

    @Override
    protected void parseAndRegister(JsonElement el, String sourcePath) {
        try {
            // Synchronize clear only if this is the primary registry file to allow multi-file support
            if (sourcePath.contains("zenith/registry/scavenge.json")) {
                ScavengeSettings.clear();
            }
            
            JsonArray root = el.getAsJsonArray();
            for (JsonElement e : root) {
                JsonObject obj = e.getAsJsonObject();
                
                // 1. Core Fields
                Identifier itemId = Identifier.of(obj.get("item").getAsString());
                float chance = obj.get("chance").getAsFloat();

                // 2. Optional Group Size
                int minGroup = 1;
                int maxGroup = 1;
                if (obj.has("group_size")) {
                    JsonObject groupObj = obj.getAsJsonObject("group_size");
                    minGroup = groupObj.get("min").getAsInt();
                    maxGroup = groupObj.get("max").getAsInt();
                }

                // 3. Optional Biome Restrictions
                java.util.Set<Identifier> biomes = new java.util.HashSet<>();
                if (obj.has("biomes")) {
                    for (JsonElement b : obj.getAsJsonArray("biomes")) {
                        biomes.add(Identifier.of(b.getAsString()));
                    }
                }

                // 4. Optional Ground Block Restrictions
                java.util.Set<Identifier> groundBlocks = new java.util.HashSet<>();
                if (obj.has("ground_blocks")) {
                    for (JsonElement gb : obj.getAsJsonArray("ground_blocks")) {
                        groundBlocks.add(Identifier.of(gb.getAsString()));
                    }
                }

                ScavengeSettings.register(new ScavengeSettings.ScavengeDefinition(
                    itemId, chance, minGroup, maxGroup, biomes, groundBlocks
                ));
            }
        } catch (Exception e) {
            Logger.error("Failed to parse scavenge settings " + sourcePath + ": " + e.getMessage());
        }
    }
}