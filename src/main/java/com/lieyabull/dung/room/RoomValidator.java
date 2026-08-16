package com.lieyabull.dung.room;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates a {@link RoomTemplate} before it may become a production template. Checks bounds,
 * block containment, interior connectivity (via a walkable-air flood fill - the guard against
 * disconnected geometry / softlocks), connections (direction, opening carved, inward clearance),
 * spawn floors (containment, solid floor, headroom / mob clearance), player spawn, shopkeeper,
 * loot points, hazards, overlaps, and room containment. Failures are reported; the validator never
 * silently "falls back" to unsafe coordinates.
 */
public final class RoomValidator {

    private RoomValidator() {}

    public static final class Result {
        public final List<RoomValidationIssue> issues;
        public boolean valid;

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

    /** Validate a template in isolation (per-template rules). Marks the template.validated flag. */
    public static Result validate(RoomTemplate tpl) {
        List<RoomValidationIssue> issues = new ArrayList<>();

        // --- identity ---
        if (tpl.id == null || tpl.id.isEmpty()) issues.add(RoomValidationIssue.error("ID", "template has no id", null));
        if (tpl.types == null || tpl.types.isEmpty()) {
            issues.add(RoomValidationIssue.error("TYPE", "template declares no room types", null));
        } else {
            for (String t : tpl.types) {
                try {
                    com.lieyabull.dung.dungeon.RoomType.valueOf(t.toUpperCase());
                } catch (IllegalArgumentException e) {
                    issues.add(RoomValidationIssue.error("TYPE", "unknown room type '" + t + "'", null));
                }
            }
        }

        // --- bounds ---
        if (tpl.bounds == null || tpl.bounds.isEmpty()) {
            issues.add(RoomValidationIssue.error("BOUND", "template has no room-bound region", null));
        } else {
            RoomBounds total = tpl.total();
            for (int i = 0; i < tpl.bounds.size(); i++) {
                RoomBounds b = tpl.bounds.get(i);
                if (b.minX > b.maxX || b.minY > b.maxY || b.minZ > b.maxZ)
                    issues.add(RoomValidationIssue.error("BOUND", "bound " + i + " is inverted (min>max)", new int[]{b.minX, b.minY, b.minZ}));
                if (b.width() <= 0 || b.height() <= 0 || b.depth() <= 0)
                    issues.add(RoomValidationIssue.error("BOUND", "bound " + i + " is degenerate", new int[]{b.minX, b.minY, b.minZ}));
                if (b.height() > 30)
                    issues.add(RoomValidationIssue.warn("BOUND", "bound " + i + " is very tall (" + b.height() + " blocks)", new int[]{b.minX, b.minY, b.minZ}));
            }
            // no duplicated bounds
            for (int i = 0; i < tpl.bounds.size(); i++) {
                for (int j = i + 1; j < tpl.bounds.size(); j++) {
                    if (sameBounds(tpl.bounds.get(i), tpl.bounds.get(j)))
                        issues.add(RoomValidationIssue.error("BOUND", "duplicate bound region " + i + " and " + j, null));
                }
            }
            // a room should not be empty of walkable air
            if (!hasWalkableInterior(tpl, issues)) {
                issues.add(RoomValidationIssue.error("GEOMETRY", "room interior has no walkable air cells (fully solid or no bounds)", null));
            }
        }

        // --- block containment ---
        if (tpl.blocks != null) {
            for (RoomBlock b : tpl.blocks) {
                if (!tpl.inAnyBound(b.x, b.y, b.z))
                    issues.add(RoomValidationIssue.error("CONTAIN", "block " + b.b + " at " + b.x + "," + b.y + "," + b.z + " lies outside every bound region", new int[]{b.x, b.y, b.z}));
            }
        }

        // --- connections ---
        validateConnections(tpl, issues);

        // --- spawn floors ---
        validateSpawnFloors(tpl, issues);

        // --- markers ---
        validateMarkers(tpl, issues);

        boolean valid = !hasError(issues);
        tpl.validated = valid;
        return new Result(issues, valid);
    }

    private static void validateConnections(RoomTemplate tpl, List<RoomValidationIssue> issues) {
        if (tpl.connectors == null || tpl.connectors.isEmpty()) {
            // A room with no connections is allowed only if it is not reachable (e.g. a pure secret
            // entered by bombing). Otherwise it is an unreachable dead-end - warn.
            boolean secret = tpl.types.contains("SECRET");
            if (!secret) issues.add(RoomValidationIssue.error("CONN", "template has no connections (unreachable except via SECRET)", null));
            return;
        }
        Set<String> seen = new HashSet<>();
        for (RoomConnector c : tpl.connectors) {
            if (c.direction == null) { issues.add(RoomValidationIssue.error("CONN", "connection has no direction", null)); continue; }
            if (c.width < 1) issues.add(RoomValidationIssue.error("CONN", "connection width must be >=1 (got " + c.width + ")", at(c)));
            if (c.height < 1) issues.add(RoomValidationIssue.error("CONN", "connection height must be >=1 (got " + c.height + ")", at(c)));
            if (c.clearance < 0) issues.add(RoomValidationIssue.error("CONN", "connection clearance must be >=0", at(c)));
            if (c.floorY < tpl.total().minY - 8 || c.floorY > tpl.total().maxY + 8)
                issues.add(RoomValidationIssue.error("CONN", "connection floorY " + c.floorY + " is outside the room height", at(c)));

            if (!tpl.inAnyBound(c.x, c.y, c.z)) {
                issues.add(RoomValidationIssue.error("CONN", "connection anchor outside every bound region", at(c)));
            }
            // opening must be carved (air) and clear inward
            if (!isAirAt(tpl, c.x, c.y, c.z)) {
                issues.add(RoomValidationIssue.error("CONN", "connection anchor is solid (doorway must be carved open)", at(c)));
            }
            // inward clearance: the passage must be air for `height` tall and `width` wide
            int[] inward = inwardOffset(c.direction);
            int cx = c.x + inward[0], cz = c.z + inward[1];
            for (int w = 0; w < c.width; w++) {
                int[] perp = perpOffset(c.direction, w - (c.width - 1) / 2);
                for (int h = 0; h < c.height; h++) {
                    int yy = c.y - c.height / 2 + h;
                    if (!isAirAt(tpl, cx + perp[0], yy, cz + perp[1])) {
                        issues.add(RoomValidationIssue.error("CONN", "connection passage is blocked inward (width x height opening must be clear)", at(c)));
                        w = c.width; break;
                    }
                }
            }
            // duplicate facing connections on same axis side
            String key = c.direction.name();
            if (!seen.add(key)) issues.add(RoomValidationIssue.warn("CONN", "multiple connections share direction " + c.direction, at(c)));
        }
    }

    private static void validateSpawnFloors(RoomTemplate tpl, List<RoomValidationIssue> issues) {
        RoomBounds total = tpl.total();
        List<SpawnFloor> sfs = tpl.spawnFloors == null ? new ArrayList<>() : tpl.spawnFloors;
        if (sfs.isEmpty()) {
            issues.add(RoomValidationIssue.error("SPAWN", "template has no spawn floor (enemies cannot be placed)", null));
            return;
        }
        for (int i = 0; i < sfs.size(); i++) {
            SpawnFloor s = sfs.get(i);
            if (!inside(total, s)) {
                issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " extends outside room bounds", at(s.minX, s.minY, s.minZ)));
            }
            // must lie within a bound region (be part of the playable interior), not floating outside
            boolean inBound = false;
            for (RoomBounds b : tpl.bounds) if (intersects(b, s)) { inBound = true; break; }
            if (!inBound) issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " is not inside any bound region", at(s.minX, s.minY, s.minZ)));
            // solid floor below (ground to stand on)
            if (!hasSolidFloorBelow(tpl, s)) {
                issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " has no solid floor below it", at(s.minX, s.minY, s.minZ)));
            }
            // mob clearance: at least 2 air blocks of headroom above the spawn level
            if (!hasHeadroom(tpl, s, 2)) {
                issues.add(RoomValidationIssue.error("SPAWN", "spawn floor " + i + " lacks " + 2 + "+ blocks of air headroom (mob clearance)", at(s.minX, s.minY, s.minZ)));
            }
        }
    }

    private static void validateMarkers(RoomTemplate tpl, List<RoomValidationIssue> issues) {
        List<RoomMarker> ms = tpl.markers == null ? new ArrayList<>() : tpl.markers;
        int playerSpawns = 0;
        boolean isSecret = tpl.types.contains("SECRET");
        for (RoomMarker m : ms) {
            if (m.type == null) { issues.add(RoomValidationIssue.error("MARKER", "marker has no type", at(m.x, m.y, m.z))); continue; }
            if (!tpl.inAnyBound(m.x, m.y, m.z)) {
                issues.add(RoomValidationIssue.error("MARKER", m.type + " marker at " + m.x + "," + m.y + "," + m.z + " is outside every bound region", at(m.x, m.y, m.z)));
            }
            switch (m.type) {
                case PLAYER_SPAWN:
                    playerSpawns++;
                    if (isAirAt(tpl, m.x, m.y, m.z)) {
                        if (!hasSolidFloorBelowAt(tpl, m.x, m.y, m.z))
                            issues.add(RoomValidationIssue.error("SPAWN", "player spawn has no solid floor below", at(m.x, m.y, m.z)));
                        if (!hasAirAbove(tpl, m.x, m.y, m.z, 2))
                            issues.add(RoomValidationIssue.error("SPAWN", "player spawn lacks headroom (2+ air above)", at(m.x, m.y, m.z)));
                    } else {
                        issues.add(RoomValidationIssue.error("SPAWN", "player spawn is inside a solid block", at(m.x, m.y, m.z)));
                    }
                    break;
                case SHOPKEEPER:
                    if (isSolidAt(tpl, m.x, m.y, m.z))
                        issues.add(RoomValidationIssue.error("SHOP", "shopkeeper marker is inside a solid block", at(m.x, m.y, m.z)));
                    break;
                case LOOT:
                    if (isSolidAt(tpl, m.x, m.y, m.z))
                        issues.add(RoomValidationIssue.error("LOOT", "loot point is inside a solid block", at(m.x, m.y, m.z)));
                    break;
                case HAZARD:
                case MECHANIC:
                case SPECIAL:
                    if (isSolidAt(tpl, m.x, m.y, m.z))
                        issues.add(RoomValidationIssue.warn("MARKER", m.type + " marker inside a solid block", at(m.x, m.y, m.z)));
                    break;
            }
        }
        // player spawn is required for runnable (non-secret) rooms
        if (playerSpawns == 0 && !isSecret) {
            issues.add(RoomValidationIssue.error("SPAWN", "no player spawn marker defined (required for non-SECRET rooms)", null));
        } else if (playerSpawns > 1) {
            issues.add(RoomValidationIssue.error("SPAWN", "multiple player spawn markers (" + playerSpawns + ") - exactly one required", null));
        }
        // shop rooms need a shopkeeper
        if (tpl.types.contains("SHOP")) {
            boolean hasShop = false;
            for (RoomMarker m : ms) if (m.type == RoomMarkerType.SHOPKEEPER) { hasShop = true; break; }
            if (!hasShop) issues.add(RoomValidationIssue.error("SHOP", "SHOP room has no SHOPKEEPER marker", null));
        }
    }

    // ================= solid / air helpers =================

    /** A template-relative cell is solid if a stored block is present and its material occludes. */
    public static boolean isSolidAt(RoomTemplate tpl, int x, int y, int z) {
        // Blocks are stored sorted by (x,z,y); later entries overwrite earlier ones (mirrors
        // RoomInstantiator). Last-wins so an explicit AIR carve over a recorded wall reads as air.
        boolean solid = false;
        for (RoomBlock b : tpl.blocks) {
            if (b.x == x && b.y == y && b.z == z) solid = isSolidMaterial(b.b);
        }
        return solid;
    }

    public static boolean isAirAt(RoomTemplate tpl, int x, int y, int z) {
        return !isSolidAt(tpl, x, y, z);
    }

    public static boolean isSolidMaterial(String blockDataStr) {
        // Registry-free solidity classification (runs headless in tests AND in-game). We cannot
        // call Material.isOccluding() here - it needs a live Paper RegistryAccess. The editor
        // records every cell (including AIR) explicitly, so a doorway carved open is recorded as
        // "minecraft:air"; we only need to tell solid occluders from transparent/non-blocking
        // blocks. Anything that is not known-transparent is treated conservatively as solid.
        try {
            String name = blockDataStr;
            int bracket = name.indexOf('[');
            if (bracket >= 0) name = name.substring(0, bracket);
            int colon = name.indexOf(':');
            if (colon >= 0) name = name.substring(colon + 1);
            name = name.toUpperCase(java.util.Locale.ROOT);
            if (name.isEmpty() || name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR"))
                return false;
            switch (name) {
                case "GLASS": case "GLASS_PANE": case "GLOWSTONE": case "SEA_LANTERN":
                case "LANTERN": case "TORCH": case "SOUL_TORCH": case "REDSTONE_TORCH":
                case "SHROOMLIGHT": case "LIGHT": case "END_ROD": case "STRUCTURE_VOID":
                    return false;
                default:
                    return true; // conservatively solid
            }
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hasSolidFloorBelowAt(RoomTemplate tpl, int x, int y, int z) {
        return isSolidAt(tpl, x, y - 1, z);
    }

    private static boolean hasSolidFloorBelow(RoomTemplate tpl, RoomBounds s) {
        // floor must be solid somewhere under the spawn floor's footprint
        for (int x = s.minX; x <= s.maxX; x++)
            for (int z = s.minZ; z <= s.maxZ; z++)
                if (isSolidAt(tpl, x, s.minY - 1, z)) return true;
        return false;
    }

    private static boolean hasAirAbove(RoomTemplate tpl, int x, int y, int z, int n) {
        for (int k = 1; k <= n; k++) if (isSolidAt(tpl, x, y + k, z)) return false;
        return true;
    }

    private static boolean hasHeadroom(RoomTemplate tpl, RoomBounds s, int n) {
        for (int x = s.minX; x <= s.maxX; x++)
            for (int z = s.minZ; z <= s.maxZ; z++)
                for (int k = 1; k <= n; k++)
                    if (isSolidAt(tpl, x, s.minY + k, z)) return false;
        return true;
    }

    // ================= walkable connectivity flood fill =================

    /** True if the room has at least one walkable interior air cell. */
    private static boolean hasWalkableInterior(RoomTemplate tpl, List<RoomValidationIssue> issues) {
        int[] start = findWalkableStart(tpl);
        if (start == null) return false;
        return floodFillReachable(tpl, start, issues);
    }

    private static int[] findWalkableStart(RoomTemplate tpl) {
        // prefer player spawn; else first walkable cell in the first bound region
        for (RoomMarker m : tpl.markers) if (m.type == RoomMarkerType.PLAYER_SPAWN && isAirAt(tpl, m.x, m.y, m.z)) return new int[]{m.x, m.y, m.z};
        if (tpl.bounds.isEmpty()) return null;
        RoomBounds b = tpl.bounds.get(0);
        for (int y = b.minY; y <= b.maxY; y++)
            for (int x = b.minX; x <= b.maxX; x++)
                for (int z = b.minZ; z <= b.maxZ; z++)
                    if (isAirAt(tpl, x, y, z)) return new int[]{x, y, z};
        return null;
    }

    /**
     * Flood fill over walkable air cells within the bound regions starting at `start`, and ensure
     * every walkable air cell is reachable. Any unreachable pocket = disconnected geometry.
     */
    private static boolean floodFillReachable(RoomTemplate tpl, int[] start, List<RoomValidationIssue> issues) {
        int[] DX = {0, 1, 0, -1, 0, 0};
        int[] DY = {0, 0, 0, 0, 1, -1};
        int[] DZ = {1, 0, -1, 0, 0, 0};
        RoomBounds total = tpl.total();
        int w = total.maxX - total.minX + 1, h = total.maxY - total.minY + 1, d = total.maxZ - total.minZ + 1;
        boolean[][][] visited = new boolean[w][h][d];
        long totalWalkable = 0;
        for (RoomBounds b : tpl.bounds)
            for (int x = b.minX; x <= b.maxX; x++)
                for (int y = b.minY; y <= b.maxY; y++)
                    for (int z = b.minZ; z <= b.maxZ; z++)
                        if (isAirAt(tpl, x, y, z)) totalWalkable++;

        ArrayDeque<int[]> q = new ArrayDeque<>();
        int sx = start[0] - total.minX, sy = start[1] - total.minY, sz = start[2] - total.minZ;
        visited[sx][sy][sz] = true;
        q.add(start);
        long reached = 0;
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            reached++;
            for (int i = 0; i < 6; i++) {
                int nx = cur[0] + DX[i], ny = cur[1] + DY[i], nz = cur[2] + DZ[i];
                if (!tpl.inAnyBound(nx, ny, nz)) continue;
                if (!isAirAt(tpl, nx, ny, nz)) continue;
                int rx = nx - total.minX, ry = ny - total.minY, rz = nz - total.minZ;
                if (visited[rx][ry][rz]) continue;
                visited[rx][ry][rz] = true;
                q.add(new int[]{nx, ny, nz});
            }
        }
        if (reached < totalWalkable) {
            issues.add(RoomValidationIssue.error("GEOMETRY", "room interior is disconnected: " + reached + "/" + totalWalkable
                    + " walkable cells reachable from the player spawn", start));
            return true; // interior exists but is disconnected
        }
        return true;
    }

    // ================= geometry helpers =================

    private static int[] inwardOffset(Direction d) {
        switch (d) {
            case NORTH: return new int[]{0, -1};
            case SOUTH: return new int[]{0, 1};
            case EAST: return new int[]{1, 0};
            case WEST: return new int[]{-1, 0};
            case UP: return new int[]{0, 0};
            case DOWN: return new int[]{0, 0};
            default: return new int[]{0, 0};
        }
    }

    private static int[] perpOffset(Direction d, int off) {
        switch (d) {
            case NORTH: case SOUTH: return new int[]{off, 0};
            case EAST: case WEST: return new int[]{0, off};
            default: return new int[]{off, 0};
        }
    }

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

    private static int[] at(RoomConnector c) { return new int[]{c.x, c.y, c.z}; }
    private static int[] at(int x, int y, int z) { return new int[]{x, y, z}; }
}