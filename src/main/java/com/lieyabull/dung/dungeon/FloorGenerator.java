package com.lieyabull.dung.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates an Isaac-like branching floor. A random walk carves a connected graph; the room
 * farthest (by BFS distance) from START becomes BOSS; treasure/shop/secret/elite are placed
 * at sensible depths (shop early, treasure mid, elite late). Ensures every non-start room is
 * reachable and has an inward door.
 */
public final class FloorGenerator {
    private final Random rng;
    private final int width;
    private final int height;
    private final int roomCount;
    private final int floorIndex;

    public FloorGenerator(Random rng, int width, int height, int roomCount, int floorIndex) {
        this.rng = rng;
        this.width = width;
        this.height = height;
        this.roomCount = roomCount;
        this.floorIndex = floorIndex;
    }

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    public Floor generate() {
        Floor f = new Floor(0, width, height);
        Floor.RoomNode start = f.start;
        List<Floor.RoomNode> nodes = new ArrayList<>();
        nodes.add(start);

        // random walk to place rooms, avoiding overlaps
        int sx = start.x, sz = start.z;
        int[][] placed = new int[width][height];
        placed[sx][sz] = 1;
        int cx = sx, cz = sz;
        int failCount = 0; // consecutive placement failures; triggers a backtrack to avoid traps
        for (int i = 0; i < roomCount - 1; i++) {
            int d = rng.nextInt(4);
            boolean placedRoom = false;
            for (int attempt = 0; attempt < 6; attempt++) {
                int nd = (d + attempt) % 4; // bias toward straight, allow turns
                int nx = cx + DX[nd], nz = cz + DZ[nd];
                if (f.inBounds(nx, nz) && placed[nx][nz] == 0) {
                    Floor.RoomNode rn = new Floor.RoomNode(nx, nz, RoomType.COMBAT);
                    rn.randomizeShape(rng);
                    f.add(rn);
                    nodes.add(rn);
                    placed[nx][nz] = 1;
                    // link both directions
                    rn.open((nd + 2) % 4);
                    f.at(cx, cz).open(nd);
                    cx = nx; cz = nz;
                    placedRoom = true;
                    break;
                }
            }
            if (placedRoom) {
                failCount = 0;
            } else if (failCount >= 2 && nodes.size() > 1) {
                // trapped: restart the walk from a random already-placed room so more rooms fit
                Floor.RoomNode rn = nodes.get(rng.nextInt(nodes.size()));
                cx = rn.x; cz = rn.z;
                failCount = 0;
            } else {
                failCount++;
            }
        }

        // BFS distances from start
        int[] dist = new int[width * height];
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[sx + sz * width] = 0;
        q.add(new int[]{sx, sz});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], z = p[1];
            Floor.RoomNode n = f.at(x, z);
            if (n == null) continue;
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = x + DX[d], nz = z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                if (dist[nx + nz * width] > dist[x + z * width] + 1) {
                    dist[nx + nz * width] = dist[x + z * width] + 1;
                    q.add(new int[]{nx, nz});
                }
            }
        }

        // farthest reachable = BOSS (skip for a degenerate 1-room floor so START isn't the boss)
        Floor.RoomNode boss = start;
        if (f.roomCount() >= 2) {
            int maxD = -1;
            for (Floor.RoomNode n : f.rooms()) {
                int d = dist[n.x + n.z * width];
                if (d > maxD) { maxD = d; boss = n; }
            }
            boss.type = RoomType.BOSS;
        }

        // Branching pass: turn some path rooms into forks (2+ doors) so exploration is a real
        // choice with dead-ends, not a snake. Branch off any combat room that has a free
        // neighbor cell (the snake alone has no 1-door leaves to grow from).
        List<Floor.RoomNode> candidates = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.COMBAT && n != boss) candidates.add(n);
        Collections.shuffle(candidates, rng);
        int branches = 0;
        for (Floor.RoomNode leaf : candidates) {
            if (branches >= 2) break;
            for (int d = 0; d < 4; d++) {
                int nx = leaf.x + DX[d], nz = leaf.z + DZ[d];
                if (f.inBounds(nx, nz) && f.at(nx, nz) == null) {
                    Floor.RoomNode rn = new Floor.RoomNode(nx, nz, RoomType.COMBAT);
                    rn.randomizeShape(rng);
                    f.add(rn);
                    rn.open((d + 2) % 4);
                    leaf.open(d);
                    branches++;
                    break;
                }
            }
        }
        // recompute distances (boss may still be farthest)
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        dist[sx + sz * width] = 0;
        q.clear();
        q.add(new int[]{sx, sz});
        while (!q.isEmpty()) {
            int[] p2 = q.poll();
            int x = p2[0], z = p2[1];
            Floor.RoomNode n = f.at(x, z);
            if (n == null) continue;
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = x + DX[d], nz = z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                if (dist[nx + nz * width] > dist[x + z * width] + 1) {
                    dist[nx + nz * width] = dist[x + z * width] + 1;
                    q.add(new int[]{nx, nz});
                }
            }
        }
        Floor.RoomNode prevBoss = boss;
        int maxD2 = -1;
        for (Floor.RoomNode n : f.rooms()) {
            int d = dist[n.x + n.z * width];
            if (d > maxD2) { maxD2 = d; boss = n; }
        }
        // reassigning the farthest room after branching can pick a NEW boss; demote the old one so
        // the floor never ends up with two BOSS rooms (skip entirely on a degenerate 1-room floor)
        if (prevBoss != boss && prevBoss.type == RoomType.BOSS) prevBoss.type = RoomType.COMBAT;
        if (f.roomCount() >= 2) boss.type = RoomType.BOSS;

        // placement: guarantee exactly one of SHOP/TREASURE/ELITE, chosen from distinct rooms.
        // Filter COMBAT (non-boss, dist>=1); fall back to ANY combat room when a tier is empty.
        List<Floor.RoomNode> combat = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.COMBAT && n != boss) combat.add(n);
        Collections.shuffle(combat, rng);
        // SHOP: any combat room (prefer one within dist<=2)
        Floor.RoomNode shopRoom = combat.stream()
                .filter(n -> dist[n.x + n.z * width] <= 2)
                .findFirst().orElse(combat.isEmpty() ? null : combat.get(0));
        if (shopRoom != null) { shopRoom.type = RoomType.SHOP; combat.remove(shopRoom); }
        // TREASURE: first remaining combat room
        if (!combat.isEmpty()) { combat.get(0).type = RoomType.TREASURE; combat.remove(0); }
        // SECRET: a deep dead-end leaf (only 1 door) -> hidden bonus loot behind a destructible
        // wall. Pick before ELITE so the deepest dead-end isn't consumed by the elite placement.
        // The secret room is disconnected from the graph: its single door is removed, and the
        // adjacent combat room becomes its "secretParent" with a destructible wall on that side.
        Floor.RoomNode secret = null;
        for (Floor.RoomNode n : combat) {
            int doors = 0;
            for (boolean d : n.doors) if (d) doors++;
            if (doors == 1 && dist[n.x + n.z * width] >= 2) { secret = n; break; }
        }
        if (secret != null) {
            // Find the adjacent combat room (the one this secret connects to via its single door)
            Floor.RoomNode secretParent = null;
            int secretDir = -1;
            for (int d = 0; d < 4; d++) {
                if (secret.doors[d]) {
                    int nx = secret.x + DX[d];
                    int nz = secret.z + DZ[d];
                    Floor.RoomNode parent = f.at(nx, nz);
                    if (parent != null) {
                        // Only disconnect if the parent has at least 2 doors (so it stays reachable)
                        int parentDoors = 0;
                        for (boolean pd : parent.doors) if (pd) parentDoors++;
                        if (parentDoors >= 2) {
                            secretParent = parent;
                            secretDir = d;
                        }
                    }
                    break;
                }
            }
            if (secretParent != null) {
                secret.type = RoomType.SECRET;
                combat.remove(secret);
                secret.secretParent = secretParent;
                secret.secretWallDir = secretDir;
                // Remove the door connection so the secret is disconnected
                secret.doors[secretDir] = false;
                secretParent.doors[(secretDir + 2) % 4] = false;
            }
        }
        // ELITE: a remaining combat room as deep as possible
        if (!combat.isEmpty()) {
            Floor.RoomNode elite = combat.stream()
                    .max(java.util.Comparator.comparingInt(n -> dist[n.x + n.z * width]))
                    .orElse(combat.get(0));
            elite.type = RoomType.ELITE;
            combat.remove(elite);
        }
        // UPGRADE (Persist) room: guaranteed every 5 floors (floor 5, 10, 15, ...). It replaces
        // one combat room (kept in the door graph so it stays reachable), placed as deep as possible.
        if ((floorIndex + 1) % 5 == 0 && !combat.isEmpty()) {
            Floor.RoomNode upgrade = combat.stream()
                    .max(java.util.Comparator.comparingInt(n -> dist[n.x + n.z * width]))
                    .orElse(combat.get(0));
            upgrade.type = RoomType.UPGRADE;
            combat.remove(upgrade);
        }
        // LOCKED: place 1-2 locked rooms on dead-end branches (rooms with only 1 door).
        // Pick from remaining combat rooms that are dead-ends and not the boss.
        List<Floor.RoomNode> deadEnds = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.COMBAT || n == boss) continue;
            int doors = 0;
            for (boolean d : n.doors) if (d) doors++;
            if (doors == 1) deadEnds.add(n);
        }
        Collections.shuffle(deadEnds, rng);
        int lockedCount = Math.min(1 + rng.nextInt(2), deadEnds.size()); // 1 or 2
        for (int i = 0; i < lockedCount; i++) {
            deadEnds.get(i).type = RoomType.LOCKED;
        }
        f.boss = boss;
        return f;
    }
}