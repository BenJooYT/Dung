package com.lieyabull.dung.items;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GiveSpecTest {

    private static final UUID TARGET = UUID.randomUUID();

    private static GiveSpec sword() {
        return GiveSpec.weapon(GiveCatalog.weapon("longsword"), TARGET);
    }

    @Test
    void defaultsAreSafe() {
        GiveSpec spec = sword();
        assertEquals(GiveSpec.Category.WEAPON, spec.category());
        assertEquals("weapon", spec.kind());
        assertEquals(Rarity.COMMON, spec.rarity());
        assertEquals(0, spec.upgradeLevel());
        assertTrue(spec.affixes().isEmpty());
        assertFalse(spec.persistent());
        assertEquals(TARGET, spec.target());
    }

    @Test
    void upgradeLevelClampsToWorkstationMaximum() {
        assertEquals(com.lieyabull.dung.game.WorkstationRules.UPGRADE_MAX, GiveSpec.MAX_UPGRADE);
        GiveSpec spec = sword();
        spec.setUpgradeLevel(99);
        assertEquals(GiveSpec.MAX_UPGRADE, spec.upgradeLevel());
        spec.setUpgradeLevel(-5);
        assertEquals(0, spec.upgradeLevel());
        spec.setUpgradeLevel(3);
        assertEquals(3, spec.upgradeLevel());
    }

    @Test
    void normalGearRejectsEmberAndAcceptsNormalRarities() {
        GiveSpec spec = sword();
        assertTrue(spec.setRarity(Rarity.MYTHIC));
        assertEquals(Rarity.MYTHIC, spec.rarity());
        assertFalse(spec.setRarity(Rarity.EMBER));
        assertEquals(Rarity.MYTHIC, spec.rarity());
        for (Rarity r : Rarity.values()) {
            if (r != Rarity.EMBER) assertTrue(spec.supportsRarity(r), r.name());
        }
    }

    @Test
    void emberguardIsEmberOnly() {
        GiveSpec spec = GiveSpec.armor(GiveCatalog.armorPiece("emberguard", 1), TARGET);
        assertEquals(Rarity.EMBER, spec.rarity());
        assertTrue(spec.emberguard());
        assertTrue(spec.supportsRarity(Rarity.EMBER));
        for (Rarity r : Rarity.values()) {
            if (r != Rarity.EMBER) assertFalse(spec.supportsRarity(r), r.name());
        }
        assertFalse(spec.setRarity(Rarity.COMMON));
        assertEquals(Rarity.EMBER, spec.rarity());
    }

    @Test
    void affixesAreKindEligibleAndToggleable() {
        GiveSpec weapon = sword();
        assertTrue(weapon.toggleAffix(Affix.VICIOUS));
        assertTrue(weapon.hasAffix(Affix.VICIOUS));
        assertTrue(weapon.toggleAffix(Affix.VICIOUS));
        assertFalse(weapon.hasAffix(Affix.VICIOUS));
        assertFalse(weapon.toggleAffix(Affix.STURDY));

        GiveSpec armor = GiveSpec.armor(GiveCatalog.armorPiece("iron", 0), TARGET);
        assertTrue(armor.toggleAffix(Affix.STURDY));
        assertTrue(armor.toggleAffix(Affix.VITAL));
        assertFalse(armor.toggleAffix(Affix.VICIOUS));

        GiveSpec shield = GiveSpec.shield(GiveCatalog.MANA_SHIELD, TARGET);
        assertTrue(shield.toggleAffix(Affix.AEGIS));
        assertFalse(shield.toggleAffix(Affix.VITAL));
    }

    @Test
    void affixValuesFollowCurrentRarity() {
        GiveSpec spec = sword();
        spec.toggleAffix(Affix.VICIOUS);
        assertEquals(2, spec.affixRolls().get(0).value());
        spec.setRarity(Rarity.EPIC);
        assertEquals((int) Math.round(Affix.VICIOUS.base * Rarity.EPIC.statMult),
                spec.affixRolls().get(0).value());
    }

    @Test
    void randomAffixesRespectRarityAndKind() {
        GiveSpec common = sword();
        common.randomizeAffixes(new Random(1));
        assertTrue(common.affixes().isEmpty());

        GiveSpec mythic = sword();
        mythic.setRarity(Rarity.MYTHIC);
        mythic.randomizeAffixes(new Random(2));
        assertFalse(mythic.affixes().isEmpty());
        assertTrue(mythic.affixes().size() <= Affix.countFor(Rarity.MYTHIC));
        for (Affix affix : mythic.affixes()) {
            assertTrue(affix.appliesTo("weapon"));
        }
    }

    @Test
    void clearAffixesEmptiesSelection() {
        GiveSpec spec = sword();
        spec.toggleAffix(Affix.ARCANE);
        spec.clearAffixes();
        assertTrue(spec.affixes().isEmpty());
    }

    @Test
    void targetCanBeUpdated() {
        UUID other = UUID.randomUUID();
        GiveSpec spec = sword();
        spec.setTarget(other);
        assertEquals(other, spec.target());
    }

    @Test
    void persistentToggleIsIndependent() {
        GiveSpec spec = sword();
        spec.togglePersistent();
        assertTrue(spec.persistent());
        spec.setPersistent(false);
        assertFalse(spec.persistent());
    }
}
