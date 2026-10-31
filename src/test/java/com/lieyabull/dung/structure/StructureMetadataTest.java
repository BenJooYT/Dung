package com.lieyabull.dung.structure;

import com.lieyabull.dung.room.RoomBounds;
import com.lieyabull.dung.room.RoomMarker;
import com.lieyabull.dung.room.RoomMarkerType;
import com.lieyabull.dung.room.SpawnFloor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trips the &lt;id&gt;.yml metadata contract through SnakeYAML. */
class StructureMetadataTest {

    @Test
    void dumpThenLoadPreservesIdentityAndTypes() {
        StructureDefinition s = new StructureDefinition();
        s.id = "stairs_room";
        s.types.add("COMBAT");
        s.types.add("ELITE");
        s.description = "a stairwell";
        s.schematic = "stairs.schem";
        s.bounds.add(new RoomBounds(0, 0, 0, 8, 5, 8));
        s.spawnFloors.add(new SpawnFloor(1, 1, 1, 7, 1, 7));
        s.markers.add(new RoomMarker(RoomMarkerType.PLAYER_SPAWN, 4, 1, 4, "spawn"));

        String yaml = StructureMetadata.dump(s);
        StructureDefinition back = StructureMetadata.load(yaml);

        assertEquals("stairs_room", back.id);
        assertEquals(java.util.List.of("COMBAT", "ELITE"), back.types);
        assertEquals("stairs.schem", back.schematic);
        assertEquals(1, back.markers.size());
        assertEquals(RoomMarkerType.PLAYER_SPAWN, back.markers.get(0).type);
    }

    @Test
    void loadDefaultsMissingFields() {
        StructureDefinition s = StructureMetadata.load("id: bare_room\ntypes: [COMBAT]\n");
        assertEquals("bare_room", s.id);
        assertTrue(s.allowedRotations.contains(0), "allowed-rotations defaults to all four");
        assertEquals("structure.schem", s.schematic, "schematic defaults to structure.schem");
        assertFalse(s.doorsConfigured);
        assertEquals(0, s.doorFloorY(0));
    }

    @Test
    void doorHeightsRoundTrip() {
        StructureDefinition s = new StructureDefinition();
        s.id = "updown";
        s.types.add("COMBAT");
        s.bounds.add(new RoomBounds(0, 0, 0, 12, 8, 12));
        s.doorsConfigured = true;
        s.door(0).floorY = 0;
        s.door(0).height = 4;
        s.door(1).floorY = 3;
        s.door(1).height = 4;

        String yaml = StructureMetadata.dump(s);
        assertTrue(yaml.contains("doors:"), yaml);
        StructureDefinition back = StructureMetadata.load(yaml);
        assertTrue(back.doorsConfigured);
        assertEquals(0, back.doorFloorY(0));
        assertEquals(4, back.doorHeight(0));
        assertEquals(3, back.doorFloorY(1));
        assertEquals(4, back.doorHeight(1));
    }
}