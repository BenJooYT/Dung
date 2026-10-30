package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorValidatorTest {

    @Test
    void unreachableRoomIsReported() {
        Floor f = new Floor(0, 5, 5);
        f.add(new Floor.RoomNode(3, 2, RoomType.COMBAT));

        FloorValidator.Result result = FloorValidator.validate(f, FloorProfile.CLASSIC);

        assertFalse(result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("unreachable")),
                result.errors().toString());
    }

    @Test
    void requiredRoomUnreachableIsReported() {
        Floor f = new Floor(0, 5, 5);
        Floor.RoomNode room = new Floor.RoomNode(3, 2, RoomType.COMBAT);
        room.role = RoomRole.REQUIRED;
        f.add(room);

        FloorValidator.Result result = FloorValidator.validate(f, FloorProfile.CLASSIC);

        assertFalse(result.ok());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("required room")),
                result.errors().toString());
    }
}
