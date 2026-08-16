package com.lieyabull.dung.meta;

import java.util.List;

/**
 * Permanent stat-upgrade tracks purchasable with shards (earned by salvaging armor mid-run).
 * Each track has a label, a cost curve, and a level cap. Effects are applied in PlayerState.
 */
public final class Upgrades {
    public record Track(String id, String label, int baseCost, int costPerLevel, int maxLevel) {}

    public static final Track DAMAGE = new Track("damage", "Permanent Damage", 6, 3, 15);
    public static final Track HEARTS = new Track("hearts", "Max Hearts", 8, 4, 15);
    public static final Track DEFENSE = new Track("defense", "Defense", 8, 5, 15);
    public static final Track CRIT = new Track("crit", "Crit Chance", 8, 4, 15);
    public static final Track SPEED = new Track("speed", "Move Speed", 10, 6, 8);
    public static final Track MANA = new Track("mana", "Max Mana", 6, 3, 15);

    public static final List<Track> ALL = List.of(DAMAGE, HEARTS, DEFENSE, CRIT, SPEED, MANA);

    private Upgrades() {}

    public static Track byId(String id) {
        for (Track t : ALL) if (t.id().equals(id)) return t;
        return null;
    }

    /** Shard cost of the NEXT level (level is the current owned level, 0-based). */
    public static int cost(Track t, int level) {
        return t.baseCost() + t.costPerLevel() * level;
    }

    /** Permanent stat delta per owned level of a track. */
    public static int delta(Track t) {
        return switch (t.id()) {
            case "damage" -> 1;
            case "hearts" -> 5;
            case "defense" -> 1;
            case "mana" -> 5;
            case "speed" -> 3;
            default -> 0; // crit handled as a fraction elsewhere
        };
    }
}
