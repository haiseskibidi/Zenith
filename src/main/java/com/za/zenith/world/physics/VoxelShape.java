package com.za.zenith.world.physics;

import java.util.ArrayList;
import java.util.List;

public class VoxelShape {
    public enum ShapeGeometry {
        CUBIC,
        RAMP
    }

    private final List<AABB> boxes;
    private ShapeGeometry geometry = ShapeGeometry.CUBIC;
    private byte metadata = 0;
    
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

    public ShapeGeometry getGeometry() {
        return geometry;
    }

    public void setGeometry(ShapeGeometry geometry) {
        this.geometry = geometry;
    }

    public byte getMetadata() {
        return metadata;
    }

    public void setMetadata(byte metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoxelShape)) return false;
        VoxelShape that = (VoxelShape) o;
        return metadata == that.metadata &&
               geometry == that.geometry &&
               java.util.Objects.equals(boxes, that.boxes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(boxes, geometry, metadata);
    }
}


