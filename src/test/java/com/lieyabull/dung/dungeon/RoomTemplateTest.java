package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTemplateTest {

    @Test
    void templatesApplyAllowedShapeAndFeatures() {
        for (RoomTemplate template : RoomTemplate.values()) {
            Floor.RoomNode room = new Floor.RoomNode(0, 0, RoomType.COMBAT);
            template.applyTo(room, new Random(7));
            assertTrue(template.shapes().contains(room.shape));
            assertEquals(new HashSet<>(template.features()), room.features);
            assertEquals(template, room.template);
        }
    }

    @Test
    void pickResolvesArchetypes() {
        assertNotNull(RoomTemplate.pick(null, new Random(1)));
        assertNotNull(RoomTemplate.pick(Archetype.CRYPT, new Random(1)));
        assertNotNull(RoomTemplate.pick(Archetype.CAVERNS, new Random(1)));
    }
}
