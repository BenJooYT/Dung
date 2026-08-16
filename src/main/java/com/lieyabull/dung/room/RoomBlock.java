package com.lieyabull.dung.room;

/**
 * A single serialized block in a room template. Coordinates are template-relative. `blockData` is
 * the lossless BlockData string (e.g. "minecraft:stone_bricks[facing=east]") preserving block
 * states; a plain material string is used for default states.
 */
public final class RoomBlock {
    public int x, y, z;
    /** BlockData string, e.g. "minecraft:stone_bricks". */
    public String b;

    public RoomBlock() {}

    public RoomBlock(int x, int y, int z, String b) {
        this.x = x; this.y = y; this.z = z;
        this.b = b;
    }
}
