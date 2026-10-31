package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between absolute clipboard/world coordinates and Dung's structure-relative metadata.
 * <p>
 * WorldEdit reports the copied region in world coordinates, while every structure definition
 * (bounds, spawn floors, markers) is relative to the clipboard minimum corner. Keeping this
 * conversion in one place prevents authored rooms from being offset, rejected by validation, or
 * pasted at the wrong location.
 */
public final class StructureAuthoring {

    private StructureAuthoring() {}

    /** The absolute clipboard extent and the relative frame it maps to. */
    public record ClipboardFrame(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public ClipboardFrame {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("clipboard maximum is below minimum");
            }
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int height() {
            return maxY - minY + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public int relativeX(int x) {
            return x - minX;
        }

        public int relativeY(int y) {
            return y - minY;
        }

        public int relativeZ(int z) {
            return z - minZ;
        }

        public int absoluteX(int x) {
            return x + minX;
        }

        public int absoluteY(int y) {
            return y + minY;
        }

        public int absoluteZ(int z) {
            return z + minZ;
        }

        /** The full 0-based footprint of the copied region. */
        public RoomBounds bounds() {
            return new RoomBounds(0, 0, 0, width() - 1, height() - 1, depth() - 1);
        }

        /** Player/mob feet level for the common case where the clipboard minimum is the floor. */
        public int floorY() {
            return 1;
        }

        public SpawnFloor defaultSpawnFloor() {
            int minX = width() > 2 ? 1 : 0;
            int minZ = depth() > 2 ? 1 : 0;
            return new SpawnFloor(minX, floorY(), minZ,
                    Math.max(minX, width() - 2), floorY(), Math.max(minZ, depth() - 2));
        }

        public RoomMarker defaultPlayerSpawn() {
            return new RoomMarker(RoomMarkerType.PLAYER_SPAWN,
                    width() / 2, floorY(), depth() / 2, "start");
        }
    }

    public static ClipboardFrame frame(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new ClipboardFrame(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static RoomBounds relative(RoomBounds absolute, ClipboardFrame frame) {
        return new RoomBounds(
                frame.relativeX(absolute.minX), frame.relativeY(absolute.minY), frame.relativeZ(absolute.minZ),
                frame.relativeX(absolute.maxX), frame.relativeY(absolute.maxY), frame.relativeZ(absolute.maxZ));
    }

    public static SpawnFloor relative(SpawnFloor absolute, ClipboardFrame frame) {
        return new SpawnFloor(
                frame.relativeX(absolute.minX), frame.relativeY(absolute.minY), frame.relativeZ(absolute.minZ),
                frame.relativeX(absolute.maxX), frame.relativeY(absolute.maxY), frame.relativeZ(absolute.maxZ));
    }

    public static RoomMarker relative(RoomMarker absolute, ClipboardFrame frame) {
        RoomMarker out = new RoomMarker(absolute.type,
                frame.relativeX(absolute.x), frame.relativeY(absolute.y), frame.relativeZ(absolute.z),
                absolute.name);
        if (absolute.region != null) out.region = relative(absolute.region, frame);
        return out;
    }

    /**
     * WorldEdit's paste applies the rotation to {@code p - origin} before adding its paste anchor.
     * To land the structure-relative min corner on {@code (ox, oy, oz)}, the anchor must therefore
     * be {@code target - rotate(min - origin)} — not {@code target - rotate(min) + origin}.
     */
    public static int[] pasteAnchor(int ox, int oy, int oz,
                                    int minX, int minY, int minZ,
                                    int originX, int originY, int originZ,
                                    int rotationSteps) {
        int[] rotatedMin = rotatePoint(minX - originX, minZ - originZ, rotationSteps);
        return new int[]{
                ox - rotatedMin[0],
                oy - minY + originY,
                oz - rotatedMin[1]
        };
    }

    /** Clockwise 90-degree turns around the clipboard origin, matching WorldEdit's rotateY mapping. */
    public static int[] rotatePoint(int x, int z, int steps) {
        return switch (Math.floorMod(steps, 4)) {
            case 1 -> new int[]{z, -x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /**
     * True when a definition still uses absolute clipboard coordinates. Legacy {@code /dung room gen}
     * wrote one bound covering the whole clipboard with its absolute min/max; only that exact shape is
     * migrated, so hand-authored relative files are untouched.
     */
    public static boolean looksLegacyAbsolute(StructureDefinition def, ClipboardFrame frame) {
        if (def == null || def.bounds == null || def.bounds.isEmpty()) return false;
        if (frame.minX() == 0 && frame.minY() == 0 && frame.minZ() == 0) return false;
        RoomBounds total = def.total();
        return total.minX == frame.minX() && total.minY == frame.minY() && total.minZ == frame.minZ()
                && total.width() == frame.width()
                && total.height() == frame.height()
                && total.depth() == frame.depth();
    }

    /**
     * Convert a legacy absolute-coordinate definition in place. Returns true when anything changed.
     * The caller should re-save the sidecar so the conversion is durable.
     */
    public static boolean migrateLegacy(StructureDefinition def, ClipboardFrame frame) {
        if (!looksLegacyAbsolute(def, frame)) return false;
        List<RoomBounds> bounds = new ArrayList<>(def.bounds.size());
        for (RoomBounds b : def.bounds) bounds.add(relative(b, frame));
        def.bounds = bounds;

        List<SpawnFloor> floors = new ArrayList<>(def.spawnFloors.size());
        for (SpawnFloor f : def.spawnFloors) floors.add(relative(f, frame));
        def.spawnFloors = floors;

        List<RoomMarker> markers = new ArrayList<>(def.markers.size());
        for (RoomMarker m : def.markers) markers.add(relative(m, frame));
        def.markers = markers;

        if (def.doorsConfigured) {
            for (int d = 0; d < 4; d++) def.door(d).floorY = frame.relativeY(def.door(d).floorY);
        }
        return true;
    }

    /**
     * Best-effort detection of authored door openings on the four outer wall faces. The generator
     * keeps the corridor on a fixed perpendicular line, so only the three cells around
     * {@code perpCenter} are inspected. A vertical air run of at least two blocks becomes a door;
     * its floor is the block below the run.
     */
    public static StructureDefinition.DoorSpec[] detectDoors(BlockLookup lookup,
                                                               RoomBounds bounds,
                                                               int perpCenter) {
        StructureDefinition.DoorSpec[] out = new StructureDefinition.DoorSpec[4];
        for (int d = 0; d < 4; d++) out[d] = new StructureDefinition.DoorSpec();
        if (lookup == null || bounds == null) return out;

        for (int d = 0; d < 4; d++) {
            int runStart = -1;
            int runEnd = -1;
            int bestStart = -1;
            int bestEnd = -1;
            int from = Math.max(bounds.minY, 0);
            for (int y = from; y <= bounds.maxY; y++) {
                boolean air = true;
                for (int along = perpCenter - 1; along <= perpCenter + 1; along++) {
                    int x;
                    int z;
                    if (d == 0 || d == 2) {
                        if (along < bounds.minX || along > bounds.maxX) { air = false; break; }
                        x = along;
                        z = d == 0 ? bounds.minZ : bounds.maxZ;
                    } else {
                        if (along < bounds.minZ || along > bounds.maxZ) { air = false; break; }
                        z = along;
                        x = d == 1 ? bounds.maxX : bounds.minX;
                    }
                    if (lookup.isSolid(x, y, z)) { air = false; break; }
                }
                if (air) {
                    if (runStart < 0) runStart = y;
                    runEnd = y;
                } else if (runStart >= 0) {
                    if (runEnd - runStart + 1 > 1
                            && (bestStart < 0 || runEnd - runStart > bestEnd - bestStart)) {
                        bestStart = runStart;
                        bestEnd = runEnd;
                    }
                    runStart = -1;
                    runEnd = -1;
                }
            }
            if (runStart >= 0 && runEnd - runStart + 1 > 1
                    && (bestStart < 0 || runEnd - runStart > bestEnd - bestStart)) {
                bestStart = runStart;
                bestEnd = runEnd;
            }
            if (bestStart >= 0) {
                int floorY = bestStart > 0 ? bestStart - 1 : 0;
                out[d] = new StructureDefinition.DoorSpec(floorY, bestEnd - bestStart + 1);
            }
        }
        return out;
    }
}
