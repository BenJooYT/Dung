package com.lieyabull.dung.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Lightweight layout templates: allowed shapes plus a feature set. */
public enum RoomTemplate {
    FOUR_PILLAR_ARENA("four_pillar_arena",
            List.of(RoomShape.SQUARE, RoomShape.RECTANGLE),
            List.of(RoomFeature.PILLARS, RoomFeature.ALTAR)),
    THE_RING("the_ring",
            List.of(RoomShape.RING),
            List.of(RoomFeature.CENTRAL_PIT)),
    THE_ALTAR("the_altar",
            List.of(RoomShape.SQUARE, RoomShape.RECTANGLE),
            List.of(RoomFeature.ALTAR, RoomFeature.PILLARS)),
    THE_PLATFORMS("the_platforms",
            List.of(RoomShape.ARENA, RoomShape.RECTANGLE),
            List.of(RoomFeature.PLATFORMS));

    private final String id;
    private final List<RoomShape> shapes;
    private final List<RoomFeature> features;

    RoomTemplate(String id, List<RoomShape> shapes, List<RoomFeature> features) {
        this.id = id;
        this.shapes = shapes;
        this.features = features;
    }

    public String id() {
        return id;
    }

    public List<RoomShape> shapes() {
        return shapes;
    }

    public List<RoomFeature> features() {
        return features;
    }

    public void applyTo(Floor.RoomNode room, Random rng) {
        room.shape = shapes.get(rng.nextInt(shapes.size()));
        room.features.clear();
        room.features.addAll(features);
        room.featureSeed = rng.nextLong();
        room.template = this;
    }

    public static RoomTemplate pick(Archetype archetype, Random rng) {
        List<RoomTemplate> pool = new ArrayList<>();
        for (RoomTemplate template : values()) {
            if (template.serves(archetype)) pool.add(template);
        }
        if (pool.isEmpty()) return null;
        return pool.get(rng.nextInt(pool.size()));
    }

    private boolean serves(Archetype archetype) {
        if (archetype == null || this == THE_PLATFORMS) return true;
        return switch (archetype) {
            case RUINS, INFERNO -> this == FOUR_PILLAR_ARENA || this == THE_ALTAR;
            case CAVERNS, OVERGROWN -> this == FOUR_PILLAR_ARENA;
            case TEMPLE -> this == THE_ALTAR || this == FOUR_PILLAR_ARENA;
            case CRYPT -> this == THE_RING || this == THE_ALTAR;
        };
    }
}
