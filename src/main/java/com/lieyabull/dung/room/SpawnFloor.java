package com.lieyabull.dung.room;

/**
 * A hand-built region where the encounter system may spawn enemies. Deliberately not tied to any
 * enemy type - the generator decides composition. Bounds are template-relative and inclusive.
 */
public final class SpawnFloor extends RoomBounds {
    public SpawnFloor() {
        super();
    }

    public SpawnFloor(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        super(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
