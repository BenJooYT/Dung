package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchetypeTest {

    @Test
    void everyArchetypeGeneratesValidFloors() {
        for (Archetype archetype : Archetype.values()) {
            FloorProfile profile = FloorProfile.forArchetype(archetype, 9, 9, 7, 5);
            for (long seed = 0; seed < 20; seed++) {
                Floor floor = new FloorGenerator(seed, profile, 0).generate();
                assertEquals(archetype, floor.archetype);
                FloorValidator.Result result = FloorValidator.validate(floor, profile);
                assertTrue(result.ok(),
                        archetype.id() + " seed " + seed + ": " + result.errors());
            }
        }
    }

    @Test
    void pickResolvesIdsAndRandom() {
        assertNull(Archetype.pick("none", new Random(1)));
        assertNull(Archetype.pick("classic", new Random(1)));
        assertEquals(Archetype.INFERNO, Archetype.pick("inferno", new Random(1)));
        assertNotNull(Archetype.pick("random", new Random(1)));
    }
}
