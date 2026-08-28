package com.lieyabull.dung.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import static com.lieyabull.dung.dungeon.BlockBatcher.setBlock;

/**
 * Builds an enclosed, lit room in the world at a base position. Doors open only in the
 * directions the graph says. Room interior is a flat floor with a ceiling and walls.
 */
public final class RoomGen {
    public static final int SQUARE = 13; // base interior width/depth at party size 1 (odd so rooms stay centered)
    public static final int LONG = 17;   // base elongated axis interior dimension at party size 1
    public static final int WALL = 1;
    public static final int ROOM_HEIGHT = 4; // interior air blocks above the floor
    /** Boss rooms are taller to accommodate the larger Ravager entity and give an imposing feel. */
    public static final int BOSS_ROOM_HEIGHT = 7; // interior air blocks above the floor for boss rooms
    /** Fixed off-axis line (from a room's base) that EVERY door/corridor is centered on, anchored
     *  to the widest room so square + long neighbours carve on the SAME line. Any code that seals
     *  or references a doorway must use this, not the room's own geometric center. */
    public static final int PERP_CENTER = Math.max(WALL + SQUARE / 2, WALL + LONG / 2); // 9

    /** Interior dimensions grow with party tier (0 = solo, 3 = 4-player) so larger parties get more
     *  elbow room. Each tier keeps the dimensions odd to stay centered. */
    public static int squareFor(int tier) { return SQUARE + 2 * tier; }
    public static int longFor(int tier) { return LONG + 2 * tier; }

    /** Half-width of the solid corridor mass around the fixed door line. It must reach from
     *  {@link #PERP_CENTER} to both edges of the widest room interior (interior spans 1..longFor)
     *  so the corridor stays walled end-to-end and a player can't fall into void beside it. */
    public static int corridorHalfFor(int tier) {
        return Math.max(PERP_CENTER - 1, longFor(tier) - PERP_CENTER);
    }

    /** Resize a room from the base (solo) dimensions to the given party tier, keeping its shape
     *  (square / long-wide / long-deep / start). Structure rooms never call this (fixed templates). */
    public static void scaleToTier(Floor.RoomNode n, int tier) {
        int sq = squareFor(tier), ln = longFor(tier);
        if (n.sizeW == SQUARE) n.sizeW = sq; else if (n.sizeW == LONG) n.sizeW = ln;
        if (n.sizeH == SQUARE) n.sizeH = sq; else if (n.sizeH == LONG) n.sizeH = ln;
    }
    // shape constants
    public static final int SHAPE_RECTANGLE = 0;
    public static final int SHAPE_L = 1;
    public static final int SHAPE_PILLAR = 2;
    public static final int SHAPE_SPLIT = 3;
    // base coordinate of the room (lowest northwest interior block)
    public record RoomBase(int x, int z) {}

    private RoomGen() {}

    public static RoomBase baseFor(Floor.RoomNode n, int spacing) {
        return baseFor(n, spacing, 0, 0);
    }

    public static RoomBase baseFor(Floor.RoomNode n, int spacing, int offsetX, int offsetZ) {
        // spacing between room centers, so rooms don't share walls
        return new RoomBase(n.x * spacing + offsetX, n.z * spacing + offsetZ);
    }

    public static void build(World w, Floor.RoomNode n, int baseY, int spacing) {
        build(w, n, baseY, spacing, LONG / 2, 0, 0);
    }

    public static void build(World w, Floor.RoomNode n, int baseY, int spacing, int offsetX, int offsetZ) {
        build(w, n, baseY, spacing, LONG / 2, offsetX, offsetZ);
    }

    /** {@code corridorHalf} is the shared half-width of the corridor side walls ({@link #corridorHalfFor}),
     *  derived from the floor's party tier so larger rooms stay fully sealed. */
    public static void build(World w, Floor.RoomNode n, int baseY, int spacing, int corridorHalf, int offsetX, int offsetZ) {
        RoomBase b = baseFor(n, spacing, offsetX, offsetZ);
        int sx = b.x, sz = b.z;
        int wl = n.sizeW + 2 * WALL;       // footprint including walls
        int wh = n.sizeH + 2 * WALL;
        // hollow out + floor; ceiling sits one row above the tallest air block
        // Boss rooms are taller (BOSS_ROOM_HEIGHT) to accommodate the larger Ravager entity
        int roomHeight = n.type == RoomType.BOSS ? BOSS_ROOM_HEIGHT : ROOM_HEIGHT;
        int ceilY = baseY + roomHeight + 1;
        // Visual themes per room type
        boolean boss = n.type == RoomType.BOSS;
        Material wallMat;
        Material floorMat;
        Material lightMat;
        switch (n.type) {
            case ELITE:
                wallMat = Material.NETHER_BRICKS;
                floorMat = Material.RED_NETHER_BRICKS;
                lightMat = Material.GLOWSTONE;
                break;
            case TREASURE:
                wallMat = Material.QUARTZ_BLOCK;
                floorMat = Material.GOLD_BLOCK;
                lightMat = Material.GLOWSTONE;
                break;
            case SHOP:
                wallMat = Material.OAK_PLANKS;
                floorMat = Material.SPRUCE_PLANKS;
                lightMat = Material.GLOWSTONE;
                break;
            case SECRET:
                wallMat = Material.MOSSY_STONE_BRICKS;
                floorMat = Material.MOSSY_COBBLESTONE;
                lightMat = Material.GLOWSTONE;
                break;
            case START:
                wallMat = Material.SMOOTH_STONE;
                floorMat = Material.POLISHED_DIORITE;
                lightMat = Material.GLOWSTONE;
                break;
            case LOCKED:
                wallMat = Material.GOLD_BLOCK;
                floorMat = Material.DARK_PRISMARINE;
                lightMat = Material.GLOWSTONE;
                break;
            case UPGRADE:
                wallMat = Material.AMETHYST_BLOCK;
                floorMat = Material.PURPUR_BLOCK;
                lightMat = Material.SEA_LANTERN;
                break;
            case BOSS:
                wallMat = Material.DEEPSLATE_BRICKS;
                floorMat = Material.POLISHED_BLACKSTONE_BRICKS;
                lightMat = Material.SHROOMLIGHT;
                break;
            default: // COMBAT
                wallMat = Material.STONE_BRICKS;
                floorMat = Material.POLISHED_ANDESITE;
                lightMat = Material.GLOWSTONE;
                break;
        }
        for (int x = 0; x < wl; x++) {
            for (int z = 0; z < wh; z++) {
                for (int y = baseY; y <= ceilY; y++) {
                    boolean wall = x == 0 || x == wl - 1 || z == 0 || z == wh - 1;
                    boolean ceiling = y == ceilY;
                    if (wall || ceiling) {
                        setBlock(w, sx + x, y, sz + z, wallMat);
                    } else if (y == baseY) {
                        setBlock(w, sx + x, y, sz + z, floorMat);
                    } else {
                        setBlock(w, sx + x, y, sz + z, Material.AIR);
                    }
                }
            }
        }
        // SECRET rooms: no door passages. The destructible wall is placed on the combat room's
        // side in enterFloor() post-processing. Here we just record where the wall should go.
        boolean secret = n.type == RoomType.SECRET && n.secretParent != null;
        if (secret) {
            // Record the wall location on the secret room's outer wall facing the parent
            int wallDir = n.secretWallDir;
            boolean horiz = wallDir == 1 || wallDir == 3;
            int wallX, wallZ;
            if (horiz) {
                wallX = wallDir == 1 ? (sx + wl - 1) : sx;
                wallZ = sz + PERP_CENTER;
            } else {
                wallX = sx + PERP_CENTER;
                wallZ = wallDir == 2 ? (sz + wh - 1) : sz;
            }
            n.destructibleWallLoc = new Location(w, wallX, baseY + ROOM_HEIGHT / 2.0 + 1, wallZ);
        } else {
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
                int half = horiz ? n.sizeW / 2 : n.sizeH / 2;
                int innerWallT = WALL + half;        // this room's wall face (the doorway depth)
                int nextWallT = spacing - innerWallT; // mirror: the neighbour seals its matching half
                // guard: if spacing is ever too small the symmetric range would be empty/unsealed; keep
                // at least the doorway block sealed so the tube can't open straight into the void
                if (nextWallT <= innerWallT) nextWallT = innerWallT + 1;
                // Carve ONLY the inter-room corridor: from this room's wall face to the neighbour's
                // wall face. t ranges over [innerWallT, nextWallT] instead of the whole spacing, so
                // the 3-wide passage and its floor never cut through either room's interior (the
                // "corridor floor leaking into the room" bug). Because this room and the neighbour
                // carve the SAME corridor, they still merge into one continuous tunnel.
                for (int t = innerWallT; t <= nextWallT; t++) {
                    for (int off = -1; off <= 1; off++) {    // exactly 3 wide
                        int px = horiz ? (axC + asg * t) : (perpC + off);
                        int pz = horiz ? (perpC + off) : (axC + asg * t);
                        setBlock(w, px, baseY, pz, Material.POLISHED_ANDESITE);
                        for (int y = baseY + 1; y <= baseY + roomHeight; y++) {
                            setBlock(w, px, y, pz, Material.AIR);
                        }
                        // roof the corridor
                        setBlock(w, px, baseY + roomHeight + 1, pz, Material.STONE_BRICKS);
                    }
                }
                // Build a NARROW corridor: the gap between the two room walls is a solid stone mass
                // spanning the widest room's interior, with the 3-wide passage (already carved above)
                // cut through the middle. Sides are solid walls instead of open floor, so the corridor
                // reads as a tunnel (not a room-in-a-room) and there is no void to fall into.
                int COW = corridorHalf;               // solid mass covers the widest room interior
                for (int t = innerWallT; t < nextWallT; t++) {
                    for (int off = -COW; off <= COW; off++) {
                        if (Math.abs(off) <= 1) continue; // the 3-wide passage is already carved
                        int px = horiz ? (axC + asg * t) : (perpC + off);
                        int pz = horiz ? (perpC + off) : (axC + asg * t);
                        for (int y = baseY; y <= baseY + roomHeight + 1; y++) {
                            setBlock(w, px, y, pz, Material.STONE_BRICKS);
                        }
                    }
                }
                // hang a lantern from the middle of odd-length corridors
                int corridorLen = nextWallT - innerWallT;
                if (corridorLen >= 3 && corridorLen % 2 == 1) {
                    int mid = innerWallT + corridorLen / 2;
                    int lx = horiz ? (axC + asg * mid) : (perpC);
                    int lz = horiz ? (perpC) : (axC + asg * mid);
                    setBlock(w, lx, baseY + roomHeight, lz, Material.LANTERN);
                }
                // Boss doorway warning: a red floor tile + red overhead glow sit right at the door
                // opening so the danger reads from the corridor BEFORE the player steps inside.
                if (boss) {
                    int px = horiz ? (axC + asg * innerWallT) : perpC;
                    int pz = horiz ? perpC : (axC + asg * innerWallT);
                    setBlock(w, px, baseY, pz, Material.REDSTONE_BLOCK);
                    setBlock(w, px, baseY + roomHeight + 1, pz, Material.SHROOMLIGHT);
                }
            }
        }
        // Apply room shape modifications after the basic shell is built
        applyShape(w, n, sx, sz, baseY, ceilY, wallMat, floorMat);
        // interior lighting: glowstone set flush WITH the ceiling (at ceilY, the roof block itself)
        // so the lamps are embedded in the roof rather than dangling below it. Tall rooms still
        // light up because the ceiling sits one row above the tallest air block.
        for (int x = WALL + 1; x < wl - 1; x += 3) {
            for (int z = WALL + 1; z < wh - 1; z += 3) {
                setBlock(w, sx + x, ceilY, sz + z, lightMat);
            }
        }
    }

    /** Apply interior shape modifications after the basic room shell is built. */
    private static void applyShape(World w, Floor.RoomNode n, int sx, int sz,
                                   int baseY, int ceilY, Material wallMat, Material floorMat) {
        int iw = n.sizeW; // interior width
        int ih = n.sizeH; // interior height
        int ix = sx + WALL; // interior origin x
        int iz = sz + WALL; // interior origin z
        switch (n.shape) {
            case SHAPE_L -> applyLShape(w, ix, iz, iw, ih, baseY, ceilY, wallMat, floorMat);
            case SHAPE_PILLAR -> applyPillarShape(w, ix, iz, iw, ih, baseY, ceilY, wallMat, floorMat);
            case SHAPE_SPLIT -> applySplitShape(w, ix, iz, iw, ih, baseY, ceilY, wallMat, floorMat);
            default -> {} // rectangle — no changes
        }
    }

    /**
     * L-shaped room: fill in a corner section (3x3 or 4x4) with wall material, creating a
     * pillar/indentation. The corner is chosen based on the room's position hash for variety.
     */
    private static void applyLShape(World w, int ix, int iz, int iw, int ih,
                                    int baseY, int ceilY, Material wallMat, Material floorMat) {
        // pick a corner based on room position
        int corner = ((ix * 7 + iz * 13) & 3);
        int lw = Math.min(4, Math.max(3, iw / 4)); // 3-4 blocks wide
        int lh = Math.min(4, Math.max(3, ih / 4)); // 3-4 blocks deep
        int cx, cz;
        switch (corner) {
            case 0 -> { cx = ix; cz = iz; }                          // NW
            case 1 -> { cx = ix + iw - lw; cz = iz; }                // NE
            case 2 -> { cx = ix; cz = iz + ih - lh; }                // SW
            default -> { cx = ix + iw - lw; cz = iz + ih - lh; }     // SE
        }
        for (int x = cx; x < cx + lw; x++) {
            for (int z = cz; z < cz + lh; z++) {
                for (int y = baseY; y <= ceilY; y++) {
                    if (y == baseY) {
                        setBlock(w, x, y, z, floorMat);
                    } else {
                        setBlock(w, x, y, z, wallMat);
                    }
                }
            }
        }
    }

    /**
     * Pillar room: place 1-4 pillars (1x1 columns from floor to ceiling) at strategic positions
     * within the interior. Pillars provide cover from enemies.
     */
    private static void applyPillarShape(World w, int ix, int iz, int iw, int ih,
                                         int baseY, int ceilY, Material wallMat, Material floorMat) {
        // number of pillars based on room size
        int pillarCount = (iw >= 15 || ih >= 15) ? 4 : 2;
        // pillar positions: avoid the center block (where the player spawns) and spread evenly
        int[][] positions;
        if (pillarCount == 4) {
            positions = new int[][]{
                {ix + iw / 4, iz + ih / 4},
                {ix + 3 * iw / 4, iz + ih / 4},
                {ix + iw / 4, iz + 3 * ih / 4},
                {ix + 3 * iw / 4, iz + 3 * ih / 4}
            };
        } else {
            positions = new int[][]{
                {ix + iw / 3, iz + ih / 3},
                {ix + 2 * iw / 3, iz + 2 * ih / 3}
            };
        }
        for (int[] p : positions) {
            int px = p[0], pz = p[1];
            for (int y = baseY; y <= ceilY; y++) {
                setBlock(w, px, y, pz, y == baseY ? floorMat : wallMat);
            }
        }
    }

    /**
     * Split room: build a partial wall (3-4 blocks long, 1 block thick) extending from one wall,
     * leaving a gap on one side so the two chambers are connected.
     */
    private static void applySplitShape(World w, int ix, int iz, int iw, int ih,
                                        int baseY, int ceilY, Material wallMat, Material floorMat) {
        // pick a wall to extend from: 0=N, 1=E, 2=S, 3=W
        int wall = ((ix * 11 + iz * 17) & 3);
        int wallLen = Math.min(4, Math.max(3, Math.min(iw, ih) / 3)); // 3-4 blocks long
        int gap = 2; // 2-block gap on one side
        switch (wall) {
            case 0 -> { // North wall: extend south, gap on the right (east side)
                int z = iz;
                int xStart = ix + gap;
                for (int x = xStart; x < xStart + wallLen && x < ix + iw; x++) {
                    for (int y = baseY + 1; y <= ceilY; y++) {
                        setBlock(w, x, y, z, wallMat);
                    }
                }
            }
            case 1 -> { // East wall: extend west, gap on the bottom (south side)
                int x = ix + iw - 1;
                int zStart = iz + gap;
                for (int z = zStart; z < zStart + wallLen && z < iz + ih; z++) {
                    for (int y = baseY + 1; y <= ceilY; y++) {
                        setBlock(w, x, y, z, wallMat);
                    }
                }
            }
            case 2 -> { // South wall: extend north, gap on the right (east side)
                int z = iz + ih - 1;
                int xStart = ix + gap;
                for (int x = xStart; x < xStart + wallLen && x < ix + iw; x++) {
                    for (int y = baseY + 1; y <= ceilY; y++) {
                        setBlock(w, x, y, z, wallMat);
                    }
                }
            }
            default -> { // West wall: extend east, gap on the bottom (south side)
                int x = ix;
                int zStart = iz + gap;
                for (int z = zStart; z < zStart + wallLen && z < iz + ih; z++) {
                    for (int y = baseY + 1; y <= ceilY; y++) {
                        setBlock(w, x, y, z, wallMat);
                    }
                }
            }
        }
    }

    /** Spawn location: exact center of the room floor, 1 above. (No spurious +0.5.) */
    public static Location center(World w, Floor.RoomNode n, int baseY, int spacing) {
        return center(w, n, baseY, spacing, 0, 0);
    }

    public static Location center(World w, Floor.RoomNode n, int baseY, int spacing, int offsetX, int offsetZ) {
        RoomBase b = baseFor(n, spacing, offsetX, offsetZ);
        return new Location(w, b.x + WALL + n.sizeW / 2.0, baseY + 1, b.z + WALL + n.sizeH / 2.0);
    }
}