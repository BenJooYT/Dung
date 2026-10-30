package com.lieyabull.dung.dungeon;

import java.util.Random;

/** Stable seed derivation so each floor generation stage gets its own reproducible stream. */
public final class FloorSeeds {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private FloorSeeds() {}

    public static long floorSeed(long runSeed, int floorIndex) {
        return mix(runSeed ^ (GOLDEN * (floorIndex + 1L)));
    }

    public static long derive(long floorSeed, String stage) {
        return mix(floorSeed ^ hash(stage));
    }

    public static Random random(long floorSeed, String stage) {
        return new Random(derive(floorSeed, stage));
    }

    public static long mix(long value) {
        long z = value + GOLDEN;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long hash(String stage) {
        long h = 1125899906842597L;
        for (int i = 0; i < stage.length(); i++) h = 31 * h + stage.charAt(i);
        return h;
    }
}
