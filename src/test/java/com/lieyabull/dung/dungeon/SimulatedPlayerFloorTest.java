package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A headless "simulated player" run over generated floors. The agent pathfinds through the room
 * graph, clears every reachable room and its loot, and finally kills the boss. Because this runs
 * against {@link FloorGenerator} (pure logic, no Bukkit server) it validates that every generated
 * floor is fully clearable, connected, and correctly assembled.
 */
public class SimulatedPlayerFloorTest {

    private static final int SEEDS = 300;

    /** BFS distances from the given room; room itself is distance 0. */
    private static int[] distances(Floor f, Floor.RoomNode from) {
        int w = f.width, h = f.height;
        int[] dist = new int[w * h];
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        dist[from.x + from.z * w] = 0;
        ArrayDeque<Floor.RoomNode> q = new ArrayDeque<>();
        q.add(from);
        int[] DX = {0, 1, 0, -1};
        int[] DZ = {-1, 0, 1, 0};
        while (!q.isEmpty()) {
            Floor.RoomNode n = q.poll();
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = n.x + DX[d], nz = n.z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                Floor.RoomNode m = f.at(nx, nz);
                if (m == null) continue;
                if (dist[nx + nz * w] > dist[n.x + n.z * w] + 1) {
                    dist[nx + nz * w] = dist[n.x + n.z * w] + 1;
                    q.add(m);
                }
            }
        }
        return dist;
    }

    /** True when the graph has an inward door for every outward door (connectivity symmetry). */
    private static boolean doorsBidirectional(Floor f) {
        for (Floor.RoomNode n : f.rooms()) {
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int[] DX = {0, 1, 0, -1};
                int[] DZ = {-1, 0, 1, 0};
                Floor.RoomNode m = f.at(n.x + DX[d], n.z + DZ[d]);
                if (m == null) return false;
                if (!m.doors[(d + 2) % 4]) return false;
            }
        }
        return true;
    }

    @Test
    void simulatedPlayerCanClearEveryGeneratedFloor() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Random rng = new Random(seed);
            int w = 12 + rng.nextInt(8);          // 12..19
            int h = 12 + rng.nextInt(8);
            int rooms = 18 + rng.nextInt(22);     // 18..39
            Floor f = new FloorGenerator(rng, w, h, rooms).generate();

            int total = f.roomCount();
            // every room reachable from START -> the agent can always make progress
            int[] dist = distances(f, f.start);
            int unreachable = 0;
            for (Floor.RoomNode n : f.rooms()) if (dist[n.x + n.z * f.width] == Integer.MAX_VALUE) unreachable++;
            assertEquals(0, unreachable, "seed " + seed + ": all rooms must be reachable");

            // exactly one boss, reachable and not the start
            List<Floor.RoomNode> bossRooms = new ArrayList<>();
            for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.BOSS) bossRooms.add(n);
            assertEquals(1, bossRooms.size(), "seed " + seed + ": exactly one BOSS room");
            Floor.RoomNode boss = bossRooms.get(0);
            assertTrue(boss != f.start, "seed " + seed + ": boss must not be the start room");
            assertTrue(dist[boss.x + boss.z * f.width] != Integer.MAX_VALUE, "seed " + seed + ": boss reachable");

            // exactly one of each of the special rooms (when enough rooms)
            assertOneOf(seed, f, RoomType.SHOP);
            assertOneOf(seed, f, RoomType.TREASURE);
            assertOneOf(seed, f, RoomType.ELITE);

            // bidirectional doors
            assertTrue(doorsBidirectional(f), "seed " + seed + ": all doors bidirectional");

            // --- the simulated player clears the floor: greedily walk to the nearest uncleared
            // room, "clear" it (collect loot), and finish by reaching + killing the boss ---
            Set<Floor.RoomNode> uncleared = new HashSet<>();
            for (Floor.RoomNode n : f.rooms()) if (n.type != RoomType.START) uncleared.add(n);
            int cleared = 0;
            int steps = 0;
            int guard = total * total; // the agent may backtrack; cap so a broken floor fails fast
            Floor.RoomNode cur = f.start;
            boolean bossCleared = false;
            while (!uncleared.isEmpty()) {
                assertTrue(steps++ < guard, "seed " + seed + ": player stuck before clearing all rooms");
                int[] d = distances(f, cur);
                Floor.RoomNode nearest = null;
                int best = Integer.MAX_VALUE;
                for (Floor.RoomNode n : uncleared) {
                    int dd = d[n.x + n.z * f.width];
                    if (dd < best) { best = dd; nearest = n; }
                }
                if (nearest == null) break; // shouldn't happen (all reachable), but stay safe
                uncleared.remove(nearest);
                if (nearest.type == RoomType.BOSS) bossCleared = true;
                cur = nearest;
                cleared++;
            }
            assertEquals(total - 1, cleared, "seed " + seed + ": player cleared every non-start room");
            assertTrue(bossCleared, "seed " + seed + ": player reached and killed the boss");
        }
    }

    private void assertOneOf(int seed, Floor f, RoomType type) {
        int count = 0;
        for (Floor.RoomNode n : f.rooms()) if (n.type == type) count++;
        assertEquals(1, count, "seed " + seed + ": exactly one " + type);
    }
}
