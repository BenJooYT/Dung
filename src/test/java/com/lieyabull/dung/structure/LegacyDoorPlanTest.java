package com.lieyabull.dung.structure;

import com.lieyabull.dung.dungeon.RoomGen;
import com.lieyabull.dung.room.RoomBounds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDoorPlanTest {

    /** Mutable voxel world with a hollow room shell (floor, ceiling, four walls). */
    private static final class Voxels {
        private final Set<Long> solid = new HashSet<>();
        private final int height;

        Voxels(int width, int height, int depth) {
            this.height = height;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    for (int z = 0; z < depth; z++) {
                        if (x == 0 || x == width - 1 || z == 0 || z == depth - 1
                                || y == 0 || y == height - 1) {
                            solid.add(key(x, y, z));
                        }
                    }
                }
            }
        }

        Voxels fill(int x, int y, int z) {
            solid.add(key(x, y, z));
            return this;
        }

        Voxels fillColumn(int x, int z) {
            for (int y = 0; y < height; y++) solid.add(key(x, y, z));
            return this;
        }

        BlockLookup lookup() {
            return (x, y, z) -> solid.contains(key(x, y, z));
        }

        private static long key(int x, int y, int z) {
            return ((long) (x + 64) << 40) | ((long) (y + 64) << 20) | (z + 64);
        }
    }

    private static final int[] NO_PREFERENCE = {-1, -1, -1, -1};

    private static StructureAuthoring.DoorLanePlan plan(int width, int height, int depth,
                                                         BlockLookup lookup, int[] preferred) {
        return StructureAuthoring.planFixedLineDoors(
                new RoomBounds(0, 0, 0, width - 1, height - 1, depth - 1), lookup, preferred,
                RoomGen.PERP_CENTER, RoomGen.PERP_CENTER);
    }

    private static boolean contains(List<int[]> cells, int x, int y, int z) {
        for (int[] c : cells) {
            if (c[0] == x && c[1] == y && c[2] == z) return true;
        }
        return false;
    }

    @Test
    void legacyApertureIsCentredOnTheConnector() {
        // combatfloored1's WEST connector: z=2 is the CENTRE of a 3-wide opening, not its start.
        List<int[]> west = StructureAuthoring.centeredApertureCells(3, 0, 4, 2, 3, 3);
        assertEquals(9, west.size());
        for (int z = 1; z <= 3; z++) {
            for (int y = 4; y <= 6; y++) {
                assertTrue(contains(west, 0, y, z), "west aperture must cover z=" + z + ", y=" + y);
            }
        }
        assertFalse(contains(west, 0, 4, 4), "west aperture must not spill past its centre");

        List<int[]> east = StructureAuthoring.centeredApertureCells(1, 12, 1, 6, 3, 3);
        assertTrue(contains(east, 12, 1, 5));
        assertTrue(contains(east, 12, 3, 7));
        assertFalse(contains(east, 12, 1, 8));

        List<int[]> south = StructureAuthoring.centeredApertureCells(2, 2, 4, 9, 3, 3);
        assertTrue(contains(south, 1, 4, 9));
        assertTrue(contains(south, 3, 6, 9));
        assertFalse(contains(south, 4, 4, 9));

        List<int[]> north = StructureAuthoring.centeredApertureCells(0, 6, 1, 0, 3, 3);
        assertTrue(contains(north, 5, 1, 0));
        assertTrue(contains(north, 7, 3, 0));
        assertFalse(contains(north, 8, 1, 0));
    }

    @Test
    void plansFlatRoomAtFloorLevel() {
        Voxels room = new Voxels(19, 8, 19);
        StructureAuthoring.DoorLanePlan plan = plan(19, 8, 19, room.lookup(), NO_PREFERENCE);

        assertTrue(plan.ok(), plan.reason());
        for (int d = 0; d < 4; d++) {
            assertEquals(0, plan.doors()[d].floorY, "direction " + d);
            assertEquals(3, plan.doors()[d].height, "direction " + d);
        }
    }

    @Test
    void plansSplitLevelAtThePaddedCentredLane() {
        // combatfloored1 shape: raised west platform (solid y=0..3 over x=1..4, z=1..8) and a
        // lower east half. Padded to 19x19 the 13x10 room sits at offX=3/offZ=4, so the fixed line
        // (9) maps to lane x=6 / z=5 in the original room. The old SOUTH opening was on the
        // platform, but this centred lane is over the lower floor; the planner must move that door
        // down instead of leaving a drop.
        Voxels room = new Voxels(13, 8, 10);
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 8; z++) {
                for (int y = 0; y <= 3; y++) {
                    room.fill(x, y, z);
                }
            }
        }
        int[] preferred = {-1, 0, 3, 3}; // authored E=0, S=3, W=3
        StructureAuthoring.DoorLanePlan plan = StructureAuthoring.planFixedLineDoors(
                new RoomBounds(0, 0, 0, 12, 7, 9), room.lookup(), preferred, 6, 5);

        assertTrue(plan.ok(), plan.reason());
        assertEquals(0, plan.doors()[0].floorY, "north");
        assertEquals(0, plan.doors()[1].floorY, "east");
        assertEquals(0, plan.doors()[2].floorY, "south must drop to the local floor");
        assertEquals(3, plan.doors()[3].floorY, "west keeps the raised platform level");
        for (int d = 0; d < 4; d++) assertEquals(3, plan.doors()[d].height, "direction " + d);
    }

    @Test
    void rejectsRoomWhoseCentredLaneIsOutsideTheInterior() {
        Voxels room = new Voxels(8, 8, 19);
        StructureAuthoring.DoorLanePlan plan = plan(8, 8, 19, room.lookup(), NO_PREFERENCE);

        assertFalse(plan.ok());
        assertEquals(0, plan.failedDirection());
        assertTrue(plan.reason().contains("north"), plan.reason());
    }

    @Test
    void flagsRoomsWhoseFixedLaneFallsOutsideTheInterior() {
        // testelite is 7x15: the N/S lane at x=8..10 is outside its 1..5 interior.
        assertTrue(StructurePadder.needsPadding(new RoomBounds(0, 0, 0, 6, 5, 14)));
        // A 10-wide room has the same problem on one axis.
        assertTrue(StructurePadder.needsPadding(new RoomBounds(0, 0, 0, 9, 5, 18)));
        // Full 19x19 and a 13-wide room keep the whole lane inside the interior.
        assertFalse(StructurePadder.needsPadding(new RoomBounds(0, 0, 0, 18, 7, 18)));
        assertFalse(StructurePadder.needsPadding(new RoomBounds(0, 0, 0, 12, 7, 18)));
    }

    @Test
    void rejectsDoorLaneThatEndsInACloset() {
        // Open a standable cell at the north lane but wall it off from the rest of the room.
        Voxels room = new Voxels(19, 8, 19);
        room.fillColumn(8, 1);
        room.fillColumn(10, 1);
        room.fillColumn(9, 2);

        StructureAuthoring.DoorLanePlan plan = plan(19, 8, 19, room.lookup(), NO_PREFERENCE);

        assertFalse(plan.ok());
        assertEquals(0, plan.failedDirection());
        assertTrue(plan.reason().contains("north"), plan.reason());
    }
}
