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

    /** The planned door levels for a legacy room, or the first direction the fixed line cannot reach. */
    public record DoorLanePlan(StructureDefinition.DoorSpec[] doors, int failedDirection, String reason) {
        public boolean ok() {
            return failedDirection < 0;
        }

        public String failedDirectionName() {
            return failedDirection < 0 ? "" : new String[]{"north", "east", "south", "west"}[failedDirection];
        }
    }

    /**
     * Plan the four door levels for a legacy room at the generator's fixed corridor line. The lane
     * is the three cells centred on {@code laneX} (north/south) or {@code laneZ} (east/west); the
     * cell immediately inside each wall must be standable (a solid floor with two blocks of air)
     * and must lead somewhere, because the floor graph may open any direction. A room that fails
     * any direction is unusable: opening a lane into solid rock or a one-block closet is worse than
     * falling back to a procedural room.
     *
     * <p>{@code bounds}, {@code preferredFloorY}, {@code laneX} and {@code laneZ} are
     * structure-relative (clipboard minimum at {@code 0,0,0}). The lanes sit at the fixed corridor
     * line in the padded frame, which is {@code PERP_CENTER - pad} in this room's own frame.
     * {@code preferredFloorY[d]} is the legacy door's authored floor, or negative when the old room
     * had no connector on that side.
     */
    public static DoorLanePlan planFixedLineDoors(RoomBounds bounds, BlockLookup lookup,
                                                   int[] preferredFloorY, int laneX, int laneZ) {
        if (bounds == null || lookup == null) {
            return new DoorLanePlan(null, 0, "no room bounds or block data");
        }
        String[] names = {"north", "east", "south", "west"};
        StructureDefinition.DoorSpec[] doors = new StructureDefinition.DoorSpec[4];
        for (int d = 0; d < 4; d++) {
            int[] counts = new int[bounds.height()];
            int lane = d == 0 || d == 2 ? laneX : laneZ;
            for (int off = -1; off <= 1; off++) {
                int along = lane + off;
                int x;
                int z;
                if (d == 0) {
                    x = along;
                    z = bounds.minZ + 1;
                } else if (d == 2) {
                    x = along;
                    z = bounds.maxZ - 1;
                } else if (d == 1) {
                    x = bounds.maxX - 1;
                    z = along;
                } else {
                    x = bounds.minX + 1;
                    z = along;
                }
                if (!isInterior(bounds, x, z)) continue;
                for (int y = bounds.minY; y + 2 < bounds.maxY; y++) {
                    if (isStandable(lookup, x, y, z)
                            && hasWalkableNeighbour(lookup, bounds, x, y, z)) {
                        counts[y - bounds.minY]++;
                    }
                }
            }
            int preferred = preferredFloorY != null && d < preferredFloorY.length
                    ? preferredFloorY[d] : Integer.MIN_VALUE;
            int chosen = -1;
            int bestCount = 0;
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] == 0) continue;
                if (bounds.minY + i == preferred) {
                    chosen = i;
                    break;
                }
                if (chosen < 0 || counts[i] > bestCount) {
                    chosen = i;
                    bestCount = counts[i];
                }
            }
            if (chosen < 0) {
                return new DoorLanePlan(null, d, "no walkable interior behind the "
                        + names[d] + " wall on the fixed corridor line");
            }
            int floorY = bounds.minY + chosen;
            int air = Math.min(3, bounds.maxY - floorY - 1);
            if (air < 2) {
                return new DoorLanePlan(null, d,
                        "only " + air + " block(s) of air above the " + names[d] + " door lane");
            }
            doors[d] = new StructureDefinition.DoorSpec(floorY, air);
        }
        return new DoorLanePlan(doors, -1, "");
    }

    private static boolean isInterior(RoomBounds b, int x, int z) {
        return x > b.minX && x < b.maxX && z > b.minZ && z < b.maxZ;
    }

    private static boolean isStandable(BlockLookup lookup, int x, int y, int z) {
        return lookup.isSolid(x, y, z)
                && !lookup.isSolid(x, y + 1, z)
                && !lookup.isSolid(x, y + 2, z);
    }

    private static boolean hasWalkableNeighbour(BlockLookup lookup, RoomBounds b,
                                                int x, int y, int z) {
        return (x > b.minX && isStandable(lookup, x - 1, y, z))
                || (x < b.maxX && isStandable(lookup, x + 1, y, z))
                || (z > b.minZ && isStandable(lookup, x, y, z - 1))
                || (z < b.maxZ && isStandable(lookup, x, y, z + 1));
    }

    /**
     * Absolute cells covered by a legacy connector's wall aperture. Legacy JSON stores the centre
     * of the opening along the wall (a width of 3 at z=2 covers z=1..3) and its bottom Y.
     */
    public static List<int[]> centeredApertureCells(int direction, int x, int y, int z,
                                                    int width, int height) {
        List<int[]> cells = new ArrayList<>();
        int start = -width / 2;
        for (int along = start; along < start + width; along++) {
            for (int up = 0; up < height; up++) {
                if (direction == 1 || direction == 3) {
                    cells.add(new int[]{x, y + up, z + along});
                } else if (direction == 0 || direction == 2) {
                    cells.add(new int[]{x + along, y + up, z});
                }
            }
        }
        return cells;
    }
}
