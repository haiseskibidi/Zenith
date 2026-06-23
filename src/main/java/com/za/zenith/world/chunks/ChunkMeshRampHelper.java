package com.za.zenith.world.chunks;

import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockTextureMapper;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;

/**
 * Utility class for generating slope/ramp block geometries during chunk mesh generation.
 */
public class ChunkMeshRampHelper {

    private static final float[][] FACE_NORMALS = new float[][]{
        {0,0,1, 0,0,1, 0,0,1, 0,0,1},
        {0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1},
        {1,0,0, 1,0,0, 1,0,0, 1,0,0},
        {-1,0,0, -1,0,0, -1,0,0, -1,0,0},
        {0,1,0, 0,1,0, 0,1,0, 0,1,0},
        {0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0}
    };

    public static boolean isRampFaceVisible(ChunkNeighborhood neighborhood, int nx, int ny, int nz) {
        if (neighborhood == null) return true; // Always render all faces in inventories/items
        if (ny < 0 || ny >= Chunk.CHUNK_HEIGHT) return true;
        int nRaw = neighborhood.getRawBlockData(nx, ny, nz);
        int nType = nRaw >> 8;
        if (nType == 0) return true;
        BlockDefinition neighborDef = BlockRegistry.getBlock(nType);
        if (neighborDef == null) return true;
        return neighborDef.is(BlockDefinition.FLAG_TRANSPARENT) || neighborDef.isAlwaysRender();
    }

    public static float getFaceBlockType(int blockType, BlockDefinition def, int face, Block block, DynamicTextureAtlas atlas) {
        float faceBlockType = (float) blockType;
        if (def != null && def.isFaceTinted(face)) {
            faceBlockType = -(faceBlockType + 1.0f);
        }
        return faceBlockType;
    }

    public static float getFaceOverlayLayer(BlockDefinition def, int face, Block block, DynamicTextureAtlas atlas) {
        float overlayLayer = -1.0f;
        if (def != null && def.isFaceTinted(face)) {
            if (def.getTextures() != null) {
                String innerKey = def.getTextures().getInner();
                String sideKey = def.getTextures().getTextureForFace(face);
                if (face == 4) {
                    overlayLayer = BlockTextureMapper.uvFor(block, face, atlas)[2];
                } else if (face < 4 && innerKey != null && !innerKey.equals(sideKey)) {
                    float[] innerUv = atlas.uvFor(innerKey);
                    if (innerUv != null) {
                        overlayLayer = innerUv[2];
                    }
                }
            }
        }
        return overlayLayer;
    }

    public static void generateRampGeometry(
        MeshData currentTarget,
        Block block,
        BlockDefinition def,
        float x, float y, float z,
        int worldX, int worldY, int worldZ,
        ChunkNeighborhood neighborhood,
        DynamicTextureAtlas atlas
    ) {
        int blockType = block.getType();
        byte dir = (byte)(block.getMetadata() & 0x0F);

        // 1. BOTTOM FACE (DOWN = 5)
        if (isRampFaceVisible(neighborhood, worldX, worldY - 1, worldZ)) {
            float[] fp = {0,0,0,  1,0,0,  1,0,1,  0,0,1};
            float[] fn = {0,-1,0,  0,-1,0,  0,-1,0,  0,-1,0};
            float fbt = getFaceBlockType(blockType, def, 5, block, atlas);
            float overlay = getFaceOverlayLayer(def, 5, block, atlas);
            currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 5, atlas), 5, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
        }

        // Generate other 4 faces based on ramp direction
        switch (dir) {
            case Block.DIR_EAST: { // +X
                // Back (EAST = 2)
                if (isRampFaceVisible(neighborhood, worldX + 1, worldY, worldZ)) {
                    float[] fp = {1,0,1,  1,0,0,  1,1,0,  1,1,1};
                    float[] fn = {1,0,0,  1,0,0,  1,0,0,  1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 2, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 2, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 2, atlas), 2, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Left (NORTH = 1)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ - 1)) {
                    float[] fp = {1,0,0,  0,0,0,  0,0,0,  1,1,0};
                    float[] fn = {0,0,-1,  0,0,-1,  0,0,-1,  0,0,-1};
                    float fbt = getFaceBlockType(blockType, def, 1, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 1, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 1, atlas), 1, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Right (SOUTH = 0)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ + 1)) {
                    float[] fp = {0,0,1,  1,0,1,  1,1,1,  0,0,1};
                    float[] fn = {0,0,1,  0,0,1,  0,0,1,  0,0,1};
                    float fbt = getFaceBlockType(blockType, def, 0, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 0, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 0, atlas), 0, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Slope (UP = 4)
                if (isRampFaceVisible(neighborhood, worldX, worldY + 1, worldZ)) {
                    float[] fp = {0,0,1,  1,1,1,  1,1,0,  0,0,0};
                    float[] fn = {-0.7071f, 0.7071f, 0,  -0.7071f, 0.7071f, 0,  -0.7071f, 0.7071f, 0,  -0.7071f, 0.7071f, 0};
                    float fbt = getFaceBlockType(blockType, def, 4, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 4, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 4, atlas), 4, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                break;
            }
            case Block.DIR_WEST: { // -X
                // Back (WEST = 3)
                if (isRampFaceVisible(neighborhood, worldX - 1, worldY, worldZ)) {
                    float[] fp = {0,0,0,  0,0,1,  0,1,1,  0,1,0};
                    float[] fn = {-1,0,0,  -1,0,0,  -1,0,0,  -1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 3, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 3, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 3, atlas), 3, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Left (NORTH = 1)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ - 1)) {
                    float[] fp = {1,0,0,  0,0,0,  0,1,0,  1,0,0};
                    float[] fn = {0,0,-1,  0,0,-1,  0,0,-1,  0,0,-1};
                    float fbt = getFaceBlockType(blockType, def, 1, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 1, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 1, atlas), 1, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Right (SOUTH = 0)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ + 1)) {
                    float[] fp = {0,0,1,  1,0,1,  1,0,1,  0,1,1};
                    float[] fn = {0,0,1,  0,0,1,  0,0,1,  0,0,1};
                    float fbt = getFaceBlockType(blockType, def, 0, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 0, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 0, atlas), 0, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Slope (UP = 4)
                if (isRampFaceVisible(neighborhood, worldX, worldY + 1, worldZ)) {
                    float[] fp = {0,1,1,  1,0,1,  1,0,0,  0,1,0};
                    float[] fn = {0.7071f, 0.7071f, 0,  0.7071f, 0.7071f, 0,  0.7071f, 0.7071f, 0,  0.7071f, 0.7071f, 0};
                    float fbt = getFaceBlockType(blockType, def, 4, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 4, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 4, atlas), 4, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                break;
            }
            case Block.DIR_SOUTH: { // +Z
                // Back (SOUTH = 0)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ + 1)) {
                    float[] fp = {0,0,1,  1,0,1,  1,1,1,  0,1,1};
                    float[] fn = {0,0,1,  0,0,1,  0,0,1,  0,0,1};
                    float fbt = getFaceBlockType(blockType, def, 0, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 0, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 0, atlas), 0, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Left (WEST = 3)
                if (isRampFaceVisible(neighborhood, worldX - 1, worldY, worldZ)) {
                    float[] fp = {0,0,0,  0,0,1,  0,1,1,  0,0,0};
                    float[] fn = {-1,0,0,  -1,0,0,  -1,0,0,  -1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 3, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 3, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 3, atlas), 3, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Right (EAST = 2)
                if (isRampFaceVisible(neighborhood, worldX + 1, worldY, worldZ)) {
                    float[] fp = {1,0,1,  1,0,0,  1,0,0,  1,1,1};
                    float[] fn = {1,0,0,  1,0,0,  1,0,0,  1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 2, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 2, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 2, atlas), 2, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Slope (UP = 4)
                if (isRampFaceVisible(neighborhood, worldX, worldY + 1, worldZ)) {
                    float[] fp = {0,1,1,  1,1,1,  1,0,0,  0,0,0};
                    float[] fn = {0, 0.7071f, -0.7071f,  0, 0.7071f, -0.7071f,  0, 0.7071f, -0.7071f,  0, 0.7071f, -0.7071f};
                    float fbt = getFaceBlockType(blockType, def, 4, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 4, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 4, atlas), 4, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                break;
            }
            case Block.DIR_NORTH:
            default: { // -Z
                // Back (NORTH = 1)
                if (isRampFaceVisible(neighborhood, worldX, worldY, worldZ - 1)) {
                    float[] fp = {1,0,0,  0,0,0,  0,1,0,  1,1,0};
                    float[] fn = {0,0,-1,  0,0,-1,  0,0,-1,  0,0,-1};
                    float fbt = getFaceBlockType(blockType, def, 1, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 1, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 1, atlas), 1, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Left (WEST = 3)
                if (isRampFaceVisible(neighborhood, worldX - 1, worldY, worldZ)) {
                    float[] fp = {0,0,0,  0,0,1,  0,0,1,  0,1,0};
                    float[] fn = {-1,0,0,  -1,0,0,  -1,0,0,  -1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 3, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 3, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 3, atlas), 3, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Right (EAST = 2)
                if (isRampFaceVisible(neighborhood, worldX + 1, worldY, worldZ)) {
                    float[] fp = {1,0,1,  1,0,0,  1,1,0,  1,0,1};
                    float[] fn = {1,0,0,  1,0,0,  1,0,0,  1,0,0};
                    float fbt = getFaceBlockType(blockType, def, 2, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 2, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 2, atlas), 2, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                // Slope (UP = 4)
                if (isRampFaceVisible(neighborhood, worldX, worldY + 1, worldZ)) {
                    float[] fp = {0,0,1,  1,0,1,  1,1,0,  0,1,0};
                    float[] fn = {0, 0.7071f, 0.7071f,  0, 0.7071f, 0.7071f,  0, 0.7071f, 0.7071f,  0, 0.7071f, 0.7071f};
                    float fbt = getFaceBlockType(blockType, def, 4, block, atlas);
                    float overlay = getFaceOverlayLayer(def, 4, block, atlas);
                    currentTarget.addFace(fp, fn, fbt, BlockTextureMapper.uvFor(block, 4, atlas), 4, x, y, z, 0, overlay, def.isSway(), neighborhood, worldX, worldY, worldZ);
                }
                break;
            }
        }
    }
}
