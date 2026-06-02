package com.za.zenith.engine.resources.loaders.settings;

import com.google.gson.JsonElement;
import com.za.zenith.engine.resources.AbstractSingleFileLoader;
import com.za.zenith.engine.resources.AssetManager;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.generation.caves.CaveSettings;

public class CaveSettingsLoader extends AbstractSingleFileLoader<CaveSettings> {
    public CaveSettingsLoader() {
        super("registry/caves.json");
    }

    @Override
    protected void parseAndRegister(JsonElement root, String sourcePath) {
        CaveSettings settings = AssetManager.getGson().fromJson(root, CaveSettings.class);
        if (settings != null) {
            settings.setSourcePath(sourcePath);
            CaveSettings.setInstance(settings);
            Logger.info("Loaded cave settings from " + sourcePath);
        }
    }
}
