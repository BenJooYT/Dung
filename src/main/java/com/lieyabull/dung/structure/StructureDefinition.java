package com.lieyabull.dung.structure;

import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

import java.util.ArrayList;
import java.util.List;

/**
 * Dung's metadata contract for one dungeon room, paired with a WorldEdit {@code .schem} file. The
 * schematic owns the physical Minecraft build; this object owns the Dung-specific behavior (which
 * room types it serves, its spawn floors, and gameplay markers). It is the "<id>.yml" sidecar loaded
 * next to every "<id>.schem". The room id is the schematic name (without {@code .schem}).
 *
 * <p>All coordinates are <em>structure-relative</em>: a block at schematic coordinate (x, y, z)
 * corresponds to metadata coordinate (x, y, z). The generator pastes the schematic at a world origin
 * and transforms the metadata identically, so the two always stay in sync after rotation.
 *
 * <p>Doorways and corridors are NOT part of the metadata: the generator carves them procedurally at
 * build time on the shared corridor line ({@code RoomGen.PERP_CENTER}), so every structure room opens
 * only the door directions the floor graph requires and connects to its neighbours without leaving a
 * hole between the room's outer wall and the corridor wall.
 *
 * <p>This class is pure (no Bukkit, no WorldEdit) so it can be round-tripped, validated, and
 * unit-tested headlessly.
 */
public final class StructureDefinition {
    /** Metadata format version - bump to signal breaking changes. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    /** Unique lowercase id, e.g. "stairs_room" (also the schematic basename). */
    public String id = "";
    /** Room types this structure can serve, e.g. ["COMBAT"], ["TREASURE"]. */
    public List<String> types = new ArrayList<>();
    /** Human-readable author note. */
    public String description = "";
    /** WorldEdit schematic file name (sibling of this metadata, e.g. "stairs_room.schem"). */
    public String schematic = "structure.schem";
    /** Default facing of the authored schematic. Rotation is picked randomly among allowed rotations. */
    public Direction facing = Direction.NORTH;
    /** Allowed clockwise rotations (0..3). Default: all four. */
    public List<Integer> allowedRotations = List.of(0, 1, 2, 3);

    /** Bound cuboids that combine into the playable room shape (>=1). */
    public List<RoomBounds> bounds = new ArrayList<>();
    /** Regions where enemies may spawn. */
    public List<SpawnFloor> spawnFloors = new ArrayList<>();
    /** Gameplay markers (player spawn, shopkeeper, loot, ...). */
    public List<RoomMarker> markers = new ArrayList<>();

    /** Logical height of the room (blocks). */
    public int roomHeight;
    /** Height (blocks) of the entry connection. */
    public int entryHeight = 3;
    /** Height (blocks) of the exit connection. */
    public int exitHeight = 3;

    public RoomBounds total() {
        RoomBounds t = null;
        for (RoomBounds b : bounds) {
            t = (t == null) ? new RoomBounds(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ) : t.union(b);
        }
        return t == null ? new RoomBounds() : t;
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

    public boolean serves(RoomType type) {
        for (String t : types) if (t.equalsIgnoreCase(type.name())) return true;
        return false;
    }

    public boolean allowsRotation(int steps) {
        return allowedRotations.contains(steps);
    }
}