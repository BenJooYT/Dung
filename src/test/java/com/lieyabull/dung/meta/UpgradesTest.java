package com.lieyabull.dung.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic tests for {@link Upgrades} — no Bukkit dependencies.
 */
public class UpgradesTest {

    // ---- byId() ----

    @Test
    void byIdReturnsDamageTrack() {
        Upgrades.Track t = Upgrades.byId("damage");
        assertNotNull(t);
        assertEquals("damage", t.id());
        assertEquals("Permanent Damage", t.label());
        assertEquals(3, t.baseCost());
        assertEquals(2, t.costPerLevel());
        assertEquals(10, t.maxLevel());
    }

    @Test
    void byIdReturnsHeartsTrack() {
        Upgrades.Track t = Upgrades.byId("hearts");
        assertNotNull(t);
        assertEquals("hearts", t.id());
        assertEquals("Max Hearts", t.label());
        assertEquals(4, t.baseCost());
        assertEquals(3, t.costPerLevel());
        assertEquals(10, t.maxLevel());
    }

    @Test
    void byIdReturnsDefenseTrack() {
        Upgrades.Track t = Upgrades.byId("defense");
        assertNotNull(t);
        assertEquals("defense", t.id());
        assertEquals("Defense", t.label());
        assertEquals(5, t.baseCost());
        assertEquals(4, t.costPerLevel());
        assertEquals(10, t.maxLevel());
    }

    @Test
    void byIdReturnsCritTrack() {
        Upgrades.Track t = Upgrades.byId("crit");
        assertNotNull(t);
        assertEquals("crit", t.id());
        assertEquals("Crit Chance", t.label());
        assertEquals(4, t.baseCost());
        assertEquals(3, t.costPerLevel());
        assertEquals(10, t.maxLevel());
    }

    @Test
    void byIdReturnsSpeedTrack() {
        Upgrades.Track t = Upgrades.byId("speed");
        assertNotNull(t);
        assertEquals("speed", t.id());
        assertEquals("Move Speed", t.label());
        assertEquals(6, t.baseCost());
        assertEquals(5, t.costPerLevel());
        assertEquals(5, t.maxLevel());
    }

    @Test
    void byIdReturnsManaTrack() {
        Upgrades.Track t = Upgrades.byId("mana");
        assertNotNull(t);
        assertEquals("mana", t.id());
        assertEquals("Max Mana", t.label());
        assertEquals(3, t.baseCost());
        assertEquals(2, t.costPerLevel());
        assertEquals(10, t.maxLevel());
    }

    @Test
    void byIdReturnsNullForUnknownId() {
        assertNull(Upgrades.byId("unknown"));
        assertNull(Upgrades.byId(""));
        assertNull(Upgrades.byId("health"));
    }

    // ---- cost() ----

    @Test
    void costForDamageLevel0() {
        // baseCost=3, costPerLevel=2, level=0 -> 3 + 2*0 = 3
        assertEquals(3, Upgrades.cost(Upgrades.DAMAGE, 0));
    }

    @Test
    void costForDamageLevel1() {
        // baseCost=3, costPerLevel=2, level=1 -> 3 + 2*1 = 5
        assertEquals(5, Upgrades.cost(Upgrades.DAMAGE, 1));
    }

    @Test
    void costForDamageLevel5() {
        // baseCost=3, costPerLevel=2, level=5 -> 3 + 2*5 = 13
        assertEquals(13, Upgrades.cost(Upgrades.DAMAGE, 5));
    }

    @Test
    void costForHeartsLevel0() {
        assertEquals(4, Upgrades.cost(Upgrades.HEARTS, 0));
    }

    @Test
    void costForHeartsLevel1() {
        assertEquals(7, Upgrades.cost(Upgrades.HEARTS, 1));
    }

    @Test
    void costForHeartsLevel5() {
        assertEquals(19, Upgrades.cost(Upgrades.HEARTS, 5));
    }

    @Test
    void costForDefenseLevel0() {
        assertEquals(5, Upgrades.cost(Upgrades.DEFENSE, 0));
    }

    @Test
    void costForDefenseLevel1() {
        assertEquals(9, Upgrades.cost(Upgrades.DEFENSE, 1));
    }

    @Test
    void costForDefenseLevel5() {
        assertEquals(25, Upgrades.cost(Upgrades.DEFENSE, 5));
    }

    @Test
    void costForCritLevel0() {
        assertEquals(4, Upgrades.cost(Upgrades.CRIT, 0));
    }

    @Test
    void costForCritLevel1() {
        assertEquals(7, Upgrades.cost(Upgrades.CRIT, 1));
    }

    @Test
    void costForCritLevel5() {
        assertEquals(19, Upgrades.cost(Upgrades.CRIT, 5));
    }

    @Test
    void costForSpeedLevel0() {
        assertEquals(6, Upgrades.cost(Upgrades.SPEED, 0));
    }

    @Test
    void costForSpeedLevel1() {
        assertEquals(11, Upgrades.cost(Upgrades.SPEED, 1));
    }

    @Test
    void costForSpeedLevel5() {
        assertEquals(31, Upgrades.cost(Upgrades.SPEED, 5));
    }

    @Test
    void costForManaLevel0() {
        assertEquals(3, Upgrades.cost(Upgrades.MANA, 0));
    }

    @Test
    void costForManaLevel1() {
        assertEquals(5, Upgrades.cost(Upgrades.MANA, 1));
    }

    @Test
    void costForManaLevel5() {
        assertEquals(13, Upgrades.cost(Upgrades.MANA, 5));
    }

    // ---- delta() ----

    @Test
    void deltaForDamage() {
        assertEquals(2, Upgrades.delta(Upgrades.DAMAGE));
    }

    @Test
    void deltaForHearts() {
        assertEquals(10, Upgrades.delta(Upgrades.HEARTS));
    }

    @Test
    void deltaForDefense() {
        assertEquals(1, Upgrades.delta(Upgrades.DEFENSE));
    }

    @Test
    void deltaForMana() {
        assertEquals(10, Upgrades.delta(Upgrades.MANA));
    }

    @Test
    void deltaForSpeed() {
        assertEquals(5, Upgrades.delta(Upgrades.SPEED));
    }

    @Test
    void deltaForCrit() {
        assertEquals(0, Upgrades.delta(Upgrades.CRIT));
    }

    // ---- ALL list ----

    @Test
    void allContainsSixTracks() {
        assertEquals(6, Upgrades.ALL.size());
    }

    @Test
    void allContainsAllTracks() {
        assertTrue(Upgrades.ALL.contains(Upgrades.DAMAGE));
        assertTrue(Upgrades.ALL.contains(Upgrades.HEARTS));
        assertTrue(Upgrades.ALL.contains(Upgrades.DEFENSE));
        assertTrue(Upgrades.ALL.contains(Upgrades.CRIT));
        assertTrue(Upgrades.ALL.contains(Upgrades.SPEED));
        assertTrue(Upgrades.ALL.contains(Upgrades.MANA));
    }

    @Test
    void allTracksHaveUniqueIds() {
        long uniqueCount = Upgrades.ALL.stream()
                .map(Upgrades.Track::id)
                .distinct()
                .count();
        assertEquals(Upgrades.ALL.size(), uniqueCount);
    }

    @Test
    void byIdReturnsCorrectTrackForEachInAll() {
        for (Upgrades.Track t : Upgrades.ALL) {
            assertSame(t, Upgrades.byId(t.id()));
        }
    }
}