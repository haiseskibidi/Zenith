package com.za.zenith.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.za.zenith.utils.Identifier;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.WoodTypeRegistry;
import com.za.zenith.engine.resources.loaders.*;
import com.za.zenith.engine.resources.loaders.generation.*;
import com.za.zenith.engine.resources.loaders.settings.*;
import com.za.zenith.engine.resources.loaders.stats.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DataLoader - Orchestrates the loading of all game data.
 * Now migrated to a modular, AssetManager-backed architecture.
 */
public class DataLoader {
    private static final Gson GSON = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(com.za.zenith.world.blocks.component.BlockComponent.class, new com.za.zenith.world.blocks.component.BlockComponentAdapter())
        .create();

    public static String getSnapshot(String path) {
        return com.za.zenith.engine.resources.AssetManager.getSnapshot(path);
    }

    public static void loadAll() {
        // Guarantee AIR as ID 0
        BlockDefinition airDef = new BlockDefinition(0, Identifier.of("zenith:air"), "block.zenith.air", false, false);
        airDef.setReplaceable(true);
        BlockRegistry.registerBlock(airDef);
        
        List<String> namespaces = loadNamespaces();
        com.za.zenith.world.blocks.component.BlockComponentRegistry.init();
        com.za.zenith.entities.parkour.animation.EasingRegistry.init();

        for (String ns : namespaces) {
            new BlockDataLoader().load(ns);
        }
        loadWoodTypes();
        com.za.zenith.utils.events.RegistryEvents.fireBlockRegistration();
        BlockRegistry.finalizeRegistration();
        
        // --- Essential Initialization Order ---
        com.za.zenith.world.items.stats.StatRegistry.getAll(); // Ensure class loaded
        com.za.zenith.world.items.stats.RarityRegistry.init();
        
        for (String ns : namespaces) {
            new StatDataLoader().load(ns);
            new RarityDataLoader().load(ns);
            new AffixRarityDataLoader().load(ns);
            new AffixDataLoader().load(ns);
            new GripDataLoader().load(ns);
        }

        com.za.zenith.world.items.ItemRegistry.init();
        
        for (String ns : namespaces) {
            new LootTableDataLoader().load(ns);
            new ItemDataLoader().load(ns);
        }
        com.za.zenith.utils.events.RegistryEvents.fireItemRegistration();
        
        com.za.zenith.world.blocks.Blocks.init();
        com.za.zenith.world.items.Items.init();

        for (String ns : namespaces) {
            new StructureDataLoader().load(ns);
            new DensityFunctionLoader().load(ns);
            new BiomeDataLoader().load(ns);
        }
        com.za.zenith.world.generation.structures.PrefabManager.init();

        for (String ns : namespaces) {
            new EntityDataLoader().load(ns);
        }

        com.za.zenith.engine.graphics.ui.blueprints.BlueprintRegistry.init();

        for (String ns : namespaces) {
            new RecipeDataLoader().load(ns);
            new GUIDataLoader().load(ns);
            new JournalCategoryLoader().load(ns);
            new JournalEntryLoader().load(ns);
            new ViewmodelDataLoader().load(ns);
            new ActionDataLoader().load(ns);
        }

        new AnimationDataLoader().load("zenith");
        new ScavengeDataLoader().load("zenith");
        new PhysicsSettingsLoader().load("zenith");
        new WorldSettingsLoader().load("zenith");
        new GenerationSettingsLoader().load("zenith");
        new CaveSettingsLoader().load("zenith");
        new SkySettingsLoader().load("zenith");
        
        Logger.info("DataLoader: All resources loaded successfully.");
    }

    private static List<String> loadNamespaces() {
        List<String> namespaces = new ArrayList<>();
        namespaces.add("zenith");
        try (InputStream is = DataLoader.class.getClassLoader().getResourceAsStream("namespaces.index")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String ns = line.trim();
                    if (!ns.isEmpty() && !namespaces.contains(ns)) namespaces.add(ns);
                }
            }
        } catch (Exception e) {
            Logger.warn("Could not read namespaces.index, using default 'zenith'");
        }
        return namespaces;
    }

    private static void loadWoodTypes() {
        try (InputStream is = DataLoader.class.getClassLoader().getResourceAsStream("zenith/registry/wood_types.json")) {
            if (is == null) return;
            JsonArray root = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonArray.class);
            List<Identifier> types = new ArrayList<>();
            for (JsonElement el : root) {
                types.add(Identifier.of(el.getAsString()));
            }
            WoodTypeRegistry.init(types);
        } catch (Exception e) {
            Logger.error("Failed to load wood types: " + e.getMessage());
        }
    }

    public static JsonObject loadJson(String path) {
        try (InputStream is = DataLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return new JsonObject();
            return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        } catch (Exception e) {
            Logger.error("Failed to load JSON from " + path + ": " + e.getMessage());
            return new JsonObject();
        }
    }

    private static List<String> listResources(String path) {
        return com.za.zenith.utils.ResourceScanner.listResources(path);
    }
}
