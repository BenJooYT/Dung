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
public final class FloorGraphGenerator {
    private final Random rng;
    private final FloorProfile profile;
    private final int width;
    private final int height;
    private final int roomCount;
    private final int floorIndex;

    public FloorGraphGenerator(Random rng, FloorProfile profile, int floorIndex) {
        this.rng = rng;
        this.profile = profile;
        this.width = profile.width();
        this.height = profile.height();
        this.roomCount = profile.roomCount();
        this.floorIndex = floorIndex;
    }

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    public Floor generate() {
        if (profile.layout() == FloorLayout.HUB) return generateHub();
        Floor f = new Floor(floorIndex, width, height);
        f.archetype = profile.archetype();
        f.modifiers.addAll(profile.modifiers());
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
                    rn.randomizeShape(rng, preferredShape());
                    rn.randomizeFeatures(rng, profile.archetype());
                    maybeApplyTemplate(rn);
                    applyModifierFeatures(rn);
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

        // Branching pass. Classic profiles keep the original leaf-only pass; loop profiles try
        // to close cycles while placing branch rooms.
        int cyclesAdded = 0;
        if (profile.targetLoops() > 0) {
            cyclesAdded = branchAndBraid(f, boss);
        } else {
            branchLeaves(f, boss);
        }
        addLoops(f, Math.max(0, profile.targetLoops() - cyclesAdded));

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
        // UPGRADE (Persist) room: guaranteed every profile.upgradeEveryFloors() floors. It replaces
        // one combat room (kept in the door graph so it stays reachable), placed as deep as possible.
        if (profile.upgradeEveryFloors() > 0
                && (floorIndex + 1) % profile.upgradeEveryFloors() == 0 && !combat.isEmpty()) {
            Floor.RoomNode upgrade = combat.stream()
                    .max(java.util.Comparator.comparingInt(n -> dist[n.x + n.z * width]))
                    .orElse(combat.get(0));
            upgrade.type = RoomType.UPGRADE;
            combat.remove(upgrade);
        }
        // EVENT: one optional loot/encounter room on larger floors.
        if (!combat.isEmpty() && profile.roomCount() >= 8) {
            Floor.RoomNode event = combat.get(rng.nextInt(combat.size()));
            event.type = RoomType.EVENT;
            event.role = RoomRole.OPTIONAL;
            event.features.add(RoomFeature.CHEST_PLATFORM);
            combat.remove(event);
        }
        // LOCKED: place 1-2 locked rooms on dead-end branches (rooms with only 1 door).
        // Pick from remaining combat rooms that are dead-ends and not the boss. Dead-ends
        // adjacent to START are excluded: locking one would iron-bar the start room's only
        // exit and trap the party with no key.
        List<Floor.RoomNode> deadEnds = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.COMBAT || n == boss) continue;
            int doors = 0;
            for (boolean d : n.doors) if (d) doors++;
            if (doors != 1) continue;
            boolean adjacentToStart = false;
            for (int d = 0; d < 4; d++) {
                if (f.at(n.x + DX[d], n.z + DZ[d]) == start) { adjacentToStart = true; break; }
            }
            if (!adjacentToStart) deadEnds.add(n);
        }
        Collections.shuffle(deadEnds, rng);
        int lockedCount = Math.min(1 + rng.nextInt(2), deadEnds.size()); // 1 or 2
        for (int i = 0; i < lockedCount; i++) {
            deadEnds.get(i).type = RoomType.LOCKED;
        }
        f.boss = boss;
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.COMBAT && n.type != RoomType.EVENT) {
                n.shape = RoomShape.RECTANGLE;
                n.features.clear();
                n.template = null;
            }
        }
        return f;
    }

    /**
     * Hub layout: a central chamber with four cardinal arms, diagonal ring corners that close
     * loops, arm extensions, and optional side rooms. The boss sits at the end of a primary arm.
     */
    private Floor generateHub() {
        Floor f = new Floor(floorIndex, width, height);
        f.archetype = profile.archetype();
        f.modifiers.addAll(profile.modifiers());
        Floor.RoomNode hub = f.start;
        hub.role = RoomRole.HUB;
        int cx = hub.x, cz = hub.z;

        List<Floor.RoomNode> arms = new ArrayList<>();
        List<Floor.RoomNode> ring = new ArrayList<>();
        List<Floor.RoomNode> optional = new ArrayList<>();

        List<Integer> dirs = new ArrayList<>();
        for (int d = 0; d < 4; d++) dirs.add(d);
        Collections.shuffle(dirs, rng);
        for (int d : dirs) {
            if (f.roomCount() >= roomCount) break;
            int nx = cx + DX[d], nz = cz + DZ[d];
            if (!f.inBounds(nx, nz) || f.at(nx, nz) != null) continue;
            Floor.RoomNode arm = placeRoom(f, nx, nz);
            openDoor(f, hub, d);
            arms.add(arm);
        }

        List<int[]> corners = new ArrayList<>();
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                int x = cx + sx, z = cz + sz;
                if (f.inBounds(x, z) && f.at(x, z) == null) corners.add(new int[]{x, z});
            }
        }
        Collections.shuffle(corners, rng);
        for (int[] c : corners) {
            if (f.roomCount() >= roomCount) break;
            int links = 0;
            for (int d = 0; d < 4; d++) {
                if (f.at(c[0] + DX[d], c[1] + DZ[d]) != null) links++;
            }
            if (links == 0) continue;
            Floor.RoomNode corner = placeRoom(f, c[0], c[1]);
            for (int d = 0; d < 4; d++) {
                if (f.at(c[0] + DX[d], c[1] + DZ[d]) != null) openDoor(f, corner, d);
            }
            if (links >= 2) ring.add(corner); else optional.add(corner);
        }

        for (int d : dirs) {
            if (f.roomCount() >= roomCount) break;
            Floor.RoomNode end = f.at(cx + DX[d], cz + DZ[d]);
            if (end == null) continue;
            while (f.roomCount() < roomCount) {
                int nx = end.x + DX[d], nz = end.z + DZ[d];
                if (!f.inBounds(nx, nz) || f.at(nx, nz) != null) break;
                Floor.RoomNode rn = placeRoom(f, nx, nz);
                openDoor(f, end, d);
                arms.add(rn);
                end = rn;
            }
        }

        int guard = 0;
        while (f.roomCount() < roomCount && guard++ < 400) {
            List<Floor.RoomNode> rooms = new ArrayList<>();
            for (Floor.RoomNode n : f.rooms()) rooms.add(n);
            Collections.shuffle(rooms, rng);
            boolean placed = false;
            for (Floor.RoomNode n : rooms) {
                if (doorCount(n) >= 4) continue;
                List<Integer> free = new ArrayList<>();
                for (int d = 0; d < 4; d++) {
                    if (n.doors[d]) continue;
                    int nx = n.x + DX[d], nz = n.z + DZ[d];
                    if (f.inBounds(nx, nz) && f.at(nx, nz) == null) free.add(d);
                }
                if (free.isEmpty()) continue;
                int d = free.get(rng.nextInt(free.size()));
                Floor.RoomNode rn = placeRoom(f, n.x + DX[d], n.z + DZ[d]);
                openDoor(f, n, d);
                optional.add(rn);
                placed = true;
                break;
            }
            if (!placed) break;
        }

        int[] dist = bfs(f, hub);
        Floor.RoomNode boss = null;
        int best = -1;
        for (Floor.RoomNode arm : arms) {
            int d = dist[arm.x + arm.z * width];
            if (d > best) {
                best = d;
                boss = arm;
            }
        }
        if (boss == null) boss = farthest(f, dist);
        if (f.roomCount() >= 2) {
            boss.type = RoomType.BOSS;
            boss.role = RoomRole.REQUIRED;
        }
        f.boss = boss;

        assignHubRooms(f, dist, boss, arms, ring, optional);
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.COMBAT && n.type != RoomType.EVENT) {
                n.shape = RoomShape.RECTANGLE;
                n.features.clear();
                n.template = null;
            }
        }
        return f;
    }

    private int[] bfs(Floor f, Floor.RoomNode start) {
        int[] dist = new int[width * height];
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<Floor.RoomNode> q = new ArrayDeque<>();
        dist[start.x + start.z * width] = 0;
        q.add(start);
        while (!q.isEmpty()) {
            Floor.RoomNode n = q.poll();
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = n.x + DX[d], nz = n.z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                Floor.RoomNode m = f.at(nx, nz);
                if (m == null) continue;
                int i = nx + nz * width;
                if (dist[i] > dist[n.x + n.z * width] + 1) {
                    dist[i] = dist[n.x + n.z * width] + 1;
                    q.add(m);
                }
            }
        }
        return dist;
    }

    private Floor.RoomNode farthest(Floor f, int[] dist) {
        Floor.RoomNode best = f.start;
        int max = -1;
        for (Floor.RoomNode n : f.rooms()) {
            int d = dist[n.x + n.z * width];
            if (d > max) {
                max = d;
                best = n;
            }
        }
        return best;
    }

    private void assignHubRooms(Floor f, int[] dist, Floor.RoomNode boss,
            List<Floor.RoomNode> arms, List<Floor.RoomNode> ring,
            List<Floor.RoomNode> optional) {
        List<Floor.RoomNode> primary = new ArrayList<>();
        for (Floor.RoomNode n : arms) {
            if (n != boss && n.type == RoomType.COMBAT) primary.add(n);
        }
        List<Floor.RoomNode> extra = new ArrayList<>();
        for (Floor.RoomNode n : ring) {
            if (n != boss && n.type == RoomType.COMBAT) extra.add(n);
        }
        for (Floor.RoomNode n : optional) {
            if (n != boss && n.type == RoomType.COMBAT) extra.add(n);
        }
        Collections.shuffle(primary, rng);
        Collections.shuffle(extra, rng);

        Floor.RoomNode shop = take(primary, extra);
        if (shop != null) {
            shop.type = RoomType.SHOP;
            shop.role = RoomRole.REQUIRED;
        }
        Floor.RoomNode treasure = take(primary, extra);
        if (treasure != null) {
            treasure.type = RoomType.TREASURE;
            treasure.role = RoomRole.REQUIRED;
        }

        Floor.RoomNode secret = takeDeadEnd(f, primary, extra, dist);
        if (secret != null) makeSecret(f, secret);

        Floor.RoomNode elite = takeDeepest(primary, extra, dist);
        if (elite != null) {
            elite.type = RoomType.ELITE;
            elite.role = RoomRole.REQUIRED;
        }

        if (profile.upgradeEveryFloors() > 0
                && (floorIndex + 1) % profile.upgradeEveryFloors() == 0) {
            Floor.RoomNode upgrade = take(primary, extra);
            if (upgrade != null) {
                upgrade.type = RoomType.UPGRADE;
                upgrade.role = RoomRole.REQUIRED;
            }
        }

        Floor.RoomNode event = take(primary, extra);
        if (event != null) {
            event.type = RoomType.EVENT;
            event.role = RoomRole.OPTIONAL;
            event.features.add(RoomFeature.CHEST_PLATFORM);
        }

        List<Floor.RoomNode> deadEnds = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.COMBAT || n == boss) continue;
            if (doorCount(n) != 1) continue;
            if (adjacentToStart(f, n)) continue;
            deadEnds.add(n);
        }
        Collections.shuffle(deadEnds, rng);
        int lockedCount = Math.min(1 + rng.nextInt(2), deadEnds.size());
        for (int i = 0; i < lockedCount; i++) {
            deadEnds.get(i).type = RoomType.LOCKED;
            deadEnds.get(i).role = RoomRole.OPTIONAL;
        }

        for (Floor.RoomNode n : ring) {
            if (n.role == RoomRole.NORMAL) n.role = RoomRole.OPTIONAL;
        }
        for (Floor.RoomNode n : optional) {
            if (n.role == RoomRole.NORMAL) n.role = RoomRole.OPTIONAL;
        }
    }

    private Floor.RoomNode take(List<Floor.RoomNode> primary, List<Floor.RoomNode> extra) {
        if (!primary.isEmpty()) return primary.remove(0);
        if (!extra.isEmpty()) return extra.remove(0);
        return null;
    }

    private Floor.RoomNode takeDeepest(List<Floor.RoomNode> primary,
            List<Floor.RoomNode> extra, int[] dist) {
        List<Floor.RoomNode> pool = new ArrayList<>(primary);
        pool.addAll(extra);
        Floor.RoomNode best = null;
        int max = -1;
        for (Floor.RoomNode n : pool) {
            int d = dist[n.x + n.z * width];
            if (d > max) {
                max = d;
                best = n;
            }
        }
        if (best != null) {
            primary.remove(best);
            extra.remove(best);
        }
        return best;
    }

    private Floor.RoomNode takeDeadEnd(Floor f, List<Floor.RoomNode> primary,
            List<Floor.RoomNode> extra, int[] dist) {
        List<Floor.RoomNode> pool = new ArrayList<>(primary);
        pool.addAll(extra);
        Collections.shuffle(pool, rng);
        for (Floor.RoomNode n : pool) {
            if (n.type != RoomType.COMBAT || doorCount(n) != 1) continue;
            if (dist[n.x + n.z * width] < 2) continue;
            int dir = -1;
            for (int d = 0; d < 4; d++) {
                if (n.doors[d]) {
                    dir = d;
                    break;
                }
            }
            Floor.RoomNode parent = dir < 0 ? null : f.at(n.x + DX[dir], n.z + DZ[dir]);
            if (parent == null || doorCount(parent) < 2) continue;
            primary.remove(n);
            extra.remove(n);
            return n;
        }
        return null;
    }

    private void makeSecret(Floor f, Floor.RoomNode secret) {
        int dir = -1;
        for (int d = 0; d < 4; d++) {
            if (secret.doors[d]) {
                dir = d;
                break;
            }
        }
        if (dir < 0) return;
        Floor.RoomNode parent = f.at(secret.x + DX[dir], secret.z + DZ[dir]);
        if (parent == null) return;
        secret.type = RoomType.SECRET;
        secret.role = RoomRole.OPTIONAL;
        secret.secretParent = parent;
        secret.secretWallDir = dir;
        secret.doors[dir] = false;
        parent.doors[(dir + 2) % 4] = false;
    }

    private static boolean adjacentToStart(Floor f, Floor.RoomNode n) {
        for (int d = 0; d < 4; d++) {
            if (f.at(n.x + DX[d], n.z + DZ[d]) == f.start) return true;
        }
        return false;
    }

    /** Original leaf-only branch pass, kept for profiles that do not ask for loops. */
    private void branchLeaves(Floor f, Floor.RoomNode boss) {
        List<Floor.RoomNode> candidates = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.COMBAT && n != boss) candidates.add(n);
        Collections.shuffle(candidates, rng);
        int branches = 0;
        for (Floor.RoomNode leaf : candidates) {
            if (branches >= profile.maxBranches()) break;
            for (int d = 0; d < 4; d++) {
                int nx = leaf.x + DX[d], nz = leaf.z + DZ[d];
                if (f.inBounds(nx, nz) && f.at(nx, nz) == null) {
                    Floor.RoomNode rn = new Floor.RoomNode(nx, nz, RoomType.COMBAT);
                    rn.randomizeShape(rng, preferredShape());
                    rn.randomizeFeatures(rng, profile.archetype());
                    maybeApplyTemplate(rn);
                    applyModifierFeatures(rn);
                    f.add(rn);
                    rn.open((d + 2) % 4);
                    leaf.open(d);
                    branches++;
                    break;
                }
            }
        }
    }

    /**
     * Branch pass for loop profiles: first place rooms that touch two existing rooms, then use
     * a two-room braid when no single cell can close a cycle, and finally add normal leaves.
     */
    private int branchAndBraid(Floor f, Floor.RoomNode boss) {
        List<Floor.RoomNode> candidates = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.COMBAT && n != boss) candidates.add(n);
        Collections.shuffle(candidates, rng);

        int added = 0;
        int cycles = 0;
        int max = profile.maxBranches();

        while (cycles < profile.targetLoops() && added < max) {
            List<int[]> cells = freeCellsWithNeighbors(f, boss, 2);
            if (cells.isEmpty()) break;
            int[] cell = cells.get(rng.nextInt(cells.size()));
            List<Integer> dirs = loopDirsAt(f, cell[0], cell[1], boss);
            if (dirs.size() < 2) break;
            Collections.shuffle(dirs, rng);
            Floor.RoomNode rn = placeRoom(f, cell[0], cell[1]);
            openDoor(f, rn, dirs.get(0));
            openDoor(f, rn, dirs.get(1));
            added++;
            cycles++;
        }

        while (cycles < profile.targetLoops() && added + 2 <= max) {
            if (!addTwoRoomLoop(f, boss)) break;
            added += 2;
            cycles++;
        }

        for (Floor.RoomNode leaf : candidates) {
            if (added >= max) break;
            for (int d = 0; d < 4; d++) {
                int nx = leaf.x + DX[d], nz = leaf.z + DZ[d];
                if (f.inBounds(nx, nz) && f.at(nx, nz) == null) {
                    Floor.RoomNode rn = new Floor.RoomNode(nx, nz, RoomType.COMBAT);
                    rn.randomizeShape(rng, preferredShape());
                    rn.randomizeFeatures(rng, profile.archetype());
                    maybeApplyTemplate(rn);
                    applyModifierFeatures(rn);
                    f.add(rn);
                    rn.open((d + 2) % 4);
                    leaf.open(d);
                    added++;
                    break;
                }
            }
        }
        return cycles;
    }

    /** Place two rooms that form a cycle with the existing tree. */
    private boolean addTwoRoomLoop(Floor f, Floor.RoomNode boss) {
        List<int[]> cCells = freeCellsWithNeighbors(f, boss, 1);
        Collections.shuffle(cCells, rng);
        for (int[] c : cCells) {
            List<Integer> aDirs = loopDirsAt(f, c[0], c[1], boss);
            if (aDirs.isEmpty()) continue;
            int aDir = aDirs.get(rng.nextInt(aDirs.size()));

            List<int[]> dCells = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int dx = c[0] + DX[d], dz = c[1] + DZ[d];
                if (!f.inBounds(dx, dz) || f.at(dx, dz) != null) continue;
                if (!loopDirsAt(f, dx, dz, boss).isEmpty()) dCells.add(new int[]{dx, dz});
            }
            if (dCells.isEmpty()) continue;

            int[] dcell = dCells.get(rng.nextInt(dCells.size()));
            List<Integer> bDirs = loopDirsAt(f, dcell[0], dcell[1], boss);
            int bDir = bDirs.get(rng.nextInt(bDirs.size()));

            Floor.RoomNode cRoom = placeRoom(f, c[0], c[1]);
            openDoor(f, cRoom, aDir);

            Floor.RoomNode dRoom = placeRoom(f, dcell[0], dcell[1]);
            int dToC = -1;
            for (int d = 0; d < 4; d++) {
                if (dcell[0] + DX[d] == c[0] && dcell[1] + DZ[d] == c[1]) {
                    dToC = d;
                    break;
                }
            }
            if (dToC < 0) return false;
            openDoor(f, dRoom, dToC);
            openDoor(f, dRoom, bDir);
            return true;
        }
        return false;
    }

    /** Close up to {@code target} extra connections between adjacent rooms. */
    private int addLoops(Floor f, int target) {
        if (target <= 0) return 0;
        List<Floor.RoomNode> rooms = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.SECRET && n.type != RoomType.BOSS) rooms.add(n);
        }
        Collections.shuffle(rooms, rng);
        int added = 0;
        for (Floor.RoomNode a : rooms) {
            if (added >= target) break;
            for (int d = 0; d < 4 && added < target; d++) {
                if (a.doors[d] || doorCount(a) >= 4) continue;
                int nx = a.x + DX[d], nz = a.z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                Floor.RoomNode b = f.at(nx, nz);
                if (b == null || b.type == RoomType.SECRET || b.type == RoomType.BOSS) continue;
                if (b.doors[(d + 2) % 4] || doorCount(b) >= 4) continue;
                a.open(d);
                b.open((d + 2) % 4);
                added++;
            }
        }
        return added;
    }

    private List<int[]> freeCellsWithNeighbors(Floor f, Floor.RoomNode boss, int min) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                if (f.at(x, z) != null) continue;
                int count = 0;
                for (int d = 0; d < 4; d++) {
                    Floor.RoomNode m = f.at(x + DX[d], z + DZ[d]);
                    if (m != null && m != boss) count++;
                }
                if (count >= min) out.add(new int[]{x, z});
            }
        }
        return out;
    }

    private List<Integer> loopDirsAt(Floor f, int x, int z, Floor.RoomNode boss) {
        List<Integer> dirs = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            Floor.RoomNode m = f.at(x + DX[d], z + DZ[d]);
            if (m != null && m != boss) dirs.add(d);
        }
        return dirs;
    }

    private RoomShape preferredShape() {
        Archetype archetype = profile.archetype();
        return archetype == null ? null : archetype.roomShape();
    }

    private void maybeApplyTemplate(Floor.RoomNode room) {
        if (rng.nextInt(100) >= 35) return;
        RoomTemplate template = RoomTemplate.pick(profile.archetype(), rng);
        if (template != null) template.applyTo(room, rng);
    }

    private void applyModifierFeatures(Floor.RoomNode room) {
        if (room.features.contains(RoomFeature.PLATFORMS)) return;
        if (profile.modifiers().contains(FloorModifier.FLOODED)) {
            room.features.add(RoomFeature.WATER_POOL);
        }
        if (profile.modifiers().contains(FloorModifier.OVERGROWN)) {
            room.features.add(RoomFeature.ROOTS);
        }
    }

    private Floor.RoomNode placeRoom(Floor f, int x, int z) {
        Floor.RoomNode rn = new Floor.RoomNode(x, z, RoomType.COMBAT);
        rn.randomizeShape(rng, preferredShape());
        rn.randomizeFeatures(rng, profile.archetype());
        maybeApplyTemplate(rn);
        applyModifierFeatures(rn);
        f.add(rn);
        return rn;
    }

    private void openDoor(Floor f, Floor.RoomNode rn, int d) {
        Floor.RoomNode m = f.at(rn.x + DX[d], rn.z + DZ[d]);
        if (m == null) return;
        rn.open(d);
        m.open((d + 2) % 4);
    }

    private static int doorCount(Floor.RoomNode n) {
        int count = 0;
        for (boolean d : n.doors) if (d) count++;
        return count;
    }
}
