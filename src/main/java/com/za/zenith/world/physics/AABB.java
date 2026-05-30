package com.za.zenith.world.physics;

import org.joml.Vector3f;

public class AABB {
    private final Vector3f min;
    private final Vector3f max;
    
    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.min = new Vector3f(minX, minY, minZ);
        this.max = new Vector3f(maxX, maxY, maxZ);
    }
    
    public AABB(Vector3f min, Vector3f max) {
        this.min = new Vector3f(min);
        this.max = new Vector3f(max);
    }
    
    public AABB offset(float x, float y, float z) {
        return new AABB(
            min.x + x, min.y + y, min.z + z,
            max.x + x, max.y + y, max.z + z
        );
    }
    
    public AABB offset(Vector3f offset) {
        return offset(offset.x, offset.y, offset.z);
    }
    
    public boolean intersects(AABB other) {
        return max.x > other.min.x && min.x < other.max.x &&
               max.y > other.min.y && min.y < other.max.y &&
               max.z > other.min.z && min.z < other.max.z;
    }
    
    public static boolean intersects(AABB boxA, float ax, float ay, float az, AABB boxB, float bx, float by, float bz) {
        return (boxA.max.x + ax > boxB.min.x + bx) && (boxA.min.x + ax < boxB.max.x + bx) &&
               (boxA.max.y + ay > boxB.min.y + by) && (boxA.min.y + ay < boxB.max.y + by) &&
               (boxA.max.z + az > boxB.min.z + bz) && (boxA.min.z + az < boxB.max.z + bz);
    }
    
    public boolean contains(Vector3f point) {
        return point.x >= min.x && point.x <= max.x &&
               point.y >= min.y && point.y <= max.y &&
               point.z >= min.z && point.z <= max.z;
    }

    /**
     * Возвращает дистанцию до пересечения луча с этим AABB.
     * Использует алгоритм "Ray-Box Intersection".
     */
    public float intersectDist(Vector3f origin, Vector3f direction) {
        float t1 = (min.x - origin.x) / direction.x;
        float t2 = (max.x - origin.x) / direction.x;
        float t3 = (min.y - origin.y) / direction.y;
        float t4 = (max.y - origin.y) / direction.y;
        float t5 = (min.z - origin.z) / direction.z;
        float t6 = (max.z - origin.z) / direction.z;

        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0 || tmin > tmax) return -1.0f;
        return tmin;
    }
    
    public Vector3f getCenter() {
        return new Vector3f(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        );
    }
    
    public Vector3f getSize() {
        return new Vector3f(
            max.x - min.x,
            max.y - min.y,
            max.z - min.z
        );
    }
    
    public Vector3f getMin() {
        return new Vector3f(min);
    }
    
    public Vector3f getMax() {
        return new Vector3f(max);
    }

    public float minX() { return min.x; }
    public float minY() { return min.y; }
    public float minZ() { return min.z; }
    public float maxX() { return max.x; }
    public float maxY() { return max.y; }
    public float maxZ() { return max.z; }
    
    @Override
    public String toString() {
        return String.format("AABB[%.2f,%.2f,%.2f -> %.2f,%.2f,%.2f]", 
            min.x, min.y, min.z, max.x, max.y, max.z);
    }

    /**
     * Результат пересечения луча с AABB.
     */
    public record RayHit(float distance, Vector3f normal) {}

    /**
     * Возвращает результат пересечения луча с этим AABB (дистанция и нормаль).
     * Возвращает null, если пересечения нет.
     */
    public RayHit raycast(Vector3f origin, Vector3f direction) {
        float invDirX = 1.0f / (Math.abs(direction.x) < 1e-6f ? (direction.x < 0 ? -1e-6f : 1e-6f) : direction.x);
        float invDirY = 1.0f / (Math.abs(direction.y) < 1e-6f ? (direction.y < 0 ? -1e-6f : 1e-6f) : direction.y);
        float invDirZ = 1.0f / (Math.abs(direction.z) < 1e-6f ? (direction.z < 0 ? -1e-6f : 1e-6f) : direction.z);

        float t1 = (min.x - origin.x) * invDirX;
        float t2 = (max.x - origin.x) * invDirX;
        float t3 = (min.y - origin.y) * invDirY;
        float t4 = (max.y - origin.y) * invDirY;
        float t5 = (min.z - origin.z) * invDirZ;
        float t6 = (max.z - origin.z) * invDirZ;

        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0 || tmin > tmax) return null;

        // Определяем нормаль на основе того, какая плоскость дала tmin
        Vector3f normal = new Vector3f();
        if (tmin == t1) normal.set(-1, 0, 0);
        else if (tmin == t2) normal.set(1, 0, 0);
        else if (tmin == t3) normal.set(0, -1, 0);
        else if (tmin == t4) normal.set(0, 1, 0);
        else if (tmin == t5) normal.set(0, 0, -1);
        else if (tmin == t6) normal.set(0, 0, 1);
        else {
            // Запасной вариант на случай неточности float
            if (Math.abs(tmin - t1) < 1e-5f) normal.set(-1, 0, 0);
            else if (Math.abs(tmin - t2) < 1e-5f) normal.set(1, 0, 0);
            else if (Math.abs(tmin - t3) < 1e-5f) normal.set(0, -1, 0);
            else if (Math.abs(tmin - t4) < 1e-5f) normal.set(0, 1, 0);
            else if (Math.abs(tmin - t5) < 1e-5f) normal.set(0, 0, -1);
            else if (Math.abs(tmin - t6) < 1e-5f) normal.set(0, 0, 1);
        }

        return new RayHit(tmin, normal);
    }
}


