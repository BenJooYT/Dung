package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomGeometryTest {

    @Test
    void fullShapesFillTheInterior() {
        for (RoomShape shape : List.of(RoomShape.RECTANGLE, RoomShape.SQUARE, RoomShape.LONG,
                RoomShape.PILLAR, RoomShape.SPLIT, RoomShape.ARENA)) {
            RoomGeometry geometry = new RoomGeometry(shape, 13, 13);
            assertTrue(geometry.inside(0, 0), shape + " corner");
            assertTrue(geometry.inside(12, 12), shape + " corner");
        }
    }

    @Test
    void lShapeCutsOneCorner() {
        RoomGeometry geometry = new RoomGeometry(RoomShape.L_SHAPED, 13, 13);
        assertTrue(geometry.inside(0, 0));
        assertTrue(geometry.inside(8, 8));
        assertFalse(geometry.inside(12, 12));
    }

    @Test
    void crossHasOpenArmsAndSolidCorners() {
        RoomGeometry geometry = new RoomGeometry(RoomShape.CROSS, 13, 13);
        assertTrue(geometry.inside(8, 8));
        assertTrue(geometry.inside(0, 8));
        assertTrue(geometry.inside(8, 0));
        assertFalse(geometry.inside(0, 0));
    }

    @Test
    void ringHasAHoleAndWalkableBand() {
        RoomGeometry geometry = new RoomGeometry(RoomShape.RING, 13, 13);
        assertFalse(geometry.inside(8, 8));
        assertTrue(geometry.inside(10, 8));
        assertTrue(geometry.inside(0, 0));
    }

    @Test
    void caveIsInsetAtTheCorners() {
        RoomGeometry geometry = new RoomGeometry(RoomShape.CAVE, 13, 13);
        assertTrue(geometry.inside(6, 6));
        assertFalse(geometry.inside(0, 0));
    }
}
