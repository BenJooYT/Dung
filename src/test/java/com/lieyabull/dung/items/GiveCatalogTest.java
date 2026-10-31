package com.lieyabull.dung.items;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GiveCatalogTest {

    @Test
    void weaponCatalogHasAllBaseWeapons() {
        assertEquals(10, GiveCatalog.WEAPONS.size());
        Set<String> ids = new HashSet<>();
        for (GiveCatalog.WeaponBase base : GiveCatalog.WEAPONS) {
            assertNotNull(base.id());
            assertTrue(ids.add(base.id()), "duplicate weapon id: " + base.id());
            assertNotNull(base.name());
            assertNotNull(base.material());
            assertNotNull(base.ability());
            assertTrue(base.baseDamage() > 0);
        }
    }

    @Test
    void armorCatalogHasSixSetsAndFourPiecesEach() {
        assertEquals(6, GiveCatalog.ARMOR_SETS.size());
        for (GiveCatalog.ArmorSet set : GiveCatalog.ARMOR_SETS) {
            assertFalse(set.emberguard());
            for (int slot = 0; slot < 4; slot++) {
                assertNotNull(set.material(slot));
                assertNotNull(set.pieceName(slot));
            }
        }
        assertEquals(24, GiveCatalog.ARMOR_PIECES.size());
        assertEquals(28, GiveCatalog.ALL_ARMOR_PIECES.size());
        assertEquals(4, GiveCatalog.EMBERGUARD_PIECES.size());
    }

    @Test
    void emberguardIsSeparateAndEmberOnly() {
        GiveCatalog.ArmorPiece piece = GiveCatalog.armorPiece("emberguard", 0);
        assertNotNull(piece);
        assertTrue(piece.set().emberguard());
        assertEquals(GiveCatalog.EMBERGUARD, piece.set());
        assertTrue(GiveCatalog.ARMOR_PIECES.stream().noneMatch(p -> p.set().emberguard()));
    }

    @Test
    void lookupsResolveKnownAndUnknownIds() {
        assertEquals("doomblade", GiveCatalog.weapon("doomblade").id());
        assertNull(GiveCatalog.weapon("nope"));
        assertEquals("iron", GiveCatalog.armorSet("iron").id());
        assertEquals("emberguard", GiveCatalog.armorSet("emberguard").id());
        assertNull(GiveCatalog.armorSet("nope"));
        assertEquals(2, GiveCatalog.armorPiece("iron", 2).slot());
        assertNull(GiveCatalog.armorPiece("iron", 4));
    }

    @Test
    void manaShieldDescriptorIsComplete() {
        assertNotNull(GiveCatalog.MANA_SHIELD.id());
        assertEquals("Mana Shield", GiveCatalog.MANA_SHIELD.name());
        assertEquals(org.bukkit.Material.SHIELD, GiveCatalog.MANA_SHIELD.material());
    }
}
