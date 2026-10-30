package com.lieyabull.dung.dungeon;

/** Gameplay/terrain features placed inside a room. */
public enum RoomFeature {
    WATER_POOL,
    LAVA_POOL,
    CENTRAL_PIT,
    PILLARS,
    STAIRS,
    BRIDGES,
    RUBBLE,
    SPIKES,
    ROOTS,
    ALTAR,
    CHEST_PLATFORM,
    PLATFORMS;

    /** Features implemented without a new damage/terrain model. Roots are an obstacle, not damage. */
    public boolean safe() {
        return this == WATER_POOL || this == PILLARS || this == RUBBLE
                || this == ALTAR || this == CHEST_PLATFORM || this == ROOTS
                || this == PLATFORMS || this == STAIRS || this == BRIDGES;
    }
}
