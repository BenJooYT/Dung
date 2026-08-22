package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.SpawnFloor;

import java.util.ArrayList;
import java.util.List;

/**
 * Rotates a {@link StructureDefinition}'s metadata consistently with the WorldEdit clipboard rotation
 * the generator applies to its {@code .schem}. Supports 0&deg;/90&deg;/180&deg;/270&deg; clockwise
 * rotation around the <em>clipboard origin</em> ({@code 0,0,0} in structure space) — the same pivot
 * {@link StructureWorldEdit#paste} uses via {@code AffineTransform.rotateY}. Every coordinate-carrying
 * part (bounds, spawn floors, markers) is rotated; Y is never changed (rotation is around the vertical
 * axis only). Rotating around the origin (not the structure's center) is what keeps the schematic and
 * the metadata synchronized, because WorldEdit rotates the clipboard around its own origin: a
 * structure-local block {@code (sx,sy,sz)} lands in the world at
 * {@code pasteOrigin + rotate(sx,sy,sz)}, and the metadata must predict exactly that position.
 *
 * <p>Doorways/corridors are not part of the metadata, so no connector rotation is needed here.
 *
 * <p>Pure math (no Bukkit / no WorldEdit), fully unit-testable.
 */
public final class StructureTransform {

    private StructureTransform() {}

    /** Rotation in 90-degree clockwise steps (0, 1, 2, 3). */
    public enum Rotation {
        NONE(0),
        CW_90(1),
        CW_180(2),
        CW_270(3);

        public final int steps;

        Rotation(int steps) { this.steps = steps; }

        public int applyToDir(int dir) {
            return (dir + (4 - steps)) % 4;
        }

        public int applyInverseToDir(int dir) {
            return (dir + steps) % 4;
        }

        public Direction applyToDirection(Direction d) {
            if (!d.isHorizontal()) return d;
            return switch (applyToDir(d.card)) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                default -> Direction.WEST;
            };
        }

        public Direction applyInverseToDirection(Direction d) {
            if (!d.isHorizontal()) return d;
            return switch (applyInverseToDir(d.card)) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.EAST;
                case 2 -> Direction.SOUTH;
                default -> Direction.WEST;
            };
        }

        public static Rotation bySteps(int steps) {
            for (Rotation r : values()) if (r.steps == steps) return r;
            throw new IllegalArgumentException("no rotation for steps " + steps);
        }
    }

    /** Pick a random allowed clockwise rotation (for cosmetic variety). */
    public static Rotation pickRandom(StructureDefinition s, java.util.Random rnd) {
        if (s.allowedRotations == null || s.allowedRotations.isEmpty()) return Rotation.NONE;
        int steps = s.allowedRotations.get(rnd.nextInt(s.allowedRotations.size()));
        return Rotation.bySteps(steps);
    }

    /** Return a rotated copy of the metadata. The original is never modified. */
    public static StructureDefinition rotate(StructureDefinition s, Rotation rot) {
        StructureDefinition out = new StructureDefinition();
        out.version = s.version;
        out.id = s.id;
        out.types = new ArrayList<>(s.types);
        out.description = s.description;
        out.schematic = s.schematic;
        out.facing = rot.applyToDirection(s.facing);
        out.allowedRotations = new ArrayList<>(s.allowedRotations);
        out.roomHeight = s.roomHeight;
        out.entryHeight = s.entryHeight;
        out.exitHeight = s.exitHeight;

        out.bounds = new ArrayList<>();
        for (RoomBounds b : s.bounds) out.bounds.add(rotateBounds(b, rot));

        out.spawnFloors = new ArrayList<>();
        for (SpawnFloor f : s.spawnFloors) out.spawnFloors.add(rotateSpawnFloor(f, rot));

        out.markers = new ArrayList<>();
        for (RoomMarker m : s.markers) out.markers.add(rotateMarker(m, rot));
        return out;
    }

    /** Rotate a structure-local coordinate around the clipboard origin (0,0,0). */
    private static int[] rotatePoint(int x, int z, Rotation rot) {
        switch (rot) {
            case CW_90: return new int[]{z, -x};
            case CW_180: return new int[]{-x, -z};
            case CW_270: return new int[]{-z, x};
            default: return new int[]{x, z};
        }
    }

    private static RoomBounds rotateBounds(RoomBounds b, Rotation rot) {
        int[] p1 = rotatePoint(b.minX, b.minZ, rot);
        int[] p2 = rotatePoint(b.maxX, b.maxZ, rot);
        RoomBounds out = new RoomBounds();
        out.minX = Math.min(p1[0], p2[0]);
        out.maxX = Math.max(p1[0], p2[0]);
        out.minZ = Math.min(p1[1], p2[1]);
        out.maxZ = Math.max(p1[1], p2[1]);
        out.minY = b.minY;
        out.maxY = b.maxY;
        return out;
    }

    private static SpawnFloor rotateSpawnFloor(SpawnFloor s, Rotation rot) {
        int[] p1 = rotatePoint(s.minX, s.minZ, rot);
        int[] p2 = rotatePoint(s.maxX, s.maxZ, rot);
        SpawnFloor out = new SpawnFloor();
        out.minX = Math.min(p1[0], p2[0]);
        out.maxX = Math.max(p1[0], p2[0]);
        out.minZ = Math.min(p1[1], p2[1]);
        out.maxZ = Math.max(p1[1], p2[1]);
        out.minY = s.minY;
        out.maxY = s.maxY;
        return out;
    }

    private static RoomMarker rotateMarker(RoomMarker m, Rotation rot) {
        RoomMarker out = new RoomMarker();
        out.type = m.type;
        int[] p = rotatePoint(m.x, m.z, rot);
        out.x = p[0];
        out.y = m.y;
        out.z = p[1];
        out.name = m.name;
        if (m.region != null) out.region = rotateBounds(m.region, rot);
        return out;
    }
}