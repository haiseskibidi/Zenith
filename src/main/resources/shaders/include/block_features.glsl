// Unified block type decoding
struct BlockInfo {
    float type;
    bool isTinted;
    bool isGlass;
    bool isWater;
    int fluidType; // 0 = none, 1 = water, 2 = oil, 3 = lava
};

BlockInfo decodeBlockInfo(float blockTypeAttr) {
    BlockInfo info;
    info.isTinted = false;
    info.isGlass = false;
    info.isWater = false;
    info.fluidType = 0;
    
    if (blockTypeAttr < -2500.0) {
        if (blockTypeAttr > -3500.0) {
            info.type = abs(blockTypeAttr) - 3000.0;
            info.isWater = true;
            info.fluidType = 1;
        } else if (blockTypeAttr > -4500.0) {
            info.type = abs(blockTypeAttr) - 4000.0;
            info.fluidType = 2; // Oil
        } else if (blockTypeAttr > -5500.0) {
            info.type = abs(blockTypeAttr) - 5000.0;
            info.fluidType = 3; // Lava
        } else {
            info.type = abs(blockTypeAttr) - 3000.0;
            info.isWater = true;
            info.fluidType = 1;
        }
    } else if (blockTypeAttr < -1500.0) {
        info.type = abs(blockTypeAttr) - 2000.0;
        info.isGlass = true;
    } else if (blockTypeAttr < -0.5) {
        info.type = abs(blockTypeAttr) - 1.0;
        info.isTinted = true;
    } else {
        info.type = blockTypeAttr;
    }
    
    return info;
}

// Brighten top face of specific blocks (like stumps)
vec3 brightenTopFace(vec3 color, float type, vec3 normal) {
    if (abs(type - 150.0) < 0.1 && normal.y > 0.9) {
        return color * 1.1;
    }
    return color;
}

// Connected textures logic for Glass
vec4 applyGlassConnections(vec4 texColor, vec2 uv, float neighborData, float glassLayer, sampler2DArray texSampler) {
    float t = 0.0625; 
    int nMask = int(neighborData + 0.5);
    bool hasLeft  = (nMask & 1) != 0;
    bool hasRight = (nMask & 2) != 0;
    bool hasDown  = (nMask & 4) != 0;
    bool hasUp    = (nMask & 8) != 0;

    bool onLeft   = uv.x < t;
    bool onRight  = uv.x > (1.0 - t);
    bool onDown   = uv.y < t;
    bool onUp     = uv.y > (1.0 - t);

    bool shouldHide = false;

    if ((onLeft && hasLeft) || (onRight && hasRight)) {
        if (!onDown && !onUp) {
            shouldHide = true;
        } else {
            bool hasVerticalNeighbor = (onDown && hasDown) || (onUp && hasUp);
            if (hasVerticalNeighbor) shouldHide = true;
        }
    }

    if (!shouldHide && ((onDown && hasDown) || (onUp && hasUp))) {
        if (!onLeft && !onRight) {
            shouldHide = true;
        } else {
            bool hasHorizontalNeighbor = (onLeft && hasLeft) || (onRight && hasRight);
            if (hasHorizontalNeighbor) shouldHide = true;
        }
    }

    if (shouldHide) {
        // Return fully transparent voxel pixel directly to avoid expensive texture sampling inside dynamic branch
        return vec4(0.0);
    }
    return texColor;
}
