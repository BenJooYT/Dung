package com.lieyabull.dung.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Checks the graph invariants a generated floor must satisfy before it is built. */
public final class FloorValidator {
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    public record Result(List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private FloorValidator() {}

    public static Result validate(Floor f, FloorProfile profile) {
        List<String> errors = new ArrayList<>();
        if (f == null || f.start == null) {
            errors.add("floor has no START room");
            return new Result(List.copyOf(errors));
        }
        if (f.start.type != RoomType.START) {
            errors.add("start room is " + f.start.type + ", expected START");
        }

        int[] dist = distances(f, errors);
        int nonSecret = 0;
        int doors = 0;
        int shops = 0, treasures = 0, elites = 0, bosses = 0, upgrades = 0;
        int locked = 0, secrets = 0, hubs = 0;
        boolean allReachable = true;
        boolean doorsValid = true;

        for (Floor.RoomNode n : f.rooms()) {
            if (!f.inBounds(n.x, n.z)) {
                errors.add("room (" + n.x + "," + n.z + ") is outside the grid");
                allReachable = false;
                continue;
            }
            switch (n.type) {
                case SHOP -> shops++;
                case TREASURE -> treasures++;
                case ELITE -> elites++;
                case BOSS -> bosses++;
                case UPGRADE -> upgrades++;
                case LOCKED -> locked++;
                case SECRET -> secrets++;
                default -> { }
            }
            if (n.role == RoomRole.HUB) hubs++;
            if (n.role.required() && dist[index(f, n)] < 0) {
                errors.add("required room (" + n.x + "," + n.z + ") is unreachable");
                allReachable = false;
            }
            if (n.type == RoomType.SECRET) continue;

            nonSecret++;
            for (boolean d : n.doors) if (d) doors++;
            if (dist[index(f, n)] < 0) {
                errors.add("room (" + n.x + "," + n.z + ") " + n.type + " is unreachable");
                allReachable = false;
            }
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = n.x + DX[d], nz = n.z + DZ[d];
                Floor.RoomNode m = f.inBounds(nx, nz) ? f.at(nx, nz) : null;
                if (m == null) {
                    errors.add("door from (" + n.x + "," + n.z + ") dir " + d + " leads nowhere");
                    doorsValid = false;
                    continue;
                }
                if (!m.doors[(d + 2) % 4]) {
                    errors.add("door from (" + n.x + "," + n.z + ") dir " + d + " is one-way");
                    doorsValid = false;
                }
            }
        }

        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.SECRET) continue;
            if (doorCount(n) != 0) {
                errors.add("SECRET (" + n.x + "," + n.z + ") must have no doors");
            }
            if (n.secretParent == null) {
                errors.add("SECRET (" + n.x + "," + n.z + ") has no parent");
                continue;
            }
            if (n.secretWallDir < 0 || n.secretWallDir > 3) {
                errors.add("SECRET (" + n.x + "," + n.z + ") has invalid wall direction");
                continue;
            }
            int px = n.x + DX[n.secretWallDir], pz = n.z + DZ[n.secretWallDir];
            if (n.secretParent.x != px || n.secretParent.z != pz) {
                errors.add("SECRET (" + n.x + "," + n.z + ") parent is not adjacent in the wall direction");
            }
            if (n.secretParent.type == RoomType.SECRET) {
                errors.add("SECRET (" + n.x + "," + n.z + ") parent is another SECRET");
            }
            if (!f.inBounds(n.secretParent.x, n.secretParent.z)
                    || dist[index(f, n.secretParent)] < 0) {
                errors.add("SECRET (" + n.x + "," + n.z + ") parent is unreachable");
            }
        }

        if (f.roomCount() >= 2) {
            if (bosses != 1) {
                errors.add("expected exactly one BOSS room, found " + bosses);
            }
            if (f.boss == null || f.boss.type != RoomType.BOSS) {
                errors.add("floor boss reference is not a BOSS room");
            }
        }

        if (profile.layout() == FloorLayout.HUB) {
            if (hubs != 1) errors.add("expected exactly one HUB room, found " + hubs);
        } else if (hubs != 0) {
            errors.add("HUB room in a non-hub layout");
        }

        if (f.roomCount() >= 7) {
            if (shops != 1) errors.add("expected exactly one SHOP room, found " + shops);
            if (treasures != 1) errors.add("expected exactly one TREASURE room, found " + treasures);
            if (elites != 1) errors.add("expected exactly one ELITE room, found " + elites);
            boolean upgradeFloor = profile.upgradeEveryFloors() > 0
                    && (f.floorIndex + 1) % profile.upgradeEveryFloors() == 0;
            if (upgradeFloor && upgrades != 1) {
                errors.add("expected exactly one UPGRADE room, found " + upgrades);
            }
            if (!upgradeFloor && upgrades != 0) {
                errors.add("expected no UPGRADE room, found " + upgrades);
            }
        }

        if (secrets > 1) errors.add("expected at most one SECRET room, found " + secrets);
        if (locked > 2) errors.add("expected at most two LOCKED rooms, found " + locked);

        for (Floor.RoomNode n : f.rooms()) {
            if (n.type != RoomType.LOCKED) continue;
            for (int d = 0; d < 4; d++) {
                if (f.at(n.x + DX[d], n.z + DZ[d]) == f.start) {
                    errors.add("LOCKED room (" + n.x + "," + n.z + ") is adjacent to START");
                }
            }
        }

        if (f.roomCount() >= 2 && f.boss != null && f.boss.type == RoomType.BOSS) {
            if (!f.inBounds(f.boss.x, f.boss.z) || dist[index(f, f.boss)] < 0) {
                errors.add("BOSS room is unreachable");
            } else if (dist[index(f, f.boss)] < profile.minBossDistance()) {
                errors.add("BOSS distance " + dist[index(f, f.boss)]
                        + " is below the required " + profile.minBossDistance());
            }
        }

        if (allReachable && doorsValid && nonSecret > 0) {
            int loops = doors / 2 - nonSecret + 1;
            if (loops < profile.minLoops()) {
                errors.add("expected at least " + profile.minLoops()
                        + " loop(s), found " + loops);
            }
        }

        return new Result(List.copyOf(errors));
    }

    private static int[] distances(Floor f, List<String> errors) {
        int[] dist = new int[Math.max(1, f.width * f.height)];
        Arrays.fill(dist, -1);
        if (!f.inBounds(f.start.x, f.start.z)) {
            errors.add("START room is outside the grid");
            return dist;
        }
        dist[index(f, f.start)] = 0;
        ArrayDeque<Floor.RoomNode> q = new ArrayDeque<>();
        q.add(f.start);
        while (!q.isEmpty()) {
            Floor.RoomNode n = q.poll();
            int from = dist[index(f, n)];
            for (int d = 0; d < 4; d++) {
                if (!n.doors[d]) continue;
                int nx = n.x + DX[d], nz = n.z + DZ[d];
                if (!f.inBounds(nx, nz)) continue;
                Floor.RoomNode m = f.at(nx, nz);
                if (m == null) continue;
                int i = nx + nz * f.width;
                if (dist[i] < 0) {
                    dist[i] = from + 1;
                    q.add(m);
                }
            }
        }
        return dist;
    }

    private static int index(Floor f, Floor.RoomNode n) {
        return n.x + n.z * f.width;
    }

    private static int doorCount(Floor.RoomNode n) {
        int count = 0;
        for (boolean d : n.doors) if (d) count++;
        return count;
    }
}
