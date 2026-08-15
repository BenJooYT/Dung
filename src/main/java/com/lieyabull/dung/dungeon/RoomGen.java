package com.lieyabull.dung.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Builds an enclosed, lit room in the world at a base position. Doors open only in the
 * directions the graph says. Room interior is a flat floor with a ceiling and walls.
 */
public final class RoomGen {
    public static final int SQUARE = 13; // common interior width/depth (odd so rooms stay centered)
    public static final int LONG = 17;   // elongated axis interior dimension
    public static final int WALL = 1;
    public static final int ROOM_HEIGHT = 4; // interior air blocks above the floor
    /** Fixed off-axis line (from a room's base) that EVERY door/corridor is centered on, anchored
     *  to the widest room so square + long neighbours carve on the SAME line. Any code that seals
     *  or references a doorway must use this, not the room's own geometric center. */
    public static final int PERP_CENTER = Math.max(WALL + SQUARE / 2, WALL + LONG / 2); // 9
    // base coordinate of the room (lowest northwest interior block)
    public record RoomBase(int x, int z) {}

    private RoomGen() {}

    public static RoomBase baseFor(Floor.RoomNode n, int spacing) {
        // spacing between room centers, so rooms don't share walls
        return new RoomBase(n.x * spacing, n.z * spacing);
    }

    public static void build(World w, Floor.RoomNode n, int baseY, int spacing) {
        RoomBase b = baseFor(n, spacing);
        int sx = b.x, sz = b.z;
        int wl = n.sizeW + 2 * WALL;       // footprint including walls
        int wh = n.sizeH + 2 * WALL;
        // hollow out + floor; ceiling sits one row above the tallest air block
        int ceilY = baseY + ROOM_HEIGHT + 1;
        // Boss rooms read as a dark menacing arena: blackstone floor + deepslate walls and red
        // lighting, so a player can tell they're about to enter the boss fight from a glance.
        boolean boss = n.type == RoomType.BOSS;
        Material wallMat = boss ? Material.DEEPSLATE_BRICKS : Material.STONE_BRICKS;
        Material floorMat = boss ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_ANDESITE;
        Material lightMat = boss ? Material.SHROOMLIGHT : Material.GLOWSTONE;
        for (int x = 0; x < wl; x++) {
            for (int z = 0; z < wh; z++) {
                for (int y = baseY; y <= ceilY; y++) {
                    boolean wall = x == 0 || x == wl - 1 || z == 0 || z == wh - 1;
                    boolean ceiling = y == ceilY;
                    if (wall || ceiling) {
                        w.getBlockAt(sx + x, y, sz + z).setType(wallMat);
                    } else if (y == baseY) {
                        w.getBlockAt(sx + x, y, sz + z).setType(floorMat);
                    } else {
                        w.getBlockAt(sx + x, y, sz + z).setType(Material.AIR);
                    }
                }
            }
        }
        // carve door passages: a 3-wide tunnel from this room's interior through its wall and
        // across the open corridor into the neighbor's interior. Both neighbors carve their own
        // door, so the overlapping strips merge into one continuous passage. The PERPENDICULAR
        // center of the passage is anchored to the grid cell (a FIXED offset scaled to the largest
        // room), NOT to each room's own center — otherwise a square (13) + long (17) pair carve at
        // different centers and the corridor splits into two staggered tubes.
        int cx0 = sx + WALL + n.sizeW / 2;   // interior center column (along-axis origin)
        int cz0 = sz + WALL + n.sizeH / 2;
        int perpRef = PERP_CENTER;           // fixed off-axis line for ALL rooms
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        for (int d = 0; d < 4; d++) {
            if (!n.doors[d]) continue;
            boolean horiz = d == 1 || d == 3;            // E/W runs along x, perp is z
            int axC  = horiz ? cx0 : cz0;                // along-axis origin
            int perpC = horiz ? (sz + perpRef) : (sx + perpRef); // FIXED perpendicular center
            int asg  = horiz ? DX[d] : DZ[d];            // along-axis direction sign
            for (int t = 0; t <= spacing; t++) {
                for (int off = -1; off <= 1; off++) {    // exactly 3 wide
                    int px = horiz ? (axC + asg * t) : (perpC + off);
                    int pz = horiz ? (perpC + off) : (axC + asg * t);
                    w.getBlockAt(px, baseY, pz).setType(Material.POLISHED_ANDESITE);
                    for (int y = baseY + 1; y <= baseY + ROOM_HEIGHT; y++) {
                        w.getBlockAt(px, y, pz).setType(Material.AIR);
                    }
                    // roof the corridor
                    w.getBlockAt(px, baseY + ROOM_HEIGHT + 1, pz).setType(Material.STONE_BRICKS);
                }
            }
            // Build a NARROW corridor: the gap between the two room walls is a solid stone mass
            // spanning the widest room's interior, with the 3-wide passage (already carved above)
            // cut through the middle. Sides are solid walls instead of open floor, so the corridor
            // reads as a tunnel (not a room-in-a-room) and there is no void to fall into.
            int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
            int innerWallT = WALL + half;        // this room's wall face (the doorway depth)
            int nextWallT = spacing - innerWallT; // mirror: the neighbour seals its matching half
            // guard: if spacing is ever too small the symmetric range would be empty/unsealed; keep
            // at least the doorway block sealed so the tube can't open straight into the void
            if (nextWallT <= innerWallT) nextWallT = innerWallT + 1;
            int COW = LONG / 2;                   // solid mass covers the widest room interior
            for (int t = innerWallT; t < nextWallT; t++) {
                for (int off = -COW; off <= COW; off++) {
                    if (Math.abs(off) <= 1) continue; // the 3-wide passage is already carved
                    int px = horiz ? (axC + asg * t) : (perpC + off);
                    int pz = horiz ? (perpC + off) : (axC + asg * t);
                    for (int y = baseY; y <= baseY + ROOM_HEIGHT + 1; y++) {
                        w.getBlockAt(px, y, pz).setType(Material.STONE_BRICKS);
                    }
                }
            }
            // hang a lantern from the middle of odd-length corridors
            int corridorLen = nextWallT - innerWallT;
            if (corridorLen >= 3 && corridorLen % 2 == 1) {
                int mid = innerWallT + corridorLen / 2;
                int lx = horiz ? (axC + asg * mid) : (perpC);
                int lz = horiz ? (perpC) : (axC + asg * mid);
                w.getBlockAt(lx, baseY + ROOM_HEIGHT + 1, lz).setType(Material.LANTERN);
            }
            // Boss doorway warning: a red floor tile + red overhead glow sit right at the door
            // opening so the danger reads from the corridor BEFORE the player steps inside.
            if (boss) {
                int px = horiz ? (axC + asg * innerWallT) : perpC;
                int pz = horiz ? perpC : (axC + asg * innerWallT);
                w.getBlockAt(px, baseY, pz).setType(Material.REDSTONE_BLOCK);
                w.getBlockAt(px, baseY + ROOM_HEIGHT + 1, pz).setType(Material.SHROOMLIGHT);
            }
        }
        // interior lighting: glowstone set flush with the ceiling so tall rooms still light up
        // (boss rooms use the red shroomlight for a menacing tint)
        for (int x = WALL + 1; x < wl - 1; x += 3) {
            for (int z = WALL + 1; z < wh - 1; z += 3) {
                w.getBlockAt(sx + x, baseY + ROOM_HEIGHT, sz + z).setType(lightMat);
            }
        }
    }

    /** Spawn location: exact center of the room floor, 1 above. (No spurious +0.5.) */
    public static Location center(World w, Floor.RoomNode n, int baseY, int spacing) {
        RoomBase b = baseFor(n, spacing);
        return new Location(w, b.x + WALL + n.sizeW / 2.0, baseY + 1, b.z + WALL + n.sizeH / 2.0);
    }
}