package com.lieyabull.dung.room;

/**
 * An axis-aligned cuboid region, expressed in template-relative coordinates (inclusive bounds).
 * A room is built from one or more of these that combine into an irregular shape.
 */
public class RoomBounds {
    public int minX, minY, minZ, maxX, maxY, maxZ;

    public RoomBounds() {}

    public RoomBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    public int width()  { return maxX - minX + 1; }
    public int height() { return maxY - minY + 1; }
    public int depth()  { return maxZ - minZ + 1; }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean intersects(RoomBounds o) {
        return minX <= o.maxX && maxX >= o.minX
            && minY <= o.maxY && maxY >= o.minY
            && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    public void normalize() {
        int a, b;
        if (minX > maxX) { a = minX; minX = maxX; maxX = a; }
        if (minY > maxY) { a = minY; minY = maxY; maxY = a; }
        if (minZ > maxZ) { a = minZ; minZ = maxZ; maxZ = a; }
    }

    /** Union with another bounds, returning a new covering bounds. */
    public RoomBounds union(RoomBounds o) {
        return new RoomBounds(
            Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
            Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }
}
