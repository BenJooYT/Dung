package com.lieyabull.dung.room;

/**
 * A room's entry/exit connection. Anchor is the center block of the opening on the room's wall
 * face, in template-relative coordinates. width/height describe the opening; y is the floor level
 * of the passage (may differ from the anchor Y for sloped stairs). clearance is extra vertical
 * headroom the generator must preserve when carving toward this connection.
 */
public final class RoomConnector {
    public Direction direction;
    public RoomConnType type;
    /** Anchor X (template-relative). */
    public int x;
    /** Anchor Y (template-relative) - the opening's vertical center. */
    public int y;
    /** Anchor Z (template-relative). */
    public int z;
    /** Passage width in blocks (>=1). */
    public int width;
    /** Passage height in blocks (>=1). */
    public int height;
    /** The floor level of the passage (template-relative), for height-matching corridors. */
    public int floorY;
    /** Extra clearance (blocks) the corridor must keep above height. */
    public int clearance;

    public RoomConnector() {}

    public RoomConnector(Direction direction, RoomConnType type, int x, int y, int z,
                         int width, int height, int floorY, int clearance) {
        this.direction = direction;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
        this.floorY = floorY;
        this.clearance = clearance;
    }

    /** Is this connection on the horizontal facing `d` (N/E/S/W)? */
    public boolean faces(Direction d) {
        return direction == d;
    }
}
