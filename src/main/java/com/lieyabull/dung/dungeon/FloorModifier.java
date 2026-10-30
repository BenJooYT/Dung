package com.lieyabull.dung.dungeon;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/** Optional per-floor environmental conditions. */
public enum FloorModifier {
    FLOODED,
    COLLAPSING,
    CORRUPTED,
    OVERGROWN,
    DARKNESS,
    INFESTED,
    FROZEN;

    /** Parse "none"/"classic", "random", or a comma-separated list (max two). */
    public static Set<FloorModifier> select(String value, Random rng) {
        if (value == null) return Set.of();
        String key = value.trim().toLowerCase();
        if (key.isEmpty() || key.equals("none") || key.equals("classic")) return Set.of();
        EnumSet<FloorModifier> out = EnumSet.noneOf(FloorModifier.class);
        if (key.equals("random")) {
            FloorModifier[] all = values();
            out.add(all[rng.nextInt(all.length)]);
            if (all.length > 1) {
                FloorModifier second;
                do {
                    second = all[rng.nextInt(all.length)];
                } while (out.contains(second));
                out.add(second);
            }
            return out;
        }
        for (String part : key.split(",")) {
            for (FloorModifier modifier : values()) {
                if (modifier.name().equalsIgnoreCase(part.trim())) out.add(modifier);
            }
            if (out.size() >= 2) break;
        }
        return out;
    }
}
