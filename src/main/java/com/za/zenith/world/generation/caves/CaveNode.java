package com.za.zenith.world.generation.caves;

public class CaveNode {
    public final double x, y, z;
    public final float radius;
    public final int depth;

    public CaveNode(double x, double y, double z, float radius, int depth) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.depth = depth;
    }
}
