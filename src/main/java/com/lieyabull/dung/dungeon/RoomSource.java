package com.lieyabull.dung.dungeon;

/**
 * Where a floor's room bodies come from. The floor graph, doors, corridors, and sealing stay
 * procedural in every mode; this only decides whether each room node gets a structure paste.
 */
public enum RoomSource {
    /** Every room is built by {@link RoomGen}. */
    PROCEDURAL,
    /** Every eligible room uses a structure from the library (procedural fallback if none fits). */
    CUSTOM,
    /** Each eligible room picks custom vs procedural independently with {@code room-source-chance}. */
    BOTH;

    public static final RoomSource DEFAULT = BOTH;

    /** Lenient parse: unknown values fall back to {@link #DEFAULT}. */
    public static RoomSource parse(String raw) {
        if (raw == null) return DEFAULT;
        return switch (raw.trim().toLowerCase()) {
            case "procedural", "procedural-only", "vanilla", "classic" -> PROCEDURAL;
            case "custom", "custom-only", "handmade", "hand-made", "structures" -> CUSTOM;
            case "both", "mixed", "mix", "hybrid" -> BOTH;
            default -> DEFAULT;
        };
    }

    public boolean usesCustom() {
        return this != PROCEDURAL;
    }
}
