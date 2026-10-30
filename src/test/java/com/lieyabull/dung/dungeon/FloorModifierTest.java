package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorModifierTest {

    @Test
    void selectHandlesNoneSpecificAndRandom() {
        assertEquals(Set.of(), FloorModifier.select("none", new Random(1)));
        assertEquals(Set.of(FloorModifier.DARKNESS, FloorModifier.FROZEN),
                FloorModifier.select("darkness,frozen", new Random(1)));
        assertEquals(2, FloorModifier.select("random", new Random(1)).size());
    }

    @Test
    void modifiersReachTheFloorAndAddFeatures() {
        FloorProfile profile = FloorProfile.forArchetype(Archetype.RUINS, 9, 9, 9, 5)
                .withModifiers(Set.of(FloorModifier.FLOODED, FloorModifier.OVERGROWN));
        Floor floor = new FloorGenerator(11L, profile, 0).generate();

        assertEquals(Set.of(FloorModifier.FLOODED, FloorModifier.OVERGROWN),
                floor.modifiers);
        boolean water = false;
        boolean roots = false;
        for (Floor.RoomNode room : floor.rooms()) {
            if (room.features.contains(RoomFeature.WATER_POOL)) water = true;
            if (room.features.contains(RoomFeature.ROOTS)) roots = true;
        }
        assertTrue(water, "FLOODED must add a water pool");
        assertTrue(roots, "OVERGROWN must add roots");
    }
}
