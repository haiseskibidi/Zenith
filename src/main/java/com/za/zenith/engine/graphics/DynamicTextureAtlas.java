package com.za.zenith.engine.graphics;

import com.za.zenith.utils.Logger;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class DynamicTextureAtlas {
    private final int tileSize;
    private final Map<String, String> keyToPath = new LinkedHashMap<>();
    private final Map<String, Integer> keyToLayer = new LinkedHashMap<>();
    private int textureId;
    private int width;
    private int height;
    private int layers;

    public DynamicTextureAtlas(int tileSize) {
        this.tileSize = tileSize;
    }

    public void add(String key, String path) {
        keyToPath.put(key, path);
    }

    public void build() {
        int count = keyToPath.size();
        if (count == 0) {
            createWhiteTexture();
            return;
        }
        
        layers = count;
        width = tileSize;
        height = tileSize;

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        
        // Use glTexImage3D to allocate the base level. 
        // Subsequent levels will be generated via glGenerateMipmap.
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);

        int index = 0;
        stbi_set_flip_vertically_on_load(true);
        for (Map.Entry<String, String> e : keyToPath.entrySet()) {
            String key = e.getKey();
            String path = e.getValue();
            try (MemoryStack stack = stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer c = stack.mallocInt(1);
                ByteBuffer img = loadImageFromPath(stack, path, w, h, c);
                boolean isStbAllocated = true;
                
                if (img == null) {
                    Logger.error("Failed to load block texture: %s (%s)", path, stbi_failure_reason());
                    img = createSolidImage(tileSize, tileSize, (byte) 255, (byte) 0, (byte) 255, (byte) 255);
                    w.put(0, tileSize);
                    h.put(0, tileSize);
                    isStbAllocated = false;
                }
                
                // Resize if needed
                int iw = w.get(0), ih = h.get(0);
                if (iw != tileSize || ih != tileSize) {
                    ByteBuffer resized = ByteBuffer.allocateDirect(tileSize * tileSize * 4);
                    for (int y = 0; y < tileSize; y++) {
                        for (int x = 0; x < tileSize; x++) {
                            int sx = x * iw / tileSize;
                            int sy = y * ih / tileSize;
                            int src = (sy * iw + sx) * 4;
                            int dst = (y * tileSize + x) * 4;
                            resized.put(dst, img.get(src));
                            resized.put(dst + 1, img.get(src + 1));
                            resized.put(dst + 2, img.get(src + 2));
                            resized.put(dst + 3, img.get(src + 3));
                        }
                    }
                    if (isStbAllocated) {
                        stbi_image_free(img);
                    }
                    img = resized;
                    isStbAllocated = false;
                }

                // --- BLACK PADDING: MIPMAP BLEEDING FIX ---
                // Force all fully transparent pixels to black to prevent white fringes during mipmap generation
                for (int i = 0; i < tileSize * tileSize; i++) {
                    int a = img.get(i * 4 + 3) & 0xFF;
                    if (a < 128) {
                        img.put(i * 4, (byte) 0);
                        img.put(i * 4 + 1, (byte) 0);
                        img.put(i * 4 + 2, (byte) 0);
                        img.put(i * 4 + 3, (byte) 0);
                    }
                }

                keyToLayer.put(key, index);
                glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, index, tileSize, tileSize, 1, GL_RGBA, GL_UNSIGNED_BYTE, img);
                
                if (isStbAllocated) stbi_image_free(img);
            }
            index++;
        }

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        // Proper voxel filtering: LINEAR_MIPMAP_LINEAR for MIN filter to smooth distance,
        // NEAREST for MAG filter to keep close-up pixels sharp.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        
        // Neutral LOD bias avoids artifacts. Negative bias can cause flickering.
        glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_LOD_BIAS, 0.0f); 

        // Enable 8x Anisotropic Filtering for superior distance quality
        try {
            if (org.lwjgl.opengl.GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                float max = glGetFloat(org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                glTexParameterf(GL_TEXTURE_2D_ARRAY, org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(8.0f, max));
            }
        } catch (Exception ignored) {}

        Logger.info("Built dynamic texture array %dx%dx%d", width, height, layers);
    }

    private void createWhiteTexture() {
        width = tileSize; height = tileSize; layers = 1;
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, width, height, layers, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
        
        ByteBuffer buffer = createSolidImage(tileSize, tileSize, (byte) 255, (byte) 255, (byte) 255, (byte) 255);
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    private ByteBuffer createSolidImage(int w, int h, byte r, byte g, byte b, byte a) {
        ByteBuffer img = ByteBuffer.allocateDirect(w * h * 4);
        for (int i = 0; i < w * h; i++) {
            int o = i * 4;
            img.put(o, r);
            img.put(o + 1, g);
            img.put(o + 2, b);
            img.put(o + 3, a);
        }
        return img;
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }

    private ByteBuffer loadImageFromPath(MemoryStack stack, String path, IntBuffer w, IntBuffer h, IntBuffer c) {
        try {
            String resourcePath = path.replace("src/main/resources/", "");
            // Handle Identifier-style pathing (e.g., zenith:textures/item/hand.png -> zenith/textures/item/hand.png)
            if (resourcePath.contains(":")) {
                resourcePath = resourcePath.replace(":", "/");
            }
            var inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (inputStream != null) {
                byte[] imageData = inputStream.readAllBytes();
                ByteBuffer imageBuffer = stack.malloc(imageData.length);
                imageBuffer.put(imageData);
                imageBuffer.flip();
                inputStream.close();
                return stbi_load_from_memory(imageBuffer, w, h, c, 4);
            }
            return stbi_load(path, w, h, c, 4);
        } catch (IOException e) {
            Logger.error("IOException while loading texture: %s", e, path);
            return null;
        }
    }

    public float getLayer(String key) {
        Integer layer = keyToLayer.get(key);
        return (layer != null) ? (float) layer : 0.0f;
    }

    public float[] uvFor(String key) {
        Integer layer = keyToLayer.get(key);
        float l = (layer != null) ? (float) layer : 0.0f;
        // Returns 12 values: U, V, W for 4 vertices
        return new float[]{
            0, 0, l,
            1, 0, l,
            1, 1, l,
            0, 1, l
        };
    }
}




