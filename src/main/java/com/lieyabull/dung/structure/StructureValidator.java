package com.lieyabull.dung.structure;

import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.RoomValidationIssue;
import com.lieyabull.dung.room.SpawnFloor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link StructureDefinition} before it may be registered into the structure library.
 * Checks the metadata (id, types, schematic, bounds, spawn floors, markers, rotations) and, when a
 * {@link BlockLookup} is supplied, the physical build (solid spawn floors, player-spawn solidity).
 * Failures are reported; an invalid structure is never used by the generator.
 */
public final class StructureValidator {

    private StructureValidator() {}

    public static final class Result {
        public final List<RoomValidationIssue> issues;
        public final boolean valid;

        Result(List<RoomValidationIssue> issues, boolean valid) {
            this.issues = issues;
            this.valid = valid;
        }

        public boolean hasErrors() {
            for (RoomValidationIssue i : issues) if (i.level == RoomValidationIssue.Level.ERROR) return true;
            return false;
        }

        public int errorCount() {
            int n = 0;
            for (RoomValidationIssue i : issues) if (i.level == RoomValidationIssue.Level.ERROR) n++;
            return n;
        }
    }

    /** Metadata-only validation (no physical block check). */
    public static Result validate(StructureDefinition s) {
        return validate(s, null);
    }

    /** Full validation including physical block checks via {@code lookup} (may be null to skip them). */
    public static Result validate(StructureDefinition s, BlockLookup lookup) {
        List<RoomValidationIssue> issues = new ArrayList<>();

        // --- identity ---
        if (s.id == null || s.id.isEmpty()) issues.add(RoomValidationIssue.error("ID", "structure has no id", null));
        if (s.types == null || s.types.isEmpty()) {
            issues.add(RoomValidationIssue.error("TYPE", "structure declares no room types", null));
        } else {
            for (String t : s.types) {
                try {
                    RoomType.valueOf(t.toUpperCase());
                } catch (IllegalArgumentException e) {
                    issues.add(RoomValidationIssue.error("TYPE", "unknown room type '" + t + "'", null));
                }
            }
        }
        if (s.schematic == null || s.schematic.trim().isEmpty()) {
            issues.add(RoomValidationIssue.error("SCHEMATIC", "structure references no schematic file", null));
        }

        // --- rotations ---
        if (s.allowedRotations == null || s.allowedRotations.isEmpty()) {
            issues.add(RoomValidationIssue.error("ROTATION", "allowed-rotations is empty (structure could never rotate)", null));
        } else {
            for (int r : s.allowedRotations) {
                if (r < 0 || r > 3) issues.add(RoomValidationIssue.error("ROTATION", "invalid rotation " + r + " (must be 0..3)", null));
            }
        }

        // --- bounds ---
        if (s.bounds == null || s.bounds.isEmpty()) {
            issues.add(RoomValidationIssue.error("BOUND", "structure has no room-bound region", null));
        } else {
            for (int i = 0; i < s.bounds.size(); i++) {
                RoomBounds b = s.bounds.get(i);
                if (b.minX > b.maxX || b.minY > b.maxY || b.minZ > b.maxZ)
                    issues.add(RoomValidationIssue.error("BOUND", "bound " + i + " is inverted (min>max)", at(b.minX, b.minY, b.minZ)));
                if (b.width() <= 0 || b.height() <= 0 || b.depth() <= 0)
                    issues.add(RoomValidationIssue.error("BOUND", "bound " + i + " is degenerate", at(b.minX, b.minY, b.minZ)));
            }
            for (int i = 0; i < s.bounds.size(); i++) {
                for (int j = i + 1; j < s.bounds.size(); j++) {
                    if (sameBounds(s.bounds.get(i), s.bounds.get(j)))
                        issues.add(RoomValidationIssue.error("BOUND", "duplicate bound region " + i + " and " + j, null));
                }
            }
        }

        // --- spawn floors ---
        validateSpawnFloors(s, issues, lookup);

        // --- markers ---
        validateMarkers(s, issues, lookup);

        boolean valid = !hasError(issues);
        return new Result(issues, valid);
    }

    private static void validateSpawnFloors(StructureDefinition s, List<RoomValidationIssue> issues, BlockLookup lookup) {
        RoomBounds total = s.total();
        List<SpawnFloor> sfs = s.spawnFloors == null ? new ArrayList<>() : s.spawnFloors;
        if (sfs.isEmpty()) {
            issues.add(RoomValidationIssue.error("SPAWN", "structure has no spawn floor (enemies cannot be placed)", null));
            return;
        }
        for (int i = 0; i < sfs.size(); i++) {
            SpawnFloor f = sfs.get(i);
            if (!inside(total, f)) {
                issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " extends outside room bounds", at(f.minX, f.minY, f.minZ)));
            }
            boolean inBound = false;
            for (RoomBounds b : s.bounds) if (intersects(b, f)) { inBound = true; break; }
            if (!inBound) issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " is not inside any bound region", at(f.minX, f.minY, f.minZ)));
            if (lookup != null) {
                if (!hasSolidFloorBelow(s, f, lookup)) {
                    issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " has no solid floor below it", at(f.minX, f.minY, f.minZ)));
                }
                if (!hasHeadroom(s, f, 2, lookup)) {
                    issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " lacks 2+ blocks of air headroom (mob clearance)", at(f.minX, f.minY, f.minZ)));
                }
            }
        }
    }

    private static void validateMarkers(StructureDefinition s, List<RoomValidationIssue> issues, BlockLookup lookup) {
        List<RoomMarker> ms = s.markers == null ? new ArrayList<>() : s.markers;
        int playerSpawns = 0;
        boolean isSecret = s.types != null && s.types.contains("SECRET");
        for (RoomMarker m : ms) {
            if (m.type == null) { issues.add(RoomValidationIssue.error("MARKER", "marker has no type", at(m.x, m.y, m.z))); continue; }
            if (!s.inAnyBound(m.x, m.y, m.z)) {
                issues.add(RoomValidationIssue.error("MARKER", m.type + " marker at " + m.x + "," + m.y + "," + m.z + " is outside every bound region", at(m.x, m.y, m.z)));
            }
            switch (m.type) {
                case PLAYER_SPAWN:
                    playerSpawns++;
                    if (lookup != null && lookup.isSolid(m.x, m.y, m.z))
                        issues.add(RoomValidationIssue.error("SPAWN", "player spawn is inside a solid block", at(m.x, m.y, m.z)));
                    break;
                case SHOPKEEPER:
                case LOOT:
                case HAZARD:
                case MECHANIC:
                case SPECIAL:
                    if (lookup != null && lookup.isSolid(m.x, m.y, m.z))
                        issues.add(RoomValidationIssue.warn("MARKER", m.type + " marker inside a solid block", at(m.x, m.y, m.z)));
                    break;
            }
        }
        if (playerSpawns == 0 && !isSecret) {
            issues.add(RoomValidationIssue.error("SPAWN", "no player spawn marker defined (required for non-SECRET rooms)", null));
        } else if (playerSpawns > 1) {
            issues.add(RoomValidationIssue.error("SPAWN", "multiple player spawn markers (" + playerSpawns + ") - exactly one required", null));
        }
        if (s.types != null && s.types.contains("SHOP")) {
            boolean hasShop = false;
            for (RoomMarker m : ms) if (m.type == RoomMarkerType.SHOPKEEPER) { hasShop = true; break; }
            if (!hasShop) issues.add(RoomValidationIssue.error("SHOP", "SHOP room has no SHOPKEEPER marker", null));
        }
    }

    // ================= block helpers =================

    private static boolean hasSolidFloorBelow(StructureDefinition s, RoomBounds f, BlockLookup lookup) {
        for (int x = f.minX; x <= f.maxX; x++)
            for (int z = f.minZ; z <= f.maxZ; z++)
                if (lookup.isSolid(x, f.minY - 1, z)) return true;
        return false;
    }

    private static boolean hasHeadroom(StructureDefinition s, RoomBounds f, int n, BlockLookup lookup) {
        for (int x = f.minX; x <= f.maxX; x++)
            for (int z = f.minZ; z <= f.maxZ; z++)
                for (int k = 1; k <= n; k++)
                    if (lookup.isSolid(x, f.minY + k, z)) return false;
        return true;
    }

    // ================= geometry helpers =================

    private static boolean sameBounds(RoomBounds a, RoomBounds b) {
        return a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ
                && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    private static boolean inside(RoomBounds outer, RoomBounds inner) {
        return outer.minX <= inner.minX && outer.maxX >= inner.maxX
                && outer.minY <= inner.minY && outer.maxY >= inner.maxY
                && outer.minZ <= inner.minZ && outer.maxZ >= inner.maxZ;
    }

    private static boolean intersects(RoomBounds a, RoomBounds b) {
        return a.minX <= b.maxX && a.maxX >= b.minX
                && a.minY <= b.maxY && a.maxY >= b.minY
                && a.minZ <= b.maxZ && a.maxZ >= b.minZ;
    }

    private static boolean hasError(List<RoomValidationIssue> issues) {
        for (RoomValidationIssue i : issues) if (i.level == RoomValidationIssue.Level.ERROR) return true;
        return false;
    }

    private static int[] at(int x, int y, int z) { return new int[]{x, y, z}; }
}