package com.za.zenith.world.blocks;

import com.za.zenith.utils.Identifier;

/**
 * Статические ссылки на базовые блоки для удобного доступа из кода.
 * Инициализируются в DataLoader.
 */
public class Blocks {
    public static BlockDefinition AIR;
    public static BlockDefinition GRASS_BLOCK;
    public static BlockDefinition DIRT;
    public static BlockDefinition STONE;
    public static BlockDefinition OAK_LOG;
    public static BlockDefinition BIRCH_LOG;
    public static BlockDefinition SPRUCE_LOG;
    public static BlockDefinition JUNGLE_LOG;
    public static BlockDefinition ACACIA_LOG;
    public static BlockDefinition DARK_OAK_LOG;
    public static BlockDefinition STRIPPED_OAK_LOG;
    public static BlockDefinition STRIPPED_BIRCH_LOG;
    public static BlockDefinition STRIPPED_SPRUCE_LOG;
    public static BlockDefinition STRIPPED_JUNGLE_LOG;
    public static BlockDefinition STRIPPED_ACACIA_LOG;
    public static BlockDefinition STRIPPED_DARK_OAK_LOG;
    
    public static BlockDefinition OAK_LEAVES;
    public static BlockDefinition BIRCH_LEAVES;
    public static BlockDefinition SPRUCE_LEAVES;
    public static BlockDefinition JUNGLE_LEAVES;
    public static BlockDefinition ACACIA_LEAVES;
    public static BlockDefinition DARK_OAK_LEAVES;
    
    public static BlockDefinition OAK_PLANKS;
    public static BlockDefinition COBBLESTONE;
    public static BlockDefinition BEDROCK;
    public static BlockDefinition SAND;
    public static BlockDefinition GRAVEL;
    public static BlockDefinition GOLD_ORE;
    public static BlockDefinition IRON_ORE;
    public static BlockDefinition COAL_ORE;
    public static BlockDefinition BOOKSHELF;
    public static BlockDefinition MOSSY_COBBLESTONE;
    public static BlockDefinition OBSIDIAN;
    public static BlockDefinition ASPHALT;
    public static BlockDefinition RUSTY_METAL;
    public static BlockDefinition GLASS;
    public static BlockDefinition BRICKS;
    public static BlockDefinition STONE_BRICKS;
    public static BlockDefinition CYAN_CONCRETE;
    public static BlockDefinition GRAY_CONCRETE;
    public static BlockDefinition WHITE_CONCRETE;
    public static BlockDefinition SNOW;
    public static BlockDefinition GRANITE;
    public static BlockDefinition DIORITE;
    public static BlockDefinition ANDESITE;
    public static BlockDefinition STONE_SLAB;
    public static BlockDefinition STONE_STAIRS;
    public static BlockDefinition BRICK_SLAB;
    public static BlockDefinition BRICK_STAIRS;
    public static BlockDefinition CAMPFIRE;
    public static BlockDefinition GENERATOR;
    public static BlockDefinition CABLE;
    public static BlockDefinition ELECTRIC_LAMP;
    public static BlockDefinition BATTERY;
    public static BlockDefinition UNFIRED_VESSEL;
    public static BlockDefinition FIRED_VESSEL;
    public static BlockDefinition CLAY;
    public static BlockDefinition WATER;
    public static BlockDefinition OIL;
    public static BlockDefinition LAVA;
    public static BlockDefinition SHORT_GRASS;
    public static BlockDefinition TALL_GRASS;
    public static BlockDefinition DIAMOND_ORE;
    public static BlockDefinition EMERALD_ORE;
    public static BlockDefinition LAPIS_ORE;
    public static BlockDefinition REDSTONE_ORE;
    public static BlockDefinition COPPER_ORE;
    public static BlockDefinition PIT_KILN;
    public static BlockDefinition BURNING_PIT_KILN;
    public static BlockDefinition STUMP;
    public static BlockDefinition UNFINISHED_STUMP;
    public static BlockDefinition RUSTY_CAR_CHASSIS;
    public static BlockDefinition CAR_TIRE;
    public static BlockDefinition TIRE_WITH_BOARD;
    public static BlockDefinition SCAVENGER_TABLE;
    public static BlockDefinition CHEST;
    
    public static void init() {
        for (java.lang.reflect.Field field : Blocks.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    Identifier id = Identifier.of("zenith", field.getName().toLowerCase());
                    BlockDefinition def = BlockRegistry.getRegistry().get(id);
                    if (def != null) {
                        field.setAccessible(true);
                        field.set(null, def);
                    } else {
                        com.za.zenith.utils.Logger.error("Blocks.init: Could not find block in registry for field %s (id: %s)", field.getName(), id.toString());
                    }
                } catch (Exception e) {
                    com.za.zenith.utils.Logger.error("Failed to auto-init block field: " + field.getName());
                }
            }
        }
    }
}


