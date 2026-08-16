package com.lieyabull.dung.room;

/**
 * A hand-placed metadata point or region for gameplay hooks (player spawn, shopkeeper, loot,
 * hazards, mechanics, etc.). Position is template-relative. An optional region (inclusive) turns a
 * point into an area.
 */
public final class RoomMarker {
    public RoomMarkerType type;
    public int x, y, z;
    public String name;
    /** Optional region (inclusive); null when this is a single point. */
    public RoomBounds region;

    public RoomMarker() {}

    public RoomMarker(RoomMarkerType type, int x, int y, int z, String name) {
        this.type = type;
        this.x = x; this.y = y; this.z = z;
        this.name = name;
    }

    public boolean isPoint() {
        return region == null;
    }

    public boolean contains(int px, int py, int pz) {
        if (region != null) return region.contains(px, py, pz);
        return px == x && py == y && pz == z;
    }
}
