package com.lieyabull.dung.game;

import com.lieyabull.dung.dungeon.Archetype;
import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.FloorGenerator;
import com.lieyabull.dung.dungeon.FloorLayout;
import com.lieyabull.dung.dungeon.FloorModifier;
import com.lieyabull.dung.dungeon.FloorProfile;
import com.lieyabull.dung.dungeon.RoomFeature;
import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.dungeon.RoomRole;
import com.lieyabull.dung.dungeon.RoomShape;
import com.lieyabull.dung.dungeon.RoomTemplate;
import com.lieyabull.dung.dungeon.RoomType;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.structure.StructureDefinition;
import com.lieyabull.dung.structure.StructureTransform;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureConnectivityTest {

    private static final int BASE_Y = 20;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private static final class FakeWorld {
        final Map<Long, Material> grid = new HashMap<>();
        final World world;
        final Material outside;

        FakeWorld(Material outside) {
            this.outside = outside;
            Map<Long, Block> blocks = new HashMap<>();
            world = (World) Proxy.newProxyInstance(StructureConnectivityTest.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getBlockAt") && args.length == 3) {
                            long kk = key((int) args[0], (int) args[1], (int) args[2]);
                            return blocks.computeIfAbsent(kk, k -> (Block) Proxy.newProxyInstance(
                                    StructureConnectivityTest.class.getClassLoader(),
                                    new Class<?>[]{Block.class},
                                    (p2, m2, a2) -> {
                                        if (m2.getName().equals("getType")) {
                                            return grid.getOrDefault(k, outside);
                                        }
                                        if (m2.getName().equals("setType")) {
                                            grid.put(k, (Material) a2[0]);
                                            return null;
                                        }
                                        return defaultValue(m2.getReturnType());
                                    }));
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> t) {
            if (t == boolean.class) return false;
            if (t == int.class) return 0;
            if (t == long.class) return 0L;
            if (t == double.class) return 0.0;
            if (t == float.class) return 0f;
            if (t == void.class) return null;
            return null;
        }

        Material at(int x, int y, int z) {
            return grid.getOrDefault(key(x, y, z), outside);
        }

        private static long key(int x, int y, int z) {
            return ((long) (x + 2000) << 42) | ((long) (y + 2000) << 21) | (z + 2000);
        }
    }

    private static StructureDefinition boxDef(int interior, int airHeight) {
        StructureDefinition def = new StructureDefinition();
        int f = interior + 2;
        int maxY = airHeight + 1;
        def.bounds.add(new RoomBounds(0, 0, 0, f - 1, maxY, f - 1));
        return def;
    }

    private static void fillPaste(FakeWorld fw, int ox, int oy, int oz, RoomBounds tot) {
        for (int x = tot.minX; x <= tot.maxX; x++) {
            for (int y = tot.minY; y <= tot.maxY; y++) {
                for (int z = tot.minZ; z <= tot.maxZ; z++) {
                    boolean perimeter = x == tot.minX || x == tot.maxX || z == tot.minZ || z == tot.maxZ;
                    Material m;
                    if (y == tot.minY) m = Material.POLISHED_ANDESITE;
                    else if (y == tot.maxY) m = Material.STONE_BRICKS;
                    else m = perimeter ? Material.STONE_BRICKS : Material.AIR;
                    fw.world.getBlockAt(ox + x, oy + y, oz + z).setType(m);
                }
            }
        }
    }

    private static int[] centerOf(FakeWorld fw, Floor.RoomNode n, int spacing) {
        Location c = RoomGen.center(fw.world, n, BASE_Y, spacing, 0, 0);
        return new int[]{c.getBlockX(), c.getBlockY(), c.getBlockZ()};
    }

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static boolean connected(FakeWorld fw, int[] a, int[] b, int[] bounds) {
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        seen.add(k(a[0], a[1], a[2]));
        q.add(a);
        int steps = 0;
        while (!q.isEmpty()) {
            if (++steps > 2000000) return false;
            int[] p = q.poll();
            if (p[0] == b[0] && p[1] == b[1] && p[2] == b[2]) return true;
            for (int[] d : DIRS) {
                int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                if (ny < BASE_Y || ny > BASE_Y + 8) continue;
                if (nx < bounds[0] || nx > bounds[1] || nz < bounds[2] || nz > bounds[3]) continue;
                if (fw.at(nx, ny, nz) != Material.AIR) continue;
                if (seen.add(k(nx, ny, nz))) q.add(new int[]{nx, ny, nz});
            }
        }
        return false;
    }

    private static long k(int x, int y, int z) {
        return ((long) (x + 2000) << 42) | ((long) (y + 2000) << 21) | (z + 2000);
    }

    private static final class BuiltFloor {
        final Floor f;
        final int spacing;
        final FakeWorld fw;

        BuiltFloor(Floor f, int spacing, FakeWorld fw) {
            this.f = f;
            this.spacing = spacing;
            this.fw = fw;
        }
    }

    private static Floor generateFloor(long seed, int tier, int floorIndex, int roomsPerFloor,
            boolean allStructures) {
        return generateFloor(seed, tier, floorIndex, roomsPerFloor, allStructures,
                FloorProfile.classic(9, 9, roomsPerFloor));
    }

    private static Floor generateFloor(long seed, int tier, int floorIndex, int roomsPerFloor,
            boolean allStructures, FloorProfile profile) {
        FloorGenerator gen = new FloorGenerator(new Random(seed), profile, floorIndex);
        Floor f = gen.generate();
        Random rnd = new Random(seed * 31 + tier);
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type == RoomType.SECRET || n.type == RoomType.LOCKED) continue;
            if (n.role == RoomRole.HUB) continue;
            if (!allStructures && n.type == RoomType.SHOP) continue;
            StructureDefinition def = n.type == RoomType.BOSS
                    ? boxDef(23, RoomGen.BOSS_ROOM_HEIGHT)
                    : boxDef(13, RoomGen.ROOM_HEIGHT);
            StructureTransform.Rotation rot =
                    StructureTransform.Rotation.bySteps(rnd.nextInt(4));
            n.structure = StructureTransform.rotate(def, rot);
        }
        for (Floor.RoomNode n : f.rooms()) {
            if (n.structure == null) RoomGen.scaleToTier(n, tier);
        }
        return f;
    }

    private static BuiltFloor buildFloor(Floor f, int spacing, int corridorHalf, Material outside) {
        FakeWorld fw = new FakeWorld(outside);
        for (Floor.RoomNode n : f.rooms()) {
            if (n.structure == null) continue;
            RoomBounds tot = n.structure.total();
            int ox = n.x * spacing - tot.minX;
            int oz = n.z * spacing - tot.minZ;
            fillPaste(fw, ox, BASE_Y - tot.minY, oz, tot);
        }
        for (Floor.RoomNode n : f.rooms()) {
            if (n.structure == null) {
                RoomGen.build(fw.world, n, BASE_Y, spacing, corridorHalf, f, 0, 0);
            }
        }
        DungeonInstance.carveStructureDoorsStatic(fw.world, f, BASE_Y, spacing, 0, 0);
        DungeonInstance.carveStructureCorridorsStatic(fw.world, f, BASE_Y, spacing, 0, 0,
                corridorHalf);
        DungeonInstance.sealDoorHeightTransitionsStatic(fw.world, f, BASE_Y, spacing, 0, 0,
                corridorHalf);
        assertMixedHeightDoorsSealed(f, fw, spacing);
        return new BuiltFloor(f, spacing, fw);
    }

    private static void assertMixedHeightDoorsSealed(Floor f, FakeWorld fw, int spacing) {
        Set<String> done = new HashSet<>();
        for (Floor.RoomNode a : f.rooms()) {
            for (int d = 0; d < 4; d++) {
                if (!a.doors[d]) continue;
                Floor.RoomNode b = f.at(a.x + DX[d], a.z + DZ[d]);
                if (b == null) continue;
                if (!done.add(DungeonInstance.edgeId(a.x, a.z, b.x, b.z))) continue;
                int ah = DungeonInstance.roomAirHeight(a);
                int bh = DungeonInstance.roomAirHeight(b);
                if (ah == bh) continue;
                boolean aShort = ah < bh;
                Floor.RoomNode shortRoom = aShort ? a : b;
                int dir = aShort ? d : (d + 2) % 4;
                int shortH = Math.min(ah, bh);
                int tallH = Math.max(ah, bh);
                boolean horiz = dir == 1 || dir == 3;
                int shortBase = horiz ? shortRoom.x * spacing : shortRoom.z * spacing;
                int shortWall = shortRoom.structure != null
                        ? DungeonInstance.structureWallAlong(
                                shortBase, shortRoom.structure.total(), dir, horiz)
                        : DungeonInstance.proceduralWallAlong(
                                shortBase, shortRoom.sizeW, shortRoom.sizeH, dir, horiz);
                int perpC = horiz
                        ? shortRoom.z * spacing + RoomGen.PERP_CENTER
                        : shortRoom.x * spacing + RoomGen.PERP_CENTER;
                for (int y = BASE_Y + shortH + 1; y <= BASE_Y + tallH + 1; y++) {
                    for (int off = -1; off <= 1; off++) {
                        int px = horiz ? shortWall : perpC + off;
                        int pz = horiz ? perpC + off : shortWall;
                        assertTrue(fw.at(px, y, pz) != Material.AIR,
                                "mixed-height door not sealed at ("
                                        + px + "," + y + "," + pz + ")");
                    }
                }
            }
        }
    }

    private static void assertFloorConnected(long seed, int tier, int floorIndex, int roomsPerFloor,
            boolean allStructures) {
        assertFloorConnected(seed, tier, floorIndex, roomsPerFloor, allStructures,
                FloorProfile.classic(9, 9, roomsPerFloor));
    }

    private static void assertFloorConnected(long seed, int tier, int floorIndex, int roomsPerFloor,
            boolean allStructures, FloorProfile profile) {
        int spacing = Math.max(profile.minSpacing(),
                25 + 2 * tier + new Random(seed).nextInt(4));
        int corridorHalf = RoomGen.corridorHalfFor(tier);
        Floor f = generateFloor(seed, tier, floorIndex, roomsPerFloor, allStructures, profile);
        BuiltFloor stone = buildFloor(f, spacing, corridorHalf, Material.STONE);
        BuiltFloor air = buildFloor(f, spacing, corridorHalf, Material.AIR);
        int minBX = Integer.MAX_VALUE, maxBX = Integer.MIN_VALUE;
        int minBZ = Integer.MAX_VALUE, maxBZ = Integer.MIN_VALUE;
        for (Floor.RoomNode n : f.rooms()) {
            minBX = Math.min(minBX, n.x * spacing);
            maxBX = Math.max(maxBX, n.x * spacing + 30);
            minBZ = Math.min(minBZ, n.z * spacing);
            maxBZ = Math.max(maxBZ, n.z * spacing + 30);
        }
        int[] bounds = {minBX - 20, maxBX + 20, minBZ - 20, maxBZ + 20};
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type == RoomType.SECRET) continue;
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                Floor.RoomNode m = f.at(n.x + DX[d], n.z + DZ[d]);
                assertTrue(m != null,
                        "seed " + seed + " tier " + tier + " floor " + floorIndex
                                + ": door leads nowhere from (" + n.x + "," + n.z + ") dir " + d);
                if (m.type == RoomType.SECRET) continue;
                assertTrue(connected(stone.fw, centerOf(stone.fw, n, spacing),
                        centerOf(stone.fw, m, spacing), bounds),
                        "seed " + seed + " tier " + tier + " floor " + floorIndex
                                + ": no walkable path (" + n.x + "," + n.z + ") " + n.type
                                + " -> (" + m.x + "," + m.z + ") " + m.type + " dir " + d);
                assertCorridorWalled(air.fw, n, m, d, spacing, seed, tier, floorIndex);
            }
        }
    }

    private static void assertCorridorWalled(FakeWorld fw, Floor.RoomNode n, Floor.RoomNode m,
            int d, int spacing, long seed, int tier, int floorIndex) {
        boolean horiz = d == 1 || d == 3;
        int perpC = horiz ? n.z * spacing + RoomGen.PERP_CENTER : n.x * spacing + RoomGen.PERP_CENTER;
        int nFoot = n.structure != null
                ? (horiz ? n.structure.total().width() : n.structure.total().depth())
                : (horiz ? n.sizeW : n.sizeH) + 2 * RoomGen.WALL;
        int mFoot = m.structure != null
                ? (horiz ? m.structure.total().width() : m.structure.total().depth())
                : (horiz ? m.sizeW : m.sizeH) + 2 * RoomGen.WALL;
        int nBase = horiz ? n.x * spacing : n.z * spacing;
        int mBase = horiz ? m.x * spacing : m.z * spacing;
        int nWall = nBase + ((d == 1 || d == 2) ? nFoot - 1 : 0);
        int mWall = mBase + ((d == 1 || d == 2) ? 0 : mFoot - 1);
        int lo = Math.min(nWall, mWall) + 1;
        int hi = Math.max(nWall, mWall) - 1;
        if (hi <= lo + 1) return;
        int ax = (lo + hi) / 2;
        String ctx = "seed " + seed + " tier " + tier + " floor " + floorIndex
                + ": corridor wall missing (" + n.x + "," + n.z + ") -> (" + m.x + "," + m.z + ")";
        for (int off : new int[]{-2, 2}) {
            int px = horiz ? ax : perpC + off;
            int pz = horiz ? perpC + off : ax;
            assertTrue(fw.at(px, BASE_Y + 1, pz) != Material.AIR, ctx + " at y+1 off " + off);
            assertTrue(fw.at(px, BASE_Y + 2, pz) != Material.AIR, ctx + " at y+2 off " + off);
        }
    }

    @Test
    void everyDoorEdgeHasAWalkableCorridor() {
        for (int floorIndex : new int[]{0, 4}) {
            for (int tier = 0; tier <= 3; tier++) {
                for (int s = 0; s < 40; s++) {
                    assertFloorConnected(1000L + s, tier, floorIndex, 7, false);
                    assertFloorConnected(2000L + s, tier, floorIndex, 7, true);
                }
            }
        }
    }

    @Test
    void everyLoopDoorHasAWalkableCorridor() {
        FloorProfile profile = new FloorProfile("loops", 9, 9, 12, 2, 2, 1, 0, 5,
                FloorLayout.CLASSIC, null, java.util.Set.of());
        for (int tier = 0; tier <= 3; tier++) {
            for (int s = 0; s < 20; s++) {
                assertFloorConnected(3000L + s, tier, 0, 12, false, profile);
                assertFloorConnected(4000L + s, tier, 0, 12, true, profile);
            }
        }
    }

    @Test
    void sevenRoomLoopDoorHasAWalkableCorridor() {
        FloorProfile profile = new FloorProfile("loops7", 9, 9, 7, 2, 1, 1, 0, 5,
                FloorLayout.CLASSIC, null, java.util.Set.of());
        for (int tier = 0; tier <= 3; tier++) {
            for (int s = 0; s < 10; s++) {
                assertFloorConnected(5000L + s, tier, 0, 7, false, profile);
                assertFloorConnected(6000L + s, tier, 0, 7, true, profile);
            }
        }
    }

    @Test
    void hubFloorDoorsHaveWalkableCorridors() {
        FloorProfile profile = FloorProfile.hub(9, 9, 15, 2, 1, 1, 0, 5);
        for (int tier = 0; tier <= 3; tier++) {
            for (int s = 0; s < 10; s++) {
                assertFloorConnected(7000L + s, tier, 0, 15, false, profile);
                assertFloorConnected(8000L + s, tier, 0, 15, true, profile);
            }
        }
    }

    private static void assertCenterConnectsToAllNeighbors(
            Floor f, Floor.RoomNode center, String label) {
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        List<Floor.RoomNode> neighbors = new java.util.ArrayList<>();
        for (int d = 0; d < 4; d++) {
            Floor.RoomNode n = new Floor.RoomNode(
                    center.x + dirs[d][0], center.z + dirs[d][1], RoomType.COMBAT);
            f.add(n);
            center.open(d);
            n.open((d + 2) % 4);
            neighbors.add(n);
        }

        FakeWorld fw = new FakeWorld(Material.AIR);
        int spacing = 30;
        int corridorHalf = RoomGen.corridorHalfFor(0);
        RoomGen.build(fw.world, center, BASE_Y, spacing, corridorHalf, f, 0, 0);
        for (Floor.RoomNode n : neighbors) {
            RoomGen.build(fw.world, n, BASE_Y, spacing, corridorHalf, f, 0, 0);
        }

        int[] cc = centerOf(fw, center, spacing);
        for (Floor.RoomNode n : neighbors) {
            int[] cn = centerOf(fw, n, spacing);
            assertTrue(connected(fw, cc, cn, new int[]{-10, 200, -10, 200}),
                    label + " must connect through every door");
        }
    }

    @Test
    void everyShapeDoorConnectsToAllNeighbors() {
        for (RoomShape shape : RoomShape.values()) {
            for (int size : new int[]{13, 17}) {
                Floor f = new Floor(0, 7, 7);
                Floor.RoomNode center = new Floor.RoomNode(2, 2, RoomType.COMBAT);
                center.shape = shape;
                center.sizeW = size;
                center.sizeH = size;
                f.add(center);
                assertCenterConnectsToAllNeighbors(f, center, "shape " + shape + " size " + size);
            }
        }
    }

    @Test
    void modifierFloorsHaveWalkableCorridors() {
        for (FloorModifier modifier : FloorModifier.values()) {
            FloorProfile profile = FloorProfile.forArchetype(Archetype.RUINS, 9, 9, 9, 5)
                    .withModifiers(java.util.Set.of(modifier));
            assertFloorConnected(12000L + modifier.ordinal(), 0, 0, 9, false, profile);
            assertFloorConnected(13000L + modifier.ordinal(), 0, 0, 9, true, profile);
        }
    }

    @Test
    void layoutTemplatesDoNotBlockDoors() {
        for (RoomTemplate template : RoomTemplate.values()) {
            Floor f = new Floor(0, 7, 7);
            Floor.RoomNode center = new Floor.RoomNode(2, 2, RoomType.COMBAT);
            template.applyTo(center, new Random(1000 + template.ordinal()));
            f.add(center);
            assertCenterConnectsToAllNeighbors(f, center, "template " + template.id());
        }
    }

    @Test
    void hazardFeaturesDoNotBlockDoors() {
        for (RoomFeature feature : List.of(
                RoomFeature.LAVA_POOL, RoomFeature.CENTRAL_PIT, RoomFeature.SPIKES)) {
            Floor f = new Floor(0, 7, 7);
            Floor.RoomNode center = new Floor.RoomNode(2, 2, RoomType.COMBAT);
            center.shape = RoomShape.RECTANGLE;
            center.features.add(feature);
            center.featureSeed = 2000L + feature.ordinal();
            f.add(center);
            assertCenterConnectsToAllNeighbors(f, center, "hazard " + feature);
        }
    }

    @Test
    void safeFeaturesDoNotBlockDoors() {
        for (RoomFeature feature : RoomFeature.values()) {
            if (!feature.safe()) continue;
            Floor f = new Floor(0, 7, 7);
            Floor.RoomNode center = new Floor.RoomNode(2, 2, RoomType.COMBAT);
            center.shape = RoomShape.RECTANGLE;
            center.features.add(feature);
            center.featureSeed = 1000L + feature.ordinal();
            f.add(center);
            assertCenterConnectsToAllNeighbors(f, center, "feature " + feature);
        }
    }

    @Test
    void archetypePaletteAppliesToCombatRooms() {
        Floor f = new Floor(0, 5, 5);
        f.archetype = Archetype.INFERNO;
        Floor.RoomNode room = new Floor.RoomNode(0, 0, RoomType.COMBAT);
        FakeWorld fw = new FakeWorld(Material.AIR);
        RoomGen.build(fw.world, room, BASE_Y, 30, RoomGen.corridorHalfFor(0), f, 0, 0);

        assertTrue(fw.at(0, BASE_Y + 1, 0) == Material.NETHER_BRICKS,
                "archetype wall palette must apply");
        assertTrue(fw.at(1, BASE_Y, 1) == Material.BLACKSTONE,
                "archetype floor palette must apply");
    }

    @Test
    void hubIsRoundedWithFlatDoorFace() {
        Floor f = new Floor(0, 5, 5);
        Floor.RoomNode hub = f.start;
        hub.role = RoomRole.HUB;
        RoomGen.scaleToTier(hub, 0);

        int spacing = 30;
        int corridorHalf = RoomGen.corridorHalfFor(0);
        FakeWorld fw = new FakeWorld(Material.AIR);
        RoomGen.build(fw.world, hub, BASE_Y, spacing, corridorHalf, f, 0, 0);

        int bx = hub.x * spacing;
        int bz = hub.z * spacing;
        int y = BASE_Y + 1;
        assertTrue(fw.at(bx + 27, y, bz + 27) != Material.AIR,
                "hub corner must be solid");
        assertTrue(fw.at(bx + 27, y, bz + 9) == Material.AIR,
                "east face at the door line must be open");
        assertTrue(fw.at(bx + 27, y, bz + 7) == Material.AIR,
                "east face must stay flat for at least five blocks");
        assertTrue(fw.at(bx + 27, y, bz + 6) != Material.AIR,
                "the wall must curve in outside the flat face");
    }

    @Test
    void mixedHeightDoorSealsShortSide() {
        Floor f = new Floor(0, 5, 5);
        Floor.RoomNode normal = f.start;
        Floor.RoomNode boss = new Floor.RoomNode(3, 2, RoomType.BOSS);
        f.add(boss);
        normal.open(1);
        boss.open(3);

        FakeWorld fw = new FakeWorld(Material.AIR);
        int spacing = 25;
        int corridorHalf = RoomGen.corridorHalfFor(0);
        RoomGen.build(fw.world, normal, BASE_Y, spacing, corridorHalf, f, 0, 0);
        RoomGen.build(fw.world, boss, BASE_Y, spacing, corridorHalf, f, 0, 0);
        DungeonInstance.sealDoorHeightTransitionsStatic(
                fw.world, f, BASE_Y, spacing, 0, 0, corridorHalf);

        int shortWall = DungeonInstance.proceduralWallAlong(
                normal.x * spacing, normal.sizeW, normal.sizeH, 1, true);
        int tallWall = DungeonInstance.proceduralWallAlong(
                boss.x * spacing, boss.sizeW, boss.sizeH, 3, true);
        int perpC = normal.z * spacing + RoomGen.PERP_CENTER;

        for (int y = BASE_Y + RoomGen.ROOM_HEIGHT + 1;
                y <= BASE_Y + RoomGen.BOSS_ROOM_HEIGHT + 1; y++) {
            for (int off = -1; off <= 1; off++) {
                assertTrue(fw.at(shortWall, y, perpC + off) != Material.AIR,
                        "short doorway must be sealed at y=" + y + " off=" + off);
            }
        }
        for (int y = BASE_Y + 1; y <= BASE_Y + RoomGen.BOSS_ROOM_HEIGHT; y++) {
            for (int off = -1; off <= 1; off++) {
                assertTrue(fw.at(tallWall, y, perpC + off) == Material.AIR,
                        "tall doorway must stay open at y=" + y + " off=" + off);
            }
        }
        int mid = (shortWall + tallWall) / 2;
        for (int off = -1; off <= 1; off++) {
            assertTrue(fw.at(mid, BASE_Y + RoomGen.BOSS_ROOM_HEIGHT + 1, perpC + off) != Material.AIR,
                    "corridor roof must be at boss height");
        }
    }
}
