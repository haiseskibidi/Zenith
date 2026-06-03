package com.za.zenith.engine.resources.loaders;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.za.zenith.engine.resources.AbstractJsonLoader;
import com.za.zenith.utils.Identifier;
import com.za.zenith.utils.Logger;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockTextures;
import com.za.zenith.world.blocks.BlockTypeRegistry;
import com.za.zenith.world.blocks.MiningSettings;

public class BlockDataLoader extends AbstractJsonLoader<BlockDefinition> {

    public BlockDataLoader() {
        super("blocks");
    }

    @Override
    protected void parseAndRegister(JsonElement el, String sourcePath) {
        try {
            JsonObject obj = el.getAsJsonObject();
            Identifier identifier = Identifier.of(obj.get("identifier").getAsString());
            
            int id;
            if (obj.has("id")) {
                id = obj.get("id").getAsInt();
            } else {
                int existingId = BlockRegistry.getRegistry().getId(identifier);
                if (existingId != -1) {
                    id = existingId;
                } else {
                    id = BlockRegistry.getRegistry().getNextAvailableId();
                }
            }
            
            String translationKey = obj.get("translationKey").getAsString();
            boolean solid = obj.get("solid").getAsBoolean();
            boolean transparent = obj.get("transparent").getAsBoolean();
            String type = obj.has("type") ? obj.get("type").getAsString() : "default";

            BlockDefinition def = BlockTypeRegistry.create(type, id, identifier, translationKey, solid, transparent);
            def.setSourcePath(sourcePath);
            
            // --- HOT RELOAD FIX: Clear existing lists ---
            try {
                java.lang.reflect.Field drField = BlockDefinition.class.getDeclaredField("dropRules");
                drField.setAccessible(true);
                ((java.util.List<?>) drField.get(def)).clear();
                
                java.lang.reflect.Field tagsField = BlockDefinition.class.getDeclaredField("tags");
                tagsField.setAccessible(true);
                ((java.util.List<?>) tagsField.get(def)).clear();
                
                java.lang.reflect.Field compField = BlockDefinition.class.getDeclaredField("components");
                compField.setAccessible(true);
                ((java.util.List<?>) compField.get(def)).clear();
            } catch (Exception e) {
                Logger.error("Failed to clear BlockDefinition lists for hot-reload: " + e.getMessage());
            }

            if (obj.has("hardness")) def.setHardness(obj.get("hardness").getAsFloat());
            if (obj.has("fluid_type")) def.setFluidType(obj.get("fluid_type").getAsString());
            if (obj.has("fellingStages")) def.setFellingStages(obj.get("fellingStages").getAsInt());
            if (obj.has("nextStage")) def.setNextStage(Identifier.of(obj.get("nextStage").getAsString()));
            if (obj.has("next_stage")) def.setNextStage(Identifier.of(obj.get("next_stage").getAsString()));
            if (obj.has("soilingAmount")) def.setSoilingAmount(obj.get("soilingAmount").getAsFloat());
            if (obj.has("cleaningAmount")) def.setCleaningAmount(obj.get("cleaningAmount").getAsFloat());
            if (obj.has("firingTemperature")) def.setFiringTemperature(obj.get("firingTemperature").getAsFloat());
            if (obj.has("requiredTool")) def.setRequiredTool(obj.get("requiredTool").getAsString());
            if (obj.has("dropItem")) def.setDropItem(obj.get("dropItem").getAsString());
            if (obj.has("dropChance")) def.setDropChance(obj.get("dropChance").getAsFloat());
            
            if (obj.has("wobble_animation")) def.setWobbleAnimation(obj.get("wobble_animation").getAsString());
            if (obj.has("interaction_cooldown")) def.setInteractionCooldown(obj.get("interaction_cooldown").getAsFloat());
            if (obj.has("healing_speed")) def.setHealingSpeed(obj.get("healing_speed").getAsFloat());
            if (obj.has("emission")) def.setEmission(obj.get("emission").getAsInt());
            if (obj.has("light")) {
                JsonObject lObj = obj.getAsJsonObject("light");
                com.za.zenith.world.lighting.LightData ld = new com.za.zenith.world.lighting.LightData();
                if (lObj.has("type")) ld.type = com.za.zenith.world.lighting.LightData.parseType(lObj.get("type").getAsString());
                if (lObj.has("color")) {
                    JsonArray c = lObj.getAsJsonArray("color");
                    ld.color.set(c.get(0).getAsFloat(), c.get(1).getAsFloat(), c.get(2).getAsFloat());
                }
                if (lObj.has("intensity")) ld.intensity = lObj.get("intensity").getAsFloat();
                if (lObj.has("radius")) ld.radius = lObj.get("radius").getAsFloat();
                if (lObj.has("spotAngle")) ld.spotAngle = lObj.get("spotAngle").getAsFloat();
                if (lObj.has("flicker")) ld.flicker = lObj.get("flicker").getAsBoolean();
                if (lObj.has("dynamic")) ld.dynamic = lObj.get("dynamic").getAsBoolean();
                def.setLightData(ld);
            }
            if (obj.has("particle_grid")) def.setParticleGridSize(obj.get("particle_grid").getAsInt());
            if (obj.has("particle_scale")) def.setParticleScale(obj.get("particle_scale").getAsFloat());
            if (obj.has("weak_spot_particles")) def.setWeakSpotParticles(obj.get("weak_spot_particles").getAsInt());
            if (obj.has("weak_spot_particle_scale")) def.setWeakSpotParticleScale(obj.get("weak_spot_particle_scale").getAsFloat());

            if (obj.has("particle_material")) {
                String mat = obj.get("particle_material").getAsString().toLowerCase();
                int matId = switch(mat) {
                    case "wood" -> com.za.zenith.world.particles.ShardParticle.MAT_WOOD;
                    case "leaves", "grass", "plant" -> com.za.zenith.world.particles.ShardParticle.MAT_LEAVES;
                    default -> com.za.zenith.world.particles.ShardParticle.MAT_GENERIC;
                };
                def.setParticleMaterial(matId);
            } else if (def.isTinted()) {
                def.setParticleMaterial(com.za.zenith.world.particles.ShardParticle.MAT_LEAVES);
            }

            if (obj.has("breaking_pattern")) {
                String pattern = obj.get("breaking_pattern").getAsString().toLowerCase();
                int patternId = switch(pattern) {
                    case "wood" -> 1;
                    case "stone" -> 2;
                    case "shatter", "glass" -> 3;
                    default -> 0;
                };
                def.setBreakingPattern(patternId);
            }

            if (obj.has("mining_logic")) {
                JsonObject ml = obj.getAsJsonObject("mining_logic");
                String strategy = ml.has("strategy") ? ml.get("strategy").getAsString() : "default";
                float precision = ml.has("precision") ? ml.get("precision").getAsFloat() : 0.2f;
                float multiplier = ml.has("miss_multiplier") ? ml.get("miss_multiplier").getAsFloat() : 1.0f;
                
                org.joml.Vector3f wsColor = new org.joml.Vector3f(1.0f, 1.0f, 1.0f);
                if (ml.has("weak_spot_color")) {
                    JsonArray col = ml.getAsJsonArray("weak_spot_color");
                    wsColor.set(col.get(0).getAsFloat(), col.get(1).getAsFloat(), col.get(2).getAsFloat());
                }
                def.setMiningSettings(new MiningSettings(strategy, precision, multiplier, wsColor));
            }

            if (obj.has("drops")) {
                JsonArray dropsArr = obj.getAsJsonArray("drops");
                for (JsonElement dropEl : dropsArr) {
                    JsonObject dropObj = dropEl.getAsJsonObject();
                    String tool = dropObj.has("tool") ? dropObj.get("tool").getAsString() : "none";
                    String item = dropObj.get("item").getAsString();
                    float chance = dropObj.has("chance") ? dropObj.get("chance").getAsFloat() : 1.0f;
                    boolean dropOnHit = dropObj.has("drop_on_hit") && dropObj.get("drop_on_hit").getAsBoolean();
                    float penalty = dropObj.has("durability_penalty") ? dropObj.get("durability_penalty").getAsFloat() : 0.6f;
                    def.addDropRule(new com.za.zenith.world.blocks.DropRule(tool, item, chance, dropOnHit, penalty));
                }
            }

            if (obj.has("supportScavenge")) def.setSupportScavenge(obj.get("supportScavenge").getAsBoolean());
            if (obj.has("requires_support")) def.setRequiresSupport(obj.get("requires_support").getAsBoolean());
            if (obj.has("alwaysRender")) def.setAlwaysRender(obj.get("alwaysRender").getAsBoolean());
            if (obj.has("replaceable")) def.setReplaceable(obj.get("replaceable").getAsBoolean());
            if (obj.has("sway")) def.setSway(obj.get("sway").getAsBoolean());

            if (obj.has("upperTexture")) {
                def.setUpperTexture("zenith/textures/block/" + obj.get("upperTexture").getAsString());
            }

            if (obj.has("placement")) {
                def.setPlacementType(com.za.zenith.world.blocks.PlacementType.valueOf(obj.get("placement").getAsString().toUpperCase()));
            }

            if (obj.has("tinted_faces") && obj.get("tinted_faces").isJsonArray()) {
                java.util.List<String> tintedFaces = new java.util.ArrayList<>();
                for (JsonElement faceEl : obj.getAsJsonArray("tinted_faces")) {
                    tintedFaces.add(faceEl.getAsString());
                }
                def.setTintedFaces(tintedFaces);
            }

            if (obj.has("tags") && obj.get("tags").isJsonArray()) {
                JsonArray tagsArr = obj.getAsJsonArray("tags");
                for (JsonElement tagEl : tagsArr) {
                    String tag = tagEl.getAsString();
                    def.addTag(tag);
                    if (tag.equals("zenith:tinted")) {
                        def.setTinted(true);
                    }
                }
            }

            if (obj.has("shape")) {
                JsonArray shapeArr = obj.getAsJsonArray("shape");
                com.za.zenith.world.physics.VoxelShape voxelShape = new com.za.zenith.world.physics.VoxelShape();
                if (shapeArr.size() > 0) {
                    for (JsonElement boxEl : shapeArr) {
                        JsonArray boxCoords = boxEl.getAsJsonArray();
                        float minX = boxCoords.get(0).getAsFloat();
                        float minY = boxCoords.get(1).getAsFloat();
                        float minZ = boxCoords.get(2).getAsFloat();
                        float maxX = boxCoords.get(3).getAsFloat();
                        float maxY = boxCoords.get(4).getAsFloat();
                        float maxZ = boxCoords.get(5).getAsFloat();
                        voxelShape.addBox(new com.za.zenith.world.physics.AABB(minX, minY, minZ, maxX, maxY, maxZ));
                    }
                    def.setShape(voxelShape);
                } else {
                    def.setShape(voxelShape);
                    def.setFullCube(false);
                }
            }
            if (obj.has("textures")) {
                JsonObject tex = obj.getAsJsonObject("textures");
                String base = "zenith/textures/block/";
                String innerTex = tex.has("inner") ? base + tex.get("inner").getAsString() : null;

                if (tex.has("all")) {
                    String all = base + tex.get("all").getAsString();
                    def.setTextures(new BlockTextures(all, all, all, all, all, all, innerTex != null ? innerTex : all));
                } else {
                    String top = base + tex.get("top").getAsString();
                    String bottom = base + tex.get("bottom").getAsString();
                    String north = base + (tex.has("north") ? tex.get("north").getAsString() : tex.get("side").getAsString());
                    String south = base + (tex.has("south") ? tex.get("south").getAsString() : tex.get("side").getAsString());
                    String east = base + (tex.has("east") ? tex.get("east").getAsString() : tex.get("side").getAsString());
                    String west = base + (tex.has("west") ? tex.get("west").getAsString() : tex.get("side").getAsString());
                    def.setTextures(new BlockTextures(top, bottom, north, south, east, west, innerTex != null ? innerTex : north));
                }
            }
            
            // --- FINAL PARTICLE MATERIAL RESOLVE (AFTER TAGS) ---
            if (obj.has("particle_material")) {
                String mat = obj.get("particle_material").getAsString().toLowerCase();
                int matId = switch(mat) {
                    case "wood" -> com.za.zenith.world.particles.ShardParticle.MAT_WOOD;
                    case "leaves", "grass", "plant" -> com.za.zenith.world.particles.ShardParticle.MAT_LEAVES;
                    default -> com.za.zenith.world.particles.ShardParticle.MAT_GENERIC;
                };
                def.setParticleMaterial(matId);
            } else if (def.isTinted()) {
                // Если блок тонируемый, он по умолчанию считается растительностью
                def.setParticleMaterial(com.za.zenith.world.particles.ShardParticle.MAT_LEAVES);
            }

            if (obj.has("components") && obj.get("components").isJsonArray()) {
                JsonArray compArr = obj.getAsJsonArray("components");
                for (JsonElement compEl : compArr) {
                    com.za.zenith.world.blocks.component.BlockComponent component = com.za.zenith.engine.resources.AssetManager.getGson().fromJson(compEl, com.za.zenith.world.blocks.component.BlockComponent.class);
                    if (component != null) {
                        def.addComponent(component);
                    }
                }
            }

            // Если форма не задана явно, и блок прозрачный - пробуем автогенерацию
            if (!obj.has("shape") && transparent) {
                def.autoGenerateShape();
            }

            BlockRegistry.registerBlock(def);
        } catch (Exception e) {
            Logger.error("Failed to parse block " + sourcePath + ": " + e.getMessage());
        }
    }
}