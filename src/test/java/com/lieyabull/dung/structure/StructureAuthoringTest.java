package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contract tests for absolute clipboard -> relative structure metadata conversion. */
class StructureAuthoringTest {

    private static final StructureAuthoring.ClipboardFrame FRAME =
            StructureAuthoring.frame(100, 64, 100, 112, 68, 112);

    @Test
    void frameBuildsAZeroBasedFootprint() {
        assertEquals(13, FRAME.width());
        assertEquals(5, FRAME.height());
        assertEquals(13, FRAME.depth());
        RoomBounds b = FRAME.bounds();
        assertEquals(0, b.minX);
        assertEquals(0, b.minY);
        assertEquals(0, b.minZ);
        assertEquals(12, b.maxX);
        assertEquals(4, b.maxY);
        assertEquals(12, b.maxZ);
    }

    @Test
    void relativeBoundsSubtractClipboardMinimum() {
        RoomBounds absolute = new RoomBounds(100, 64, 100, 112, 68, 112);
        RoomBounds relative = StructureAuthoring.relative(absolute, FRAME);
        assertEquals(0, relative.minX);
        assertEquals(0, relative.minY);
        assertEquals(0, relative.minZ);
        assertEquals(12, relative.maxX);
        assertEquals(4, relative.maxY);
        assertEquals(12, relative.maxZ);
    }

    @Test
    void relativeMarkersAndSpawnFloorsSubtractTheSameOffset() {
        RoomMarker absolute = new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 106, 65, 106, "sign");
        absolute.region = new RoomBounds(101, 65, 101, 111, 65, 111);

        RoomMarker relative = StructureAuthoring.relative(absolute, FRAME);
        assertEquals(6, relative.x);
        assertEquals(1, relative.y);
        assertEquals(6, relative.z);
        assertEquals(RoomMarkerType.PLAYER_SPAWN, relative.type);
        assertEquals(1, relative.region.minX);
        assertEquals(11, relative.region.maxX);
        assertEquals(1, relative.region.minZ);
        assertEquals(11, relative.region.maxZ);

        SpawnFloor floor = StructureAuthoring.relative(
                new SpawnFloor(101, 65, 101, 111, 65, 111), FRAME);
        assertEquals(1, floor.minX);
        assertEquals(1, floor.minY);
        assertEquals(1, floor.minZ);
        assertEquals(11, floor.maxX);
        assertEquals(1, floor.maxY);
        assertEquals(11, floor.maxZ);
    }

    @Test
    void defaultsUseRelativeWallsAndFloorHeight() {
        SpawnFloor floor = FRAME.defaultSpawnFloor();
        assertEquals(1, floor.minX);
        assertEquals(1, floor.minY);
        assertEquals(1, floor.minZ);
        assertEquals(11, floor.maxX);
        assertEquals(1, floor.maxY);
        assertEquals(11, floor.maxZ);

        RoomMarker spawn = FRAME.defaultPlayerSpawn();
        assertEquals(RoomMarkerType.PLAYER_SPAWN, spawn.type);
        assertEquals(6, spawn.x);
        assertEquals(1, spawn.y);
        assertEquals(6, spawn.z);
    }

    @Test
    void absoluteRoundTripsRelativeCoordinates() {
        assertEquals(106, FRAME.absoluteX(6));
        assertEquals(65, FRAME.absoluteY(1));
        assertEquals(106, FRAME.absoluteZ(6));
    }

    @Test
    void pasteAnchorLandsTheMinCornerForNonZeroOrigins() {
        int[] anchor = StructureAuthoring.pasteAnchor(
                20, 130, 20,
                100, 64, 100,
                105, 70, 105,
                0);
        assertEquals(25, anchor[0]);
        assertEquals(136, anchor[1]);
        assertEquals(25, anchor[2]);
        // anchor + (min - origin) == target
        assertEquals(20, anchor[0] + (100 - 105));
        assertEquals(130, anchor[1] + (64 - 70));
        assertEquals(20, anchor[2] + (100 - 105));
    }

    @Test
    void pasteAnchorUsesTheRotatedOriginRelativeMin() {
        int[] anchor = StructureAuthoring.pasteAnchor(
                20, 130, 20,
                100, 64, 100,
                105, 70, 105,
                1);
        assertEquals(25, anchor[0]);
        assertEquals(136, anchor[1]);
        assertEquals(15, anchor[2]);
        // Rotated (min - origin) = (-5, 5)
        assertEquals(20, anchor[0] + (-5));
        assertEquals(20, anchor[2] + 5);
    }

    @Test
    void detectsDoorFloorAndHeightOnTheFixedLine() {
        RoomBounds bounds = new RoomBounds(0, 0, 0, 12, 8, 12);
        BlockLookup lookup = (x, y, z) -> {
            boolean solid = x == 0 || x == 12 || z == 0 || z == 12 || y == 0 || y == 8;
            // East-wall opening at y=4..7, z=5..7 (the fixed perpCenter line is 6).
            if (x == 12 && y >= 4 && y <= 7 && z >= 5 && z <= 7) solid = false;
            return solid;
        };
        StructureDefinition.DoorSpec[] doors = StructureAuthoring.detectDoors(lookup, bounds, 6);
        assertEquals(3, doors[1].floorY);
        assertEquals(4, doors[1].height);
        assertEquals(0, doors[0].floorY);
        assertEquals(0, doors[0].height);
    }

    @Test
    void legacyDoorFloorYIsShiftedWithTheRestOfTheMetadata() {
        StructureDefinition def = new StructureDefinition();
        def.id = "legacy";
        def.bounds.add(new RoomBounds(100, 64, 100, 112, 68, 112));
        def.doorsConfigured = true;
        def.door(0).floorY = 67;
        def.door(0).height = 4;

        assertTrue(StructureAuthoring.migrateLegacy(def, FRAME));
        assertEquals(3, def.doorFloorY(0));
        assertEquals(4, def.doorHeight(0));
    }

    @Test
    void legacyAbsoluteDefinitionIsMigratedInPlace() {
        StructureDefinition def = new StructureDefinition();
        def.id = "legacy";
        def.types.add("COMBAT");
        def.bounds.add(new RoomBounds(100, 64, 100, 112, 68, 112));
        def.spawnFloors.add(new SpawnFloor(101, 65, 101, 111, 65, 111));
        def.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 106, 65, 106, "sign"));

        assertTrue(StructureAuthoring.looksLegacyAbsolute(def, FRAME));
        assertTrue(StructureAuthoring.migrateLegacy(def, FRAME));

        assertEquals(0, def.total().minX);
        assertEquals(0, def.total().minY);
        assertEquals(0, def.total().minZ);
        assertEquals(12, def.total().maxX);
        assertEquals(1, def.spawnFloors.get(0).minX);
        assertEquals(1, def.spawnFloors.get(0).minY);
        assertEquals(6, def.markers.get(0).x);
        assertEquals(1, def.markers.get(0).y);

        assertFalse(StructureAuthoring.migrateLegacy(def, FRAME), "already relative");
    }

    @Test
    void alreadyRelativeDefinitionIsNotClassifiedAsLegacy() {
        StructureDefinition def = new StructureDefinition();
        def.bounds.add(new RoomBounds(0, 0, 0, 12, 4, 12));
        assertFalse(StructureAuthoring.looksLegacyAbsolute(def, FRAME));
    }

    @Test
    void frameAtOriginDoesNotRewriteRelativeMetadata() {
        StructureAuthoring.ClipboardFrame origin = StructureAuthoring.frame(0, 0, 0, 12, 4, 12);
        StructureDefinition def = new StructureDefinition();
        def.bounds.add(new RoomBounds(0, 0, 0, 12, 4, 12));
        assertFalse(StructureAuthoring.looksLegacyAbsolute(def, origin));
    }
}
