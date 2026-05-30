package com.za.zenith.world.physics;

import java.util.ArrayList;
import java.util.List;

public class VoxelShape {
    private final List<AABB> boxes;
    
    public VoxelShape() {
        this.boxes = new ArrayList<>();
    }
    
    public VoxelShape(AABB box) {
        this.boxes = new ArrayList<>();
        this.boxes.add(box);
    }
    
    public VoxelShape addBox(AABB box) {
        this.boxes.add(box);
        return this;
    }
    
    public List<AABB> getBoxes() {
        return boxes;
    }

    public boolean isFullCube() {
        if (boxes.size() != 1) return false;
        AABB box = boxes.get(0);
        return box.minX() == 0 && box.minY() == 0 && box.minZ() == 0 &&
               box.maxX() == 1 && box.maxY() == 1 && box.maxZ() == 1;
    }
    
    public VoxelShape offset(float x, float y, float z) {
        VoxelShape newShape = new VoxelShape();
        for (AABB box : boxes) {
            newShape.addBox(box.offset(x, y, z));
        }
        return newShape;
    }
    
    public static final VoxelShape FULL_CUBE = new VoxelShape(new AABB(0, 0, 0, 1, 1, 1));
    public static final VoxelShape SLAB_BOTTOM = new VoxelShape(new AABB(0, 0, 0, 1, 0.5f, 1));
    public static final VoxelShape SLAB_TOP = new VoxelShape(new AABB(0, 0.5f, 0, 1, 1, 1));
    public static final VoxelShape SLAB_NORTH = new VoxelShape(new AABB(0, 0, 0, 1, 1, 0.5f));
    public static final VoxelShape SLAB_SOUTH = new VoxelShape(new AABB(0, 0, 0.5f, 1, 1, 1));
    public static final VoxelShape SLAB_WEST = new VoxelShape(new AABB(0, 0, 0, 0.5f, 1, 1));
    public static final VoxelShape SLAB_EAST = new VoxelShape(new AABB(0.5f, 0, 0, 1, 1, 1));
}


