# Vertex Compression & Frustum Culling Plan

## 1. Goal
Reduce vertex size significantly to alleviate VRAM bandwidth bottlenecks and ensure chunks are rendered front-to-back for early Z-culling.

## 2. Architecture & Changes
### 2.1 Vertex Compression
Currently, vertices use multiple `float` attributes (e.g. position, texCoord, normal, blockType, etc.), totaling around 60+ bytes.
We will pack them into smaller integers:
- Pack `x` (0-15), `y` (0-255), `z` (0-15) and `normalIndex` (0-5) into a single integer.
- Pack `u` (0-1), `v` (0-1), `textureLayer` into another integer or short.
- Pack `light`, `ao`, `blockType` and other metadata into an integer.

We will use `Float.intBitsToFloat` to pass them via the existing `float[]` array infrastructure, and `floatBitsToInt` in GLSL to unpack them, avoiding changing the underlying buffer types if possible, or migrate to `ByteBuffer` directly.

### 2.2 Rendering Order (Front-to-Back)
Modify `Renderer.java`'s `renderWorld` loop so that `visibleChunks` is sorted correctly:
- Calculate camera position relative to chunk centers.
- Sort `visibleChunks` by distance ascending.

## 3. Verification
- Validate the game renders exactly the same as before.
- Validate FPS increase when looking at large vistas.
- Check for any bitwise precision issues.
