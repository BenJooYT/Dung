package com.lieyabull.dung.room;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for the room-template subsystem: JSON round-trip fidelity, validator
 * (self-containment + connectivity), marker rules, and connector lookup. No Bukkit needed.
 *
 * Conventions under test (shared with {@link RoomValidator}): a PLAYER_SPAWN marker sits at an
 * AIR cell with solid floor below and headroom above; a SpawnFloor's minY is the mob's feet cell
 * (floor is one below); doorways must be carved open (air at the connector anchor).
 */
public class RoomTemplateTest {

    /** A small valid COMBAT shell: 3x3 footprint, 5 tall (y=0..4, roof at y=4). */
    private RoomTemplate shellRoom() {
        RoomTemplate t = new RoomTemplate();
        t.id = "t_shell";
        t.types.add("COMBAT");
        t.bounds.add(new RoomBounds(0, 0, 0, 2, 4, 2));
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 3; z++) {
                    boolean wall = x == 0 || x == 2 || z == 0 || z == 2;
                    boolean roof = y == 4;
                    boolean doorway = x == 1 && z == 0 && y >= 1 && y <= 3;
                    String b;
                    if (doorway) b = "minecraft:air";
                    else if (wall || roof) b = "minecraft:stone_bricks";
                    else if (y == 0) b = "minecraft:polished_andesite";
                    else b = "minecraft:air";
                    t.blocks.add(new RoomBlock(x, y, z, b));
                }
            }
        }
        t.connectors.add(new RoomConnector(Direction.NORTH, RoomConnType.DOOR, 1, 1, 0, 1, 3, 0, 0));
        // spawn floor at feet cell y=1, solid floor below at y=0
        t.spawnFloors.add(new SpawnFloor(1, 1, 1, 1, 1, 1));
        t.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 1, 1, 1, "start"));
        return t;
    }

    @Test
    void jsonRoundTripPreservesData() {
        RoomTemplate t = shellRoom();
        String json = RoomIo.toJson(t);
        RoomTemplate back = RoomIo.fromJson(json);
        assertEquals(t.id, back.id);
        assertEquals(t.types, back.types);
        assertEquals(t.blocks.size(), back.blocks.size()); // deterministic, sorted, no drops
        assertEquals(1, back.connectors.size());
        RoomConnector c = back.connectors.get(0);
        assertEquals(Direction.NORTH, c.direction);
        assertEquals(RoomConnType.DOOR, c.type);
        assertEquals(3, c.height);
        assertEquals(1, back.spawnFloors.size());
        assertEquals(RoomMarkerType.PLAYER_SPAWN, back.markers.get(0).type);
        assertEquals(3, back.total().width());
        assertEquals(5, back.total().height());
        assertEquals(3, back.total().depth());
    }

    @Test
    void connectorFacingFindsSharedSide() {
        RoomTemplate t = shellRoom();
        assertNotNull(t.connectorFacing(Direction.NORTH));
        assertNull(t.connectorFacing(Direction.SOUTH));
    }

    @Test
    void markersOfFiltersByType() {
        RoomTemplate t = shellRoom();
        assertEquals(1, t.markersOf(RoomMarkerType.PLAYER_SPAWN).size());
        assertEquals(0, t.markersOf(RoomMarkerType.SHOPKEEPER).size());
    }

    @Test
    void validShellPassesValidation() {
        RoomTemplate t = shellRoom();
        RoomValidator.Result r = RoomValidator.validate(t);
        assertTrue(r.valid, r.issues.toString());
    }

    @Test
    void disconnectedGeometryFailsValidation() {
        // two chambers split by a full-height wall with only one doorway on the left chamber
        RoomTemplate t = new RoomTemplate();
        t.id = "t_split";
        t.types.add("COMBAT");
        t.bounds.add(new RoomBounds(0, 0, 0, 4, 2, 4));
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean roof = y == 2;
                    boolean divider = y == 1 && x == 2; // seals right chamber from left at air level
                    String b;
                    if (wall || roof || divider) b = "minecraft:stone_bricks";
                    else if (y == 0) b = "minecraft:polished_andesite";
                    else b = "minecraft:air";
                    t.blocks.add(new RoomBlock(x, y, z, b));
                }
            }
        }
        // one doorway on the north wall of the LEFT chamber (x=1)
        for (int y = 1; y <= 2; y++) {
            t.blocks.add(new RoomBlock(1, y, 0, "minecraft:air"));
        }
        t.connectors.add(new RoomConnector(Direction.NORTH, RoomConnType.DOOR, 1, 1, 0, 1, 2, 0, 0));
        t.spawnFloors.add(new SpawnFloor(1, 1, 1, 1, 1, 1));
        t.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 1, 1, 1, "start"));
        RoomValidator.Result r = RoomValidator.validate(t);
        assertFalse(r.valid);
        boolean disconnected = r.issues.stream()
                .anyMatch(i -> i.message.contains("disconnected"));
        assertTrue(disconnected, r.issues.toString());
    }

    @Test
    void missingRequiredSpawnMarkerIsReported() {
        RoomTemplate t = shellRoom();
        t.markers.clear();
        RoomValidator.Result r = RoomValidator.validate(t);
        assertTrue(r.issues.stream().anyMatch(i -> i.message.contains("no player spawn marker")));
    }

    @Test
    void secretRoomMayLackPlayerSpawnAndConnections() {
        RoomTemplate t = new RoomTemplate();
        t.id = "t_secret";
        t.types.add("SECRET");
        t.bounds.add(new RoomBounds(0, 0, 0, 2, 4, 2));
        // a closed 3x3x5 box (roof at y=4) with air cells inside (reachable only via parent's secret wall)
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 3; z++) {
                    boolean wall = x == 0 || x == 2 || z == 0 || z == 2 || y == 4;
                    String b = (wall || y == 0) ? "minecraft:stone_bricks" : "minecraft:air";
                    t.blocks.add(new RoomBlock(x, y, z, b));
                }
            }
        }
        t.spawnFloors.add(new SpawnFloor(1, 1, 1, 1, 1, 1));
        RoomValidator.Result r = RoomValidator.validate(t);
        assertTrue(r.valid, r.issues.toString());
    }
}