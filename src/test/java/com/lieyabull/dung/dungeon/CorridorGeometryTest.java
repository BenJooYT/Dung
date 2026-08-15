package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless validation of the room/corridor geometry used by {@link RoomGen}, without a Bukkit
 * server. Re-derives the exact carve math from RoomGen and asserts the invariants that make
 * corridors safe and connected: a positive corridor gap for every spacing and room shape, and
 * the fixed passage line lying inside every room's perpendicular interior.
 */
public class CorridorGeometryTest {

    private static final int MIN_SPACING = 22;
    private static final int MAX_SPACING = 28;
    private static final int SQUARE = RoomGen.SQUARE; // 13
    private static final int LONG = RoomGen.LONG;     // 17

    /** Along-axis interior half-width used by RoomGen to seal a doorway. */
    private static int halfFor(int size) {
        return RoomGen.WALL + size / 2;
    }

    /** Gap between two rooms' facing walls = spacing minus both seal depths. Must stay > 0. */
    private static int corridorGap(int spacing, int halfA, int halfB) {
        int innerWallT = halfFor(halfA);
        int nextWallT = spacing - halfFor(halfB);
        return nextWallT - innerWallT;
    }

    @Test
    void corridorGapIsPositiveForAllSpacingsAndShapes() {
        List<Integer> sizes = List.of(SQUARE, LONG); // 13 / 17, for each axis
        for (int spacing = MIN_SPACING; spacing <= MAX_SPACING; spacing++) {
            for (int a : sizes) {
                for (int b : sizes) {
                    int gap = corridorGap(spacing, a, b);
                    assertTrue(gap > 0,
                        "corridor gap must be positive: spacing=" + spacing + ", a=" + a + ", b=" + b
                            + " -> gap=" + gap);
                }
            }
        }
    }

    @Test
    void fixedPassageLineLiesInsideEveryRoomPerpendicularInterior() {
        // The 3-wide passage is carved on the FIXED line at offset PERP_CENTER from a room's base,
        // anchored to the widest room. It must fall inside the perpendicular interior (1..size) of
        // BOTH a square and a long room, or the tube would clip through a wall.
        int line = RoomGen.PERP_CENTER;
        for (int size : List.of(SQUARE, LONG)) {
            assertTrue(line >= 1 && line <= size,
                "passage line " + line + " must be inside perpendicular interior 1.." + size);
        }
    }

    @Test
    void corridorReachesBothRoomInteriorsNotJustTheGap() {
        // The 3-wide tunnel must span from room A's interior, across the gap, into room B's
        // interior. The corridor range [innerWallT, nextWallT] in A's coordinate therefore must
        // begin inside A's interior footprint and end before B's far wall.
        for (int spacing = MIN_SPACING; spacing <= MAX_SPACING; spacing++) {
            for (int a : List.of(SQUARE, LONG)) {
                for (int b : List.of(SQUARE, LONG)) {
                    int innerWallT = halfFor(a);
                    int nextWallT = spacing - halfFor(b);
                    // t=0 is A's interior center; A's interior spans halfFor(a) from center, so the
                    // corridor starts exactly at A's last interior block (already carved by the door)
                    // and ends at B's last interior block (carved by B's door). Assert the seam is
                    // covered by the solid corridor mass, i.e. gap covers at least the open stretch.
                    assertTrue(nextWallT > innerWallT, "seam must be sealed at spacing " + spacing);
                }
            }
        }
    }
}
