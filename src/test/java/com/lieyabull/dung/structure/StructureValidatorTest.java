package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.Direction;
import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure metadata validation for the structure library (no clipboard / no WorldEdit). */
class StructureValidatorTest {

    private static StructureDefinition valid() {
        StructureDefinition s = new StructureDefinition();
        s.id = "test";
        s.types.add("COMBAT");
        s.schematic = "structure.schem";
        s.bounds.add(new RoomBounds(0, 0, 0, 6, 3, 6));
        s.spawnFloors.add(new SpawnFloor(1, 1, 1, 5, 1, 5));
        s.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 3, 1, 3, "start"));
        return s;
    }

    @Test
    void acceptsAWellFormedStructure() {
        StructureValidator.Result r = StructureValidator.validate(valid());
        assertFalse(r.hasErrors(), r.issues.toString());
    }

    @Test
    void rejectsMissingSpawnFloor() {
        StructureDefinition s = valid();
        s.spawnFloors.clear();
        assertTrue(StructureValidator.validate(s).hasErrors());
    }

    @Test
    void rejectsMissingPlayerSpawn() {
        StructureDefinition s = valid();
        s.markers.clear();
        assertTrue(StructureValidator.validate(s).hasErrors());
    }

    @Test
    void rejectsUnknownRoomType() {
        StructureDefinition s = valid();
        s.types.clear();
        s.types.add("NOT_A_TYPE");
        assertTrue(StructureValidator.validate(s).hasErrors());
    }

    @Test
    void rejectsInvertedBounds() {
        StructureDefinition s = valid();
        s.bounds.clear();
        s.bounds.add(new RoomBounds(6, 0, 0, 0, 3, 6));
        assertTrue(StructureValidator.validate(s).hasErrors());
    }

    @Test
    void shopRoomRequiresShopkeeperMarker() {
        StructureDefinition s = valid();
        s.types.set(0, "SHOP");
        assertTrue(StructureValidator.validate(s).hasErrors(), "SHOP rooms need a SHOPKEEPER marker");
    }
}