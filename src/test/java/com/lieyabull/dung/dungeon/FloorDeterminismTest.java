package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorDeterminismTest {

    private static String snapshot(Floor f) {
        List<String> rooms = new ArrayList<>();
        for (Floor.RoomNode n : f.rooms()) {
            StringBuilder sb = new StringBuilder();
            sb.append(n.x).append(',').append(n.z)
                    .append('|').append(n.type)
                    .append('|').append(n.sizeW).append('x').append(n.sizeH)
                    .append('|').append(n.shape)
                    .append('|');
            for (boolean d : n.doors) sb.append(d ? '1' : '0');
            sb.append('|').append(n.secretParent == null
                            ? "-" : n.secretParent.x + "," + n.secretParent.z)
                    .append('|').append(n.secretWallDir);
            rooms.add(sb.toString());
        }
        Collections.sort(rooms);
        return "boss=" + f.boss.x + "," + f.boss.z + "\n" + String.join("\n", rooms);
    }

    @Test
    void sameSeedProducesIdenticalFloor() {
        Floor a = new FloorGenerator(987654321L, FloorProfile.CLASSIC, 0).generate();
        Floor b = new FloorGenerator(987654321L, FloorProfile.CLASSIC, 0).generate();
        assertEquals(snapshot(a), snapshot(b));
    }

    @Test
    void legacyConstructorMatchesClassicProfile() {
        Floor legacy = new FloorGenerator(new Random(42), 9, 9, 7, 0).generate();
        Floor profiled = new FloorGenerator(new Random(42), FloorProfile.CLASSIC, 0).generate();
        assertEquals(snapshot(legacy), snapshot(profiled));
    }

    @Test
    void differentSeedsProduceDifferentFloors() {
        Set<String> floors = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            floors.add(snapshot(new FloorGenerator(seed, FloorProfile.CLASSIC, 0).generate()));
        }
        assertTrue(floors.size() > 1, "different seeds must change the generated floor");
    }

    @Test
    void loopProfileAddsRequestedLoops() {
        FloorProfile profile = new FloorProfile("loops", 12, 12, 24, 4, 3, 2, 3, 5,
                FloorLayout.CLASSIC, null, java.util.Set.of());
        Floor f = new FloorGenerator(2026L, profile, 0).generate();
        FloorValidator.Result result = FloorValidator.validate(f, profile);
        assertTrue(result.ok(), result.errors().toString());
        int rooms = 0;
        int doors = 0;
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type == RoomType.SECRET) continue;
            rooms++;
            for (boolean d : n.doors) if (d) doors++;
        }
        assertEquals(profile.targetLoops(), doors / 2 - rooms + 1);
    }

    @Test
    void sevenRoomLoopProfileBraidsAtLeastOneLoop() {
        FloorProfile profile = new FloorProfile("loops7", 9, 9, 7, 2, 1, 1, 0, 5,
                FloorLayout.CLASSIC, null, java.util.Set.of());
        for (long seed = 0; seed < 32; seed++) {
            Floor f = new FloorGenerator(seed, profile, 0).generate();
            int rooms = 0;
            int doors = 0;
            for (Floor.RoomNode n : f.rooms()) {
                if (n.type == RoomType.SECRET) continue;
                rooms++;
                for (boolean d : n.doors) if (d) doors++;
            }
            assertTrue(doors / 2 - rooms + 1 >= profile.minLoops(),
                    "seed " + seed + " should contain at least one loop");
        }
    }

    @Test
    void hubLayoutMarksStartAsLargeHub() {
        FloorProfile profile = FloorProfile.hub(9, 9, 7, 2, 0, 0, 0, 5);
        Floor f = new FloorGenerator(4242L, profile, 0).generate();
        assertEquals(RoomRole.HUB, f.start.role);
        int hubs = 0;
        for (Floor.RoomNode n : f.rooms()) if (n.role == RoomRole.HUB) hubs++;
        assertEquals(1, hubs);
        assertEquals(RoomGen.HUB_INTERIOR + 2 * RoomGen.WALL + 1, profile.minSpacing());

        for (Floor.RoomNode n : f.rooms()) RoomGen.scaleToTier(n, 0);
        assertEquals(RoomGen.HUB_INTERIOR, f.start.sizeW);
        assertEquals(RoomGen.HUB_INTERIOR, f.start.sizeH);
        assertEquals(RoomRole.REQUIRED, f.boss.role);
    }

    @Test
    void hubLayoutBuildsArmsAndLoops() {
        FloorProfile profile = FloorProfile.hub(9, 9, 15, 2, 1, 1, 0, 5);
        Floor f = new FloorGenerator(77L, profile, 0).generate();

        assertEquals(RoomRole.HUB, f.start.role);
        int startDoors = 0;
        for (boolean d : f.start.doors) if (d) startDoors++;
        assertEquals(4, startDoors);

        int rooms = 0;
        int doors = 0;
        boolean optional = false;
        boolean required = false;
        for (Floor.RoomNode n : f.rooms()) {
            if (n.type == RoomType.SECRET) continue;
            rooms++;
            for (boolean d : n.doors) if (d) doors++;
            if (n.role == RoomRole.OPTIONAL) optional = true;
            if (n.role == RoomRole.REQUIRED) required = true;
        }
        assertTrue(doors / 2 - rooms + 1 >= profile.minLoops(),
                "hub ring must close at least the requested loops");
        assertTrue(optional, "hub must have optional side/ring rooms");
        assertTrue(required, "hub must have required destinations");
    }

    @Test
    void hubUpgradeFloorHasOneUpgradeRoom() {
        FloorProfile profile = FloorProfile.hub(9, 9, 15, 2, 1, 1, 0, 5);
        Floor f = new FloorGenerator(123L, profile, 4).generate();
        int upgrades = 0;
        for (Floor.RoomNode n : f.rooms()) if (n.type == RoomType.UPGRADE) upgrades++;
        assertEquals(1, upgrades);
        FloorValidator.Result result = FloorValidator.validate(f, profile);
        assertTrue(result.ok(), result.errors().toString());
    }

    @Test
    void classicLayoutHasNoHubRole() {
        Floor f = new FloorGenerator(4242L, FloorProfile.CLASSIC, 0).generate();
        for (Floor.RoomNode n : f.rooms()) assertEquals(RoomRole.NORMAL, n.role);
    }

    @Test
    void stageSeedsAreStableAndDistinct() {
        long floor = FloorSeeds.floorSeed(1234L, 0);
        assertEquals(FloorSeeds.derive(floor, "graph"), FloorSeeds.derive(floor, "graph"));
        assertNotEquals(FloorSeeds.derive(floor, "graph"), FloorSeeds.derive(floor, "structures"));
        assertNotEquals(FloorSeeds.floorSeed(1234L, 0), FloorSeeds.floorSeed(1234L, 1));
    }
}
