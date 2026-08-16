package com.lieyabull.dung.room;

/**
 * What kind of transition a connection represents. This drives how the generator carves the
 * passage and how the room is sealed/unsealed during a run.
 */
public enum RoomConnType {
    /** Standard doorway into another combat room (carved corridor, sealable). */
    DOOR,
    /** Long corridor run between rooms. */
    CORRIDOR,
    /** Wide open archway with no sealable door. */
    OPENING,
    /** Locked-room entrance (barrier present until a key is used). */
    LOCKED,
    /** Secret-room entrance (behind a destructible wall; not reachable through the door graph). */
    SECRET,
    /** Boss-room entrance (no return). */
    BOSS,
    /** Vertical shaft / stairs to a higher or lower level. */
    STAIR,
    /** Vertical shaft / drop to a lower level. */
    SHAFT;

    public static RoomConnType byName(String s) {
        for (RoomConnType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        throw new IllegalArgumentException("no RoomConnType '" + s + "'");
    }
}
