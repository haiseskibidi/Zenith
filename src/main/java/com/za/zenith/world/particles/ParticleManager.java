package com.za.zenith.world.particles;

import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockTextures;
import com.za.zenith.world.blocks.FellingLogBlockDefinition;
import com.za.zenith.world.blocks.WoodTypeRegistry;
import com.za.zenith.utils.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Глобальный менеджер классической системы частиц.
 * Упор на визуальную стабильность и отсутствие коллизий.
 */
public class ParticleManager {
    private static ParticleManager instance;
    private final List<Particle> particles = new ArrayList<>();
    private final List<Particle> pendingAdd = new ArrayList<>();
    private int spawnedThisTick = 0;

    public static ParticleManager getInstance() {
        if (instance == null) instance = new ParticleManager();
        return instance;
    }

    public void addParticle(Particle p) {
        if (p != null) {
            pendingAdd.add(p);
        }
    }

    public void update(float deltaTime, World world) {
        spawnedThisTick = 0;
        if (!pendingAdd.isEmpty()) {
            particles.addAll(pendingAdd);
            pendingAdd.clear();
        }

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(deltaTime);
            if (p.isRemoved()) {
                it.remove();
            }
        }
        
        int limit = com.za.zenith.world.physics.PhysicsSettings.getInstance().particleLimit;
        if (particles.size() > limit) {
            int toRemove = Math.min(particles.size(), particles.size() - limit + 100);
            for (int i = 0; i < toRemove && !particles.isEmpty(); i++) particles.remove(0);
        }
    }

    private BlockTextures resolveTextures(BlockDefinition def, int metadata) {
        BlockTextures tex = def.getTextures();
        if (tex == null && def instanceof FellingLogBlockDefinition) {
            Identifier logId = WoodTypeRegistry.getLogId(metadata & 0x7F);
            if (logId != null) {
                BlockDefinition realLog = BlockRegistry.getBlock(logId);
                if (realLog != null) tex = realLog.getTextures();
            }
        }
        return tex;
    }

    private String getStrippedTexture(BlockDefinition def, int metadata, BlockTextures originalTex) {
        Identifier logId = WoodTypeRegistry.getLogId(metadata & 0x7F);
        if (logId != null) {
            Identifier strippedId = Identifier.of(logId.getNamespace(), "stripped_" + logId.getPath());
            BlockDefinition strippedDef = BlockRegistry.getBlock(strippedId);
            if (strippedDef != null && strippedDef.getTextures() != null) {
                return strippedDef.getTextures().getNorth();
            }
        }
        return originalTex.getInner();
    }

    public void spawnImpact(Vector3f pos, Vector3f normal, BlockDefinition def, int metadata, int baseCount, float damage) {
        if (baseCount <= 0) return;

        int budget = com.za.zenith.world.physics.PhysicsSettings.getInstance().particleBudget;
        int count = Math.max(1, (int)(baseCount * (damage * 2.5f)));
        if (spawnedThisTick + count > budget) {
            count = Math.max(0, budget - spawnedThisTick);
        }
        if (count <= 0) return;
        spawnedThisTick += count;

        com.za.zenith.engine.graphics.DynamicTextureAtlas atlas = com.za.zenith.engine.core.GameLoop.getInstance().getRenderer().getAtlas();
        
        // 1. ОПРЕДЕЛЯЕМ ГРАНЬ ПО НОРМАЛИ
        int faceIndex = 0;
        float nx = Math.abs(normal.x), ny = Math.abs(normal.y), nz = Math.abs(normal.z);
        if (ny > nx && ny > nz) faceIndex = (normal.y > 0) ? 4 : 5; // Top/Bottom
        else if (nx > nz) faceIndex = (normal.x > 0) ? 2 : 3; // East/West
        else faceIndex = (normal.z > 0) ? 0 : 1; // South/North

        // 2. ВЫБОР ТЕКСТУРЫ
        BlockTextures tex = resolveTextures(def, metadata);
        String texKey = "zenith/textures/default.png";
        if (tex != null) {
            if (def instanceof FellingLogBlockDefinition && faceIndex < 4) {
                texKey = getStrippedTexture(def, metadata, tex);
            } else {
                texKey = tex.getTextureForFace(faceIndex);
            }
        }

        // 3. ВЫБОР ЦВЕТА И ОВЕРЛЕЯ
        Vector3f color = new Vector3f(1, 1, 1);
        int overlayLayer = -1;
        
        if (def.isTinted()) {
            color.set(com.za.zenith.engine.graphics.ColorProvider.getGrassColor());
            
            // Если у нас боковая грань и есть оверлей (как у травы)
            if (faceIndex < 4 && tex != null) {
                String innerKey = tex.getInner();
                String sideKey = tex.getTextureForFace(faceIndex);
                if (innerKey != null && !innerKey.equals(sideKey)) {
                    overlayLayer = (int)atlas.getLayer(innerKey);
                }
            }
        }

        int texLayer = (int)atlas.getLayer(texKey);
        float scale = 0.12f * def.getWeakSpotParticleScale();

        for (int i = 0; i < count; i++) {
            Vector3f pPos = new Vector3f(pos).add(new Vector3f(normal).mul(0.1f)).add(
                (float)(Math.random() - 0.5) * 0.05f,
                (float)(Math.random() - 0.5) * 0.05f,
                (float)(Math.random() - 0.5) * 0.05f
            );

            Vector3f vel = new Vector3f(normal).add(
                (float)(Math.random() - 0.5) * 0.5f,
                (float)(Math.random() - 0.5) * 0.5f,
                (float)(Math.random() - 0.5) * 0.5f
            );
            vel.normalize().mul(1.5f + (float)Math.random() * 2.5f);

            pendingAdd.add(new ShardParticle(pPos, vel, 0.4f + (float)Math.random() * 0.4f, scale, texLayer, overlayLayer, color));
        }
    }

    /**
     * Создает эффект разрушения блока (Shatter).
     */
    public void spawnShatter(BlockPos pos, Block block) {
        BlockDefinition def = BlockRegistry.getBlock(block.getType());
        if (def == null || block.getType() == 0) return;

        com.za.zenith.world.physics.VoxelShape shape = block.getShape();
        if (shape == null || shape.getBoxes().isEmpty()) return;

        float minX = 1, minY = 1, minZ = 1, maxX = 0, maxY = 0, maxZ = 0;
        for (com.za.zenith.world.physics.AABB box : shape.getBoxes()) {
            minX = Math.min(minX, box.getMin().x); minY = Math.min(minY, box.getMin().y); minZ = Math.min(minZ, box.getMin().z);
            maxX = Math.max(maxX, box.getMax().x); maxY = Math.max(maxY, box.getMax().y); maxZ = Math.max(maxZ, box.getMax().z);
        }

        float sizeX = maxX - minX, sizeY = maxY - minY, sizeZ = maxZ - minZ;
        int grid = (sizeX * sizeY * sizeZ < 0.15f) ? 1 : 2; 

        int budget = com.za.zenith.world.physics.PhysicsSettings.getInstance().particleBudget;
        if (spawnedThisTick + grid * grid * grid > budget) return;
        spawnedThisTick += grid * grid * grid;

        float stepX = sizeX / grid, stepY = sizeY / grid, stepZ = sizeZ / grid;
        
        com.za.zenith.engine.graphics.DynamicTextureAtlas atlas = com.za.zenith.engine.core.GameLoop.getInstance().getRenderer().getAtlas();
        BlockTextures tex = resolveTextures(def, block.getMetadata());
        float baseScale = 0.12f * def.getParticleScale();

        for (int x = 0; x < grid; x++) {
            for (int y = 0; y < grid; y++) {
                for (int z = 0; z < grid; z++) {
                    Vector3f pPos = new Vector3f(pos.x() + minX + x * stepX + stepX/2f, pos.y() + minY + y * stepY + stepY/2f, pos.z() + minZ + z * stepZ + stepZ/2f);
                    Vector3f vel = new Vector3f(pPos).sub(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f).normalize().mul(1.0f + (float)Math.random() * 2.0f);
                    vel.y += 1.5f;

                    // 1. ВЫБОР ТЕКСТУРЫ
                    String texKey = "zenith/textures/default.png";
                    if (tex != null) {
                        if (y == grid - 1 && !tex.getTop().equals(tex.getNorth())) texKey = tex.getTop();
                        else if (y == 0 && !tex.getBottom().equals(tex.getNorth())) texKey = tex.getBottom();
                        else if (def instanceof FellingLogBlockDefinition) texKey = getStrippedTexture(def, block.getMetadata(), tex);
                        else texKey = tex.getSouth(); // Always use base side texture for shatter particles to avoid spawning overlay particles
                    }

                    // 2. ВЫБОР ЦВЕТА И ОВЕРЛЕЯ
                    Vector3f color = new Vector3f(1, 1, 1);
                    int overlayLayer = -1;
                    if (def.isTinted()) {
                        color.set(com.za.zenith.engine.graphics.ColorProvider.getGrassColor());
                        // Оверлей для боковых частиц (если это трава)
                        if (y < grid - 1 && tex != null) {
                            String innerKey = tex.getInner();
                            if (innerKey != null && !innerKey.equals(tex.getSouth())) {
                                overlayLayer = (int)atlas.getLayer(innerKey);
                            }
                        }
                    }

                    pendingAdd.add(new ShardParticle(pPos, vel, 0.6f + (float)Math.random() * 0.6f, baseScale, (int)atlas.getLayer(texKey), overlayLayer, color));
                }
            }
        }
    }

    public List<Particle> getActiveParticles() { return particles; }
}
