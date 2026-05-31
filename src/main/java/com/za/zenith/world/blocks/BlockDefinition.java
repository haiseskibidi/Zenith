package com.za.zenith.world.blocks;

import com.google.gson.annotations.SerializedName;
import com.za.zenith.utils.I18n;
import com.za.zenith.utils.Identifier;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.blocks.entity.BlockEntity;
import com.za.zenith.world.physics.VoxelShape;
import com.za.zenith.world.items.Item;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.entities.ItemEntity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class BlockDefinition implements com.za.zenith.utils.LiveReloadable {
    private transient String sourcePath;

    @Override
    public String getSourcePath() { return sourcePath; }

    @Override
    public void setSourcePath(String path) { this.sourcePath = path; }

    private transient int flags = 0;
    public static final int FLAG_SOLID = 1;
    public static final int FLAG_TRANSPARENT = 2;
    public static final int FLAG_LEAVES = 4;
    public static final int FLAG_TINTED = 8;
    public static final int FLAG_FULL_CUBE = 16;
    public static final int FLAG_TRANSLUCENT = 32;

    @SerializedName("tinted_faces")
    private List<String> tintedFaces = null;
    private transient int tintedFacesMask = 63; // 111111 binary, all 6 faces tinted by default

    public void computeFlags() {
        flags = 0;
        if (solid) flags |= FLAG_SOLID;
        if (transparent) flags |= FLAG_TRANSPARENT;
        if (translucent) flags |= FLAG_TRANSLUCENT;
        if (identifier != null && identifier.toString().contains("leaves")) flags |= FLAG_LEAVES;
        if (tinted || tags.contains("zenith:tinted")) {
            flags |= FLAG_TINTED;
            if (tintedFaces != null) {
                tintedFacesMask = 0;
                for (String face : tintedFaces) {
                    switch (face.toLowerCase()) {
                        case "north" -> tintedFacesMask |= (1 << 0);
                        case "south" -> tintedFacesMask |= (1 << 1);
                        case "east" -> tintedFacesMask |= (1 << 2);
                        case "west" -> tintedFacesMask |= (1 << 3);
                        case "top", "up" -> tintedFacesMask |= (1 << 4);
                        case "bottom", "down" -> tintedFacesMask |= (1 << 5);
                        case "all" -> tintedFacesMask = 63;
                        case "sides" -> tintedFacesMask |= 15; // North, South, East, West
                    }
                }
            } else {
                tintedFacesMask = 63;
            }
        }
        if (fullCube) flags |= FLAG_FULL_CUBE;
    }

    public boolean is(int flag) {
        return (flags & flag) != 0;
    }

    public boolean isFaceTinted(int face) {
        return is(FLAG_TINTED) && (tintedFacesMask & (1 << face)) != 0;
    }

    public void setTintedFaces(List<String> tintedFaces) {
        this.tintedFaces = tintedFaces;
    }

    @Override
    public void onLiveReload() {
        computeFlags();
        updateTraits();
        // Мгновенное обновление мира при изменении свойств блока
        com.za.zenith.engine.graphics.Renderer r = com.za.zenith.engine.core.GameLoop.getInstance().getRenderer();
        if (r != null) r.rebuildAllChunks();
    }

    private transient final int id;
    private Identifier identifier;
    @SerializedName("translationKey")
    private String name;
    private boolean solid;
    private boolean transparent;
    private boolean translucent;
    private float hardness = 1.0f; // Default hardness
    private float wetnessFactor = 0.5f; // How shiny the block gets when wet (0.0 = absorbs, 1.0 = glossy)
    private String requiredTool = "none"; // pickaxe, shovel, axe, crowbar, knife
    
    // Legacy support fields
    @SerializedName("dropItem")
    private String dropItem = null;
    @SerializedName("dropChance")
    private float dropChance = 1.0f;
    
    // Advanced drop rules
    private transient final List<DropRule> dropRules = new ArrayList<>();
    private transient final List<String> tags = new ArrayList<>();
    
    @SerializedName("supportScavenge")
    private boolean canSupportScavenge = false;
    @SerializedName("fellingStages")
    private int fellingStages = 0;
    @SerializedName("next_stage")
    private Identifier nextStage = null;
    @SerializedName("alwaysRender")
    private boolean alwaysRender = false;
    @SerializedName("replaceable")
    private boolean replaceable = false;
    @SerializedName("requires_support")
    private boolean requiresSupport = false;
    private transient boolean tinted = false;
    @SerializedName("sway")
    private boolean sway = false;
    @SerializedName("upperTexture")
    private String upperTexture = null;
 // Текстура для верхней части DOUBLE_PLANT
    @SerializedName("placement")
    private PlacementType placementType = PlacementType.DEFAULT;
    private BlockTextures textures;
    @SerializedName("fullCube")
    private boolean fullCube = true;
    @SerializedName("soilingAmount")
    private float soilingAmount = 0.0f;
    @SerializedName("cleaningAmount")
    private float cleaningAmount = 0.0f;
    @SerializedName("firingTemperature")
    private float firingTemperature = 0.0f;
    @SerializedName("components")
    private final List<com.za.zenith.world.blocks.component.BlockComponent> components = new ArrayList<>();

    private transient com.za.zenith.world.blocks.component.BlockComponent traitCraftingSurface;
    private transient com.za.zenith.world.blocks.component.BlockComponent traitCarvable;

    public void addComponent(com.za.zenith.world.blocks.component.BlockComponent component) {
        components.add(component);
        updateTraits();
    }

    private void updateTraits() {
        traitCraftingSurface = null;
        traitCarvable = null;
        for (var comp : components) {
            if (comp instanceof com.za.zenith.world.blocks.component.CraftingSurfaceComponent) traitCraftingSurface = comp;
            if (comp instanceof com.za.zenith.world.blocks.component.CarvableComponent) traitCarvable = comp;
        }
    }

    public <T extends com.za.zenith.world.blocks.component.BlockComponent> T getComponent(Class<T> clazz) {
        if (clazz.isInstance(traitCraftingSurface)) return clazz.cast(traitCraftingSurface);
        if (clazz.isInstance(traitCarvable)) return clazz.cast(traitCarvable);

        for (var comp : components) {
            if (clazz.isInstance(comp)) return clazz.cast(comp);
        }
        return null;
    }

    public List<com.za.zenith.world.blocks.component.BlockComponent> getComponents() {
        return components;
    }

    @SerializedName("wobble_animation")
    private String wobbleAnimation = "block_wobble";
    @SerializedName("breaking_pattern")
    private int breakingPattern = 0; // 0=Generic, 1=Wood, 2=Stone, etc.
    @SerializedName("mining_logic")
    private MiningSettings miningSettings = MiningSettings.DEFAULT;
    @SerializedName("interaction_cooldown")
    private float interactionCooldown = -1.0f; // -1 means use PhysicsSettings.baseMiningCooldown
    @SerializedName("healing_speed")
    private float healingSpeed = 0.1f; // Default: heals 10% of max health per second
    @SerializedName("emission")
    private int emission = 0; // Light emission level (0-15)
    private transient com.za.zenith.world.lighting.LightData lightData = null;
    @SerializedName("particle_grid")
    private int particleGridSize = 2; // Default 2x2x2 shards for cleaner look
    private transient int innerTextureIndex = -1;
    @SerializedName("weak_spot_particles")
    private int weakSpotParticles = 2; // Default particles on weak spot hit
    @SerializedName("particle_scale")
    private float particleScale = 1.0f; // Multiplier for destruction shards
    @SerializedName("weak_spot_particle_scale")
    private float weakSpotParticleScale = 1.0f; // Multiplier for impact shards
    private transient int particleMaterial = com.za.zenith.world.particles.ShardParticle.MAT_GENERIC;

    public int getParticleMaterial() { return particleMaterial; }
    public void setParticleMaterial(int material) { this.particleMaterial = material; }

    public com.za.zenith.world.lighting.LightData getLightData() { return lightData; }
    public void setLightData(com.za.zenith.world.lighting.LightData lightData) { this.lightData = lightData; }

    public int getEmission() { return emission; }
    public BlockDefinition setEmission(int emission) { this.emission = emission; return this; }

    public float getParticleScale() { return particleScale; }
    public void setParticleScale(float scale) { this.particleScale = scale; }
    public float getWeakSpotParticleScale() { return weakSpotParticleScale; }
    public void setWeakSpotParticleScale(float scale) { this.weakSpotParticleScale = scale; }

    public int getWeakSpotParticles() { return weakSpotParticles; }
    public void setWeakSpotParticles(int count) { this.weakSpotParticles = count; }

    public int getParticleGridSize() { return particleGridSize; }
    public void setParticleGridSize(int size) { this.particleGridSize = size; }
    public int getInnerTextureIndex() { return innerTextureIndex; }
    public void setInnerTextureIndex(int index) { this.innerTextureIndex = index; }

    public float getHealingSpeed() {
        return healingSpeed;
    }

    public BlockDefinition setHealingSpeed(float healingSpeed) {
        this.healingSpeed = healingSpeed;
        return this;
    }

    public float getInteractionCooldown() {
        if (interactionCooldown < 0) {
            return com.za.zenith.world.physics.PhysicsSettings.getInstance().baseMiningCooldown;
        }
        return interactionCooldown;
    }

    public void setInteractionCooldown(float interactionCooldown) {
        this.interactionCooldown = interactionCooldown;
    }

    // ... в методе getTextures() или аналогичном ...
    public String getWobbleAnimation() {
        return wobbleAnimation;
    }

    public MiningSettings getMiningSettings() {
        return miningSettings;
    }

    public void setMiningSettings(MiningSettings miningSettings) {
        this.miningSettings = miningSettings;
    }

    public void setWobbleAnimation(String wobbleAnimation) {
        this.wobbleAnimation = wobbleAnimation;
    }

    public int getBreakingPattern() {
        return breakingPattern;
    }

    public void setBreakingPattern(int breakingPattern) {
        this.breakingPattern = breakingPattern;
    }

    public void setUpperTexture(String upperTexture) {
        this.upperTexture = upperTexture;
    }

    public String getUpperTexture() {
        return upperTexture;
    }

    public float getFiringTemperature() {
        return firingTemperature;
    }

    public void setFiringTemperature(float firingTemperature) {
        this.firingTemperature = firingTemperature;
    }

    public float getSoilingAmount() {
        return soilingAmount;
    }

    public void setSoilingAmount(float soilingAmount) {
        this.soilingAmount = soilingAmount;
    }

    public float getCleaningAmount() {
        return cleaningAmount;
    }

    public void setCleaningAmount(float cleaningAmount) {
        this.cleaningAmount = cleaningAmount;
    }

    // Default shape is a full cube. Subclasses can override.
    protected VoxelShape shape = VoxelShape.FULL_CUBE;

    public BlockDefinition(int id, String name, boolean solid, boolean transparent) {
        this.id = id;
        this.identifier = Identifier.of(name.replace("block.", "").replace(".", ":"));
        this.name = name;
        this.solid = solid;
        this.transparent = transparent;
    }

    public BlockDefinition(int id, Identifier identifier, String translationKey, boolean solid, boolean transparent) {
        this.id = id;
        this.identifier = identifier;
        this.name = translationKey;
        this.solid = solid;
        this.transparent = transparent;
    }

    public BlockDefinition addDropRule(DropRule rule) {
        this.dropRules.add(rule);
        return this;
    }

    public BlockDefinition addTag(String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
        return this;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public List<DropRule> getDropRules() {
        return dropRules;
    }

    public BlockDefinition setTextures(BlockTextures textures) {
        this.textures = textures;
        return this;
    }

    public BlockDefinition setShape(VoxelShape shape) {
        this.shape = shape;
        this.fullCube = shape.isFullCube();
        return this;
    }

    /**
     * Автоматически генерирует форму блока на основе его текстуры.
     * Используется для неполных блоков без явно заданной формы в JSON.
     */
    public void autoGenerateShape() {
        if (textures == null) return;
        
        // Для прозрачных блоков (как кресты) или неполных берем основную текстуру
        String texPath = textures.getTop(); 
        com.za.zenith.world.physics.AABB texAABB = com.za.zenith.utils.TextureAABBGenerator.generateAABB(texPath);
        
        if (texAABB != null) {
            // AAA Polish: Добавляем небольшой padding (0.05 блока), чтобы по мелким предметам было легче попасть.
            // Иначе хитбокс палки — это линия шириной в 2 пикселя.
            float padding = 0.05f;
            this.shape = new VoxelShape(new com.za.zenith.world.physics.AABB(
                Math.max(0.0f, texAABB.minX() - padding), Math.max(0.0f, texAABB.minY() - padding), Math.max(0.0f, texAABB.minZ() - padding),
                Math.min(1.0f, texAABB.maxX() + padding), Math.min(1.0f, texAABB.maxY() + padding), Math.min(1.0f, texAABB.maxZ() + padding)
            ));
            this.fullCube = false;
        }
    }

    public BlockDefinition setHardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    public BlockDefinition setRequiredTool(String tool) {
        this.requiredTool = tool.toLowerCase();
        return this;
    }

    public BlockDefinition setPlacementType(PlacementType type) {
        this.placementType = type;
        return this;
    }

    public int getId() {
        return id;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public String getName() {
        return I18n.get(name);
    }

    public float getHardness() {
        return hardness;
    }

    public float getWetnessFactor() {
        return wetnessFactor;
    }

    public String getRequiredTool() {
        return requiredTool;
    }

    public String getDropItem() {
        return dropItem;
    }

    public BlockDefinition setDropItem(String dropItem) {
        this.dropItem = dropItem;
        return this;
    }

    public float getDropChance() {
        return dropChance;
    }

    public BlockDefinition setDropChance(float dropChance) {
        this.dropChance = dropChance;
        return this;
    }

    public boolean canSupportScavenge() {
        return canSupportScavenge;
    }

    public BlockDefinition setSupportScavenge(boolean support) {
        this.canSupportScavenge = support;
        return this;
    }

    public int getFellingStages() {
        return fellingStages;
    }

    public BlockDefinition setFellingStages(int fellingStages) {
        this.fellingStages = fellingStages;
        return this;
    }

    public Identifier getNextStage() {
        return nextStage;
    }

    public BlockDefinition setNextStage(Identifier nextStage) {
        this.nextStage = nextStage;
        return this;
    }

    public PlacementType getPlacementType() {
        return placementType;
    }

    public boolean isAlwaysRender() {
        return alwaysRender;
    }

    public BlockDefinition setAlwaysRender(boolean alwaysRender) {
        this.alwaysRender = alwaysRender;
        return this;
    }

    public boolean isReplaceable() {
        return replaceable;
    }

    public BlockDefinition setReplaceable(boolean replaceable) {
        this.replaceable = replaceable;
        return this;
    }

    public boolean isSway() {
        return sway;
    }

    public BlockDefinition setSway(boolean sway) {
        this.sway = sway;
        return this;
    }

    public boolean isTinted() {
        return tinted;
    }

    public BlockDefinition setTinted(boolean tinted) {
        this.tinted = tinted;
        return this;
    }

    public boolean isSolid() {
        return solid;
    }

    public void setRequiresSupport(boolean requiresSupport) {
        this.requiresSupport = requiresSupport;
    }

    public boolean requiresSupport() {
        return requiresSupport;
    }

    /**
     * Вызывается, когда один из соседних блоков изменяется.
     * @param world Мир
     * @param pos Позиция текущего блока
     * @param neighborBlock Новый блок соседа
     * @param dir Направление к изменившемуся соседу
     */
    public void onNeighborChange(com.za.zenith.world.World world, BlockPos pos, Block neighborBlock, com.za.zenith.utils.Direction dir) {
        if (components != null) {
            for (var component : components) {
                component.onNeighborChanged(world, pos, pos.offset(dir));
            }
        }
        
        // Базовая логика: если блоку нужна опора и блок снизу стал воздухом — разрушаемся
        if (requiresSupport && dir == com.za.zenith.utils.Direction.DOWN) {
            if (neighborBlock.isAir()) {
                world.destroyBlock(pos, null);
                return;
            }
        }

        // Логика DOUBLE_PLANT
        if (placementType == PlacementType.DOUBLE_PLANT) {
            Block current = world.getBlock(pos);
            if (current.getMetadata() == 0) { // Низ
                // Если верх (UP) стал воздухом или не является верхом этого же растения
                if (dir == com.za.zenith.utils.Direction.UP) {
                    if (neighborBlock.isAir() || neighborBlock.getType() != id || neighborBlock.getMetadata() != 1) {
                        world.destroyBlock(pos, null);
                    }
                }
            } else if (current.getMetadata() == 1) { // Верх
                // Если низ (DOWN) стал воздухом или не является низом этого же растения
                if (dir == com.za.zenith.utils.Direction.DOWN) {
                    if (neighborBlock.isAir() || neighborBlock.getType() != id || neighborBlock.getMetadata() != 0) {
                        world.destroyBlock(pos, null);
                    }
                }
            }
        }
    }

    public boolean isTransparent() {
        return transparent;
    }

    public boolean isFullCube() {
        return fullCube;
    }


    public BlockDefinition setFullCube(boolean fullCube) {
        this.fullCube = fullCube;
        return this;
    }

    public BlockTextures getTextures() {
        return textures;
    }
    
    public VoxelShape getShape(byte metadata) {
        return shape;
    }

    public com.za.zenith.world.physics.VoxelShape getShape(com.za.zenith.world.World world, BlockPos pos, byte metadata) {
        com.za.zenith.world.physics.VoxelShape base = getShape(metadata);
        if (world == null || pos == null || components.isEmpty()) return base;

        java.util.List<com.za.zenith.world.physics.AABB> dynamicBoxes = new java.util.ArrayList<>(base.getBoxes());
        boolean changed = false;
        for (var component : components) {
            int before = dynamicBoxes.size();
            component.addDynamicBoxes(world, pos, dynamicBoxes);
            if (dynamicBoxes.size() > before) changed = true;
        }

        if (!changed) return base;
        
        com.za.zenith.world.physics.VoxelShape complex = new com.za.zenith.world.physics.VoxelShape();
        for (var box : dynamicBoxes) complex.addBox(box);
        return complex;
    }

    /**
     * @return true, если блок имеет логику взаимодействия на ПКМ.
     */
    public boolean hasOnUse() {
        if (components != null) {
            for (var component : components) {
                if (component.hasOnUse()) return true;
            }
        }
        return false;
    }

    /**
     * Возвращает список активных зон взаимодействия.
     * Если список пуст, взаимодействие работает по всему хитбоксу (если hasOnUse = true).
     */
    public java.util.List<InteractionZone> getInteractionZones(com.za.zenith.world.World world, BlockPos pos) {
        if (components == null || components.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<InteractionZone> zones = new java.util.ArrayList<>();
        for (var component : components) {
            zones.addAll(component.getInteractionZones(world, pos));
        }
        return zones;
    }

    public boolean isInteractableAt(com.za.zenith.world.World world, BlockPos pos, org.joml.Vector3f localHit) {
        if (!hasOnUse()) return false;
        
        java.util.List<InteractionZone> zones = getInteractionZones(world, pos);
        if (zones.isEmpty()) return true; // Весь блок интерактивен
        
        for (InteractionZone zone : zones) {
            if (zone.contains(localHit)) return true;
        }
        return false;
    }

    /**
     * Вызывается при нажатии ПКМ по блоку.
     * @param hitX Относительная координата X клика (0.0-1.0)
     * @param hitY Относительная координата Y клика (0.0-1.0)
     * @param hitZ Относительная координата Z клика (0.0-1.0)
     * @return true, если действие было поглощено и стандартная обработка не требуется.
     */
    public boolean onUse(com.za.zenith.world.World world, BlockPos pos, com.za.zenith.entities.Player player, com.za.zenith.world.items.ItemStack heldStack, float hitX, float hitY, float hitZ) {
        if (components != null) {
            for (var component : components) {
                if (component.onUse(world, pos, player, heldStack, hitX, hitY, hitZ)) return true;
            }
        }
        return false;
    }

    /**
     * Вызывается при нажатии ЛКМ по блоку.
     */
    public boolean onLeftClick(com.za.zenith.world.World world, BlockPos pos, com.za.zenith.entities.Player player, com.za.zenith.world.items.ItemStack heldStack, float hitX, float hitY, float hitZ, boolean isNewClick) {
        if (components != null) {
            for (var component : components) {
                if (component.onLeftClick(world, pos, player, heldStack, hitX, hitY, hitZ, isNewClick)) return true;
            }
        }
        return false;
    }

    /**
     * Создает новую сущность блока для данного определения.
     * Переопределяется в подклассах для блоков с логикой.
     */
    public BlockEntity createBlockEntity(BlockPos pos) {
        if (!components.isEmpty()) {
            var be = new com.za.zenith.world.blocks.entity.ModularBlockEntity(pos);
            be.ensureInventory(this);
            return be;
        }
        return null;
    }

    public boolean hasBlockEntity() {
        return !components.isEmpty();
    }

    /**
     * Вызывается непосредственно перед тем, как блок будет заменен на воздух или другой блок игроком.
     */
    public void onDestroyed(com.za.zenith.world.World world, BlockPos pos, Block block, com.za.zenith.entities.Player player) {
        if (components != null) {
            for (var component : components) {
                component.onBreak(world, pos, player);
            }
        }
    }

    /**
     * Спавнит предметы при разрушении блока.
     */
    public void spawnDrops(com.za.zenith.world.World world, BlockPos pos, Block block, com.za.zenith.entities.Player player) {
        // Advanced drops
        if (!dropRules.isEmpty()) {
            String heldTool = "none";
            if (player != null && player.getInventory().getSelectedItemStack() != null) {
                Item item = player.getInventory().getSelectedItemStack().getItem();
                com.za.zenith.world.items.component.ToolComponent tool = item.getComponent(com.za.zenith.world.items.component.ToolComponent.class);
                if (tool != null) heldTool = tool.type().name().toLowerCase();
            }

            for (DropRule rule : dropRules) {
                if (rule.requiredToolType().equalsIgnoreCase("none") || rule.requiredToolType().equalsIgnoreCase(heldTool)) {
                    if (Math.random() <= rule.chance()) {
                        Item itemToGive = com.za.zenith.world.items.ItemRegistry.getItem(Identifier.of(rule.dropItemIdentifier()));
                        if (itemToGive != null) {
                            spawnDropEntity(world, pos, new ItemStack(itemToGive));
                        }
                    }
                }
            }
        } else {
            // Legacy drops
            float chance = dropChance;
            if (Math.random() <= chance) {
                Item itemToGive = (dropItem != null) ? com.za.zenith.world.items.ItemRegistry.getItem(Identifier.of(dropItem)) : com.za.zenith.world.items.ItemRegistry.getItem(identifier);
                if (itemToGive != null) {
                    spawnDropEntity(world, pos, new ItemStack(itemToGive));
                }
            }
        }
    }

    private void spawnDropEntity(com.za.zenith.world.World world, BlockPos pos, ItemStack stack) {
        Vector3f dropPos = new Vector3f(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
        ItemEntity drop = new ItemEntity(dropPos, stack);
        drop.getVelocity().set((float)Math.random() * 0.2f - 0.1f, 0.2f, (float)Math.random() * 0.2f - 0.1f);
        drop.setAngularVelocity(new Vector3f((float)(Math.random() - 0.5) * 10f, (float)(Math.random() - 0.5) * 10f, (float)(Math.random() - 0.5) * 10f));
        world.spawnEntity(drop);
    }

    /**
     * Вызывается, когда игрок завершил разрушение блока (breakingProgress >= 1.0).
     * Если возвращает true, блок удаляется из мира.
     * Если возвращает false, блок остается (используется для многоступенчатого срубания).
     */
    public boolean onBlockBreak(com.za.zenith.world.World world, BlockPos pos, Block block, com.za.zenith.entities.Player player) {
        return true;
    }
}


