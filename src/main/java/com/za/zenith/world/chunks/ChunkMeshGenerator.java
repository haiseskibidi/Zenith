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
    
    public record ChunkMeshResult(long version, Mesh[] opaqueSections, Mesh[] translucentSections, float spawnTime) {
        public void cleanup() {
            if (opaqueSections != null) for (Mesh m : opaqueSections) if (m != null) m.cleanup();
            if (translucentSections != null) for (Mesh m : translucentSections) if (m != null) m.cleanup();
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

    public record RawChunkMeshResult(RawMeshData[] opaque, RawMeshData[] translucent, long version, float firstSpawnTime) {
        public ChunkMeshResult upload(MeshPool pool) {
            Mesh[] opaqueMeshes = new Mesh[Chunk.NUM_SECTIONS];
            Mesh[] translucentMeshes = new Mesh[Chunk.NUM_SECTIONS];
            for (int i = 0; i < Chunk.NUM_SECTIONS; i++) {
                if (opaque != null && opaque[i] != null) opaqueMeshes[i] = opaque[i].createMesh(pool);
                if (translucent != null && translucent[i] != null) translucentMeshes[i] = translucent[i].createMesh(pool);
            }
            return new ChunkMeshResult(version, opaqueMeshes, translucentMeshes, firstSpawnTime);
        }

        public void cleanup() {
            if (opaque != null) for (RawMeshData r : opaque) if (r != null) r.cleanup();
            if (translucent != null) for (RawMeshData r : translucent) if (r != null) r.cleanup();
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
        for (AABB box : shape.getBoxes()) {
            Vector3f min = box.getMin(), max = box.getMax();
            float[][] facePositions = new float[][]{
                {min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z},
                {max.x, min.y, min.z,  min.x, min.y, min.z,  min.x, max.y, min.z,  max.x, max.y, min.z},
                {max.x, min.y, max.z,  max.x, min.y, min.z,  max.x, max.y, min.z,  max.x, max.y, max.z},
                {min.x, min.y, min.z,  min.x, min.y, max.z,  min.x, max.y, max.z,  min.x, max.y, min.z},
                {min.x, max.y, max.z,  max.x, max.y, max.z,  max.x, max.y, min.z,  min.x, max.y, min.z},
                {min.x, min.y, min.z,  max.x, min.y, min.z,  max.x, min.y, max.z,  min.x, min.y, max.z}
            };
            for (int face = 0; face < 6; face++) {
                float faceBlockType = (float)block.getType();
                if (isTranslucent) {
                    faceBlockType = -(faceBlockType + 2000.0f);
                } else if (def.isTinted()) {
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
                data.addFace(facePositions[face], FACE_NORMALS[face], faceBlockType, BlockTextureMapper.uvFor(block, face, atlas), face, -0.5f, 0, -0.5f, 0, overlayLayer, def.isSway(), neighborhood, wx, wy, wz);
            }
        }
        return data.build();
    }

    public static Mesh generateCustomAABBMesh(Block block, AABB box, DynamicTextureAtlas atlas) {
        MeshData data = new MeshData(512);
        BlockDefinition def = BlockRegistry.getBlock(block.getType());
        boolean canSway = def != null && def.isSway();
        Vector3f min = box.getMin(), max = box.getMax();
        float[][] facePositions = new float[][]{
            {min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z},
            {max.x, min.y, min.z,  min.x, min.y, min.z,  min.x, max.y, min.z,  max.x, max.y, min.z},
            {max.x, min.y, max.z,  max.x, min.y, min.z,  max.x, max.y, min.z,  max.x, max.y, max.z},
            {min.x, min.y, min.z,  min.x, min.y, max.z,  min.x, max.y, max.z,  min.x, max.y, min.z},
            {min.x, max.y, max.z,  max.x, max.y, max.z,  max.x, max.y, min.z,  min.x, max.y, min.z},
            {min.x, min.y, min.z,  max.x, min.y, min.z,  max.x, min.y, max.z,  min.x, min.y, max.z}
        };
        for (int face = 0; face < 6; face++) {
            data.addFace(facePositions[face], FACE_NORMALS[face], (float) block.getType(), BlockTextureMapper.uvFor(block, face, atlas), face, 0, 0, 0, 0, -1.0f, canSway, null, 0, 0, 0);
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
                    Vector3f min = box.getMin(), max = box.getMax();
                    float[][] facePositions = new float[][]{
                        {min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z},
                        {max.x, min.y, min.z,  min.x, min.y, min.z,  min.x, max.y, min.z,  max.x, max.y, min.z},
                        {max.x, min.y, max.z,  max.x, min.y, min.z,  max.x, max.y, min.z,  max.x, max.y, max.z},
                        {min.x, min.y, min.z,  min.x, min.y, max.z,  min.x, max.y, max.z,  min.x, max.y, min.z},
                        {min.x, max.y, max.z,  max.x, max.y, max.z,  max.x, max.y, min.z,  min.x, max.y, min.z},
                        {min.x, min.y, min.z,  max.x, min.y, min.z,  max.x, min.y, max.z,  min.x, min.y, max.z}
                    };

                    float faceBlockType = (float)nBlock.getType();
                    float overlayLayer = -1.0f;
                    if (nDef.isTinted()) {
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

                    data.addFace(facePositions[oppFace], FACE_NORMALS[oppFace], faceBlockType, BlockTextureMapper.uvFor(nBlock, oppFace, atlas), oppFace, ox, oy, oz, 0, overlayLayer, nDef.isSway(), neighborhood, nPos.x(), nPos.y(), nPos.z());
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
        
        RawMeshData[] opaqueResults = new RawMeshData[Chunk.NUM_SECTIONS];
        RawMeshData[] translucentResults = new RawMeshData[Chunk.NUM_SECTIONS];

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
            if (section == null || section.isEmpty()) continue;

            // NEW: Calculate visibility mask for occlusion culling
            section.calculateVisibility(chunk, secIdx);

            chunkOpaque.clear();
            chunkTranslucent.clear();

            int startY = secIdx * ChunkSection.SECTION_SIZE;
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int ly = 0; ly < ChunkSection.SECTION_SIZE; ly++) {
                    int y = startY + ly;
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        int rawData = chunk.getRawBlockData(x, y, z);
                        int blockType = rawData >> 8;
                        if (blockType == 0) continue;

                        Block block = chunk.getBlock(x, y, z);
                        BlockDefinition def = BlockRegistry.getBlock(blockType);
                        if (def == null) continue;

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

                        boolean isLeaves = def.is(BlockDefinition.FLAG_LEAVES);
                        boolean isTranslucent = def.is(BlockDefinition.FLAG_TRANSLUCENT);
                        MeshData currentTarget = isTranslucent ? chunkTranslucent : chunkOpaque;

                        int worldX = cx * Chunk.CHUNK_SIZE + x;
                        int worldY = y;
                        int worldZ = cz * Chunk.CHUNK_SIZE + z;

                        for (AABB box : shape.getBoxes()) {
                            Vector3f min = box.getMin(), max = box.getMax();
                            float[][] facePositions = new float[][]{
                                {min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z},
                                {max.x, min.y, min.z,  min.x, min.y, min.z,  min.x, max.y, min.z,  max.x, max.y, min.z},
                                {max.x, min.y, max.z,  max.x, min.y, min.z,  max.x, max.y, min.z,  max.x, max.y, max.z},
                                {min.x, min.y, min.z,  min.x, min.y, max.z,  min.x, max.y, max.z,  min.x, max.y, min.z},
                                {min.x, max.y, max.z,  max.x, max.y, max.z,  max.x, max.y, min.z,  min.x, max.y, min.z},
                                {min.x, min.y, min.z,  max.x, min.y, min.z,  max.x, min.y, max.z,  min.x, min.y, max.z}
                            };
                            
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
                                    case 0: onBoundary = (box.getMax().z == 1.0f); break; 
                                    case 1: onBoundary = (box.getMin().z == 0.0f); break; 
                                    case 2: onBoundary = (box.getMax().x == 1.0f); break; 
                                    case 3: onBoundary = (box.getMin().x == 0.0f); break; 
                                    case 4: onBoundary = (box.getMax().y == 1.0f); break; 
                                    case 5: onBoundary = (box.getMin().y == 0.0f); break; 
                                }

                                if (def.isAlwaysRender() || !onBoundary) {
                                    drawFace = true;
                                } else if (nType == 0) {
                                    drawFace = true;
                                } else if (isTranslucent && nType == blockType) {
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
                                    if (isTranslucent) {
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
                                    if (isTranslucent) {
                                        faceBlockType = -(faceBlockType + 2000.0f);
                                    } else if (def != null && def.is(BlockDefinition.FLAG_TINTED)) {
                                        boolean isGrassBlock = def.getIdentifier().getPath().contains("grass_block");
                                        if (!isGrassBlock || face <= 4) { 
                                            faceBlockType = -(faceBlockType + 1.0f);
                                        }

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
                                    currentTarget.addFace(facePositions[face], FACE_NORMALS[face], faceBlockType, BlockTextureMapper.uvFor(block, face, atlas), face, (float)x, (float)y, (float)z, neighborMask, overlayLayer, def.isSway(), neighborhood, worldX, worldY, worldZ);
                                }
                            }
                        }
                    }
                }
            }
            opaqueResults[secIdx] = chunkOpaque.buildRaw();
            translucentResults[secIdx] = chunkTranslucent.buildRaw();
        }

        return new RawChunkMeshResult(opaqueResults, translucentResults, version, chunk.getFirstSpawnTime());
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
}
