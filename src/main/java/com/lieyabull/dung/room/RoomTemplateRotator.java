package com.lieyabull.dung.room;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a rotated copy of a {@link RoomTemplate} so its connectors align with the door directions
 * required by the floor graph. Supports 0&deg;, 90&deg;, 180&deg;, and 270&deg; clockwise rotation
 * around the template's vertical center axis.
 *
 * <p>Rotation transforms every part of the template:
 * <ul>
 *   <li>Blocks &mdash; (x, z) coordinates rotated around the template center</li>
 *   <li>Connectors &mdash; direction, position, and width/height swapped for 90&deg;/270&deg;</li>
 *   <li>Bounds &mdash; min/max x and z swapped/reflected</li>
 *   <li>Markers &mdash; point positions rotated</li>
 *   <li>Spawn floors &mdash; min/max x and z rotated</li>
 * </ul>
 * Y coordinates are never modified (rotation is around the vertical axis only).
 */
public final class RoomTemplateRotator {

    private RoomTemplateRotator() {}

    /** Rotation in 90-degree clockwise steps (0, 1, 2, 3). */
    public enum Rotation {
        NONE(0),
        CW_90(1),
        CW_180(2),
        CW_270(3);

        public final int steps;

        Rotation(int steps) { this.steps = steps; }

        /** Apply this rotation to a cardinal direction index (0=N, 1=E, 2=S, 3=W). */
        public int applyToDir(int dir) {
            return (dir + steps) % 4;
        }

        /** Apply the inverse rotation to a cardinal direction index. */
        public int applyInverseToDir(int dir) {
            return (dir + (4 - steps)) % 4;
        }

        /** Rotate a Direction enum value. */
        public Direction applyToDirection(Direction d) {
            if (!d.isHorizontal()) return d;
            int newCard = applyToDir(d.card);
            return switch (newCard) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                case 3 -> Direction.WEST;
                default -> d;
            };
        }

        /** Inverse-rotate a Direction enum value (for mapping template connectors to room doors). */
        public Direction applyInverseToDirection(Direction d) {
            if (!d.isHorizontal()) return d;
            int newCard = applyInverseToDir(d.card);
            return switch (newCard) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                case 3 -> Direction.WEST;
                default -> d;
            };
        }
    }

    /**
     * Determine the rotation needed so that the template's connectors align with the room's
     * open door directions. The algorithm finds a rotation where every open door direction
     * on the room node has a matching connector on the template (after inverse-rotating the
     * connector's direction).
     *
     * @param template the original (unrotated) template
     * @param openDoors boolean[4] where index 0=N, 1=E, 2=S, 3=W indicates an open door
     * @return the required rotation, or null if no rotation makes all doors match
     */
    public static Rotation requiredRotation(RoomTemplate template, boolean[] openDoors) {
        for (Rotation rot : Rotation.values()) {
            if (matches(template, openDoors, rot)) return rot;
        }
        return null;
    }

    private static boolean matches(RoomTemplate template, boolean[] openDoors, Rotation rot) {
        for (int d = 0; d < 4; d++) {
            if (!openDoors[d]) continue;
            // The room needs a connector facing direction d.
            // After applying rotation rot, the template's connector that originally faced
            // direction `origDir` will now face direction d.
            // So we need a template connector facing `rot.applyInverseToDir(d)`.
            Direction needed = switch (rot.applyInverseToDir(d)) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                case 3 -> Direction.WEST;
                default -> throw new IllegalStateException();
            };
            if (template.connectorFacing(needed) == null) return false;
        }
        return true;
    }

    /**
     * Create a rotated copy of the template. The original template is not modified.
     *
     * @param template the original template
     * @param rot      the rotation to apply
     * @return a new RoomTemplate with all coordinates/directions rotated
     */
    public static RoomTemplate rotate(RoomTemplate template, Rotation rot) {
        if (rot == Rotation.NONE) {
            // Return a shallow copy so callers can safely modify the copy
            return copyShallow(template);
        }

        RoomBounds tot = template.total();
        // Use double-precision center so odd-width/depth templates rotate symmetrically.
        // Integer division would bias the center by 0.5 for even dimensions, causing connectors
        // on the wall face to end up outside the rotated bounds.
        double cx = (tot.minX + tot.maxX) / 2.0;  // rotation center X
        double cz = (tot.minZ + tot.maxZ) / 2.0;  // rotation center Z

        RoomTemplate out = new RoomTemplate();
        out.version = template.version;
        out.id = template.id;
        out.types = new ArrayList<>(template.types);
        out.description = template.description;
        out.validated = template.validated;

        // Rotate bounds
        out.bounds = new ArrayList<>();
        for (RoomBounds b : template.bounds) {
            out.bounds.add(rotateBounds(b, cx, cz, rot));
        }

        // Rotate connectors
        out.connectors = new ArrayList<>();
        for (RoomConnector c : template.connectors) {
            out.connectors.add(rotateConnector(c, cx, cz, rot));
        }

        // Rotate spawn floors
        out.spawnFloors = new ArrayList<>();
        for (SpawnFloor s : template.spawnFloors) {
            out.spawnFloors.add(rotateSpawnFloor(s, cx, cz, rot));
        }

        // Rotate markers
        out.markers = new ArrayList<>();
        for (RoomMarker m : template.markers) {
            out.markers.add(rotateMarker(m, cx, cz, rot));
        }

        // Rotate blocks
        out.blocks = new ArrayList<>();
        for (RoomBlock b : template.blocks) {
            int[] p = rotatePoint(b.x, b.z, cx, cz, rot);
            out.blocks.add(new RoomBlock(p[0], b.y, p[1], b.b));
        }
        // Re-sort blocks in (x, z, y) order
        out.blocks.sort((a, bb) -> {
            int c = Integer.compare(a.x, bb.x);
            if (c != 0) return c;
            c = Integer.compare(a.z, bb.z);
            if (c != 0) return c;
            return Integer.compare(a.y, bb.y);
        });

        return out;
    }

    /** Rotate a point (x, z) around center (cx, cz) by the given rotation. Returns [newX, newZ]
     *  rounded to the nearest integer (half rounds away from zero). */
    private static int[] rotatePoint(int x, int z, double cx, double cz, Rotation rot) {
        double dx = x - cx;
        double dz = z - cz;
        double nx, nz;
        switch (rot) {
            case CW_90:   // (dx, dz) -> (dz, -dx)
                nx = cx + dz;
                nz = cz - dx;
                break;
            case CW_180:  // (dx, dz) -> (-dx, -dz)
                nx = cx - dx;
                nz = cz - dz;
                break;
            case CW_270:  // (dx, dz) -> (-dz, dx)
                nx = cx - dz;
                nz = cz + dx;
                break;
            default:
                nx = x; nz = z;
        }
        return new int[]{(int) Math.round(nx), (int) Math.round(nz)};
    }

    private static RoomBounds rotateBounds(RoomBounds b, double cx, double cz, Rotation rot) {
        int[] p1 = rotatePoint(b.minX, b.minZ, cx, cz, rot);
        int[] p2 = rotatePoint(b.maxX, b.maxZ, cx, cz, rot);
        RoomBounds out = new RoomBounds();
        out.minX = Math.min(p1[0], p2[0]);
        out.minZ = Math.min(p1[1], p2[1]);
        out.maxX = Math.max(p1[0], p2[0]);
        out.maxZ = Math.max(p1[1], p2[1]);
        out.minY = b.minY;
        out.maxY = b.maxY;
        return out;
    }

    private static RoomConnector rotateConnector(RoomConnector c, double cx, double cz, Rotation rot) {
        RoomConnector out = new RoomConnector();
        out.direction = rot.applyToDirection(c.direction);
        out.type = c.type;
        int[] p = rotatePoint(c.x, c.z, cx, cz, rot);
        out.x = p[0];
        out.y = c.y;
        out.z = p[1];
        out.floorY = c.floorY;
        out.clearance = c.clearance;
        // Width (horizontal along wall face) and height (vertical) are unchanged by Y-axis rotation.
        // The passage is the same size; only its direction and position change.
        out.width = c.width;
        out.height = c.height;
        return out;
    }

    private static SpawnFloor rotateSpawnFloor(SpawnFloor s, double cx, double cz, Rotation rot) {
        int[] p1 = rotatePoint(s.minX, s.minZ, cx, cz, rot);
        int[] p2 = rotatePoint(s.maxX, s.maxZ, cx, cz, rot);
        SpawnFloor out = new SpawnFloor();
        out.minX = Math.min(p1[0], p2[0]);
        out.minZ = Math.min(p1[1], p2[1]);
        out.maxX = Math.max(p1[0], p2[0]);
        out.maxZ = Math.max(p1[1], p2[1]);
        out.minY = s.minY;
        out.maxY = s.maxY;
        return out;
    }

    private static RoomMarker rotateMarker(RoomMarker m, double cx, double cz, Rotation rot) {
        RoomMarker out = new RoomMarker();
        out.type = m.type;
        int[] p = rotatePoint(m.x, m.z, cx, cz, rot);
        out.x = p[0];
        out.y = m.y;
        out.z = p[1];
        out.name = m.name;
        if (m.region != null) {
            out.region = rotateBounds(m.region, cx, cz, rot);
        }
        return out;
    }

    /** Create a shallow copy of a template (new lists but same element references). */
    private static RoomTemplate copyShallow(RoomTemplate template) {
        RoomTemplate out = new RoomTemplate();
        out.version = template.version;
        out.id = template.id;
        out.types = new ArrayList<>(template.types);
        out.description = template.description;
        out.validated = template.validated;
        out.bounds = new ArrayList<>(template.bounds);
        out.connectors = new ArrayList<>(template.connectors);
        out.spawnFloors = new ArrayList<>(template.spawnFloors);
        out.markers = new ArrayList<>(template.markers);
        out.blocks = new ArrayList<>(template.blocks);
        return out;
    }
}