package com.lieyabull.dung.dungeon;

import java.util.List;
import java.util.Random;

/** Builds a floor graph and validates it, regenerating a bounded number of times. */
public final class FloorGenerator {
    private static final int MAX_ATTEMPTS = 3;

    private final Random rng;
    private final FloorProfile profile;
    private final int floorIndex;

    public FloorGenerator(Random rng, FloorProfile profile, int floorIndex) {
        this.rng = rng;
        this.profile = profile;
        this.floorIndex = floorIndex;
    }

    public FloorGenerator(long seed, FloorProfile profile, int floorIndex) {
        this(new Random(seed), profile, floorIndex);
    }

    /** Compatibility entry point for callers that still pass the classic dimensions directly. */
    public FloorGenerator(Random rng, int width, int height, int roomCount, int floorIndex) {
        this(rng, FloorProfile.classic(width, height, roomCount), floorIndex);
    }

    public Floor generate() {
        List<String> errors = List.of();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Floor floor = new FloorGraphGenerator(rng, profile, floorIndex).generate();
            FloorValidator.Result result = FloorValidator.validate(floor, profile);
            if (result.ok()) return floor;
            errors = result.errors();
        }
        throw new IllegalStateException("Floor generation failed validation after "
                + MAX_ATTEMPTS + " attempts (profile " + profile.id()
                + ", floor " + (floorIndex + 1) + "): " + errors);
    }
}
