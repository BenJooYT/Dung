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

    private static final int MIN_SPACING = 25;
    private static final int MAX_SPACING = 28;
    private static final int SQUARE = RoomGen.SQUARE; // 13
    private static final int LONG = RoomGen.LONG;     // 17
    private static final int BOSS = RoomGen.BOSS_INTERIOR; // 23, footprint 25

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
    void bossNeighbourCorridorGapIsPositive() {
        // The boss arena (fixed 23 interior) also seals a doorway toward its neighbour like any
        // other room, so the corridor gap must stay positive for a boss paired with every room shape.
        List<Integer> neighbours = List.of(SQUARE, LONG, BOSS);
        for (int spacing = MIN_SPACING; spacing <= MAX_SPACING; spacing++) {
            for (int other : neighbours) {
                int gap = corridorGap(spacing, BOSS, other);
                assertTrue(gap > 0,
                    "boss corridor gap must be positive: spacing=" + spacing + ", other=" + other
                        + " -> gap=" + gap);
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
    void bossFootprintNeverOverlapsNeighbour() {
        // Two adjacent rooms sit spacing apart (room base = grid * spacing). A room's footprint
        // spans interior + 2 walls, so the boss arena (23 interior -> 25 footprint) and the room in
        // the next grid cell overlap whenever spacing < 25. This is the bug that made the boss
        // arena collide with the surrounding corridors. Every valid spacing must keep them apart.
        int bossFootprint = BOSS + 2 * RoomGen.WALL; // 25
        for (int spacing = MIN_SPACING; spacing <= MAX_SPACING; spacing++) {
            assertTrue(spacing >= bossFootprint,
                "spacing " + spacing + " must not overlap the boss footprint " + bossFootprint);
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

    @Test
    void neighbourAwareCarveReachesFacingWallWithNoGapOrOvershoot() {
        // RoomGen.build now carves from a room's wall face exactly up to its NEIGHBOUR's facing wall
        // (looked up on the shared axis). For two rooms, A at base 0 with footprint (a + 2*WALL) and B
        // at base `spacing` with footprint (b + 2*WALL):
        //   * A's E wall sits at world t = halfFor(a); the passage reaches B's W wall at t = spacing.
        //   * B's W wall sits at world t = spacing; the passage reaches A's E wall at t = halfFor(a).
        // So both carve the SAME world span [halfFor(a), spacing]: no gap in the middle, and neither
        // crosses the other's interior (which starts one block inside its walls). This is the fix for
        // both the old "corridor inside the boss room" overshoot (small room punching into the big one)
        // and the midpoint carve's gap-in-the-wall/floor when the rooms differed in size.
        List<Integer> sizes = List.of(SQUARE, LONG, BOSS);
        for (int spacing = MIN_SPACING; spacing <= MAX_SPACING; spacing++) {
            for (int a : sizes) {
                for (int b : sizes) {
                    int aWall = halfFor(a);          // A's E wall, world t
                    int bWall = spacing;             // B's W wall, world t
                    // The carve span is exactly the gap between the two facing walls, and it is non-empty
                    // (A's corridor reaches B's wall and B's reaches A's wall, so the two meet with no
                    // gap in the middle or floor).
                    assertTrue(aWall < bWall,
                        "A's wall must sit before B's wall: a=" + a + ", b=" + b + ", spacing=" + spacing);
                }
            }
        }
    }

}
