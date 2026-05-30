package com.za.zenith.engine.resources;

import com.google.gson.JsonElement;
import com.za.zenith.utils.Logger;
import com.za.zenith.utils.ResourceScanner;

import java.util.List;

public abstract class AbstractJsonLoader<T> implements IResourceLoader {
    protected final String relativePath;
    
    public AbstractJsonLoader(String relativePath) {
        this.relativePath = relativePath;
        AssetManager.registerLoader(this);
    }

    @Override
    public boolean reload(String path) {
        if (path.startsWith("zenith/" + relativePath + "/")) {
            String rawJson = AssetManager.getSnapshot(path);
            if (rawJson != null) {
                JsonElement element = AssetManager.getGson().fromJson(rawJson, JsonElement.class);
                parseAndRegister(element, path);
                return true;
            }
        }
        return false;
    }

    @Override
    public void load(String namespace) {
        String fullPath = namespace + "/" + relativePath;
        List<String> files = ResourceScanner.listResources(fullPath);
        
        if (files.isEmpty()) {
            // Check if it's a direct file path (ending in .json)
            if (fullPath.endsWith(".json")) {
                try {
                    String rawJson = AssetManager.readAndSnapshot(fullPath);
                    if (rawJson != null) {
                        JsonElement element = AssetManager.getGson().fromJson(rawJson, JsonElement.class);
                        parseAndRegister(element, fullPath);
                    }
                } catch (Exception e) {
                    // It might not exist, which is fine
                }
            }
            return;
        }

        for (String file : files) {
            String filePath = fullPath + "/" + file;
            try {
                String rawJson = AssetManager.readAndSnapshot(filePath);
                if (rawJson != null) {
                    JsonElement element = AssetManager.getGson().fromJson(rawJson, JsonElement.class);
                    parseAndRegister(element, filePath);
                }
            } catch (Exception e) {
                Logger.error("Failed to parse resource " + filePath + ": " + e.getMessage());
            }
        }
    }

    protected abstract void parseAndRegister(JsonElement root, String sourcePath);
}