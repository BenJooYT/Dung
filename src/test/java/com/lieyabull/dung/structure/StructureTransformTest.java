package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that rotating a {@link StructureDefinition}'s metadata stays consistent with the WorldEdit
 * clipboard rotation the generator applies: bounds/spawn floors/markers rotate around the same
 * vertical center and clockwise steps map to the same direction the clipboard rotates. Doorways are
 * not part of the metadata (they are carved procedurally), so there is no connector rotation.
 */
class StructureTransformTest {

    /** A 5x5 room (0..4) with a spawn floor and markers, centered at (2,2). */
    private static StructureDefinition sample() {
        StructureDefinition s = new StructureDefinition();
        s.id = "test";
        s.types.add("COMBAT");
        s.bounds.add(new RoomBounds(0, 0, 0, 4, 3, 4));
        s.spawnFloors.add(new SpawnFloor(1, 1, 1, 3, 1, 3));
        s.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 2, 1, 2, "start"));
        return s;
    }

    @Test
    void cw90RotatesMarkerPosition() {
        StructureDefinition rot = StructureTransform.rotate(sample(), StructureTransform.Rotation.CW_90);
        RoomMarker m = rot.markersOf(RoomMarkerType.PLAYER_SPAWN).get(0);
        assertEquals(2, m.x, "CW90 maps (x,z)->(z,-x): (2,2) stays (2,-2)");
        assertEquals(-2, m.z, "CW90 maps (x,z)->(z,-x): (2,2) stays (2,-2)");
    }

    @Test
    void cw180FlipsSpawnFloor() {
        StructureDefinition rot = StructureTransform.rotate(sample(), StructureTransform.Rotation.CW_180);
        SpawnFloor f = rot.spawnFloors.get(0);
        assertEquals(-3, f.minX, "CW180 maps (x,z)->(-x,-z)");
        assertEquals(-1, f.maxX, "CW180 maps (x,z)->(-x,-z)");
        assertEquals(-3, f.minZ, "CW180 maps (x,z)->(-x,-z)");
        assertEquals(-1, f.maxZ, "CW180 maps (x,z)->(-x,-z)");
    }

    @Test
    void pickRandomStaysWithinAllowedRotations() {
        StructureDefinition s = sample();
        s.allowedRotations = java.util.List.of(1, 3);
        java.util.Random rnd = new java.util.Random(1234L);
        for (int i = 0; i < 50; i++) {
            int steps = StructureTransform.pickRandom(s, rnd).steps;
            assertEquals(true, s.allowedRotations.contains(steps), "rotation step must be allowed");
        }
    }

    @Test
    void pickRandomFallsBackToNoneWhenEmpty() {
        StructureDefinition s = sample();
        s.allowedRotations = java.util.List.of();
        assertEquals(StructureTransform.Rotation.NONE,
                StructureTransform.pickRandom(s, new java.util.Random(1L)));
    }

    @Test
    void rotationPreservesIdAndTypes() {
        StructureDefinition rot = StructureTransform.rotate(sample(), StructureTransform.Rotation.CW_90);
        assertEquals("test", rot.id);
        assertEquals(java.util.List.of("COMBAT"), rot.types);
    }

    @Test
    void boundsRotateAroundCenter() {
        // Force a non-square shape so rotation changes width/depth: 5 wide x 3 deep.
        StructureDefinition s = sample();
        s.bounds.clear();
        s.bounds.add(new RoomBounds(0, 0, 0, 4, 3, 2));
        StructureDefinition rot = StructureTransform.rotate(s, StructureTransform.Rotation.CW_90);
        RoomBounds t = rot.total();
        assertEquals(3, t.width(), "width should become the old depth after 90deg CW");
        assertEquals(5, t.depth(), "depth should become the old width after 90deg CW");
    }
}