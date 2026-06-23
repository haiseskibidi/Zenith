package com.za.zenith.world.chunks;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.BlockTextureMapper;
import com.za.zenith.world.physics.AABB;
import com.za.zenith.world.physics.VoxelShape;
import com.za.zenith.engine.graphics.DynamicTextureAtlas;
import com.za.zenith.utils.Direction;

/**
 * Builds the raw mesh data for chunk sections, handling greedy voxel extraction and visibility culling.
 */
public class ChunkMeshBuilder {
    private static final ThreadLocal<MeshData> threadOpaque = ThreadLocal.withInitial(() -> new MeshData(131072));
    private static final ThreadLocal<MeshData> threadTranslucent = ThreadLocal.withInitial(() -> new MeshData(32768));
    private static final ThreadLocal<MeshData> threadWater = ThreadLocal.withInitial(() -> new MeshData(32768));

    public static RawChunkMeshResult generateRawMesh(Chunk chunk, World world, DynamicTextureAtlas atlas) {
        MeshData chunkOpaque = threadOpaque.get();
        MeshData chunkTranslucent = threadTranslucent.get();
        MeshData chunkWater = threadWater.get();
        
        RawMeshData[] opaqueResults = new RawMeshData[Chunk.NUM_SECTIONS];
        RawMeshData[] translucentResults = new RawMeshData[Chunk.NUM_SECTIONS];
        RawMeshData[] waterResults = new RawMeshData[Chunk.NUM_SECTIONS];
        long[] visibilityMasks = new long[Chunk.NUM_SECTIONS];

        ChunkNeighborhood neighborhood = new ChunkNeighborhood(world, chunk.getPosition().x(), chunk.getPosition().z());
        long version = chunk.getDirtyCounter();

        int cx = chunk.getPosition().x();
        int cz = chunk.getPosition().z();

        int[][][] faceNeighbors = new int[][][]{
            {{-1,0,0}, {1,0,0}, {0,-1,0}, {0,1,0}},
            {{1,0,0}, {-1,0,0}, {0,-1,0}, {0,1,0}},
            {{0,0,1}, {0,0,-1}, {0,-1,0}, {0,1,0}},
            {{0,0,-1}, {0,0,1}, {0,-1,0}, {0,1,0}},
            {{-1,0,0}, {1,0,0}, {0,0,1}, {0,0,-1}},
            {{-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}}
        };

        for (int secIdx = 0; secIdx < Chunk.NUM_SECTIONS; secIdx++) {
            ChunkSection section = chunk.getSections()[secIdx];
            if (section == null || section.isEmpty()) {
                visibilityMasks[secIdx] = -1L;
                continue;
            }

            section.calculateVisibility(chunk, secIdx);
            visibilityMasks[secIdx] = section.getVisibilityMask();

            chunkOpaque.clear();
            chunkTranslucent.clear();
            chunkWater.clear();

            int startY = secIdx * ChunkSection.SECTION_SIZE;
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int ly = 0; ly < ChunkSection.SECTION_SIZE; ly++) {
                    int y = startY + ly;
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        int rawData = chunk.getRawBlockData(x, y, z);
                        int blockType = rawData >> 8;
                        if (blockType == 0) continue;

                        BlockDefinition def = BlockRegistry.getBlock(blockType);
                        if (def == null) continue;

                        Block block = chunk.getBlock(x, y, z);
                        float finalBlockType = (float)blockType;
                        if (def.is(BlockDefinition.FLAG_TINTED)) finalBlockType = -(finalBlockType + 1.0f);

                        if (def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.CROSS_PLANE || def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.DOUBLE_PLANT) {
                            float[] uvs = BlockTextureMapper.uvFor(block, 0, atlas);
                            float overlayLayer = uvs[2]; 
                            float weightOffset = (def.getPlacementType() == com.za.zenith.world.blocks.PlacementType.DOUBLE_PLANT && block.getMetadata() == 1) ? 1.0f : 0.0f;
                            ChunkMeshGenerator.addCrossPlane(chunkOpaque, (float)x, (float)y, (float)z, 0, 0, 1, 1, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, cx * Chunk.CHUNK_SIZE + x, y, cz * Chunk.CHUNK_SIZE + z);
                            ChunkMeshGenerator.addCrossPlane(chunkOpaque, (float)x, (float)y, (float)z, 0, 1, 1, 0, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, cx * Chunk.CHUNK_SIZE + x, y, cz * Chunk.CHUNK_SIZE + z);
                            continue;
                        }

                        VoxelShape shape = block.getShape();
                        if (shape == null) continue;

                        boolean isWater = def.isFluid();
                        boolean isLeaves = def.is(BlockDefinition.FLAG_LEAVES);
                        boolean isTranslucent = def.is(BlockDefinition.FLAG_TRANSLUCENT);
                        MeshData currentTarget = isWater ? chunkWater : (isTranslucent ? chunkTranslucent : chunkOpaque);

                        int worldX = cx * Chunk.CHUNK_SIZE + x;
                        int worldY = y;
                        int worldZ = cz * Chunk.CHUNK_SIZE + z;

                        if (shape.getGeometry() == VoxelShape.ShapeGeometry.RAMP) {
                            ChunkMeshRampHelper.generateRampGeometry(currentTarget, block, def, x, y, z, worldX, worldY, worldZ, neighborhood, atlas);
                            continue;
                        }

                        for (AABB box : shape.getBoxes()) {
                            float h0 = 0.0f, h1 = 0.0f, h2 = 0.0f, h3 = 0.0f;
                            if (isWater) {
                                h0 = ChunkMeshWaterHelper.getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 0, 1, blockType);
                                h1 = ChunkMeshWaterHelper.getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 1, 1, blockType);
                                h2 = ChunkMeshWaterHelper.getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 1, 0, blockType);
                                h3 = ChunkMeshWaterHelper.getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 0, 0, blockType);
                            }
                            
                            for (int face = 0; face < 6; face++) {
                                Direction dir = Direction.values()[face];
                                int nx = worldX + dir.getDx();
                                int ny = worldY + dir.getDy();
                                int nz = worldZ + dir.getDz();
                                
                                int nRaw = neighborhood.getRawBlockData(nx, ny, nz);
                                int nType = nRaw >> 8;
                                
                                boolean drawFace = true;
                                BlockDefinition neighborDef = BlockRegistry.getBlock(nType);

                                boolean onBoundary = false;
                                switch (face) {
                                    case 0: onBoundary = (box.maxZ() == 1.0f); break; 
                                    case 1: onBoundary = (box.minZ() == 0.0f); break; 
                                    case 2: onBoundary = (box.maxX() == 1.0f); break; 
                                    case 3: onBoundary = (box.minX() == 0.0f); break; 
                                    case 4: onBoundary = (box.maxY() == 1.0f); break; 
                                    case 5: onBoundary = (box.minY() == 0.0f); break; 
                                }

                                if (def.isAlwaysRender() || !onBoundary) {
                                    drawFace = true;
                                } else if (nType == 0) {
                                    drawFace = true;
                                } else if ((isTranslucent || isWater) && nType == blockType) {
                                    drawFace = false;
                                } else if (neighborDef != null && neighborDef.is(BlockDefinition.FLAG_LEAVES)) {
                                    drawFace = !isLeaves || (nType != blockType);
                                    if (isLeaves && neighborDef.is(BlockDefinition.FLAG_LEAVES)) drawFace = true;
                                } else if (neighborDef != null && neighborDef.hasTag("treecapitator")) {
                                    if (face >= 4) {
                                        drawFace = true;
                                    } else {
                                        drawFace = (nType != blockType);
                                    }
                                } else if (neighborDef == null) {
                                    drawFace = true;
                                } else if (!neighborDef.is(BlockDefinition.FLAG_TRANSPARENT) && !neighborDef.isAlwaysRender()) {
                                    drawFace = false;
                                } else {
                                    drawFace = true;
                                }

                                if (drawFace) {
                                    float neighborMask = 0;
                                    if (isWater) {
                                        neighborMask = ChunkMeshWaterHelper.getWaterFlowDirection(neighborhood, worldX, worldY, worldZ, blockType);
                                    } else if (isTranslucent) {
                                        for (int i = 0; i < 4; i++) {
                                            int rawN = neighborhood.getRawBlockData(worldX + faceNeighbors[face][i][0], worldY + faceNeighbors[face][i][1], worldZ + faceNeighbors[face][i][2]);
                                            BlockDefinition nDef = BlockRegistry.getBlock(rawN >> 8);
                                            if (nDef != null && nDef.is(BlockDefinition.FLAG_TRANSPARENT)) {
                                                neighborMask += (float)Math.pow(2, i);
                                            }
                                        }
                                    }
                                    
                                    float faceBlockType = (float)blockType;
                                    float overlayLayer = -1.0f;
                                    if (isWater) {
                                        faceBlockType = -(faceBlockType + 3000.0f + (def.getFluidIndex() - 1) * 1000.0f);
                                    } else if (isTranslucent) {
                                        faceBlockType = -(faceBlockType + 2000.0f);
                                    } else if (def != null && def.isFaceTinted(face)) {
                                        faceBlockType = -(faceBlockType + 1.0f);

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
                                    float[] fp = currentTarget.getFaceVertices(face, box, h0, h1, h2, h3, isWater);
                                    currentTarget.addFace(fp, ChunkMeshGenerator.FACE_NORMALS[face], faceBlockType, BlockTextureMapper.uvFor(block, face, atlas), face, (float)x, (float)y, (float)z, neighborMask, overlayLayer, def.isSway(), neighborhood, worldX, worldY, worldZ);
                                }
                            }
                        }
                    }
                }
            }
            opaqueResults[secIdx] = chunkOpaque.buildRaw();
            translucentResults[secIdx] = chunkTranslucent.buildRaw();
            waterResults[secIdx] = chunkWater.buildRaw();
        }

        return new RawChunkMeshResult(opaqueResults, translucentResults, waterResults, version, chunk.getFirstSpawnTime(), visibilityMasks);
    }
}
