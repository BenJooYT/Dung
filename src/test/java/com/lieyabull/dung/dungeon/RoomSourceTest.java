package com.lieyabull.dung.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSourceTest {

    @Test
    void parsesTheThreeModes() {
        assertEquals(RoomSource.PROCEDURAL, RoomSource.parse("procedural"));
        assertEquals(RoomSource.CUSTOM, RoomSource.parse("custom"));
        assertEquals(RoomSource.BOTH, RoomSource.parse("both"));
    }

    @Test
    void acceptsAliasesAndCaseInsensitiveValues() {
        assertEquals(RoomSource.PROCEDURAL, RoomSource.parse("Procedural-Only"));
        assertEquals(RoomSource.CUSTOM, RoomSource.parse("handmade"));
        assertEquals(RoomSource.CUSTOM, RoomSource.parse("STRUCTURES"));
        assertEquals(RoomSource.BOTH, RoomSource.parse("Hybrid"));
        assertEquals(RoomSource.BOTH, RoomSource.parse("mixed"));
    }

    @Test
    void unknownOrNullFallsBackToBoth() {
        assertEquals(RoomSource.DEFAULT, RoomSource.parse(null));
        assertEquals(RoomSource.DEFAULT, RoomSource.parse("nonsense"));
        assertEquals(RoomSource.BOTH, RoomSource.DEFAULT);
    }

    @Test
    void customUsageMatchesTheMode() {
        assertFalse(RoomSource.PROCEDURAL.usesCustom());
        assertTrue(RoomSource.CUSTOM.usesCustom());
        assertTrue(RoomSource.BOTH.usesCustom());
    }
}
