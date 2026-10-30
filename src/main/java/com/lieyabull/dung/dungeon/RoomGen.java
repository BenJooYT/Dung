package com.lieyabull.dung.dungeon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;
import java.util.Set;

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
        // Hub chambers are fixed-size and intentionally larger than every ordinary room, including
        // the boss arena; FloorProfile.minSpacing reserves the footprint.
        if (n.role == RoomRole.HUB) {
            n.sizeW = HUB_INTERIOR;
            n.sizeH = HUB_INTERIOR;
            n.shape = RoomShape.RECTANGLE;
            return;
        }
        // Boss arenas are ~75% larger in width and length than a normal room (matches the enlarged
        // boss template). The room node's shape constants (SQUARE/LONG) are just placeholders here —
        // a boss room always becomes a square-ish arena.
        if (n.type == RoomType.BOSS) { sq = BOSS_INTERIOR; ln = BOSS_INTERIOR; }
        if (n.sizeW == SQUARE) n.sizeW = sq; else if (n.sizeW == LONG) n.sizeW = ln;
        if (n.sizeH == SQUARE) n.sizeH = sq; else if (n.sizeH == LONG) n.sizeH = ln;
    }

    /** Interior width/depth of a procedural boss arena: 13 -> 23, keeping the ~75% size increase and an
     *  odd dimension so the room stays centered (also what the default BOSS template uses). */
    public static final int BOSS_INTERIOR = 23;
    /** Interior width/depth of a hub chamber. Fixed so floor spacing can reserve room for it. */
    public static final int HUB_INTERIOR = 27;
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

    public static void build(World w, Floor.RoomNode n, int baseY, int spacing, Floor floor) {
        build(w, n, baseY, spacing, LONG / 2, floor, 0, 0);
    }

    public static void build(World w, Floor.RoomNode n, int baseY, int spacing, int offsetX, int offsetZ, Floor floor) {
        build(w, n, baseY, spacing, LONG / 2, floor, offsetX, offsetZ);
    }

    /** {@code corridorHalf} is the shared half-width of the corridor side walls ({@link #corridorHalfFor}),
     *  derived from the floor's party tier so larger rooms stay fully sealed. {@code floor} lets each room
     *  find its neighbour so it can carve its doorway up to the neighbour's actual wall face. */
    public static void build(World w, Floor.RoomNode n, int baseY, int spacing, int corridorHalf, Floor floor, int offsetX, int offsetZ) {
        RoomBase b = baseFor(n, spacing, offsetX, offsetZ);
        int sx = b.x, sz = b.z;
        int wl = n.sizeW + 2 * WALL;       // footprint including walls
        int wh = n.sizeH + 2 * WALL;
        // hollow out + floor; ceiling sits one row above the tallest air block
        // Boss rooms are taller (BOSS_ROOM_HEIGHT) to accommodate the larger Ravager entity
        // Visual themes per room type
        boolean boss = n.type == RoomType.BOSS;
        boolean elevated = n.features.contains(RoomFeature.PLATFORMS);
        int baseRoomHeight = boss ? BOSS_ROOM_HEIGHT : ROOM_HEIGHT;
        int elevation = elevated ? 2 : 0;
        int roomHeight = baseRoomHeight + elevation;
        int doorHeight = baseRoomHeight;
        int ceilY = baseY + roomHeight + 1;
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
            case EVENT:
                wallMat = Material.PURPUR_BLOCK;
                floorMat = Material.END_STONE_BRICKS;
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
        Archetype archetype = floor == null ? null : floor.archetype;
        if (archetype != null
                && (n.type == RoomType.COMBAT || n.type == RoomType.START)) {
            wallMat = archetype.wallMat();
            floorMat = archetype.floorMat();
            lightMat = archetype.lightMat();
        }
        Set<FloorModifier> mods = floor == null ? Set.of() : floor.modifiers;
        if (n.type == RoomType.COMBAT || n.type == RoomType.START) {
            if (mods.contains(FloorModifier.OVERGROWN)) {
                wallMat = Material.MOSSY_STONE_BRICKS;
                floorMat = Material.MOSS_BLOCK;
                lightMat = Material.GLOWSTONE;
            }
            if (mods.contains(FloorModifier.FROZEN)) {
                wallMat = Material.PACKED_ICE;
                floorMat = Material.PACKED_ICE;
                lightMat = Material.GLOWSTONE;
            }
            if (mods.contains(FloorModifier.FLOODED)) {
                wallMat = Material.PRISMARINE_BRICKS;
                floorMat = Material.PRISMARINE;
                lightMat = Material.SEA_LANTERN;
            }
            if (mods.contains(FloorModifier.CORRUPTED)) {
                wallMat = Material.CRYING_OBSIDIAN;
                floorMat = Material.BLACKSTONE;
                lightMat = Material.SHROOMLIGHT;
            }
            if (mods.contains(FloorModifier.COLLAPSING)) {
                wallMat = Material.CRACKED_STONE_BRICKS;
                floorMat = Material.COBBLESTONE;
                lightMat = Material.GLOWSTONE;
            }
        }
        boolean hub = n.role == RoomRole.HUB;
        int hubCx = WALL + n.sizeW / 2;
        int hubCz = WALL + n.sizeH / 2;
        int hubHalfW = n.sizeW / 2;
        int hubHalfH = n.sizeH / 2;
        int hubCorner = hub
                ? Math.max(1, Math.min(6, Math.min(hubHalfW, hubHalfH) - 2))
                : 0;
        RoomGeometry geometry = hub ? null : new RoomGeometry(n.shape, n.sizeW, n.sizeH);
        for (int x = 0; x < wl; x++) {
            for (int z = 0; z < wh; z++) {
                boolean inside = hub
                        ? (x >= WALL && x < WALL + n.sizeW
                            && z >= WALL && z < WALL + n.sizeH
                            && hubInside(x - hubCx, z - hubCz, hubHalfW, hubHalfH, hubCorner))
                        : (x >= WALL && x < WALL + n.sizeW
                            && z >= WALL && z < WALL + n.sizeH
                            && (geometry.inside(x - WALL, z - WALL)
                                || inDoorChannel(n, x, z, n.sizeW, n.sizeH)));
                int floorH = elevated ? elevationAt(n, x, z) : 0;
                int floorY = baseY + floorH;
                Material surface = floorMat;
                if (elevated && n.template == RoomTemplate.THE_PLATFORMS
                        && floorH == 1 && bridgeAt(n, x, z)) {
                    surface = Material.OAK_PLANKS;
                }
                for (int y = baseY; y <= ceilY; y++) {
                    boolean wall = x == 0 || x == wl - 1 || z == 0 || z == wh - 1;
                    boolean ceiling = y == ceilY;
                    if (!inside) {
                        setBlock(w, sx + x, y, sz + z, wallMat);
                    } else if (y < floorY) {
                        setBlock(w, sx + x, y, sz + z, wallMat);
                    } else if (wall || ceiling) {
                        setBlock(w, sx + x, y, sz + z, wallMat);
                    } else if (y == floorY) {
                        setBlock(w, sx + x, y, sz + z, surface);
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
                // Carve from THIS room's wall face exactly up to the neighbour's facing wall. Using a
                // fixed mirror ("spacing - innerWallT") or a plain midpoint is only safe when the two
                // rooms are identically sized: a small room sealing against a much bigger one (e.g.
                // the boss arena) either punches its 3-wide passage INTO the big room's interior
                // ("the corridor is inside the boss room") or, with the midpoint, leaves its wall mass
                // short of the neighbour — a gap in the middle/floor of the corridor. By looking the
                // neighbour up and carving up to ITS actual wall face, both carves meet at the same
                // shared boundary: no overshoot into either interior and no gap between the walls.
                int nbWallWorld;
                Floor.RoomNode m = floor == null ? null : floor.at(n.x + DX[d], n.z + DZ[d]);
                if (m == null) {
                    nbWallWorld = axC + asg * spacing; // no neighbour: carve a short doorway stub
                } else if (horiz) {
                    int mw = (m.structure != null) ? m.structure.total().width() : (m.sizeW + 2 * WALL);
                    int mbase = m.x * spacing + offsetX;
                    nbWallWorld = mbase + (d == 1 ? 0 : mw - 1);
                } else {
                    int mh = (m.structure != null) ? m.structure.total().depth() : (m.sizeH + 2 * WALL);
                    int mbase = m.z * spacing + offsetZ;
                    nbWallWorld = mbase + (d == 0 ? mh - 1 : 0);
                }
                int nextWallT = (nbWallWorld - axC) / asg;
                // guard: if the two rooms are flush or the range would be empty, keep the doorway
                // block sealed so the tube can't open straight into the void.
                if (nextWallT <= innerWallT) nextWallT = innerWallT + 1;
                // Carve the inter-room corridor: from this room's wall face to the neighbour's wall
                // face. t ranges over [innerWallT, nextWallT], so the 3-wide passage and its floor
                // cover the whole gap between the walls but never cut into either room's interior.
                // This room and the neighbour carve the same boundary, so they merge into one tunnel.
                for (int t = innerWallT; t <= nextWallT; t++) {
                    for (int off = -1; off <= 1; off++) {    // exactly 3 wide
                        int px = horiz ? (axC + asg * t) : (perpC + off);
                        int pz = horiz ? (perpC + off) : (axC + asg * t);
                        setBlock(w, px, baseY, pz, Material.POLISHED_ANDESITE);
                        for (int y = baseY + 1; y <= baseY + doorHeight; y++) {
                            setBlock(w, px, y, pz, Material.AIR);
                        }
                        // roof the corridor
                        setBlock(w, px, baseY + doorHeight + 1, pz, Material.STONE_BRICKS);
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
                        for (int y = baseY; y <= baseY + doorHeight + 1; y++) {
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
                    BlockBatcher.setHangingLantern(w, lx, baseY + doorHeight, lz);
                }
                // Boss doorway warning: a red floor tile + red overhead glow sit right at the door
                // opening so the danger reads from the corridor BEFORE the player steps inside.
                if (boss) {
                    int px = horiz ? (axC + asg * innerWallT) : perpC;
                    int pz = horiz ? perpC : (axC + asg * innerWallT);
                    setBlock(w, px, baseY, pz, Material.REDSTONE_BLOCK);
                    setBlock(w, px, baseY + doorHeight + 1, pz, Material.SHROOMLIGHT);
                }
            }
        }
        // Apply room shape modifications and safe features after the basic shell is built.
        if (!hub) {
            applyShape(w, n, sx, sz, baseY, ceilY, wallMat, floorMat);
            applyFeatures(w, n, sx, sz, baseY, ceilY, geometry, wallMat);
            decorateModifiers(w, n, sx, sz, baseY, ceilY, geometry, mods);
        }
        // interior lighting: glowstone set flush WITH the ceiling (at ceilY, the roof block itself)
        // so the lamps are embedded in the roof rather than dangling below it. Tall rooms still
        // light up because the ceiling sits one row above the tallest air block.
        if (!mods.contains(FloorModifier.DARKNESS)) {
            for (int x = WALL + 1; x < wl - 1; x += 3) {
                for (int z = WALL + 1; z < wh - 1; z += 3) {
                    if (hub && !hubInside(x - hubCx, z - hubCz, hubHalfW, hubHalfH, hubCorner)) {
                        continue;
                    }
                    if (!hub && !geometry.inside(x - WALL, z - WALL)) {
                        continue;
                    }
                    setBlock(w, sx + x, ceilY, sz + z, lightMat);
                }
            }
        }
    }

    /** Rounded-rectangle mask for hub chambers: flat cardinal faces with quarter-circle corners. */
    private static boolean hubInside(int dx, int dz, int halfW, int halfH, int corner) {
        int ax = Math.abs(dx), az = Math.abs(dz);
        if (ax > halfW || az > halfH) return false;
        int flatX = halfW - corner;
        int flatZ = halfH - corner;
        if (ax <= flatX || az <= flatZ) return true;
        int ex = ax - flatX, ez = az - flatZ;
        return ex * ex + ez * ez <= corner * corner;
    }

    /** Apply interior shape modifications after the basic room shell is built. */
    private static void applyShape(World w, Floor.RoomNode n, int sx, int sz,
                                   int baseY, int ceilY, Material wallMat, Material floorMat) {
        int iw = n.sizeW; // interior width
        int ih = n.sizeH; // interior height
        int ix = sx + WALL; // interior origin x
        int iz = sz + WALL; // interior origin z
        switch (n.shape) {
            case PILLAR -> applyPillarShape(w, ix, iz, iw, ih, baseY, ceilY, wallMat, floorMat);
            case SPLIT -> applySplitShape(w, ix, iz, iw, ih, baseY, ceilY, wallMat, floorMat);
            default -> {} // full mask — no interior modification
        }
    }

    /** Apply the room's safe feature set after the shell and shape pass. */
    private static void applyFeatures(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, int ceilY, RoomGeometry geometry, Material wallMat) {
        if (n.features.isEmpty()) return;
        if (n.features.contains(RoomFeature.PLATFORMS)) return;
        Random rng = new Random(n.featureSeed);
        for (RoomFeature feature : n.features) {
            switch (feature) {
                case PILLARS -> applyFeaturePillars(w, n, sx, sz, baseY, ceilY, geometry, wallMat);
                case RUBBLE -> applyFeatureRubble(w, n, sx, sz, baseY, geometry, rng);
                case ALTAR -> applyFeatureAltar(w, n, sx, sz, baseY, geometry, rng);
                case CHEST_PLATFORM -> applyFeatureChest(w, n, sx, sz, baseY, geometry, rng);
                case WATER_POOL -> applyFeatureWater(w, n, sx, sz, baseY, geometry, rng);
                case LAVA_POOL -> applyFeatureLava(w, n, sx, sz, baseY, geometry, rng);
                case CENTRAL_PIT -> applyFeaturePit(w, n, sx, sz, baseY, ceilY, geometry, rng);
                case SPIKES -> applyFeatureSpikes(w, n, sx, sz, baseY, geometry, rng);
                case ROOTS -> applyFeatureRoots(w, n, sx, sz, baseY, geometry, rng);
                case STAIRS -> applyFeatureStairs(w, n, sx, sz, baseY, geometry, wallMat, rng);
                case BRIDGES -> applyFeatureBridge(w, n, sx, sz, baseY, geometry, rng);
                default -> { }
            }
        }
    }

    private static void applyFeaturePillars(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, int ceilY, RoomGeometry g, Material wallMat) {
        int iw = n.sizeW, ih = n.sizeH;
        int[][] positions = {
            {WALL + iw / 4, WALL + ih / 4},
            {WALL + 3 * iw / 4, WALL + ih / 4},
            {WALL + iw / 4, WALL + 3 * ih / 4},
            {WALL + 3 * iw / 4, WALL + 3 * ih / 4}
        };
        for (int[] p : positions) {
            if (!canFeature(g, n, p[0], p[1])) continue;
            for (int y = baseY + 1; y < ceilY; y++) {
                setBlock(w, sx + p[0], y, sz + p[1], wallMat);
            }
        }
    }

    private static void applyFeatureRubble(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int placed = 0;
        for (int attempt = 0; attempt < 12 && placed < 5; attempt++) {
            int x = WALL + rng.nextInt(n.sizeW);
            int z = WALL + rng.nextInt(n.sizeH);
            if (!canFeature(g, n, x, z)) continue;
            setBlock(w, sx + x, baseY + 1, sz + z, Material.COBBLESTONE);
            placed++;
        }
    }

    private static void applyFeatureAltar(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area;
        if (n.template == RoomTemplate.THE_ALTAR || n.template == RoomTemplate.FOUR_PILLAR_ARENA) {
            area = new int[]{WALL + n.sizeW / 2, WALL + n.sizeH / 2};
        } else {
            area = findArea(g, n, rng, 1);
        }
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(w, sx + area[0] + dx, baseY, sz + area[1] + dz,
                        dx == 0 && dz == 0
                                ? Material.CHISELED_POLISHED_BLACKSTONE
                                : Material.POLISHED_BLACKSTONE);
            }
        }
    }

    private static void applyFeatureChest(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area = findArea(g, n, rng, 1);
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(w, sx + area[0] + dx, baseY, sz + area[1] + dz,
                        Material.POLISHED_BLACKSTONE);
            }
        }
        setBlock(w, sx + area[0], baseY + 1, sz + area[1], Material.CHEST);
    }

    private static void applyFeatureWater(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area = findArea(g, n, rng, 1);
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = sx + area[0] + dx;
                int z = sz + area[1] + dz;
                setBlock(w, x, baseY - 1, z, Material.STONE);
                setBlock(w, x, baseY, z, Material.WATER);
            }
        }
    }

    /** Small modifier-only decoration pass; kept above head height so it never blocks paths. */
    private static void decorateModifiers(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, int ceilY, RoomGeometry g, Set<FloorModifier> mods) {
        if (!mods.contains(FloorModifier.INFESTED)) return;
        Random rng = new Random(n.featureSeed ^ 0x1F1F1F1FL);
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = WALL + rng.nextInt(n.sizeW);
            int z = WALL + rng.nextInt(n.sizeH);
            if (!canFeature(g, n, x, z)) continue;
            int y = Math.min(baseY + 3, ceilY - 1);
            setBlock(w, sx + x, y, sz + z, Material.COBWEB);
        }
    }

    private static void applyFeatureStairs(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Material wallMat, Random rng) {
        int[] area = findArea(g, n, rng, 1);
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = dz == 1 ? 2 : 1;
                for (int y = baseY + 1; y <= baseY + h; y++) {
                    setBlock(w, sx + area[0] + dx, y, sz + area[1] + dz, wallMat);
                }
            }
        }
    }

    private static void applyFeatureBridge(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area = findArea(g, n, rng, 2);
        if (area == null) return;
        boolean alongX = rng.nextBoolean();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sx + area[0] + dx;
                int z = sz + area[1] + dz;
                setBlock(w, x, baseY - 1, z, Material.STONE);
                setBlock(w, x, baseY, z, Material.AIR);
            }
        }
        for (int i = -2; i <= 2; i++) {
            for (int j = -1; j <= 1; j++) {
                int x = sx + area[0] + (alongX ? i : j);
                int z = sz + area[1] + (alongX ? j : i);
                setBlock(w, x, baseY, z, Material.OAK_PLANKS);
            }
        }
    }

    private static void applyFeatureLava(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area = findArea(g, n, rng, 2);
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = sx + area[0] + dx;
                int z = sz + area[1] + dz;
                setBlock(w, x, baseY - 1, z, Material.STONE);
                setBlock(w, x, baseY, z, Material.LAVA);
            }
        }
    }

    private static void applyFeaturePit(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, int ceilY, RoomGeometry g, Random rng) {
        int[] area;
        if (n.shape == RoomShape.RING) {
            area = new int[]{WALL + g.centerX(), WALL + g.centerZ()};
        } else {
            area = findArea(g, n, rng, 2);
        }
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = sx + area[0] + dx;
                int z = sz + area[1] + dz;
                setBlock(w, x, baseY - 1, z, Material.STONE);
                setBlock(w, x, baseY, z, Material.AIR);
                if (n.shape == RoomShape.RING) {
                    for (int y = baseY + 1; y < ceilY; y++) {
                        setBlock(w, x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    private static void applyFeatureSpikes(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int[] area = findArea(g, n, rng, 1);
        if (area == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlock(w, sx + area[0] + dx, baseY, sz + area[1] + dz,
                        Material.MAGMA_BLOCK);
            }
        }
    }

    private static void applyFeatureRoots(World w, Floor.RoomNode n, int sx, int sz,
            int baseY, RoomGeometry g, Random rng) {
        int placed = 0;
        for (int attempt = 0; attempt < 12 && placed < 5; attempt++) {
            int x = WALL + rng.nextInt(n.sizeW);
            int z = WALL + rng.nextInt(n.sizeH);
            if (!canFeature(g, n, x, z)) continue;
            setBlock(w, sx + x, baseY + 1, sz + z, Material.MANGROVE_ROOTS);
            placed++;
        }
    }

    private static int[] findArea(RoomGeometry g, Floor.RoomNode n, Random rng, int radius) {
        for (int attempt = 0; attempt < 32; attempt++) {
            int x = WALL + rng.nextInt(n.sizeW);
            int z = WALL + rng.nextInt(n.sizeH);
            boolean ok = true;
            for (int dx = -radius; dx <= radius && ok; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!canFeature(g, n, x + dx, z + dz)) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok) return new int[]{x, z};
        }
        return null;
    }

    private static boolean canFeature(RoomGeometry g, Floor.RoomNode n, int x, int z) {
        if (x < WALL || x >= WALL + n.sizeW || z < WALL || z >= WALL + n.sizeH) return false;
        if (!g.inside(x - WALL, z - WALL)) return false;
        if (inDoorChannel(n, x, z, n.sizeW, n.sizeH)) return false;
        int cx = WALL + n.sizeW / 2;
        int cz = WALL + n.sizeH / 2;
        return Math.abs(x - cx) > 1 || Math.abs(z - cz) > 1;
    }

    /** Terraced floor height: 2 in the center, 1 on the middle ring, 0 at the edges. */
    private static int elevationAt(Floor.RoomNode n, int x, int z) {
        if (n.structure != null || !n.features.contains(RoomFeature.PLATFORMS)) return 0;
        int cx = WALL + n.sizeW / 2;
        int cz = WALL + n.sizeH / 2;
        int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
        int half = Math.min(n.sizeW, n.sizeH);
        int plateau = Math.max(2, Math.min(4, half / 3));
        int h = 2 - Math.max(0, d - plateau);
        h = Math.max(0, Math.min(2, h));
        if (n.template == RoomTemplate.THE_PLATFORMS && h == 1 && !bridgeAt(n, x, z)) {
            h = 0;
        }
        return h;
    }

    /** The safe bridge lane from one door through the gap ring to the plateau. */
    private static boolean bridgeAt(Floor.RoomNode n, int x, int z) {
        if (n.template != RoomTemplate.THE_PLATFORMS) return false;
        int dir = safeDoorDir(n);
        int cx = WALL + n.sizeW / 2;
        int cz = WALL + n.sizeH / 2;
        return switch (dir) {
            case 0 -> Math.abs(x - PERP_CENTER) <= 1 && z < cz;
            case 1 -> Math.abs(z - PERP_CENTER) <= 1 && x > cx;
            case 2 -> Math.abs(x - PERP_CENTER) <= 1 && z > cz;
            default -> Math.abs(z - PERP_CENTER) <= 1 && x < cx;
        };
    }

    private static int safeDoorDir(Floor.RoomNode n) {
        if (n.doors[2]) return 2;
        for (int d = 0; d < 4; d++) {
            if (n.doors[d]) return d;
        }
        return 2;
    }

    /** World floor height for a room column, used by elevation-aware spawns. */
    public static int floorY(Floor.RoomNode n, int worldX, int worldZ, int baseY,
            int spacing, int offsetX, int offsetZ) {
        RoomBase b = baseFor(n, spacing, offsetX, offsetZ);
        return baseY + elevationAt(n, worldX - b.x, worldZ - b.z);
    }

    /** Reserve a 3-wide channel from each open door into the shaped interior. */
    private static boolean inDoorChannel(Floor.RoomNode n, int x, int z, int sizeW, int sizeH) {
        int perp = PERP_CENTER;
        int depth = 3;
        if (n.doors[1] && x >= sizeW - depth + 1 && Math.abs(z - perp) <= 1) return true;
        if (n.doors[3] && x <= depth && Math.abs(z - perp) <= 1) return true;
        if (n.doors[2] && z >= sizeH - depth + 1 && Math.abs(x - perp) <= 1) return true;
        if (n.doors[0] && z <= depth && Math.abs(x - perp) <= 1) return true;
        return false;
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
        int cx = WALL + n.sizeW / 2;
        int cz = WALL + n.sizeH / 2;
        if (n.shape == RoomShape.RING) {
            RoomGeometry g = new RoomGeometry(n.shape, n.sizeW, n.sizeH);
            cx = WALL + g.centerX() + g.holeHalf() + 1;
            cz = WALL + g.centerZ();
        }
        int fy = baseY + elevationAt(n, cx, cz);
        return new Location(w, b.x + cx + 0.5, fy + 1, b.z + cz + 0.5);
    }
}