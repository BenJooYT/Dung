package com.lieyabull.dung.game;

import com.lieyabull.dung.dungeon.Floor;
import com.lieyabull.dung.dungeon.FloorGenerator;
import com.lieyabull.dung.dungeon.RoomGen;
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
        return new int[]{c.getBlockX(), BASE_Y + 1, c.getBlockZ()};
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
        FloorGenerator gen = new FloorGenerator(new Random(seed), 9, 9, roomsPerFloor, floorIndex);
        Floor f = gen.generate();
        Random rnd = new Random(seed * 31 + tier);
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type == RoomType.SECRET || n.type == RoomType.LOCKED) continue;
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
        return new BuiltFloor(f, spacing, fw);
    }

    private static void assertFloorConnected(long seed, int tier, int floorIndex, int roomsPerFloor,
            boolean allStructures) {
        int spacing = 25 + 2 * tier + new Random(seed).nextInt(4);
        int corridorHalf = RoomGen.corridorHalfFor(tier);
        Floor f = generateFloor(seed, tier, floorIndex, roomsPerFloor, allStructures);
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
}
