package com.za.zenith.world.chunks;

import com.za.zenith.engine.graphics.Mesh;
import com.za.zenith.engine.graphics.MeshPool;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.physics.AABB;

public class MeshData {
    private static final int[] FACE_INDICES = {0,1,2, 2,3,0};

    com.za.zenith.utils.FloatArrayList interleavedData;
    com.za.zenith.utils.IntArrayList indices;
    int vertexIndex = 0;
    private final float[] tempLightBuf = new float[2];
    private final float[] tempFaceVertices = new float[12];

    public float[] getFaceVertices(int face, AABB box, float h0, float h1, float h2, float h3, boolean isWater) {
        return VoxelVertexHelper.fillFaceVertices(face, box, h0, h1, h2, h3, isWater, tempFaceVertices);
    }

    MeshData(int initialCapacity) {
        interleavedData = new com.za.zenith.utils.FloatArrayList(initialCapacity);
        indices = new com.za.zenith.utils.IntArrayList(initialCapacity / 2);
    }

    void clear() {
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
            float px = fp[v*3] + ox; float py = fp[v*3+1] + oy; float pz = fp[v*3+2] + oz;
            float vx = fp[v*3]; float vy = fp[v*3+1]; float vz = fp[v*3+2];

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

            float ao = ChunkMeshLighting.calculateAO(neighborhood, wx, wy, wz, face, vx, vy, vz);
            ChunkMeshLighting.calculateSmoothLight(neighborhood, wx, wy, wz, face, vx, vy, vz, tempLightBuf);

            int u16 = (int)(finalU * 65535.0f) & 0xFFFF;
            int v16 = (int)(finalV * 65535.0f) & 0xFFFF;
            int packedTex = u16 | (v16 << 16);

            int texLayer = ((int)fullUv[2]) & 0xFFF;
            int overLayer = (((int)overlayLayer) + 1) & 0xFFF;
            int packedLayers = texLayer | (overLayer << 12) | ((face & 0x7) << 24);

            int bType = ((int)blockTypeId) & 0xFFFF;
            int nMask = ((int)neighborMask) & 0x3F;
            int wt = weight > 0.5f ? 1 : 0;
            
            float wetness = 0.5f;
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

            interleavedData.add(px); interleavedData.add(py); interleavedData.add(pz);
            interleavedData.add(Float.intBitsToFloat(packedTex));
            interleavedData.add(Float.intBitsToFloat(packedLayers));
            interleavedData.add(Float.intBitsToFloat(packedBlock));
            interleavedData.add(Float.intBitsToFloat(packedLight));
        }
        for (int idx : FACE_INDICES) indices.add(vertexIndex + idx);
        vertexIndex += 4;
    }

    public void addRawQuad(float[] fp, float[] uv, float[] fn, float blockTypeId, float overlayLayer, boolean canSway, float weightOffset, float[] light, float ao) {
        float minY = fp[1], maxY = fp[1];
        for (int v = 1; v < 4; v++) {
            minY = Math.min(minY, fp[v*3+1]);
            maxY = Math.max(maxY, fp[v*3+1]);
        }

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
        
        org.joml.Vector3f min = new org.joml.Vector3f(Float.MAX_VALUE);
        org.joml.Vector3f max = new org.joml.Vector3f(-Float.MAX_VALUE);
        for (int i = 0; i < dLen; i += 7) {
            float px = internal[i]; float py = internal[i+1]; float pz = internal[i+2];
            min.x = Math.min(min.x, px); min.y = Math.min(min.y, py); min.z = Math.min(min.z, pz);
            max.x = Math.max(max.x, px);
            max.y = Math.max(max.y, py); max.z = Math.max(max.z, pz);
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
        raw.cleanup();
        return mesh;
    }
}
