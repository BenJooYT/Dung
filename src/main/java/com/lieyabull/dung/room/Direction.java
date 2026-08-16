package com.lieyabull.dung.room;

/**
 * Cardinal direction of a room connection. The four horizontal directions match the existing
 * Dung door convention (N=0, E=1, S=2, W=3 with DX={0,1,0,-1}, DZ={-1,0,1,0}). UP/DOWN are used for
 * vertical (multi-floor / split-level) connections.
 */
public enum Direction {
    NORTH(0, 0, -1, 0),
    EAST(1, 1, 0, 1),
    SOUTH(2, 0, 1, 2),
    WEST(3, -1, 0, 3),
    UP(4, 0, 0, -1),
    DOWN(5, 0, 0, -1);

    /** 0..5 unique id for serialization. */
    public final int id;
    /** Unit offset along x. */
    public final int dx;
    /** Unit offset along z. */
    public final int dz;
    /** N/E/S/W index in the Dung DX/DZ arrays (0..3); -1 for vertical. */
    public final int card;

    Direction(int id, int dx, int dz, int card) {
        this.id = id;
        this.dx = dx;
        this.dz = dz;
        this.card = card;
    }

    public boolean isHorizontal() {
        return card >= 0;
    }

    public Direction opposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case EAST: return WEST;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            case UP: return DOWN;
            case DOWN: return UP;
            default: throw new IllegalStateException();
        }
    }

    public static Direction byId(int id) {
        for (Direction d : values()) if (d.id == id) return d;
        throw new IllegalArgumentException("no Direction for id " + id);
    }
}
