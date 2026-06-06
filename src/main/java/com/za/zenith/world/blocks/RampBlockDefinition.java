package com.za.zenith.world.blocks;

import com.za.zenith.utils.Identifier;
import com.za.zenith.world.physics.VoxelShape;
import com.za.zenith.world.physics.AABB;

public class RampBlockDefinition extends BlockDefinition {
    
    private static final VoxelShape[] SHAPES = new VoxelShape[6];
    
    static {
        // We use a full AABB box so that broadphase collisions are detected,
        // and then our specialized physics code handles the slope math.
        for (int i = 0; i < 6; i++) {
            VoxelShape shape = new VoxelShape(new AABB(0, 0, 0, 1, 1, 1));
            shape.setGeometry(VoxelShape.ShapeGeometry.RAMP);
            shape.setMetadata((byte) i);
            SHAPES[i] = shape;
        }
    }
    
    public RampBlockDefinition(int id, String name, boolean solid, boolean transparent) {
        super(id, name, solid, transparent);
    }

    public RampBlockDefinition(int id, Identifier identifier, String translationKey, boolean solid, boolean transparent) {
        super(id, identifier, translationKey, solid, transparent);
    }
    
    @Override
    public VoxelShape getShape(byte metadata) {
        int dir = metadata & 0x0F;
        if (dir >= 0 && dir < SHAPES.length) {
            return SHAPES[dir];
        }
        return SHAPES[Block.DIR_NORTH];
    }

    @Override
    public boolean isFullCube() {
        return false;
    }
}
