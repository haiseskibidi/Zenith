package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshPool;
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
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class ChunkMeshGenerator {
    private static final float[][] FACE_NORMALS = new float[][]{
        {0,0,1, 0,0,1, 0,0,1, 0,0,1},
        {0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1},
        {1,0,0, 1,0,0, 1,0,0, 1,0,0},
        {-1,0,0, -1,0,0, -1,0,0, -1,0,0},
        {0,1,0, 0,1,0, 0,1,0, 0,1,0},
        {0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0}
    };
    private static final int[] FACE_INDICES = {0,1,2, 2,3,0};
    
    public record ChunkMeshResult(long version, Mesh[] opaqueSections, Mesh[] translucentSections, Mesh[] waterSections, float spawnTime) {
        public void cleanup() {
            if (opaqueSections != null) for (Mesh m : opaqueSections) if (m != null) m.cleanup();
            if (translucentSections != null) for (Mesh m : translucentSections) if (m != null) m.cleanup();
            if (waterSections != null) for (Mesh m : waterSections) if (m != null) m.cleanup();
        }
    }

    public record RawMeshData(java.nio.FloatBuffer dataBuffer, int dataLen, java.nio.IntBuffer indicesBuffer, int idxLen, org.joml.Vector3f min, org.joml.Vector3f max) {
        public Mesh createMesh(MeshPool pool) {
            if (pool != null) {
                return new Mesh(pool, dataBuffer, indicesBuffer, min, max);
            }
            return new Mesh(dataBuffer, dataLen, indicesBuffer, idxLen, min, max, Mesh.VertexFormat.COMPRESSED_CHUNK);
        }

        public void cleanup() {
            com.za.zenith.utils.NioBufferPool.returnFloat(dataBuffer);
            com.za.zenith.utils.NioBufferPool.returnInt(indicesBuffer);
        }
    }

    public record RawChunkMeshResult(RawMeshData[] opaque, RawMeshData[] translucent, RawMeshData[] water, long version, float firstSpawnTime, long[] visibilityMasks) {
        public ChunkMeshResult upload(MeshPool pool) {
            Mesh[] opaqueMeshes = new Mesh[Chunk.NUM_SECTIONS];
            Mesh[] translucentMeshes = new Mesh[Chunk.NUM_SECTIONS];
            Mesh[] waterMeshes = new Mesh[Chunk.NUM_SECTIONS];
            for (int i = 0; i < Chunk.NUM_SECTIONS; i++) {
                if (opaque != null && opaque[i] != null) opaqueMeshes[i] = opaque[i].createMesh(pool);
                if (translucent != null && translucent[i] != null) translucentMeshes[i] = translucent[i].createMesh(pool);
                if (water != null && water[i] != null) waterMeshes[i] = water[i].createMesh(pool);
            }
            return new ChunkMeshResult(version, opaqueMeshes, translucentMeshes, waterMeshes, firstSpawnTime);
        }

        public void cleanup() {
            if (opaque != null) for (RawMeshData r : opaque) if (r != null) r.cleanup();
            if (translucent != null) for (RawMeshData r : translucent) if (r != null) r.cleanup();
            if (water != null) for (RawMeshData r : water) if (r != null) r.cleanup();
        }
    }

    public static class ChunkNeighborhood {
        private final Chunk[][] neighborhood = new Chunk[3][3];
        private final int centerChunkX;
        private final int centerChunkZ;
        private final BlockPos breakingPos;

        public ChunkNeighborhood(World world, int cx, int cz) {
            this(world, cx, cz, null);
        }

        public ChunkNeighborhood(World world, int cx, int cz, BlockPos breakingPos) {
            this.breakingPos = breakingPos;
            this.centerChunkX = cx;
            this.centerChunkZ = cz;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    neighborhood[dx + 1][dz + 1] = world.getChunkInternal(cx + dx, cz + dz);
                }
            }
        }

        public boolean isBreaking(int x, int y, int z) {
            return breakingPos != null && breakingPos.x() == x && breakingPos.y() == y && breakingPos.z() == z;
        }

        public Chunk getChunk(int worldX, int worldZ) {
            int dx = (worldX >> 4) - centerChunkX + 1;
            int dz = (worldZ >> 4) - centerChunkZ + 1;
            if (dx < 0 || dx > 2 || dz < 0 || dz > 2) return null;
            return neighborhood[dx][dz];
        }

        public int getRawBlockData(int x, int y, int z) {
            if (isBreaking(x, y, z)) return 0;
            if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
            Chunk c = getChunk(x, z);
            if (c == null) return 0;
            return c.getRawBlockData(x & 15, y, z & 15);
        }

        public int getSunlight(int x, int y, int z) {
            if (y >= Chunk.CHUNK_HEIGHT) return 15;
            if (y < 0) return 0;
            if (breakingPos != null && x == breakingPos.x() && y == breakingPos.y() && z == breakingPos.z()) {
                int maxSun = 0;
                maxSun = Math.max(maxSun, getSunlightRaw(x + 1, y, z));
                maxSun = Math.max(maxSun, getSunlightRaw(x - 1, y, z));
                maxSun = Math.max(maxSun, getSunlightRaw(x, y + 1, z));
                maxSun = Math.max(maxSun, getSunlightRaw(x, y - 1, z));
                maxSun = Math.max(maxSun, getSunlightRaw(x, y, z + 1));
                maxSun = Math.max(maxSun, getSunlightRaw(x, y, z - 1));
                return maxSun;
            }
            return getSunlightRaw(x, y, z);
        }

        private int getSunlightRaw(int x, int y, int z) {
            if (y >= Chunk.CHUNK_HEIGHT) return 15;
            if (y < 0) return 0;
            Chunk c = getChunk(x, z);
            if (c == null || !c.isReady()) return (y >= 128) ? 15 : 0;
            return c.getSunlight(x & 15, y, z & 15);
        }

        public int getBlockLight(int x, int y, int z) {
            if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
            if (breakingPos != null && x == breakingPos.x() && y == breakingPos.y() && z == breakingPos.z()) {
                int maxBlock = 0;
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x + 1, y, z));
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x - 1, y, z));
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y + 1, z));
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y - 1, z));
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y, z + 1));
                maxBlock = Math.max(maxBlock, getBlockLightRaw(x, y, z - 1));
                return maxBlock;
            }
            return getBlockLightRaw(x, y, z);
        }

        private int getBlockLightRaw(int x, int y, int z) {
            if (y < 0 || y >= Chunk.CHUNK_HEIGHT) return 0;
            Chunk c = getChunk(x, z);
            if (c == null || !c.isReady()) return 0;
            return c.getBlockLight(x & 15, y, z & 15);
        }
    }

    public static class MeshData {
        com.za.zenith.utils.FloatArrayList interleavedData;
        com.za.zenith.utils.IntArrayList indices;
        int vertexIndex = 0;
        private final float[] tempLightBuf = new float[2];
        private final float[] tempFaceVertices = new float[12];

        public float[] getFaceVertices(int face, AABB box, float h0, float h1, float h2, float h3, boolean isWater) {
            float minX = box.minX();
            float minY = box.minY();
            float minZ = box.minZ();
            float maxX = box.maxX();
            float maxY = box.maxY();
            float maxZ = box.maxZ();
            if (isWater) {
                switch (face) {
                    case 0:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = minY + h1; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = minY + h0; tempFaceVertices[11] = maxZ;
                        break;
                    case 1:
                        tempFaceVertices[0] = maxX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = minX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = minX; tempFaceVertices[7] = minY + h3; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = maxX; tempFaceVertices[10] = minY + h2; tempFaceVertices[11] = minZ;
                        break;
                    case 2:
                        tempFaceVertices[0] = maxX; tempFaceVertices[1] = minY; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = minY + h2; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = maxX; tempFaceVertices[10] = minY + h1; tempFaceVertices[11] = maxZ;
                        break;
                    case 3:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = minX; tempFaceVertices[4] = minY; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = minX; tempFaceVertices[7] = minY + h0; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = minY + h3; tempFaceVertices[11] = minZ;
                        break;
                    case 4:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY + h0; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY + h1; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = minY + h2; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = minY + h3; tempFaceVertices[11] = minZ;
                        break;
                    case 5:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = minY; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = minY; tempFaceVertices[11] = maxZ;
                        break;
                }
            } else {
                switch (face) {
                    case 0:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = maxY; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = maxY; tempFaceVertices[11] = maxZ;
                        break;
                    case 1:
                        tempFaceVertices[0] = maxX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = minX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = minX; tempFaceVertices[7] = maxY; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = maxX; tempFaceVertices[10] = maxY; tempFaceVertices[11] = minZ;
                        break;
                    case 2:
                        tempFaceVertices[0] = maxX; tempFaceVertices[1] = minY; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = maxY; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = maxX; tempFaceVertices[10] = maxY; tempFaceVertices[11] = maxZ;
                        break;
                    case 3:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = minX; tempFaceVertices[4] = minY; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = minX; tempFaceVertices[7] = maxY; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = maxY; tempFaceVertices[11] = minZ;
                        break;
                    case 4:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = maxY; tempFaceVertices[2] = maxZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = maxY; tempFaceVertices[5] = maxZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = maxY; tempFaceVertices[8] = minZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = maxY; tempFaceVertices[11] = minZ;
                        break;
                    case 5:
                        tempFaceVertices[0] = minX; tempFaceVertices[1] = minY; tempFaceVertices[2] = minZ;
                        tempFaceVertices[3] = maxX; tempFaceVertices[4] = minY; tempFaceVertices[5] = minZ;
                        tempFaceVertices[6] = maxX; tempFaceVertices[7] = minY; tempFaceVertices[8] = maxZ;
                        tempFaceVertices[9] = minX; tempFaceVertices[10] = minY; tempFaceVertices[11] = maxZ;
                        break;
                }
            }
            return tempFaceVertices;
        }

        MeshData(int initialCapacity) {
            interleavedData = new com.za.zenith.utils.FloatArrayList(initialCapacity);
            indices = new com.za.zenith.utils.IntArrayList(initialCapacity / 2);
        }

        void clear() {
            // Memory Guard: Shrink buffers if they grew too large (e.g. after a very complex chunk)
            // 128k elements * 4 bytes = 512KB per buffer. 
            // Total ~1MB per thread, which is fine.
            interleavedData.clear(128 * 1024);
            indices.clear(128 * 1024);
            vertexIndex = 0;
        }

        public void addFace(float[] fp, float[] fn, float blockTypeId, float[] fullUv, int face, float ox, float oy, float oz, float neighborMask, float overlayLayer, boolean canSway, ChunkNeighborhood neighborhood, int wx, int wy, int wz) {
            float minY = fp[1], maxY = fp[1];
            for (int v = 1; v < 4; v++) {
                minY = Math.min(minY, fp[v*3+1]);
                maxY = Math.max(maxY, fp[v*3+1]);
            }

            int packedPos = 0;
            if (ox >= 0.0f && oy >= 0.0f && oz >= 0.0f) {
                packedPos = (int)ox + ((int)oz) * 16 + ((int)oy) * 256;
            }

            for (int v = 0; v < 4; v++) {
                float px = fp[v*3] + ox;
                float py = fp[v*3+1] + oy;
                float pz = fp[v*3+2] + oz;
                
                float vx = fp[v*3];
                float vy = fp[v*3+1];
                float vz = fp[v*3+2];

                float lu = 0, lv = 0;
                switch (face) {
                    case 0: lu = vx; lv = vy; break;
                    case 1: lu = 1.0f - vx; lv = vy; break;
                    case 2: lu = 1.0f - vz; lv = vy; break;
                    case 3: lu = vz; lv = vy; break;
                    case 4: lu = vx; lv = 1.0f - vz; break;
                    case 5: lu = vx; lv = vz; break;
                }
                float topU = fullUv[0] * (1 - lu) + fullUv[3] * lu;
                float topV = fullUv[1] * (1 - lu) + fullUv[4] * lu;
                float botU = fullUv[9] * (1 - lu) + fullUv[6] * lu;
                float botV = fullUv[10] * (1 - lu) + fullUv[7] * lu;
                float finalU = topU * (1 - lv) + botU * lv;
                float finalV = topV * (1 - lv) + botV * lv;

                float weight = 0.0f;
                if (canSway) {
                    if (maxY > minY) {
                        weight = (vy > minY + 0.001f) ? 1.0f : 0.0f;
                    } else {
                        weight = (face == 4) ? 1.0f : 0.0f;
                    }
                }

                float ao = calculateAO(neighborhood, wx, wy, wz, face, vx, vy, vz);
                calculateSmoothLight(neighborhood, wx, wy, wz, face, vx, vy, vz, tempLightBuf);

                int u16 = (int)(finalU * 65535.0f) & 0xFFFF;
                int v16 = (int)(finalV * 65535.0f) & 0xFFFF;
                int packedTex = u16 | (v16 << 16);

                int texLayer = ((int)fullUv[2]) & 0xFFF;
                int overLayer = (((int)overlayLayer) + 1) & 0xFFF;
                int packedLayers = texLayer | (overLayer << 12) | ((face & 0x7) << 24);

                int bType = ((int)blockTypeId) & 0xFFFF;
                int nMask = ((int)neighborMask) & 0x3F;
                int wt = weight > 0.5f ? 1 : 0;
                
                // Pack wetness factor (0.0 - 1.0) into 4 bits (0-15)
                float wetness = 0.5f; // Default
                if (bType != 0) {
                    int realType = bType;
                    if (realType >= 2000) realType -= 2000;
                    else if (blockTypeId < 0.0f) realType = (int) (Math.abs(blockTypeId) - 1.0f);
                    
                    BlockDefinition bDef = BlockRegistry.getBlock(realType);
                    if (bDef != null) wetness = bDef.getWetnessFactor();
                }
                int wFactor = (int)(wetness * 15.0f) & 0xF;
                
                int packedBlock = bType | (nMask << 16) | (wt << 22) | (wFactor << 23);

                int l0 = ((int)tempLightBuf[0]) & 0xF;
                int l1 = ((int)tempLightBuf[1]) & 0xF;
                int aoi = ao > 0.8f ? 3 : (ao > 0.6f ? 2 : (ao > 0.4f ? 1 : 0));
                int pPos = packedPos & 0xFFFF;
                int packedLight = l0 | (l1 << 4) | (aoi << 8) | (pPos << 10);

                interleavedData.add(px);
                interleavedData.add(py);
                interleavedData.add(pz);
                interleavedData.add(Float.intBitsToFloat(packedTex));
                interleavedData.add(Float.intBitsToFloat(packedLayers));
                interleavedData.add(Float.intBitsToFloat(packedBlock));
                interleavedData.add(Float.intBitsToFloat(packedLight));
            }
            
            for (int idx : FACE_INDICES) indices.add(vertexIndex + idx);
            vertexIndex += 4;
        }

        private float calculateAO(ChunkNeighborhood neighborhood, int x, int y, int z, int face, float vx, float vy, float vz) {
            if (neighborhood == null) return 1.0f;
            
            int nx = (vx > 0.0f) ? 1 : -1;
            int ny = (vy > 0.0f) ? 1 : -1;
            int nz = (vz > 0.0f) ? 1 : -1;

            int side1, side2, corner;
            
            switch (face) {
                case 0: // North (+Z)
                case 1: // South (-Z)
                    int fz = z + (face == 0 ? 1 : -1);
                    side1 = isSolid(neighborhood, x + nx, y, fz) ? 1 : 0;
                    side2 = isSolid(neighborhood, x, y + ny, fz) ? 1 : 0;
                    corner = isSolid(neighborhood, x + nx, y + ny, fz) ? 1 : 0;
                    break;
                case 2: // East (+X)
                case 3: // West (-X)
                    int fx = x + (face == 2 ? 1 : -1);
                    side1 = isSolid(neighborhood, fx, y + ny, z) ? 1 : 0;
                    side2 = isSolid(neighborhood, fx, y, z + nz) ? 1 : 0;
                    corner = isSolid(neighborhood, fx, y + ny, z + nz) ? 1 : 0;
                    break;
                case 4: // Up (+Y)
                case 5: // Down (-Y)
                    int fy = y + (face == 4 ? 1 : -1);
                    side1 = isSolid(neighborhood, x + nx, fy, z) ? 1 : 0;
                    side2 = isSolid(neighborhood, x, fy, z + nz) ? 1 : 0;
                    corner = isSolid(neighborhood, x + nx, fy, z + nz) ? 1 : 0;
                    break;
                default: return 1.0f;
            }

            if (side1 == 1 && side2 == 1) return 0.3f;
            return 1.0f - (side1 + side2 + corner) * 0.2f;
        }

        private boolean isSolid(ChunkNeighborhood neighborhood, int x, int y, int z) {
            if (neighborhood == null) return false;
            int rawData = neighborhood.getRawBlockData(x, y, z);
            int type = rawData >> 8;
            if (type == 0) return false;
            BlockDefinition def = BlockRegistry.getBlock(type);
            if (def == null) return false;
            if (def.is(BlockDefinition.FLAG_LEAVES)) return false;
            return def.is(BlockDefinition.FLAG_SOLID) && !def.is(BlockDefinition.FLAG_TRANSPARENT);
        }

        private void calculateSmoothLight(ChunkNeighborhood neighborhood, int x, int y, int z, int face, float vx, float vy, float vz, float[] out) {
            if (neighborhood == null) {
                out[0] = 15f; out[1] = 0f;
                return;
            }
            
            Direction dir = Direction.values()[face];
            int fx = x + dir.getDx();
            int fy = y + dir.getDy();
            int fz = z + dir.getDz();

            int nx = (vx > 0.0f) ? 1 : -1;
            int ny = (vy > 0.0f) ? 1 : -1;
            int nz = (vz > 0.0f) ? 1 : -1;

            float totalSun = 0;
            float totalBlock = 0;

            float centralSun = neighborhood.getSunlight(fx, fy, fz);
            float centralBlock = neighborhood.getBlockLight(fx, fy, fz);
            
            for (int i = 0; i < 4; i++) {
                int sx = fx, sy = fy, sz = fz;
                if (face < 2) { // Z face
                    if (i == 1 || i == 3) sx += nx;
                    if (i == 2 || i == 3) sy += ny;
                } else if (face < 4) { // X face
                    if (i == 1 || i == 3) sy += ny;
                    if (i == 2 || i == 3) sz += nz;
                } else { // Y face
                    if (i == 1 || i == 3) sx += nx;
                    if (i == 2 || i == 3) sz += nz;
                }
                
                totalSun += neighborhood.getSunlight(sx, sy, sz);
                totalBlock += neighborhood.getBlockLight(sx, sy, sz);
            }

            out[0] = Math.max(centralSun, totalSun * 0.25f);
            out[1] = Math.max(centralBlock, totalBlock * 0.25f);
        }

        public void addRawQuad(float[] fp, float[] uv, float[] fn, float blockTypeId, float overlayLayer, boolean canSway, float weightOffset, float[] light, float ao) {
            float minY = fp[1], maxY = fp[1];
            for (int v = 1; v < 4; v++) {
                minY = Math.min(minY, fp[v*3+1]);
                maxY = Math.max(maxY, fp[v*3+1]);
            }

            // CRITICAL: Compute packedPos ONCE from the quad center, not per-vertex.
            // Cross-plane vertices (e.g. grass at (x,y,z)) extend to (x+1,y+1,z+1),
            // so per-vertex floor() would assign edge vertices to the ADJACENT block's packedPos.
            // This caused partial vertex hiding (gray triangle to crosshair).
            float centerX = (fp[0] + fp[3] + fp[6] + fp[9]) * 0.25f;
            float centerY = (fp[1] + fp[4] + fp[7] + fp[10]) * 0.25f;
            float centerZ = (fp[2] + fp[5] + fp[8] + fp[11]) * 0.25f;
            int lx = ((int) Math.floor(centerX)) & 15;
            int lz = ((int) Math.floor(centerZ)) & 15;
            int ly = (int) Math.floor(centerY);
            if (ly < 0) ly = 0;
            if (ly > 255) ly = 255;
            int pPos = (lx + lz * 16 + ly * 256) & 0xFFFF;

            for (int v = 0; v < 4; v++) {
                float py = fp[v*3+1];
                float weight = 0.0f;
                if (canSway) {
                    weight = weightOffset + ((py > minY + 0.001f) ? 1.0f : 0.0f);
                }

                int u16 = (int)(uv[v*3] * 65535.0f) & 0xFFFF;
                int v16 = (int)(uv[v*3+1] * 65535.0f) & 0xFFFF;
                int packedTex = u16 | (v16 << 16);

                int texLayer = ((int)uv[v*3+2]) & 0xFFF;
                int overLayer = (((int)overlayLayer) + 1) & 0xFFF;
                int face = 4;
                if (fn[v*3+1] < -0.5f) face = 5;
                else if (fn[v*3] > 0.5f) face = 2;
                else if (fn[v*3] < -0.5f) face = 3;
                else if (fn[v*3+2] > 0.5f) face = 0;
                else if (fn[v*3+2] < -0.5f) face = 1;
                int packedLayers = texLayer | (overLayer << 12) | ((face & 0x7) << 24);

                int bType = ((int)blockTypeId) & 0xFFFF;
                int nMask = 0;
                int wt = weight > 0.5f ? 1 : 0;
                int packedBlock = bType | (nMask << 16) | (wt << 22);

                int l0 = ((int)light[0]) & 0xF;
                int l1 = ((int)light[1]) & 0xF;
                int aoi = ao > 0.8f ? 3 : (ao > 0.6f ? 2 : (ao > 0.4f ? 1 : 0));
                
                int packedLight = l0 | (l1 << 4) | (aoi << 8) | (pPos << 10);

                interleavedData.add(fp[v*3]);
                interleavedData.add(py);
                interleavedData.add(fp[v*3+2]);
                interleavedData.add(Float.intBitsToFloat(packedTex));
                interleavedData.add(Float.intBitsToFloat(packedLayers));
                interleavedData.add(Float.intBitsToFloat(packedBlock));
                interleavedData.add(Float.intBitsToFloat(packedLight));
            }
            for (int idx : FACE_INDICES) indices.add(vertexIndex + idx);
            vertexIndex += 4;
        }

        public void addRawQuad(float[] fp, float[] uv, float[] fn, float blockTypeId, float overlayLayer, boolean canSway, float weightOffset) {
            addRawQuad(fp, uv, fn, blockTypeId, overlayLayer, canSway, weightOffset, new float[]{15f, 0f}, 1.0f);
        }

        public RawMeshData buildRaw() {
            if (interleavedData.isEmpty()) return null;
            
            int dLen = interleavedData.size();
            java.nio.FloatBuffer fb = com.za.zenith.utils.NioBufferPool.rentFloat(dLen);
            float[] internal = interleavedData.getInternalArray();
            fb.put(internal, 0, dLen).flip();
            
            // Calculate AABB in background thread
            org.joml.Vector3f min = new org.joml.Vector3f(Float.MAX_VALUE);
            org.joml.Vector3f max = new org.joml.Vector3f(-Float.MAX_VALUE);
            for (int i = 0; i < dLen; i += 7) {
                float px = internal[i];
                float py = internal[i+1];
                float pz = internal[i+2];
                min.x = Math.min(min.x, px); min.y = Math.min(min.y, py); min.z = Math.min(min.z, pz);
                max.x = Math.max(max.x, px); max.y = Math.max(max.y, py); max.z = Math.max(max.z, pz);
            }

            int iLen = indices.size();
            java.nio.IntBuffer ib = com.za.zenith.utils.NioBufferPool.rentInt(iLen);
            ib.put(indices.getInternalArray(), 0, iLen).flip();
            
            return new RawMeshData(fb, dLen, ib, iLen, min, max); 
        }

        public Mesh build() {
            RawMeshData raw = buildRaw();
            if (raw == null) return null;
            Mesh mesh = raw.createMesh(null);
            raw.cleanup(); // CRITICAL: Return buffers to pool
            return mesh;
        }
    }

    private static final ThreadLocal<MeshData> threadOpaque = ThreadLocal.withInitial(() -> new MeshData(131072));
    private static final ThreadLocal<MeshData> threadTranslucent = ThreadLocal.withInitial(() -> new MeshData(32768));
    private static final ThreadLocal<MeshData> threadWater = ThreadLocal.withInitial(() -> new MeshData(32768));

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
            generateRampGeometry(data, block, def, -0.5f, 0.0f, -0.5f, wx, wy, wz, neighborhood, atlas);
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

                    float ox = dir.getDx();
                    float oy = dir.getDy();
                    float oz = dir.getDz();

                    float[] fp = data.getFaceVertices(oppFace, box, 0, 0, 0, 0, false);
                    data.addFace(fp, FACE_NORMALS[oppFace], faceBlockType, BlockTextureMapper.uvFor(nBlock, oppFace, atlas), oppFace, ox, oy, oz, 0, overlayLayer, nDef.isSway(), neighborhood, nPos.x(), nPos.y(), nPos.z());
                }
            }
        }

        if (data.interleavedData.isEmpty()) return null;
        return data.build();
    }

    public static ChunkMeshResult generateMesh(Chunk chunk, World world, DynamicTextureAtlas atlas) {
        return generateRawMesh(chunk, world, atlas).upload(null);
    }

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
            {{-1,0,0}, {1,0,0}, {0,-1,0}, {0,1,0}}, // Face 0
            {{1,0,0}, {-1,0,0}, {0,-1,0}, {0,1,0}}, // Face 1
            {{0,0,1}, {0,0,-1}, {0,-1,0}, {0,1,0}}, // Face 2
            {{0,0,-1}, {0,0,1}, {0,-1,0}, {0,1,0}}, // Face 3
            {{-1,0,0}, {1,0,0}, {0,0,1}, {0,0,-1}}, // Face 4
            {{-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}}  // Face 5
        };

        for (int secIdx = 0; secIdx < Chunk.NUM_SECTIONS; secIdx++) {
            ChunkSection section = chunk.getSections()[secIdx];
            if (section == null || section.isEmpty()) {
                visibilityMasks[secIdx] = -1L;
                continue;
            }

            // NEW: Calculate visibility mask for occlusion culling
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
                            addCrossPlane(chunkOpaque, (float)x, (float)y, (float)z, 0, 0, 1, 1, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, cx * Chunk.CHUNK_SIZE + x, y, cz * Chunk.CHUNK_SIZE + z);
                            addCrossPlane(chunkOpaque, (float)x, (float)y, (float)z, 0, 1, 1, 0, uvs, finalBlockType, overlayLayer, weightOffset, neighborhood, cx * Chunk.CHUNK_SIZE + x, y, cz * Chunk.CHUNK_SIZE + z);
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
                            generateRampGeometry(currentTarget, block, def, x, y, z, worldX, worldY, worldZ, neighborhood, atlas);
                            continue;
                        }

                        for (AABB box : shape.getBoxes()) {
                            float h0 = 0.0f, h1 = 0.0f, h2 = 0.0f, h3 = 0.0f;
                            if (isWater) {
                                h0 = getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 0, 1, blockType);
                                h1 = getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 1, 1, blockType);
                                h2 = getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 1, 0, blockType);
                                h3 = getCornerWaterHeight(neighborhood, worldX, worldY, worldZ, 0, 0, blockType);
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
                                        neighborMask = getWaterFlowDirection(neighborhood, worldX, worldY, worldZ, blockType);
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
                                    currentTarget.addFace(fp, FACE_NORMALS[face], faceBlockType, BlockTextureMapper.uvFor(block, face, atlas), face, (float)x, (float)y, (float)z, neighborMask, overlayLayer, def.isSway(), neighborhood, worldX, worldY, worldZ);
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

    private static void addCrossPlane(MeshData data, float ox, float oy, float oz, float x0, float z0, float x1, float z1, float[] uvs, float blockTypeId, float overlayLayer, float weightOffset, ChunkNeighborhood neighborhood, int wx, int wy, int wz) {
        float l = uvs[2];
        float[] light = {15f, 0f};
        float ao = 1.0f;
        
        if (neighborhood != null) {
            light[0] = neighborhood.getSunlight(wx, wy, wz);
            light[1] = neighborhood.getBlockLight(wx, wy, wz);
        }

        data.addRawQuad(
            new float[]{ox+x0, oy, oz+z0,  ox+x1, oy, oz+z1,  ox+x1, oy+1.0f, oz+z1,  ox+x0, oy+1.0f, oz+z0},
            new float[]{
                uvs[0], uvs[1], l,
                uvs[3], uvs[4], l,
                uvs[3], uvs[7], l,
                uvs[0], uvs[10], l
            },
            new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0},
            blockTypeId,
            overlayLayer,
            true,
            weightOffset,
            light,
            ao
        );
        data.addRawQuad(
            new float[]{ox+x0, oy+1.0f, oz+z0,  ox+x1, oy+1.0f, oz+z1,  ox+x1, oy, oz+z1,  ox+x0, oy, oz+z0},
            new float[]{
                uvs[0], uvs[10], l,
                uvs[3], uvs[7], l,
                uvs[3], uvs[4], l,
                uvs[0], uvs[1], l
            },
            new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0},
            blockTypeId,
            overlayLayer,
            true,
            weightOffset,
            light,
            ao
        );
    }

    private static void addCrossPlane(MeshData data, float ox, float oy, float oz, float x0, float z0, float x1, float z1, float[] uvs, float blockTypeId, float overlayLayer, float weightOffset) {
        addCrossPlane(data, ox, oy, oz, x0, z0, x1, z1, uvs, blockTypeId, overlayLayer, weightOffset, null, 0, 0, 0);
    }

    private static float getCornerWaterHeight(ChunkNeighborhood neighborhood, int wx, int wy, int wz, int dx, int dz, int waterId) {
        int xOffset = (dx == 0) ? -1 : 1;
        int zOffset = (dz == 0) ? -1 : 1;
        
        float sumHeight = 0;
        int count = 0;
        boolean hasSourceOrFalling = false;
        
        // 1. Сначала сканируем на наличие источника (0) или падающего столба (8)
        for (int ox = 0; ox <= 1; ox++) {
            for (int oz = 0; oz <= 1; oz++) {
                int bx = wx + (ox == 0 ? 0 : xOffset);
                int bz = wz + (oz == 0 ? 0 : zOffset);
                
                int rawAbove = neighborhood.getRawBlockData(bx, wy + 1, bz);
                if ((rawAbove >> 8) == waterId) {
                    return 1.0f; // Вода сверху делает угол полным
                }
                
                int raw = neighborhood.getRawBlockData(bx, wy, bz);
                int type = raw >> 8;
                if (type == waterId) {
                    int level = raw & 0xFF;
                    if (level == 0 || level == 8) {
                        hasSourceOrFalling = true;
                    }
                }
            }
        }
        
        // 2. Рассчитываем высоту угла
        for (int ox = 0; ox <= 1; ox++) {
            for (int oz = 0; oz <= 1; oz++) {
                int bx = wx + (ox == 0 ? 0 : xOffset);
                int bz = wz + (oz == 0 ? 0 : zOffset);
                
                int raw = neighborhood.getRawBlockData(bx, wy, bz);
                int type = raw >> 8;
                if (type == waterId) {
                    int level = raw & 0xFF;
                    if (level == 8) {
                        sumHeight += 1.0f;
                    } else {
                        // Ограничиваем максимальную высоту источника (level 0) до 0.875f (14/16 как в Minecraft)
                        sumHeight += ((8 - level) / 8.0f) * 0.875f;
                    }
                    count++;
                } else if (!hasSourceOrFalling) {
                    // Соседний воксель - не вода. Проверяем, является ли он обрывом (только если нет источника/падающей воды)
                    com.za.zenith.world.blocks.BlockDefinition neighborDef = BlockRegistry.getBlock(type);
                    if (neighborDef.isReplaceable()) {
                        int rawBelow = neighborhood.getRawBlockData(bx, wy - 1, bz);
                        int typeBelow = rawBelow >> 8;
                        com.za.zenith.world.blocks.BlockDefinition belowDef = BlockRegistry.getBlock(typeBelow);
                        if (belowDef.isReplaceable()) {
                            // Это обрыв! Приписываем ему высоту 0.0f
                            sumHeight += 0.0f;
                            count++;
                        }
                    }
                }
            }
        }
        
        if (count == 0) return 0.875f;
        return sumHeight / count;
    }

    private static float getWaterFlowDirection(ChunkNeighborhood neighborhood, int wx, int wy, int wz, int fluidId) {
        int currentLevel = getWaterLevel(neighborhood, wx, wy, wz, fluidId);
        if (currentLevel < 0) return 15.0f;
        
        int levelW = getWaterLevel(neighborhood, wx - 1, wy, wz, fluidId);
        int levelE = getWaterLevel(neighborhood, wx + 1, wy, wz, fluidId);
        int levelN = getWaterLevel(neighborhood, wx, wy, wz - 1, fluidId);
        int levelS = getWaterLevel(neighborhood, wx, wy, wz + 1, fluidId);
        
        float dx = 0;
        float dz = 0;
        
        if (levelW >= 0 && levelW != 8) dx += (currentLevel - levelW);
        if (levelE >= 0 && levelE != 8) dx -= (currentLevel - levelE);
        if (levelN >= 0 && levelN != 8) dz += (currentLevel - levelN);
        if (levelS >= 0 && levelS != 8) dz -= (currentLevel - levelS);
        
        if (dx == 0 && dz == 0) return 15.0f;
        
        double angle = Math.atan2(dz, dx);
        if (angle < 0) angle += 2.0 * Math.PI;
        
        int quantized = (int) Math.round((angle / (2.0 * Math.PI)) * 16.0) % 16;
        return (float) quantized;
    }

    private static int getWaterLevel(ChunkNeighborhood neighborhood, int x, int y, int z, int waterId) {
        int raw = neighborhood.getRawBlockData(x, y, z);
        int type = raw >> 8;
        if (type != waterId) return -1;
        return raw & 0xFF;
    }

    private static boolean isRampFaceVisible(ChunkNeighborhood neighborhood, int nx, int ny, int nz) {
        if (neighborhood == null) return true; // Always render all faces in inventories/items
        if (ny < 0 || ny >= Chunk.CHUNK_HEIGHT) return true;
        int nRaw = neighborhood.getRawBlockData(nx, ny, nz);
        int nType = nRaw >> 8;
        if (nType == 0) return true;
        BlockDefinition neighborDef = BlockRegistry.getBlock(nType);
        if (neighborDef == null) return true;
        return neighborDef.is(BlockDefinition.FLAG_TRANSPARENT) || neighborDef.isAlwaysRender();
    }

    private static float getFaceBlockType(int blockType, BlockDefinition def, int face, Block block, DynamicTextureAtlas atlas) {
        float faceBlockType = (float) blockType;
        if (def != null && def.isFaceTinted(face)) {
            faceBlockType = -(faceBlockType + 1.0f);
        }
        return faceBlockType;
    }

    private static float getFaceOverlayLayer(BlockDefinition def, int face, Block block, DynamicTextureAtlas atlas) {
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

    private static void generateRampGeometry(
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
