package com.lieyabull.dung.items;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests for {@link Affix} — no Bukkit dependencies. */
public class AffixTest {

    @Test
    void appliesToHonorsKindMask() {
        assertTrue(Affix.VICIOUS.appliesTo("weapon"));
        assertFalse(Affix.VICIOUS.appliesTo("armor"));
        assertTrue(Affix.STURDY.appliesTo("armor"));
        assertFalse(Affix.STURDY.appliesTo("weapon"));
        // VITAL applies to both weapon and armor
        assertTrue(Affix.VITAL.appliesTo("weapon"));
        assertTrue(Affix.VITAL.appliesTo("armor"));
        assertFalse(Affix.VITAL.appliesTo("shield"));
        // AEGIS only shields
        assertTrue(Affix.AEGIS.appliesTo("shield"));
        assertFalse(Affix.AEGIS.appliesTo("weapon"));
        // null kind is never eligible
        assertFalse(Affix.VICIOUS.appliesTo(null));
    }

    @Test
    void poolForFiltersByKind() {
        List<Affix> weaponPool = Affix.poolFor("weapon");
        assertFalse(weaponPool.isEmpty());
        for (Affix a : weaponPool) assertTrue(a.appliesTo("weapon"));
        List<Affix> shieldPool = Affix.poolFor("shield");
        assertFalse(shieldPool.isEmpty());
        for (Affix a : shieldPool) assertTrue(a.appliesTo("shield"));
    }

    @Test
    void countForScalesWithRarity() {
        assertEquals(0, Affix.countFor(Rarity.COMMON));
        assertEquals(1, Affix.countFor(Rarity.UNCOMMON));
        assertEquals(1, Affix.countFor(Rarity.RARE));
        assertEquals(2, Affix.countFor(Rarity.EPIC));
        assertEquals(2, Affix.countFor(Rarity.LEGENDARY));
        assertEquals(3, Affix.countFor(Rarity.MYTHIC));
        assertEquals(0, Affix.countFor(null));
    }

    @Test
    void valueScalesWithRarityMult() {
        int common = Affix.VICIOUS.valueFor(Rarity.COMMON);
        int mythic = Affix.VICIOUS.valueFor(Rarity.MYTHIC);
        assertTrue(mythic > common, "MYTHIC value should exceed COMMON value");
        // COMMON statMult is 1.0 so value == base
        assertEquals(Affix.VICIOUS.base, common);
    }

    @Test
    void rollProducesNoMoreThanPoolAndDistinct() {
        for (Rarity r : Rarity.values()) {
            List<Affix.AffixRoll> rolls = Affix.roll(r, "weapon", new Random(1));
            int expected = Affix.countFor(r);
            int poolSize = Affix.poolFor("weapon").size();
            assertEquals(Math.min(expected, poolSize), rolls.size());
            // all distinct affixes
            long distinct = rolls.stream().map(x -> x.affix()).distinct().count();
            assertEquals(rolls.size(), distinct);
        }
    }

    @Test
    void rollNeverExceedsPoolForEmptyKind() {
        assertEquals(0, Affix.roll(Rarity.MYTHIC, "nonexistent", new Random(1)).size());
        assertEquals(0, Affix.roll(Rarity.MYTHIC, null, new Random(1)).size());
    }

    @Test
    void rollMaxedReturnsEveryKindEligibleAffixAtMaxValue() {
        List<Affix.AffixRoll> rolls = Affix.rollMaxed(Rarity.MYTHIC, "weapon");
        assertEquals(Affix.poolFor("weapon").size(), rolls.size());
        for (Affix.AffixRoll r : rolls) {
            assertEquals(r.affix().valueFor(Rarity.MYTHIC), r.value());
            assertTrue(r.affix().appliesTo("weapon"));
        }
        // empty pool for a bogus kind -> empty
        assertTrue(Affix.rollMaxed(Rarity.MYTHIC, "bogus").isEmpty());
    }

    @Test
    void serializeRoundTrips() {
        Affix.AffixRoll roll = new Affix.AffixRoll(Affix.VICIOUS, 7);
        String s = Affix.serialize(roll);
        assertEquals("vicious:7", s);
        assertEquals("vicious", com.lieyabull.dung.game.WorkstationRules.affixIdOf(s));
    }
}
