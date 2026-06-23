package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.world.World;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.physics.AABB;
import com.za.zenith.world.physics.VoxelShape;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.world.blocks.BlockTextureMapper;
import com.za.zenith.utils.Direction;

/**
 * Handles geometry generation for blocks and single voxel models.
 * Delegates full chunk mesh building to {@link ChunkMeshBuilder}.
 */
public class ChunkMeshGenerator {
    public static final float[][] FACE_NORMALS = new float[][]{
        {0,0,1, 0,0,1, 0,0,1, 0,0,1},
        {0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1},
        {1,0,0, 1,0,0, 1,0,0, 1,0,0},
        {-1,0,0, -1,0,0, -1,0,0, -1,0,0},
        {0,1,0, 0,1,0, 0,1,0, 0,1,0},
        {0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0}
    };

    public static ChunkMeshResult generateMesh(Chunk chunk, World world, DynamicTextureAtlas atlas) {
        return ChunkMeshBuilder.generateRawMesh(chunk, world, atlas).upload(null);
    }

    public static Mesh generateSingleBlockMesh(Block block, DynamicTextureAtlas atlas, World world, BlockPos pos) {
        MeshData data = new MeshData(512);
        BlockDefinition def = BlockRegistry.getBlock(block.getType());
        if (def == null) return null;
        
        boolean isTranslucent = def.is(BlockDefinition.FLAG_TRANSLUCENT);
        float finalBlockType = (float)block.getType();
        if (def.isTinted()) {
            finalBlockType = -(finalBlockType + 1.0f);
        }

        int wx = (pos != null) ? pos.x() : 0;
        int wy = (pos != null) ? pos.y() : 0;
        int wz = (pos != null) ? pos.z() : 0;

        ChunkNeighborhood neighborhood = null;
        if (world != null && pos != null) {
            neighborhood = new ChunkNeighborhood(world, pos.x() >> 4, pos.z() >> 4, pos);
        }

        if (def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.CROSS_PLANE || def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.DOUBLE_PLANT) {
            float[] uvs = BlockTextureMapper.uvFor(block, 0, atlas);
            float overlayLayer = uvs[2];
            float weightOffset = (def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.DOUBLE_PLANT && block.getMetadata() == 1) ? 1.0f : 0.0f;
            addCrossPlane(data, -0.5f, 0, -0.5f, 0, 0, 1, 1, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, wx, wy, wz);
            addCrossPlane(data, -0.5f, 0, -0.5f, 0, 1, 1, 0, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, wx, wy, wz);
            return data.build();
        }

        VoxelShape shape = block.getShape();
        if (shape == null) return null;
        if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
            ChunkMeshRampHelper.generateRampGeometry(data, block, def, -0.5f, 0.0f, -0.5f, wx, wy, wz, neighborhood, atlas);
            return data.build();
        }
        for (AABB box : shape.getBoxes()) {
            for (int face = 0; face < 6; face++) {
                float faceBlockType = (float)block.getType();
                if (def.isFluid()) {
                    faceBlockType = -(faceBlockType + 3000.0f + (def.getFluidIndex() - 1) * 1000.0f);
                } else if (isTranslucent) {
                    faceBlockType = -(faceBlockType + 2000.0f);
                } else if (def.isFaceTinted(face)) {
                    faceBlockType = -(faceBlockType + 1.0f);
                }
                float overlayLayer = -1.0f;
                if (def.isTinted()) {
                    if (def.getTextures() != null) {
                        String innerKey = def.getTextures().getInner();
                        String sideKey = def.getTextures().getTextureForFace(face);
                        if (face < 4 && innerKey != null && !innerKey.equals(sideKey)) {
                            float[] innerUv = atlas.uvFor(innerKey);
                            if (innerUv != null) {
                                overlayLayer = innerUv[2];
                            }
                        }
                    }
                }
                float[] fp = data.getFaceVertices(face, box, 0, 0, 0, 0, false);
                data.addFace(fp, FACE_NORMALS[face], faceBlockType, BlockTextureMapper.uvFor(block, face, atlas), face, -0.5f, 0, -0.5f, 0, overlayLayer, def.isSway(), neighborhood, wx, wy, wz);
            }
        }
        return data.build();
    }

    public static Mesh generateCustomAABBMesh(Block block, AABB box, DynamicTextureAtlas atlas) {
        MeshData data = new MeshData(512);
        BlockDefinition def = BlockRegistry.getBlock(block.getType());
        boolean canSway = def != null && def.isSway();
        for (int face = 0; face < 6; face++) {
            float[] fp = data.getFaceVertices(face, box, 0, 0, 0, 0, false);
            data.addFace(fp, FACE_NORMALS[face], (float) block.getType(), BlockTextureMapper.uvFor(block, face, atlas), face, 0, 0, 0, 0, -1.0f, canSway, null, 0, 0, 0);
        }
        return data.build();
    }

    public static Mesh generateHoleMesh(BlockPos pos, World world, DynamicTextureAtlas atlas) {
        MeshData data = new MeshData(512);
        int[] oppositeFaces = {1, 0, 3, 2, 5, 4}; 
        ChunkNeighborhood neighborhood = new ChunkNeighborhood(world, pos.x() >> 4, pos.z() >> 4, pos);

        for (int face = 0; face < 6; face++) {
            Direction dir = Direction.values()[face];
            BlockPos nPos = new BlockPos(pos.x() + dir.getDx(), pos.y() + dir.getDy(), pos.z() + dir.getDz());
            Block nBlock = world.getBlock(nPos);
            BlockDefinition nDef = BlockRegistry.getBlock(nBlock.getType());

            if (nBlock.getType() != 0 && nDef != null && nDef.getPlacementType() == com.za.zenith.world.blocks.PlacementType.DEFAULT && nDef.is(BlockDefinition.FLAG_SOLID) && !nDef.is(BlockDefinition.FLAG_TRANSPARENT)) {
                int oppFace = oppositeFaces[face];
                VoxelShape shape = nBlock.getShape();
                if (shape == null) continue;
                for (AABB box : shape.getBoxes()) {
                    float faceBlockType = (float)nBlock.getType();
                    float overlayLayer = -1.0f;
                    if (nDef.isFaceTinted(oppFace)) {
                        faceBlockType = -(faceBlockType + 1.0f);
                        if (nDef.getTextures() != null) {
                            String innerKey = nDef.getTextures().getInner();
                            String sideKey = nDef.getTextures().getTextureForFace(oppFace);
                            if (oppFace < 4 && innerKey != null && !innerKey.equals(sideKey)) {
                                float[] innerUv = atlas.uvFor(innerKey);
                                if (innerUv != null) {
                                    overlayLayer = innerUv[2];
                                }
                            }
                        }
                    }

                    float ox = dir.getDx(); float oy = dir.getDy(); float oz = dir.getDz();
                    float[] fp = data.getFaceVertices(oppFace, box, 0, 0, 0, 0, false);
                    data.addFace(fp, FACE_NORMALS[oppFace], faceBlockType, BlockTextureMapper.uvFor(nBlock, oppFace, atlas), oppFace, ox, oy, oz, 0, overlayLayer, nDef.isSway(), neighborhood, nPos.x(), nPos.y(), nPos.z());
                }
            }
        }

        if (data.interleavedData.isEmpty()) return null;
        return data.build();
    }

    public static void addCrossPlane(MeshData data, float ox, float oy, float oz, float x0, float z0, float x1, float z1, float[] uvs, float blockTypeId, float overlayLayer, float weightOffset, ChunkNeighborhood neighborhood, int wx, int wy, int wz) {
        float l = uvs[2]; float[] light = {15f, 0f}; float ao = 1.0f;
        if (neighborhood != null) {
            light[0] = neighborhood.getSunlight(wx, wy, wz);
            light[1] = neighborhood.getBlockLight(wx, wy, wz);
        }

        data.addRawQuad(
            new float[]{ox+x0, oy, oz+z0,  ox+x1, oy, oz+z1,  ox+x1, oy+1.0f, oz+z1,  ox+x0, oy+1.0f, oz+z0},
            new float[]{uvs[0], uvs[1], l, uvs[3], uvs[4], l, uvs[3], uvs[7], l, uvs[0], uvs[10], l},
            new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0},
            blockTypeId, overlayLayer, true, weightOffset, light, ao
        );
        data.addRawQuad(
            new float[]{ox+x0, oy+1.0f, oz+z0,  ox+x1, oy+1.0f, oz+z1,  ox+x1, oy, oz+z1,  ox+x0, oy, oz+z0},
            new float[]{uvs[0], uvs[10], l, uvs[3], uvs[7], l, uvs[3], uvs[4], l, uvs[0], uvs[1], l},
            new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0},
            blockTypeId, overlayLayer, true, weightOffset, light, ao
        );
    }

    public static void addCrossPlane(MeshData data, float ox, float oy, float oz, float x0, float z0, float x1, float z1, float[] uvs, float blockTypeId, float overlayLayer, float weightOffset) {
        addCrossPlane(data, ox, oy, oz, x0, z0, x1, z1, uvs, blockTypeId, overlayLayer, weightOffset, null, 0, 0, 0);
    }
}
