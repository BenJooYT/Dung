package com.lieyabull.dung.room;

/**
 * Semantics of a hand-placed metadata point/region in a room template. These are gameplay hooks;
 * they never carry enemy types (encounter composition stays with the generator) and never replace
 * the hand-built block design.
 */
public enum RoomMarkerType {
    /** Where a player is placed when entering the room (required for runnable rooms). */
    PLAYER_SPAWN,
    /** Where a shopkeeper NPC (or shop counter) is placed. */
    SHOPKEEPER,
    /** Where loot / reward pedestals should spawn. */
    LOOT,
    /** A hazard area (lava, pitfalls, etc.) the generator must keep enemies/players out of. */
    HAZARD,
    /** A special mechanic trigger (pressure plate, button, event area). */
    MECHANIC,
    /** Free-form custom hook (name stored on the marker). */
    SPECIAL;

    public static RoomMarkerType byName(String s) {
        for (RoomMarkerType t : values()) if (t.name().equalsIgnoreCase(s)) return t;
        throw new IllegalArgumentException("no RoomMarkerType '" + s + "'");
    }
}
