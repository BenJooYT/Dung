package com.lieyabull.dung.room;

import java.util.ArrayList;
import java.util.List;

/**
 * A versioned, deterministic, self-contained room asset. Holds every part of a room: the hand-built
 * block structure (with block states), one or more bound cuboids forming the playable shape, explicit
 * connections, spawn floors, and gameplay markers. Coordinates are relative to the template origin
 * (the author's first selection corner). This object is the canonical, Git-diffable representation
 * written to and loaded from the room asset JSON.
 */
public final class RoomTemplate {
    /** Asset format version - bump to signal breaking changes. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    /** Unique lowercase identifier, e.g. "combat_crossroads". */
    public String id = "";
    /** Room types this template can serve, e.g. ["COMBAT"], ["TREASURE"], ["COMBAT","ELITE"]. */
    public List<String> types = new ArrayList<>();
    /** Human-readable author note. */
    public String description = "";
    /** Flag set only after a successful validation; invalid templates are never registered. */
    public boolean validated = false;

    /** Bound cuboids that combine into the playable room shape (>=1). */
    public List<RoomBounds> bounds = new ArrayList<>();
    /** Explicit entry/exit connections. */
    public List<RoomConnector> connectors = new ArrayList<>();
    /** Regions where enemies may spawn. */
    public List<SpawnFloor> spawnFloors = new ArrayList<>();
    /** Gameplay markers (player spawn, shopkeeper, loot, hazards, mechanics...). */
    public List<RoomMarker> markers = new ArrayList<>();
    /** Serialized block structure (template-relative). */
    public List<RoomBlock> blocks = new ArrayList<>();

    public RoomBounds total() {
        RoomBounds t = null;
        for (RoomBounds b : bounds) t = (t == null) ? new RoomBounds(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ) : t.union(b);
        if (t == null) return new RoomBounds();
        return t;
    }

    public boolean inAnyBound(int x, int y, int z) {
        for (RoomBounds b : bounds) if (b.contains(x, y, z)) return true;
        return false;
    }

    public List<RoomMarker> markersOf(RoomMarkerType type) {
        List<RoomMarker> out = new ArrayList<>();
        for (RoomMarker m : markers) if (m.type == type) out.add(m);
        return out;
    }

    public RoomConnector connectorFacing(Direction d) {
        for (RoomConnector c : connectors) if (c.direction == d) return c;
        return null;
    }
}
